package dev.agentbuddy.brain.tool;

import com.google.gson.JsonObject;

/**
 * A game action the LLM (or the GUI) may request. All gameplay goes through this interface; the LLM never runs
 * raw commands. {@link #run} is called on the server thread with arguments already validated against the schema.
 */
public interface Tool {
    ToolSpec spec();

    ToolResult run(String player, JsonObject args);

    /** Fix common model quirks (e.g. "iron_ore" for "iron") before schema validation. */
    default JsonObject normalize(JsonObject args) {
        return args;
    }

    /** Credits this call costs. Override for costs that depend on the target (e.g. distance). */
    default int cost(String player, JsonObject args) {
        return spec().credits();
    }
}
