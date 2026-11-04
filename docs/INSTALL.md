# Installing Agent Buddy (Windows)

Current build: **0.2.0 = milestone M2**. The Agent can be crafted, placed, picked up again, and follows you around.
Talking to it (`@agent ...`), the GUI and the tools come in M3–M7, so the local LLM is not used yet.

## 1. Minecraft 1.21.1 + NeoForge
1. In the Minecraft Launcher: **Installations → New installation → Version: release 1.21.1** → Create, then Play once and quit. This downloads 1.21.1.
2. Download the NeoForge installer:
   https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.265/neoforge-21.1.265-installer.jar
3. Run it (double-click, or `java -jar neoforge-21.1.265-installer.jar`), choose **Install client** and keep the default folder (`%APPDATA%\.minecraft`).
4. In the launcher, the profile **NeoForge 21.1.265** now exists. Under Edit → More options → JVM arguments, set `-Xmx4G` (the spec keeps the game heap at about 4 GB, so the model gets the VRAM).

## 2. The mod
Copy `agentbuddy-0.2.0.jar` into `%APPDATA%\.minecraft\mods\` (create the folder if needed) and start the NeoForge profile. **Mods** on the title screen should list "Agent Buddy 0.2.0".

To build the jar yourself, run `gradlew.bat build` in the repo. Output: `build\libs\agentbuddy-<version>.jar`.

## 3. In game (M2)
- **Get the Agent:** Creative inventory → Tools & Utilities, or craft it:
  ```
  .  S  .      S = spyglass
  I  R  I      R = redstone
  I  .  I      I = iron ingot
  ```
- **Place:** right-click a block with the item. The Agent belongs to whoever places a fresh item.
- **Pick up:** sneak (Shift) + right-click the Agent. Only its owner can. It becomes the item again, keeping its identity (name and, later, waystones and ignore list).
- **Follows you**, flying, and teleports to you if you get more than 24 blocks away. It doesn't despawn.
- If it dies, it drops its item, so it is never lost.

## 4. Local LLM (needed from M7 on; you can set it up now)
From the repo folder, put the LLM next to your real game install:

```
scripts\setup-local-llm.cmd -GameDir "%APPDATA%\.minecraft"
```

This creates `%APPDATA%\.minecraft\localLLM\{bin,models,logs}`, installs llama-server b12110 (CUDA 12 build on an NVIDIA GPU), and downloads Qwen3.5-4B Q4_K_M (2.55 GB, asks first). Every file is checked by size and sha256.

## Known limitations of 0.2.0
- The model and texture are placeholders defined in code, not a Blockbench model.
- Agents created with `/summon` have no owner and cannot be picked up.
- Only tested in the dev environment (`gradlew runClient`, game tests), not through the real launcher. If the profile fails to start, send `%APPDATA%\.minecraft\logs\latest.log`.
