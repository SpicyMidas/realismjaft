package dev.agentbuddy.brain.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.agentbuddy.brain.fake.MutableClock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Lifecycle tests against {@link StubLlamaServer}. The stub is launched via a shell script, so POSIX only. */
@DisabledOnOs(OS.WINDOWS)
class ManagedServerTest {
    @TempDir
    Path dir;
    private final MutableClock clock = new MutableClock();
    private ManagedServer server;

    private static final ChatRequest HELLO = new ChatRequest(List.of(ChatMessage.user("hi")), null, "auto", 0.2, 10, 1);

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(dir.resolve("bin"));
        Files.createDirectories(dir.resolve("models"));
        Files.writeString(dir.resolve("models/test.gguf"), "x");
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
    }

    private void installStub() throws IOException {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String script = "#!/bin/sh\nexec '" + java + "' -cp '" + System.getProperty("java.class.path") + "' "
                + StubLlamaServer.class.getName() + " \"$@\"\n";
        Path bin = ManagedServer.binaryPath(dir);
        Files.writeString(bin, script);
        Files.setPosixFilePermissions(bin, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private ManagedServer newServer(String model) {
        LlmConfig cfg = LlmConfig.DEFAULTS.withModel(model);
        server = new ManagedServer(dir, new LlmConfig(cfg.model(), 0, 99, 4096, 0.2, 80, 10, false, 30, 10, "off", List.of()),
                clock, false);
        return server;
    }

    @Test
    void missingBinaryIsOneShortLine() {
        ManagedServer s = newServer("test.gguf");
        LlmException e = assertThrows(LlmException.class, () -> s.chat(HELLO));
        assertEquals("LLM binary missing.", e.userMessage());
        assertEquals(ManagedServer.State.FAILED, s.state());
    }

    @Test
    void missingModelIsOneShortLine() throws IOException {
        installStub();
        LlmException e = assertThrows(LlmException.class, () -> newServer("nope.gguf").chat(HELLO));
        assertEquals("LLM model missing.", e.userMessage());
    }

    @Test
    void startsLazilyWithExpectedFlagsAndKeyOutOfArgv() throws Exception {
        installStub();
        ManagedServer s = newServer("test.gguf");
        assertEquals(ManagedServer.State.STOPPED, s.state());
        assertFalse(Files.exists(dir.resolve("bin/stub-args.txt")), "must not start before first use");

        ChatResponse r = s.chat(HELLO); // stub returns 401 unless the env api key is sent as bearer
        assertEquals("stub", r.content());
        assertEquals(10, r.timings().promptN());
        assertEquals(ManagedServer.State.READY, s.state());

        List<String> argv = Files.readAllLines(dir.resolve("bin/stub-args.txt"));
        String joined = String.join(" ", argv);
        assertTrue(joined.contains("--host 127.0.0.1"), joined);
        assertTrue(joined.contains("--jinja"), joined);
        assertTrue(joined.contains("--reasoning off"), joined);
        assertTrue(joined.contains("-ngl 99") && joined.contains("-c 4096") && joined.contains("-np 1"), joined);
        assertFalse(joined.contains("api-key"), joined);
        assertTrue(Files.exists(dir.resolve("llama-server.pid")));
        assertTrue(Files.list(dir.resolve("logs")).anyMatch(p -> p.getFileName().toString().startsWith("llama-server-")));
    }

    @Test
    void stopsWhenIdleAndRestartsOnNextUse() throws Exception {
        installStub();
        ManagedServer s = newServer("test.gguf");
        s.chat(HELLO);
        long firstPid = s.pid().orElseThrow();
        ProcessHandle first = ProcessHandle.of(firstPid).orElseThrow();

        clock.advance(Duration.ofMinutes(9));
        assertFalse(s.checkIdle());
        clock.advance(Duration.ofMinutes(2));
        assertTrue(s.checkIdle());
        assertEquals(ManagedServer.State.STOPPED, s.state());
        first.onExit().get(10, TimeUnit.SECONDS);
        assertFalse(first.isAlive());
        assertFalse(Files.exists(dir.resolve("llama-server.pid")));

        assertEquals("stub", s.chat(HELLO).content());
        assertNotEquals(firstPid, s.pid().orElseThrow());
    }

    @Test
    void crashWhileLoadingIsReported() throws IOException {
        installStub();
        Files.writeString(dir.resolve("models/crash.gguf"), "x");
        LlmException e = assertThrows(LlmException.class, () -> newServer("crash.gguf").chat(HELLO));
        assertEquals("LLM server crashed.", e.userMessage());
        assertTrue(e.getMessage().contains("code 3"), e.getMessage());
    }

    @Test
    void serverThatDiedBetweenRequestsIsRestarted() throws Exception {
        installStub();
        ManagedServer s = newServer("test.gguf");
        s.chat(HELLO);
        ProcessHandle h = ProcessHandle.of(s.pid().orElseThrow()).orElseThrow();
        h.destroyForcibly();
        h.onExit().get(10, TimeUnit.SECONDS);
        assertEquals("stub", s.chat(HELLO).content());
    }

    @Test
    void closeKillsProcess() throws Exception {
        installStub();
        ManagedServer s = newServer("test.gguf");
        s.chat(HELLO);
        ProcessHandle h = ProcessHandle.of(s.pid().orElseThrow()).orElseThrow();
        s.close();
        h.onExit().get(10, TimeUnit.SECONDS);
        assertFalse(h.isAlive());
        assertEquals(ManagedServer.State.STOPPED, s.state());
    }

    @Test
    void staleProcessFromCrashedJvmIsKilledOnStart() throws Exception {
        installStub();
        // Simulate an orphan left by a JVM that died without its shutdown hook.
        int port;
        try (java.net.ServerSocket ss = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            port = ss.getLocalPort();
        }
        Process orphan = new ProcessBuilder(ManagedServer.binaryPath(dir).toString(), "-m", "x", "--port", Integer.toString(port))
                .directory(dir.toFile()).start();
        OpenAiHttp probe = new OpenAiHttp(java.net.URI.create("http://127.0.0.1:" + port), null, Duration.ofSeconds(2));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (probe.getStatus("/health", Duration.ofSeconds(1)) != 200 && System.nanoTime() < deadline) Thread.sleep(100);
        assertTrue(orphan.isAlive(), "orphan must be running before the test start");
        Instant start = orphan.toHandle().info().startInstant().orElseThrow();
        Files.writeString(dir.resolve("llama-server.pid"), orphan.pid() + " " + start.toEpochMilli());

        newServer("test.gguf").chat(HELLO);
        assertTrue(orphan.waitFor(10, TimeUnit.SECONDS), "orphan should have been killed");
    }

    @Test
    void pidFileWithReusedPidIsNotKilled() throws Exception {
        Process unrelated = new ProcessBuilder("sleep", "30").start();
        try {
            Files.writeString(dir.resolve("llama-server.pid"), unrelated.pid() + " 12345");
            newServer("test.gguf").killStaleProcess();
            assertTrue(unrelated.isAlive(), "different start time means a different process");
            assertFalse(Files.exists(dir.resolve("llama-server.pid")));
        } finally {
            unrelated.destroyForcibly();
        }
    }
}
