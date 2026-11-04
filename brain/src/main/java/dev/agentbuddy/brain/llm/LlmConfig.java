package dev.agentbuddy.brain.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Contents of {@code localLLM/config.json}. Missing keys fall back to the defaults below.
 *
 * @param port      0 = pick a free loopback port at each start
 * @param reasoning value for llama-server {@code --reasoning}; "off" disables thinking (verified for Qwen3.5 and Gemma 4)
 * @param extraArgs appended to the llama-server command line as-is
 */
public record LlmConfig(String model, int port, int gpuLayers, int ctxSize, double temperature, int maxTokens,
                        int idleTimeoutMinutes, boolean startOnWorldJoin, int startTimeoutSeconds,
                        int requestTimeoutSeconds, String reasoning, List<String> extraArgs) {

    public static final LlmConfig DEFAULTS = new LlmConfig("Qwen3.5-4B-Q4_K_M.gguf", 0, 99, 4096, 0.2, 80,
            10, false, 180, 120, "off", List.of());

    public LlmConfig {
        extraArgs = extraArgs == null ? List.of() : List.copyOf(extraArgs);
    }

    public static LlmConfig load(Path configJson) throws IOException {
        if (!Files.exists(configJson)) return DEFAULTS;
        JsonObject o = JsonParser.parseString(Files.readString(configJson)).getAsJsonObject();
        JsonObject merged = new Gson().toJsonTree(DEFAULTS).getAsJsonObject();
        for (String key : o.keySet()) merged.add(key, o.get(key));
        return new Gson().fromJson(merged, LlmConfig.class);
    }

    public LlmConfig withModel(String newModel) {
        return new LlmConfig(newModel, port, gpuLayers, ctxSize, temperature, maxTokens, idleTimeoutMinutes,
                startOnWorldJoin, startTimeoutSeconds, requestTimeoutSeconds, reasoning, extraArgs);
    }

    public LlmConfig withExtraArgs(List<String> args) {
        return new LlmConfig(model, port, gpuLayers, ctxSize, temperature, maxTokens, idleTimeoutMinutes,
                startOnWorldJoin, startTimeoutSeconds, requestTimeoutSeconds, reasoning, args);
    }

    public LlmConfig withReasoning(String newReasoning) {
        return new LlmConfig(model, port, gpuLayers, ctxSize, temperature, maxTokens, idleTimeoutMinutes,
                startOnWorldJoin, startTimeoutSeconds, requestTimeoutSeconds, newReasoning, extraArgs);
    }
}
