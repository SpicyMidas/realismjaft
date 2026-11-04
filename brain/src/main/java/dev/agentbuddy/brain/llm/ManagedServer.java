package dev.agentbuddy.brain.llm;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns a llama-server process from {@code <llmDir>/bin}, started lazily on the first request.
 *
 * <p>The process is stopped after {@code idleTimeoutMinutes} without requests (to give VRAM back to the game),
 * on {@link #close()}, and by a JVM shutdown hook. A hard JVM kill skips the hook; the next start then kills the
 * orphan via the pid file (pid + process start time, so a reused pid is never killed).
 */
public final class ManagedServer implements LlmBackend, AutoCloseable {
    public enum State { STOPPED, STARTING, READY, FAILED }

    private static final Logger LOG = LoggerFactory.getLogger(ManagedServer.class);
    private static final Duration HEALTH_POLL = Duration.ofMillis(250);
    private static final Duration STOP_GRACE = Duration.ofSeconds(5);

    private final Path llmDir;
    private final LlmConfig config;
    private final Clock clock;
    // Held while starting/stopping and for each request, so idle shutdown never kills an in-flight request.
    private final ReentrantLock lock = new ReentrantLock();
    private final ScheduledExecutorService idleTimer;

    private volatile State state = State.STOPPED;
    private volatile String lastFailure;
    private volatile Process process;
    private volatile Instant lastUse;
    private OpenAiHttp http;
    private String apiKey;
    private Path logFile;
    private Thread shutdownHook;

    public ManagedServer(Path llmDir, LlmConfig config) {
        this(llmDir, config, Clock.systemUTC(), true);
    }

    /** @param scheduleIdleCheck false in tests, which call {@link #checkIdle()} directly with a fake clock */
    ManagedServer(Path llmDir, LlmConfig config, Clock clock, boolean scheduleIdleCheck) {
        this.llmDir = llmDir;
        this.config = config;
        this.clock = clock;
        if (scheduleIdleCheck && config.idleTimeoutMinutes() > 0) {
            this.idleTimer = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "agentbuddy-llm-idle");
                t.setDaemon(true);
                return t;
            });
            long periodSeconds = Math.max(5, Math.min(30, config.idleTimeoutMinutes() * 60L / 4));
            idleTimer.scheduleAtFixedRate(this::checkIdle, periodSeconds, periodSeconds, TimeUnit.SECONDS);
        } else {
            this.idleTimer = null;
        }
    }

    public static Path binaryPath(Path llmDir) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
        return llmDir.resolve("bin").resolve(windows ? "llama-server.exe" : "llama-server");
    }

    public State state() {
        return state;
    }

    /** Detail of the last failure (for logs), or null. */
    public String lastFailure() {
        return lastFailure;
    }

    public Path logFile() {
        return logFile;
    }

    /** HTTP client of the running server, for diagnostics; null when not running. */
    public OpenAiHttp http() {
        return http;
    }

    public Optional<Long> pid() {
        Process p = process;
        return p == null ? Optional.empty() : Optional.of(p.pid());
    }

    @Override
    public ChatResponse chat(ChatRequest request) throws LlmException {
        lock.lock();
        try {
            ensureRunning();
            lastUse = clock.instant();
            try {
                return http.chat(request);
            } catch (LlmException e) {
                Process p = process;
                if (p != null && !p.isAlive()) {
                    String detail = "llama-server exited with code " + p.exitValue() + " during a request; see " + logFile;
                    stopLocked();
                    state = State.FAILED;
                    lastFailure = detail;
                    throw new LlmException("LLM server stopped.", detail, e);
                }
                throw e;
            } finally {
                lastUse = clock.instant();
            }
        } finally {
            lock.unlock();
        }
    }

    /** Starts the server now instead of on the first request (e.g. on world join). */
    public void start() throws LlmException {
        lock.lock();
        try {
            ensureRunning();
        } finally {
            lock.unlock();
        }
    }

    /** Stops the server if it has been idle longer than the timeout. Returns true if it stopped it. */
    public boolean checkIdle() {
        if (state != State.READY || config.idleTimeoutMinutes() <= 0) return false;
        Instant last = lastUse;
        if (last == null || Duration.between(last, clock.instant()).toMinutes() < config.idleTimeoutMinutes()) return false;
        if (!lock.tryLock()) return false;
        try {
            if (state != State.READY) return false;
            LOG.info("Stopping llama-server after {} min idle", config.idleTimeoutMinutes());
            stopLocked();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            stopLocked();
        } finally {
            lock.unlock();
        }
        if (idleTimer != null) idleTimer.shutdownNow();
    }

    private void ensureRunning() throws LlmException {
        Process p = process;
        if (state == State.READY && p != null && p.isAlive()) return;
        if (p != null) stopLocked();
        startLocked();
    }

    private void startLocked() throws LlmException {
        state = State.STARTING;
        Path bin = binaryPath(llmDir);
        if (!Files.isRegularFile(bin)) throw fail("LLM binary missing.", "llama-server not found at " + bin);
        Path model = llmDir.resolve("models").resolve(config.model());
        if (!Files.isRegularFile(model)) throw fail("LLM model missing.", "model not found at " + model);

        killStaleProcess();

        int port;
        try {
            port = config.port() > 0 ? config.port() : freeLoopbackPort();
            Files.createDirectories(llmDir.resolve("logs"));
        } catch (IOException e) {
            throw fail("LLM failed to start.", "setup failed: " + e.getMessage());
        }
        apiKey = randomKey();
        logFile = llmDir.resolve("logs").resolve("llama-server-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".log");

        List<String> cmd = new ArrayList<>(List.of(bin.toString(),
                "-m", model.toString(),
                "--host", "127.0.0.1",
                "--port", Integer.toString(port),
                "--jinja",
                "--reasoning", config.reasoning(),
                "-ngl", Integer.toString(config.gpuLayers()),
                "-c", Integer.toString(config.ctxSize()),
                "-np", "1"));
        cmd.addAll(config.extraArgs());

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(bin.getParent().toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        // Passed via env, not argv, so other local processes cannot read it from the command line.
        // Without a key llama-server accepts requests from any web page (CORS allows all origins).
        pb.environment().put("LLAMA_API_KEY", apiKey);
        LOG.info("Starting llama-server: {} (log: {})", String.join(" ", cmd), logFile);
        try {
            process = pb.start();
        } catch (IOException e) {
            throw fail("LLM failed to start.", "could not launch " + bin + ": " + e.getMessage());
        }
        writePidFile(process);
        registerShutdownHook();
        http = new OpenAiHttp(URI.create("http://127.0.0.1:" + port), apiKey,
                Duration.ofSeconds(config.requestTimeoutSeconds()));
        waitUntilHealthy();
        state = State.READY;
        lastUse = clock.instant();
        LOG.info("llama-server ready on port {} (pid {})", port, process.pid());
    }

    private void waitUntilHealthy() throws LlmException {
        long deadline = System.nanoTime() + Duration.ofSeconds(config.startTimeoutSeconds()).toNanos();
        while (System.nanoTime() < deadline) {
            Process p = process;
            if (p == null || !p.isAlive()) {
                int code = p == null ? -1 : p.exitValue();
                throw fail("LLM server crashed.", "llama-server exited with code " + code + " while loading; see " + logFile);
            }
            if (http.getStatus("/health", Duration.ofSeconds(2)) == 200) return;
            try {
                Thread.sleep(HEALTH_POLL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw fail("LLM start cancelled.", "interrupted while waiting for /health");
            }
        }
        throw fail("LLM start timed out.", "no healthy /health within " + config.startTimeoutSeconds() + " s; see " + logFile);
    }

    private LlmException fail(String userMessage, String detail) {
        LOG.warn("llama-server: {}", detail);
        stopLocked();
        state = State.FAILED;
        lastFailure = detail;
        return new LlmException(userMessage, detail);
    }

    private void stopLocked() {
        Process p = process;
        process = null;
        http = null;
        if (p != null) terminate(p);
        deletePidFile();
        removeShutdownHook();
        state = State.STOPPED;
    }

    private static void terminate(Process p) {
        p.destroy();
        try {
            if (!p.waitFor(STOP_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
                p.destroyForcibly();
                p.waitFor(STOP_GRACE.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private Path pidFile() {
        return llmDir.resolve("llama-server.pid");
    }

    private void writePidFile(Process p) {
        long start = p.toHandle().info().startInstant().map(Instant::toEpochMilli).orElse(-1L);
        try {
            Files.writeString(pidFile(), p.pid() + " " + start);
        } catch (IOException e) {
            LOG.warn("Could not write {}: {}", pidFile(), e.getMessage());
        }
    }

    private void deletePidFile() {
        try {
            Files.deleteIfExists(pidFile());
        } catch (IOException e) {
            LOG.warn("Could not delete {}: {}", pidFile(), e.getMessage());
        }
    }

    /** Kills a llama-server left behind by a previous JVM that died without running its shutdown hook. */
    void killStaleProcess() {
        Path f = pidFile();
        if (!Files.exists(f)) return;
        try {
            String[] parts = Files.readString(f).trim().split("\\s+");
            long pid = Long.parseLong(parts[0]);
            long start = parts.length > 1 ? Long.parseLong(parts[1]) : -1;
            Optional<ProcessHandle> h = ProcessHandle.of(pid).filter(ProcessHandle::isAlive);
            boolean same = start > 0 && h.flatMap(x -> x.info().startInstant())
                    .map(i -> i.toEpochMilli() == start).orElse(false);
            if (same) {
                LOG.warn("Killing stale llama-server pid {} from a previous run", pid);
                ProcessHandle handle = h.get();
                handle.destroy();
                try {
                    handle.onExit().get(STOP_GRACE.toMillis(), TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    handle.destroyForcibly();
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warn("Ignoring unreadable {}: {}", f, e.getMessage());
        }
        deletePidFile();
    }

    private void registerShutdownHook() {
        if (shutdownHook != null) return;
        shutdownHook = new Thread(() -> {
            Process p = process;
            if (p != null) terminate(p);
            deletePidFile();
        }, "agentbuddy-llm-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void removeShutdownHook() {
        if (shutdownHook == null) return;
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down; the hook is running or has run.
        }
        shutdownHook = null;
    }

    private static int freeLoopbackPort() throws IOException {
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return s.getLocalPort();
        }
    }

    private static String randomKey() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }
}
