package dev.agentbuddy.brain.replay;

import dev.agentbuddy.brain.Reply;
import dev.agentbuddy.brain.ToolCallParser;
import dev.agentbuddy.brain.llm.ChatResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Markdown summary of a replay run. */
final class ReplayReport {
    private ReplayReport() {
    }

    static String render(List<ModelRun> runs) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Brain replay results\n\n");
        sb.append("- Date: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        sb.append("- Host: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch"))
                .append(", ").append(Runtime.getRuntime().availableProcessors()).append(" CPUs, GPU: ")
                .append(runs.get(0).vramTotal == null ? "none detected (nvidia-smi unavailable)" : runs.get(0).vramTotal).append("\n");
        sb.append("- llama-server: ").append(runs.get(0).buildInfo).append("\n");
        sb.append("- Sampling: temperature/maxTokens from config.json, seed ").append(ModelRun.SEED).append("\n\n");

        sb.append("## Pass rate per group\n\n| Model |");
        for (String g : ReplayCase.GROUPS) sb.append(' ').append(g).append(" |");
        sb.append(" total |\n|---|");
        sb.append("---|".repeat(ReplayCase.GROUPS.size() + 1)).append('\n');
        for (ModelRun r : runs) {
            sb.append("| ").append(r.label).append(" |");
            int pass = 0;
            for (String g : ReplayCase.GROUPS) {
                List<ModelRun.Outcome> in = r.outcomes.stream().filter(o -> o.c().group().equals(g)).toList();
                long p = in.stream().filter(ModelRun.Outcome::pass).count();
                pass += (int) p;
                sb.append(' ').append(in.isEmpty() ? "-" : p + "/" + in.size()).append(" |");
            }
            sb.append(' ').append(pass).append('/').append(r.outcomes.size()).append(" |\n");
        }

        sb.append("\n## Latency and memory\n\n");
        sb.append("| Model | server start | LLM calls | per call median / p90 | per turn median | prompt tokens median | prompt tok/s | gen tok/s | VRAM (nvidia-smi) | llama.cpp buffers (MiB) |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (ModelRun r : runs) {
            List<Double> call = new ArrayList<>(), turn = new ArrayList<>(), prompt = new ArrayList<>(), pps = new ArrayList<>(), gps = new ArrayList<>();
            for (ModelRun.Outcome o : r.outcomes) {
                double t = 0;
                for (Reply.Step s : o.reply().steps()) {
                    call.add((double) s.response().wallMillis());
                    t += s.response().wallMillis();
                    ChatResponse.Timings tm = s.response().timings();
                    if (tm != null) {
                        prompt.add((double) (tm.promptN() + tm.cacheN()));
                        if (tm.promptN() >= 16) pps.add(tm.promptPerSecond());
                        if (tm.predictedN() >= 4) gps.add(tm.predictedPerSecond());
                    }
                }
                turn.add(t);
            }
            String vram = r.vramPeak == null ? "n/a (no NVIDIA GPU)"
                    : "peak " + r.vramPeak + " MiB, +" + (r.vramPeak - r.vramBaseline) + " over baseline";
            StringBuilder buf = new StringBuilder();
            r.buffersMiB.forEach((k, v) -> buf.append(buf.length() == 0 ? "" : ", ").append(k).append(' ').append(Math.round(v)));
            if (!r.bufferDevices.isEmpty()) buf.append(" on ").append(String.join("/", r.bufferDevices));
            sb.append("| ").append(r.label).append(" | ").append(r.startMillis).append(" ms | ").append(call.size())
                    .append(" | ").append(ms(median(call))).append(" / ").append(ms(pct(call, 0.9)))
                    .append(" | ").append(ms(median(turn)))
                    .append(" | ").append(Math.round(median(prompt)))
                    .append(" | ").append(String.format(Locale.ROOT, "%.0f", median(pps)))
                    .append(" | ").append(String.format(Locale.ROOT, "%.1f", median(gps)))
                    .append(" | ").append(vram).append(" | ").append(buf.length() == 0 ? "n/a" : buf).append(" |\n");
        }

        sb.append("\n## How the model answered\n\n| Model | structured tool_calls | recovered from text | malformed | free text | forced re-runs | corrective retries |\n|---|---|---|---|---|---|---|\n");
        for (ModelRun r : runs) {
            Map<ToolCallParser.Source, Integer> src = new EnumMap<>(ToolCallParser.Source.class);
            int forced = 0, corrective = 0;
            for (ModelRun.Outcome o : r.outcomes) {
                for (Reply.Step s : o.reply().steps()) {
                    src.merge(s.source(), 1, Integer::sum);
                    if ("required".equals(s.toolChoice())) forced++;
                    if (s.error() != null) corrective++;
                }
            }
            sb.append("| ").append(r.label).append(" | ").append(src.getOrDefault(ToolCallParser.Source.STRUCTURED, 0))
                    .append(" | ").append(src.getOrDefault(ToolCallParser.Source.RECOVERED, 0))
                    .append(" | ").append(src.getOrDefault(ToolCallParser.Source.MALFORMED, 0))
                    .append(" | ").append(src.getOrDefault(ToolCallParser.Source.NONE, 0))
                    .append(" | ").append(forced).append(" | ").append(corrective).append(" |\n");
        }

        sb.append("\n## Protocol checks\n\n");
        for (ModelRun r : runs) {
            sb.append("**").append(r.label).append("**\n\n");
            for (ModelRun.Check c : r.checks) {
                sb.append("- ").append(c.pass() ? "PASS" : "FAIL").append(" ").append(c.name()).append(": `")
                        .append(c.detail().replace("`", "'")).append("`\n");
            }
            sb.append('\n');
        }

        sb.append("## Failed cases\n\n");
        for (ModelRun r : runs) {
            List<ModelRun.Outcome> failed = r.outcomes.stream().filter(o -> !o.pass()).toList();
            sb.append("**").append(r.label).append("** (").append(failed.size()).append(")\n\n");
            for (ModelRun.Outcome o : failed) {
                sb.append("- `").append(o.c().id()).append("` \"").append(o.c().say()).append("\" -> \"")
                        .append(o.reply().text()).append("\" (tool: ").append(o.reply().tool()).append(") ")
                        .append(String.join("; ", o.failures())).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static double median(List<Double> v) {
        return pct(v, 0.5);
    }

    private static double pct(List<Double> v, double p) {
        if (v.isEmpty()) return 0;
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        int idx = (int) Math.ceil(p * s.size()) - 1;
        return s.get(Math.max(0, Math.min(s.size() - 1, idx)));
    }

    private static String ms(double v) {
        return v >= 10_000 ? String.format(Locale.ROOT, "%.1f s", v / 1000) : Math.round(v) + " ms";
    }
}
