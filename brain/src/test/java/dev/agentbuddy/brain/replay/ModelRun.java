package dev.agentbuddy.brain.replay;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.agentbuddy.brain.Brain;
import dev.agentbuddy.brain.Reply;
import dev.agentbuddy.brain.fake.FakeTools;
import dev.agentbuddy.brain.fake.FakeWorld;
import dev.agentbuddy.brain.llm.ChatMessage;
import dev.agentbuddy.brain.llm.ChatRequest;
import dev.agentbuddy.brain.llm.ChatResponse;
import dev.agentbuddy.brain.llm.ExternalServer;
import dev.agentbuddy.brain.llm.LlmBackend;
import dev.agentbuddy.brain.llm.LlmConfig;
import dev.agentbuddy.brain.llm.LlmException;
import dev.agentbuddy.brain.llm.ManagedServer;
import dev.agentbuddy.brain.llm.OpenAiHttp;
import dev.agentbuddy.brain.tool.Cooldowns;
import dev.agentbuddy.brain.tool.ToolRegistry;
import dev.agentbuddy.brain.tool.ToolRunner;
import dev.agentbuddy.brain.tool.ToolSpecs;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** State and results for one model (or one external server) in the replay suite. */
final class ModelRun {
    record Outcome(ReplayCase c, Reply reply, List<String> failures) {
        boolean pass() {
            return failures.isEmpty();
        }
    }

    record Check(String name, boolean pass, String detail) {
    }

    static final int SEED = 42;
    private static final Pattern BUFFER = Pattern.compile("(\\S+)\\s+(model|KV|RS|compute) buffer size =\\s+([\\d.]+) MiB");

    final String label;
    final List<Outcome> outcomes = new ArrayList<>();
    final List<Check> checks = new ArrayList<>();
    final Map<String, Double> buffersMiB = new LinkedHashMap<>();
    final List<String> bufferDevices = new ArrayList<>();
    String buildInfo = "?";
    long startMillis;
    Integer vramBaseline;
    Integer vramPeak;
    String vramTotal;
    boolean started;

    private final Path llmDir;
    private final LlmConfig config;
    private final String external;
    private ManagedServer server;
    private LlmBackend backend;
    private OpenAiHttp http;
    private BufferedWriter jsonl;
    private static final Gson GSON = new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private final Gson gson = GSON;

    ModelRun(String label, Path llmDir, LlmConfig config, String external) {
        this.label = label;
        this.llmDir = llmDir;
        this.config = config;
        this.external = external;
    }

    void start() throws Exception {
        Vram.Sample before = Vram.sample();
        if (before != null) {
            vramBaseline = before.usedMiB();
            vramTotal = before.totalMiB() + " MiB " + before.name();
        }
        long t0 = System.nanoTime();
        if (external.isBlank()) {
            // -lv 4 makes llama.cpp log its buffer sizes, which the report uses as a memory estimate.
            List<String> extra = new ArrayList<>(config.extraArgs());
            extra.addAll(List.of("-lv", "4"));
            server = new ManagedServer(llmDir, config.withModel(label).withExtraArgs(extra));
            server.start();
            backend = server;
            http = server.http();
            parseBuffers(server.logFile());
        } else {
            ExternalServer ext = new ExternalServer(URI.create(external), System.getenv("LLM_API_KEY"), Duration.ofSeconds(config.requestTimeoutSeconds()));
            backend = ext;
            http = ext.http();
        }
        startMillis = (System.nanoTime() - t0) / 1_000_000;
        try {
            JsonObject props = http.getJson("/props");
            if (props.has("build_info")) buildInfo = props.get("build_info").getAsString();
        } catch (LlmException e) {
            buildInfo = "unavailable (" + e.getMessage() + ")";
        }
        sampleVram();
        Files.createDirectories(llmDir.resolve("logs"));
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        jsonl = Files.newBufferedWriter(llmDir.resolve("logs").resolve("replay-" + safe(label) + "-" + stamp + ".jsonl"), StandardCharsets.UTF_8);
        started = true;
    }

    void stop() throws IOException {
        if (jsonl != null) jsonl.close();
        if (server != null) server.close();
    }

    // --- protocol checks ---------------------------------------------------------------------------------------

    Check checkTemplateThinkingOff() throws LlmException {
        String prompt = renderedPrompt(http);
        boolean on = thinkingOn(prompt);
        return add(new Check("thinking off: rendered prompt", !on, "prompt ends " + tail(prompt)));
    }

    Check checkResponseThinkingOff() throws LlmException {
        ChatResponse r = backend.chat(new ChatRequest(List.of(ChatMessage.user("What is 2+2? Answer with one word.")),
                null, "auto", config.temperature(), 40, SEED));
        boolean ok = r.reasoningContent().isBlank() && !r.content().isBlank() && !r.content().contains("<think>");
        return add(new Check("thinking off: no reasoning_content", ok,
                "content=" + gson.toJson(r.content()) + " reasoning_chars=" + r.reasoningContent().length()));
    }

    Check checkStructuredToolCall() throws LlmException {
        JsonArray tools = new JsonArray();
        tools.add(ToolSpecs.FIND_ORE.toOpenAi());
        ChatResponse r = backend.chat(new ChatRequest(List.of(ChatMessage.user("Find the nearest iron ore.")),
                tools, "auto", config.temperature(), 80, SEED));
        boolean ok = !r.toolCalls().isEmpty() && "find_ore".equals(r.toolCalls().get(0).name());
        String detail = "finish_reason=" + r.finishReason() + " tool_calls="
                + (r.toolCalls().isEmpty() ? "[]" : r.toolCalls().get(0).name() + r.toolCalls().get(0).argumentsJson())
                + " content=" + gson.toJson(r.content());
        return add(new Check("tool calls come back as structured tool_calls", ok, detail));
    }

    /** Starts a second server without --reasoning off to show the flag is what disables thinking. */
    Check checkControlReasoningAuto() throws Exception {
        if (!external.isBlank()) return add(new Check("control: --reasoning auto", true, "skipped for external server"));
        try (ManagedServer control = new ManagedServer(llmDir, config.withModel(label).withReasoning("auto"))) {
            control.start();
            String prompt = renderedPrompt(control.http());
            boolean on = thinkingOn(prompt);
            return add(new Check("control: without --reasoning off thinking is on", on, "prompt ends " + tail(prompt)));
        }
    }

    private static String renderedPrompt(OpenAiHttp h) throws LlmException {
        JsonObject body = new JsonObject();
        JsonArray msgs = new JsonArray();
        JsonObject m = new JsonObject();
        m.addProperty("role", "user");
        m.addProperty("content", "hi");
        msgs.add(m);
        body.add("messages", msgs);
        return h.post("/apply-template", body).get("prompt").getAsString();
    }

    /** Qwen3.5 opens a think block; Gemma 4 puts a think token in the system turn. */
    static boolean thinkingOn(String prompt) {
        return prompt.endsWith("<think>\n") || prompt.contains("<|think|>");
    }

    private Check add(Check c) {
        checks.add(c);
        return c;
    }

    // --- cases -------------------------------------------------------------------------------------------------

    Outcome runCase(ReplayCase c, JsonObject baseWorld) throws IOException {
        FakeWorld w = c.buildWorld(baseWorld);
        ToolRunner runner = new ToolRunner(new ToolRegistry(FakeTools.all(w)), w.credits, new Cooldowns(Clock.systemUTC()));
        Brain brain = new Brain(backend, runner, Runnable::run,
                new Brain.Config(config.temperature(), config.maxTokens(), SEED, 4, 8, 4));
        Reply reply = brain.handle(w.player, c.say(), w.context());
        Outcome o = new Outcome(c, reply, c.check(reply));
        outcomes.add(o);
        sampleVram();
        writeJsonl(o, w);
        return o;
    }

    private void writeJsonl(Outcome o, FakeWorld w) throws IOException {
        JsonObject j = new JsonObject();
        j.addProperty("model", label);
        j.addProperty("id", o.c().id());
        j.addProperty("group", o.c().group());
        j.addProperty("say", o.c().say());
        j.addProperty("context", w.context().render());
        j.addProperty("reply", o.reply().text());
        j.addProperty("tool", o.reply().tool());
        if (o.reply().args() != null) j.add("args", o.reply().args());
        j.addProperty("pass", o.pass());
        j.add("failures", gson.toJsonTree(o.failures()));
        if (o.reply().failure() != null) j.addProperty("internal_failure", o.reply().failure());
        JsonArray steps = new JsonArray();
        for (Reply.Step s : o.reply().steps()) {
            JsonObject js = new JsonObject();
            js.addProperty("tool_choice", s.toolChoice());
            js.addProperty("source", s.source().name());
            if (s.error() != null) js.addProperty("error", s.error());
            js.addProperty("wall_ms", s.response().wallMillis());
            js.addProperty("finish_reason", s.response().finishReason());
            js.addProperty("content", s.response().content());
            JsonArray calls = new JsonArray();
            s.response().toolCalls().forEach(tc -> {
                JsonObject c = new JsonObject();
                c.addProperty("name", tc.name());
                c.addProperty("arguments", tc.argumentsJson());
                calls.add(c);
            });
            js.add("tool_calls", calls);
            if (s.response().timings() != null) js.add("timings", gson.toJsonTree(s.response().timings()));
            steps.add(js);
        }
        j.add("steps", steps);
        jsonl.write(j.toString());
        jsonl.newLine();
        jsonl.flush();
    }

    private void sampleVram() {
        Vram.Sample s = Vram.sample();
        if (s != null) vramPeak = vramPeak == null ? s.usedMiB() : Math.max(vramPeak, s.usedMiB());
    }

    private void parseBuffers(Path log) throws IOException {
        if (log == null || !Files.exists(log)) return;
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            Matcher m = BUFFER.matcher(line);
            if (!m.find()) continue;
            buffersMiB.merge(m.group(2), Double.parseDouble(m.group(3)), Double::sum);
            String dev = m.group(1);
            if (!bufferDevices.contains(dev)) bufferDevices.add(dev);
        }
    }

    private static String tail(String s) {
        String t = s.length() > 40 ? s.substring(s.length() - 40) : s;
        return GSON.toJson(t);
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    /** nvidia-smi sampling; null when there is no NVIDIA GPU or driver. */
    static final class Vram {
        record Sample(int usedMiB, int totalMiB, String name) {
        }

        static Sample sample() {
            try {
                Process p = new ProcessBuilder("nvidia-smi", "--query-gpu=memory.used,memory.total,name",
                        "--format=csv,noheader,nounits").redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() != 0 || out.isEmpty()) return null;
                String[] parts = out.split("\\R")[0].split(",\\s*");
                return new Sample(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), parts[2].trim());
            } catch (IOException | RuntimeException e) {
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }
}
