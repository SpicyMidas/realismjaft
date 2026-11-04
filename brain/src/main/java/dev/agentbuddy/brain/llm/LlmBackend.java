package dev.agentbuddy.brain.llm;

/**
 * Chat completion with tools (OpenAI-compatible). Blocking: call it from a worker thread, never the server thread.
 */
public interface LlmBackend {
    ChatResponse chat(ChatRequest request) throws LlmException;
}
