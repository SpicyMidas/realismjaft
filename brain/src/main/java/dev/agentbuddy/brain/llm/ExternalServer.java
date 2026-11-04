package dev.agentbuddy.brain.llm;

import java.net.URI;
import java.time.Duration;

/** Any already-running OpenAI-compatible server (Ollama, LM Studio, a manually started llama-server). */
public final class ExternalServer implements LlmBackend {
    private final OpenAiHttp http;

    public ExternalServer(URI baseUrl, String apiKey, Duration requestTimeout) {
        this.http = new OpenAiHttp(baseUrl, apiKey, requestTimeout);
    }

    public OpenAiHttp http() {
        return http;
    }

    @Override
    public ChatResponse chat(ChatRequest request) throws LlmException {
        return http.chat(request);
    }
}
