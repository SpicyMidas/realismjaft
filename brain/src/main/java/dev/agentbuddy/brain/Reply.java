package dev.agentbuddy.brain;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.llm.ChatResponse;
import dev.agentbuddy.brain.tool.ToolResult;
import java.util.List;

/**
 * What the Agent says, plus a trace for logs and tests.
 *
 * @param tool    tool that ran, or null for a free-text reply
 * @param failure internal detail when something went wrong (never shown to the player), or null
 */
public record Reply(String text, String tool, JsonObject args, ToolResult result, List<Step> steps, String failure) {
    public Reply {
        steps = List.copyOf(steps);
    }

    /** One LLM round trip. {@code error} is the validation/parse problem that caused a retry, if any. */
    public record Step(String toolChoice, ChatResponse response, ToolCallParser.Source source, String error) {
    }
}
