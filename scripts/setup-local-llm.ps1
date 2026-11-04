<#
.SYNOPSIS
  Creates <GameDir>/localLLM/{bin,models,logs} and fetches a pinned llama-server build and GGUF model(s).

.DESCRIPTION
  Everything downloaded is pinned (llama.cpp release tag, Hugging Face commit) and checked against the
  size and sha256 recorded below. When the GitHub / Hugging Face APIs are reachable, their published
  digests are cross-checked too. TLS verification is never disabled.
  Asks before every download larger than 100 MB unless -Yes is given.

  Works in Windows PowerShell 5.1 and PowerShell 7 (Windows or Linux).

.EXAMPLE
  scripts\setup-local-llm.cmd
  scripts\setup-local-llm.cmd -Models both
  scripts\setup-local-llm.cmd -GameDir "$env:APPDATA\.minecraft" -Backend vulkan
#>
[CmdletBinding()]
param(
    # Game directory that will contain localLLM/. Default: <repo>/run (the dev client's game dir).
    [string]$GameDir,
    # auto: CUDA 12 if nvidia-smi is found, else Vulkan (Windows); CPU on Linux.
    [ValidateSet('auto', 'cuda12', 'vulkan', 'cpu')]
    [string]$Backend = 'auto',
    [ValidateSet('qwen', 'gemma', 'both', 'none')]
    [string]$Models = 'qwen',
    # Answer yes to every download prompt.
    [switch]$Yes,
    # Replace a llama-server in bin/ that this script did not install.
    [switch]$Force,
    # Only for testing the Windows asset path from another OS.
    [ValidateSet('auto', 'windows', 'linux')]
    [string]$Platform = 'auto'
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# ---------------------------------------------------------------------------------------------
# Pinned downloads. Sizes and hashes were measured from the actual files when the pin was set.
# ---------------------------------------------------------------------------------------------
$LlamaTag = 'b12110'
$LlamaBase = "https://github.com/ggml-org/llama.cpp/releases/download/$LlamaTag"
$LlamaApi = "https://api.github.com/repos/ggml-org/llama.cpp/releases/tags/$LlamaTag"

$LlamaAssets = @{
    'windows-cuda12' = @(
        @{ Name = "llama-$LlamaTag-bin-win-cuda-12.4-x64.zip"; Size = 287014657; Sha256 = '1ce755bc3a061889234048f88e33328e17971eaad73c4666cfeb26026b317fee' },
        @{ Name = 'cudart-llama-bin-win-cuda-12.4-x64.zip'; Size = 391443627; Sha256 = '8c79a9b226de4b3cacfd1f83d24f962d0773be79f1e7b75c6af4ded7e32ae1d6' }
    )
    'windows-vulkan' = @(
        @{ Name = "llama-$LlamaTag-bin-win-vulkan-x64.zip"; Size = 37661146; Sha256 = '9732a5e48887ca54d0bfe4af4324dc85fd56cd8cda19b52df3542fa6ebe6874f' }
    )
    'windows-cpu'    = @(
        @{ Name = "llama-$LlamaTag-bin-win-cpu-x64.zip"; Size = 24708445; Sha256 = '98901e327c252875accaf2d1bf364e10e868843b0b63468baad1aa945cdc5da2' }
    )
    'linux-cpu'      = @(
        @{ Name = "llama-$LlamaTag-bin-ubuntu-x64.tar.gz"; Size = 23051958; Sha256 = '368227edcc229be5f9bed16a2223d79b5e41d0ac2e19bcf5825e82291c060e55' }
    )
}

$ModelFiles = @{
    'qwen'  = @{
        Repo = 'unsloth/Qwen3.5-4B-GGUF'; Revision = 'e87f176479d0855a907a41277aca2f8ee7a09523'
        Name = 'Qwen3.5-4B-Q4_K_M.gguf'; Size = 2740937888
        Sha256 = '00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4'
    }
    'gemma' = @{
        Repo = 'unsloth/gemma-4-E2B-it-GGUF'; Revision = '0314792d7f1f7e229411f620751375812bb9faf2'
        Name = 'gemma-4-E2B-it-Q4_K_M.gguf'; Size = 3106738272
        Sha256 = '740185b21d22ceb83a11c3aa62ad5842ef32c70f6096d756bbee85a1e4ec34b8'
    }
}

$PromptThresholdBytes = 100MB

# ---------------------------------------------------------------------------------------------

function Test-OnWindows {
    if ($Platform -ne 'auto') { return $Platform -eq 'windows' }
    return [System.Environment]::OSVersion.Platform -eq [System.PlatformID]::Win32NT
}

function Format-Size([long]$Bytes) {
    if ($Bytes -ge 1GB) { return '{0:N2} GB' -f ($Bytes / 1GB) }
    return '{0:N1} MB' -f ($Bytes / 1MB)
}

function Get-CurlExe {
    # In Windows PowerShell 5.1 'curl' is an alias for Invoke-WebRequest; curl.exe ships with Windows 10+.
    foreach ($name in @('curl.exe', 'curl')) {
        $cmd = Get-Command $name -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($cmd) { return $cmd.Source }
    }
    throw 'curl was not found. Windows 10/11 ship curl.exe in System32; install curl or download the files manually (see below).'
}

function Confirm-Download([string]$What, [long]$Bytes) {
    if ($Bytes -lt $PromptThresholdBytes -or $Yes) { return $true }
    $answer = Read-Host "Download $What ($(Format-Size $Bytes))? [y/N]"
    return $answer -match '^(y|yes)$'
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Test-FileMatches([string]$Path, [long]$Size, [string]$Sha256) {
    if (-not (Test-Path -LiteralPath $Path)) { return $false }
    if ((Get-Item -LiteralPath $Path).Length -ne $Size) { return $false }
    return (Get-Sha256 $Path) -eq $Sha256
}

# Downloads $Url to $Dest via a .part file (resumable), then checks size and sha256.
function Get-VerifiedFile([string]$Url, [string]$Dest, [long]$Size, [string]$Sha256) {
    $part = "$Dest.part"
    if (Test-Path -LiteralPath $part) {
        $have = (Get-Item -LiteralPath $part).Length
        if ($have -gt $Size) { Remove-Item -LiteralPath $part }
    }
    $have = 0
    if (Test-Path -LiteralPath $part) { $have = (Get-Item -LiteralPath $part).Length }
    if ($have -lt $Size) {
        $curl = Get-CurlExe
        $curlArgs = @('--fail', '--location', '--retry', '3', '--retry-delay', '2', '--output', $part)
        if ($have -gt 0) { $curlArgs += @('--continue-at', '-') }
        $curlArgs += $Url
        & $curl @curlArgs
        if ($LASTEXITCODE -ne 0) { throw "curl failed (exit $LASTEXITCODE) for $Url" }
    }
    $got = (Get-Item -LiteralPath $part).Length
    if ($got -ne $Size) { throw "Size mismatch for $(Split-Path -Leaf $Dest): expected $Size bytes, got $got. The .part file was kept; rerun to resume." }
    $hash = Get-Sha256 $part
    if ($hash -ne $Sha256) {
        Remove-Item -LiteralPath $part
        throw "sha256 mismatch for $(Split-Path -Leaf $Dest): expected $Sha256, got $hash. The file was deleted."
    }
    Move-Item -LiteralPath $part -Destination $Dest -Force
}

# Cross-checks pinned values against GitHub's published asset digests. Unreachable API = warning only.
function Test-AgainstGitHub($Assets) {
    try {
        $release = Invoke-RestMethod -Uri $LlamaApi -Headers @{ 'Accept' = 'application/vnd.github+json' } -TimeoutSec 20
    } catch {
        Write-Warning "GitHub API unreachable ($($_.Exception.Message.Trim())). Relying on the pinned sha256 values only."
        return
    }
    foreach ($a in $Assets) {
        $published = $release.assets | Where-Object { $_.name -eq $a.Name } | Select-Object -First 1
        if (-not $published) { throw "Release $LlamaTag has no asset named $($a.Name)." }
        if ([long]$published.size -ne $a.Size) { throw "GitHub reports $($published.size) bytes for $($a.Name); pinned is $($a.Size)." }
        if ($published.digest -and $published.digest -ne "sha256:$($a.Sha256)") {
            throw "GitHub digest for $($a.Name) is $($published.digest); pinned is sha256:$($a.Sha256)."
        }
    }
    Write-Host "GitHub release metadata matches the pinned sizes/hashes."
}

function Test-AgainstHuggingFace($Model) {
    $api = "https://huggingface.co/api/models/$($Model.Repo)/tree/$($Model.Revision)"
    try {
        $tree = Invoke-RestMethod -Uri $api -TimeoutSec 20
    } catch {
        Write-Warning "Hugging Face API unreachable ($($_.Exception.Message.Trim())). Relying on the pinned sha256 only."
        return
    }
    $entry = $tree | Where-Object { $_.path -eq $Model.Name } | Select-Object -First 1
    if (-not $entry) { throw "$($Model.Repo)@$($Model.Revision) has no file $($Model.Name)." }
    if ([long]$entry.size -ne $Model.Size -or $entry.lfs.oid -ne $Model.Sha256) {
        throw "Hugging Face metadata for $($Model.Name) (size $($entry.size), sha256 $($entry.lfs.oid)) does not match the pinned values."
    }
}

function Get-NvidiaDriverVersion {
    $smi = Get-Command 'nvidia-smi' -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $smi) { return $null }
    $out = & $smi.Source '--query-gpu=driver_version,name' '--format=csv,noheader' 2>$null | Select-Object -First 1
    if ($LASTEXITCODE -ne 0 -or -not $out) { return $null }
    return $out.Trim()
}

function Resolve-BackendKey {
    if (-not (Test-OnWindows)) {
        if ($Backend -notin @('auto', 'cpu')) { throw "On Linux this script only installs the CPU build (-Backend cpu)." }
        return 'linux-cpu'
    }
    $choice = $Backend
    if ($choice -eq 'auto') {
        $driver = Get-NvidiaDriverVersion
        if ($driver) {
            Write-Host "NVIDIA GPU found: $driver"
            $choice = 'cuda12'
        } else {
            Write-Host 'No nvidia-smi found; using the Vulkan build.'
            $choice = 'vulkan'
        }
    }
    if ($choice -eq 'cuda12') {
        $driver = Get-NvidiaDriverVersion
        if ($driver) {
            $major = [int](($driver -split '[.,]')[0])
            # CUDA 12.x minor-version compatibility needs driver >= 528.33 on Windows.
            if ($major -lt 528) { Write-Warning "NVIDIA driver $driver is older than 528; the CUDA 12 build may not start. Update the driver or use -Backend vulkan." }
        }
    }
    return "windows-$choice"
}

# Extracts an archive into $BinDir, flattening a single top-level folder (the Linux tarballs have one).
function Expand-IntoBin([string]$Archive, [string]$BinDir, [string]$TempRoot) {
    $tmp = Join-Path $TempRoot ([System.IO.Path]::GetFileNameWithoutExtension($Archive) + '.x')
    if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Recurse -Force }
    New-Item -ItemType Directory -Path $tmp | Out-Null
    if ($Archive.EndsWith('.zip')) {
        Expand-Archive -LiteralPath $Archive -DestinationPath $tmp -Force
    } else {
        & tar -xzf $Archive -C $tmp
        if ($LASTEXITCODE -ne 0) { throw "tar failed for $Archive" }
    }
    $src = $tmp
    $entries = @(Get-ChildItem -LiteralPath $tmp -Force)
    if ($entries.Count -eq 1 -and $entries[0].PSIsContainer) { $src = $entries[0].FullName }
    Get-ChildItem -LiteralPath $src -Force | Copy-Item -Destination $BinDir -Recurse -Force
    Remove-Item -LiteralPath $tmp -Recurse -Force
}

function Write-ManualSteps($BinItems, $ModelItems, [string]$Root) {
    Write-Host ''
    Write-Host '--- Manual steps (network blocked or a download failed) ---'
    Write-Host "1. Create folders: $(Join-Path $Root 'bin'), $(Join-Path $Root 'models'), $(Join-Path $Root 'logs')"
    $n = 2
    foreach ($a in $BinItems) {
        Write-Host "$n. Download $LlamaBase/$($a.Name)"
        Write-Host "   Expected: $($a.Size) bytes, sha256 $($a.Sha256)"
        Write-Host "   Extract its contents directly into $(Join-Path $Root 'bin') (no subfolder)."
        $n++
    }
    foreach ($m in $ModelItems) {
        Write-Host "$n. Download https://huggingface.co/$($m.Repo)/resolve/$($m.Revision)/$($m.Name)"
        Write-Host "   Expected: $($m.Size) bytes, sha256 $($m.Sha256)"
        Write-Host "   Save as $(Join-Path (Join-Path $Root 'models') $m.Name)"
        $n++
    }
    Write-Host 'Check a hash with: Get-FileHash -Algorithm SHA256 <file>'
}

# ---------------------------------------------------------------------------------------------

if (-not $GameDir) { $GameDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'run' }
$root = Join-Path $GameDir 'localLLM'
$binDir = Join-Path $root 'bin'
$modelsDir = Join-Path $root 'models'
$logsDir = Join-Path $root 'logs'
$downloadsDir = Join-Path $root 'downloads'
foreach ($d in @($binDir, $modelsDir, $logsDir, $downloadsDir)) {
    if (-not (Test-Path -LiteralPath $d)) { New-Item -ItemType Directory -Path $d | Out-Null }
}
$root = (Resolve-Path -LiteralPath $root).Path
Write-Host "localLLM: $root"

if ($PSVersionTable.PSEdition -ne 'Core') {
    # Windows PowerShell 5.1: make sure TLS 1.2 is offered. Certificate validation is unchanged.
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
}

$selectedModels = @()
if ($Models -in @('qwen', 'both')) { $selectedModels += $ModelFiles['qwen'] }
if ($Models -in @('gemma', 'both')) { $selectedModels += $ModelFiles['gemma'] }

$configPath = Join-Path $root 'config.json'
if (-not (Test-Path -LiteralPath $configPath)) {
    $defaultModel = if ($selectedModels.Count -gt 0) { $selectedModels[0].Name } else { $ModelFiles['qwen'].Name }
    $config = [ordered]@{
        model              = $defaultModel
        port               = 0
        gpuLayers          = 99
        ctxSize            = 4096
        temperature        = 0.2
        maxTokens          = 80
        idleTimeoutMinutes = 10
        startOnWorldJoin   = $false
    }
    $config | ConvertTo-Json | Set-Content -LiteralPath $configPath -Encoding ASCII
    Write-Host "Wrote $configPath"
}

$backendKey = Resolve-BackendKey
$binAssets = $LlamaAssets[$backendKey]
$markerPath = Join-Path $binDir '.installed'
$marker = "$LlamaTag $backendKey"
$exeName = if ($backendKey -like 'windows-*') { 'llama-server.exe' } else { 'llama-server' }

try {
    $installed = if (Test-Path -LiteralPath $markerPath) { (Get-Content -LiteralPath $markerPath -Raw).Trim() } else { $null }
    if ($installed -eq $marker -and (Test-Path -LiteralPath (Join-Path $binDir $exeName))) {
        Write-Host "llama-server $marker already installed."
    } else {
        $foreign = (Test-Path -LiteralPath (Join-Path $binDir $exeName)) -and -not $installed
        if ($foreign -and -not $Force) {
            throw "bin/ already contains a llama-server this script did not install. Rerun with -Force to replace it."
        }
        Test-AgainstGitHub $binAssets
        $total = ($binAssets | Measure-Object -Property Size -Sum).Sum
        if (-not (Confirm-Download "llama.cpp $LlamaTag ($backendKey)" $total)) { throw 'Download declined.' }
        foreach ($a in $binAssets) {
            $dest = Join-Path $downloadsDir $a.Name
            if (Test-FileMatches $dest $a.Size $a.Sha256) {
                Write-Host "Have $($a.Name) (verified)."
            } else {
                Write-Host "Downloading $($a.Name) ($(Format-Size $a.Size))..."
                Get-VerifiedFile "$LlamaBase/$($a.Name)" $dest $a.Size $a.Sha256
                Write-Host "Verified $($a.Name)."
            }
        }
        Get-ChildItem -LiteralPath $binDir -Force | Remove-Item -Recurse -Force
        foreach ($a in $binAssets) { Expand-IntoBin (Join-Path $downloadsDir $a.Name) $binDir $downloadsDir }
        $exe = Join-Path $binDir $exeName
        if (-not (Test-Path -LiteralPath $exe)) { throw "$exeName not found in bin/ after extraction." }
        Set-Content -LiteralPath $markerPath -Value $marker -Encoding ASCII
        Write-Host "Installed llama-server $marker into $binDir"
        foreach ($a in $binAssets) { Remove-Item -LiteralPath (Join-Path $downloadsDir $a.Name) -Force }
    }

    foreach ($m in $selectedModels) {
        $dest = Join-Path $modelsDir $m.Name
        if ((Test-Path -LiteralPath $dest) -and (Get-Item -LiteralPath $dest).Length -eq $m.Size) {
            Write-Host "Checking existing $($m.Name)..."
            if ((Get-Sha256 $dest) -eq $m.Sha256) { Write-Host "Have $($m.Name) (verified)."; continue }
            Write-Warning "$($m.Name) exists but its sha256 does not match; downloading again."
            Remove-Item -LiteralPath $dest
        }
        Test-AgainstHuggingFace $m
        if (-not (Confirm-Download "$($m.Name) from $($m.Repo)" $m.Size)) { Write-Host "Skipped $($m.Name)."; continue }
        Write-Host "Downloading $($m.Name) ($(Format-Size $m.Size))..."
        Get-VerifiedFile "https://huggingface.co/$($m.Repo)/resolve/$($m.Revision)/$($m.Name)" $dest $m.Size $m.Sha256
        Write-Host "Verified $($m.Name)."
    }
} catch {
    Write-Host ''
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    Write-ManualSteps $binAssets $selectedModels $root
    exit 1
}

if ((Get-ChildItem -LiteralPath $downloadsDir -Force | Measure-Object).Count -eq 0) { Remove-Item -LiteralPath $downloadsDir }
Write-Host 'Done.'
