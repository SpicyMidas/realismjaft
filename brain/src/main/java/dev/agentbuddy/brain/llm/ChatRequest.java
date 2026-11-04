package dev.agentbuddy.brain.llm;

import com.google.gson.JsonArray;
import java.util.List;

/**
 * OpenAI-style chat completion request.
 *
 * @param tools      OpenAI "tools" array (function definitions); empty for no tools
 * @param toolChoice "auto", "required" or "none"
 * @param seed       fixed sampling seed, or null
 */
public record ChatRequest(List<ChatMessage> messages, JsonArray tools, String toolChoice,
                          double temperature, int maxTokens, Integer seed) {
    public ChatRequest {
        messages = List.copyOf(messages);
        tools = tools == null ? new JsonArray() : tools.deepCopy();
    }

    public ChatRequest withToolChoice(String choice) {
        return new ChatRequest(messages, tools, choice, temperature, maxTokens, seed);
    }

    public ChatRequest withMessages(List<ChatMessage> newMessages) {
        return new ChatRequest(newMessages, tools, toolChoice, temperature, maxTokens, seed);
    }
}
