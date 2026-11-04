package dev.agentbuddy.brain.llm;

import java.util.List;

/**
 * @param content          assistant text (may be empty when tool calls are present)
 * @param reasoningContent thinking text if the server extracted any; should stay empty with thinking off
 * @param timings          llama-server timings, or null for servers that do not report them
 * @param wallMillis       client-side round trip time
 */
public record ChatResponse(String content, List<ToolCall> toolCalls, String finishReason,
                           String reasoningContent, Timings timings, long wallMillis) {
    public ChatResponse {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        reasoningContent = reasoningContent == null ? "" : reasoningContent;
    }

    /** Token counts and speeds reported by llama-server in the "timings" object. */
    public record Timings(int cacheN, int promptN, double promptMs, int predictedN, double predictedMs) {
        public double promptPerSecond() {
            return promptMs > 0 ? promptN * 1000.0 / promptMs : 0;
        }

        public double predictedPerSecond() {
            return predictedMs > 0 ? predictedN * 1000.0 / predictedMs : 0;
        }
    }
}
