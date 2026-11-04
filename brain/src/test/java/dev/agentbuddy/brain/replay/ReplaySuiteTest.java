package dev.agentbuddy.brain.replay;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import dev.agentbuddy.brain.llm.LlmConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/**
 * Replay eval against a real llama-server: {@code gradlew :brain:replay [-Pllm.model=all|<file>] [-Pllm.external=url]}.
 * Every case is its own test, so failures show up individually; the summary table is written at the end to
 * {@code brain/build/reports/replay/summary.md} and to {@code localLLM/logs/}.
 */
@Tag("replay")
class ReplaySuiteTest {
    private static final List<ModelRun> RUNS = new ArrayList<>();
    private static Path llmDir;

    @TestFactory
    Stream<DynamicNode> replay() throws IOException {
        llmDir = Path.of(System.getProperty("llm.dir", "../run/localLLM")).toAbsolutePath().normalize();
        LlmConfig config = LlmConfig.load(llmDir.resolve("config.json"));
        ReplayCase.Suite suite = ReplayCase.load();
        String external = System.getProperty("llm.external", "");
        Set<String> only = Arrays.stream(System.getProperty("llm.cases", "").split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        List<ReplayCase> cases = suite.cases().stream().filter(c -> only.isEmpty() || only.contains(c.id())).toList();
        List<String> models = external.isBlank() ? models(config, System.getProperty("llm.model", "")) : List.of(external);
        return models.stream().map(m -> dynamicContainer(m, nodes(new ModelRun(m, llmDir, config, external), cases, suite)));
    }

    private List<String> models(LlmConfig config, String which) throws IOException {
        if (which.isBlank()) return List.of(config.model());
        if (!which.equals("all")) return List.of(which);
        try (Stream<Path> files = Files.list(llmDir.resolve("models"))) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".gguf") && !n.startsWith("mmproj"))
                    .sorted().toList();
        }
    }

    private Stream<DynamicNode> nodes(ModelRun run, List<ReplayCase> cases, ReplayCase.Suite suite) {
        List<DynamicNode> n = new ArrayList<>();
        n.add(dynamicTest("start server", () -> {
            RUNS.add(run);
            run.start();
        }));
        n.add(dynamicTest("protocol: thinking off (rendered prompt)", () -> assertCheck(run, run::checkTemplateThinkingOff)));
        n.add(dynamicTest("protocol: thinking off (no reasoning_content)", () -> assertCheck(run, run::checkResponseThinkingOff)));
        n.add(dynamicTest("protocol: structured tool_calls", () -> assertCheck(run, run::checkStructuredToolCall)));
        for (ReplayCase c : cases) {
            n.add(dynamicTest(c.group() + "/" + c.id(), () -> {
                assertTrue(run.started, "server did not start");
                ModelRun.Outcome o = run.runCase(c, suite.baseWorld());
                assertTrue(o.pass(), "\"" + c.say() + "\" -> \"" + o.reply().text() + "\" " + o.failures());
            }));
        }
        n.add(dynamicTest("stop server", run::stop));
        n.add(dynamicTest("control: --reasoning auto (flag is load-bearing)", () -> assertCheck(run, run::checkControlReasoningAuto)));
        return n.stream();
    }

    private interface CheckFn {
        ModelRun.Check run() throws Exception;
    }

    private static void assertCheck(ModelRun run, CheckFn fn) throws Exception {
        assertTrue(run.started, "server did not start");
        ModelRun.Check c = fn.run();
        assertTrue(c.pass(), c.name() + ": " + c.detail());
    }

    @AfterAll
    static void writeReport() throws IOException {
        if (RUNS.isEmpty()) return;
        String md = ReplayReport.render(RUNS);
        Path dir = Path.of(System.getProperty("replay.reportDir", "build/reports/replay"));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("summary.md"), md);
        Files.createDirectories(llmDir.resolve("logs"));
        Files.writeString(llmDir.resolve("logs").resolve("replay-summary-"
                + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".md"), md);
        System.out.println(md);
    }
}
