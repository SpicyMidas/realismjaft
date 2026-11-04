package dev.agentbuddy.brain.tool;

import com.google.gson.JsonObject;
import java.time.Duration;

/**
 * The single entry point for running tools, used by both the LLM path and the GUI.
 *
 * <p>{@link #validate} is pure (worker thread is fine). {@link #execute} touches game state and must run on the
 * server thread. Order of checks in execute: cooldown, credits, then the tool itself (which enforces its range).
 * Credits and cooldown are only applied when the tool succeeded, so "No iron within 64." or a mistyped waystone
 * name costs nothing and does not lock the tool.
 */
public final class ToolRunner {
    private final ToolRegistry registry;
    private final Credits credits;
    private final Cooldowns cooldowns;

    public ToolRunner(ToolRegistry registry, Credits credits, Cooldowns cooldowns) {
        this.registry = registry;
        this.credits = credits;
        this.cooldowns = cooldowns;
    }

    public ToolRegistry registry() {
        return registry;
    }

    public record Validated(Tool tool, JsonObject args) {
    }

    /** Either {@code call} or {@code error} is set. */
    public record Validation(Validated call, String error) {
        public boolean valid() {
            return call != null;
        }
    }

    public Validation validate(String toolName, JsonObject rawArgs) {
        Tool tool = registry.get(toolName).orElse(null);
        if (tool == null) return new Validation(null, "unknown tool " + toolName);
        JsonObject normalized = tool.normalize(rawArgs == null ? new JsonObject() : rawArgs.deepCopy());
        SchemaValidator.Result r = SchemaValidator.validate(tool.spec().parameters(), normalized);
        if (!r.valid()) return new Validation(null, toolName + ": " + String.join("; ", r.errors()));
        return new Validation(new Validated(tool, r.args()), null);
    }

    public ToolResult execute(String player, Validated call) {
        ToolSpec spec = call.tool().spec();
        Duration wait = cooldowns.remaining(player, spec.name());
        if (!wait.isZero()) {
            long seconds = (wait.toMillis() + 999) / 1000;
            return ToolResult.fail(ToolResult.COOLDOWN, ToolResult.data("seconds", seconds));
        }
        int cost = call.tool().cost(player, call.args());
        int have = credits.balance(player);
        if (have < cost) return ToolResult.fail(ToolResult.NO_CREDITS, ToolResult.data("need", cost, "have", have));
        ToolResult result = call.tool().run(player, call.args());
        if (result.ok()) {
            cooldowns.start(player, spec.name(), spec.cooldown());
            if (cost > 0) credits.tryDebit(player, cost);
        }
        return result;
    }
}
