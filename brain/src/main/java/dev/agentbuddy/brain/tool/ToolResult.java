package dev.agentbuddy.brain.tool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outcome of running a tool. Failures are explicit: {@code ok=false} plus a {@code reason} code, so the reply can
 * state the absence directly instead of guessing.
 *
 * @param data template values (positions, counts, names); never contains ignored blocks
 */
public record ToolResult(boolean ok, String reason, Map<String, Object> data) {
    // Reasons shared by all tools.
    public static final String COOLDOWN = "cooldown";
    public static final String NO_CREDITS = "no_credits";
    public static final String NOT_FOUND = "not_found";
    public static final String NOTHING_IN_SIGHT = "nothing_in_sight";
    public static final String OUT_OF_RANGE = "out_of_range";
    public static final String NO_AGENT = "no_agent";

    public ToolResult {
        if (!ok && reason == null) throw new IllegalArgumentException("failed result needs a reason");
        data = Map.copyOf(data);
    }

    public static ToolResult ok(Map<String, Object> data) {
        return new ToolResult(true, null, data);
    }

    public static ToolResult fail(String reason, Map<String, Object> data) {
        return new ToolResult(false, reason, data);
    }

    public static ToolResult fail(String reason) {
        return fail(reason, Map.of());
    }

    /** Small builder for the data map, keeping insertion order readable in logs. */
    public static Map<String, Object> data(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    public String str(String key) {
        Object v = data.get(key);
        return v == null ? "" : v.toString();
    }

    public int integer(String key) {
        Object v = data.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }
}
