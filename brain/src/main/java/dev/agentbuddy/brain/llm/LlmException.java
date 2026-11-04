package dev.agentbuddy.brain.llm;

/** LLM unavailable or failed. {@link #userMessage()} is the one short line the Agent says. */
public class LlmException extends Exception {
    private final String userMessage;

    public LlmException(String userMessage, String detail) {
        this(userMessage, detail, null);
    }

    public LlmException(String userMessage, String detail, Throwable cause) {
        super(detail, cause);
        this.userMessage = userMessage;
    }

    public String userMessage() {
        return userMessage;
    }
}
