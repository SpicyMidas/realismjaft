package dev.agentbuddy.brain.llm;

/** A tool call as returned by the server. {@code argumentsJson} is unparsed; the brain validates it. */
public record ToolCall(String id, String name, String argumentsJson) {
}
