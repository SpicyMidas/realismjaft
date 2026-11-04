package dev.agentbuddy.brain.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Minimal client for an OpenAI-compatible /v1/chat/completions endpoint. */
public final class OpenAiHttp {
    private final URI baseUrl;
    private final String apiKey;
    private final Duration requestTimeout;
    private final HttpClient client;

    public OpenAiHttp(URI baseUrl, String apiKey, Duration requestTimeout) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.requestTimeout = requestTimeout;
        // Loopback servers must not go through a system HTTP proxy.
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .proxy(isLoopback(baseUrl) ? HttpClient.Builder.NO_PROXY : java.net.ProxySelector.getDefault())
                .build();
    }

    public URI baseUrl() {
        return baseUrl;
    }

    public ChatResponse chat(ChatRequest request) throws LlmException {
        long start = System.nanoTime();
        JsonObject body = post("/v1/chat/completions", toJson(request));
        long wall = (System.nanoTime() - start) / 1_000_000;
        return parse(body, wall);
    }

    /** POST a JSON body and return the JSON object response. Used by chat and by diagnostics. */
    public JsonObject post(String path, JsonObject json) throws LlmException {
        HttpRequest.Builder b = HttpRequest.newBuilder(baseUrl.resolve(path))
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()));
        if (apiKey != null && !apiKey.isEmpty()) b.header("Authorization", "Bearer " + apiKey);
        HttpResponse<String> resp;
        try {
            resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new LlmException("LLM timed out.", "timeout after " + requestTimeout + " on " + path, e);
        } catch (IOException e) {
            throw new LlmException("LLM not reachable.", "I/O error on " + path + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM request cancelled.", "interrupted", e);
        }
        if (resp.statusCode() / 100 != 2) {
            throw new LlmException("LLM error.", "HTTP " + resp.statusCode() + " on " + path + ": " + truncate(resp.body()));
        }
        try {
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new LlmException("LLM error.", "non-JSON response on " + path + ": " + truncate(resp.body()), e);
        }
    }

    /** GET a JSON object (e.g. /props). */
    public JsonObject getJson(String path) throws LlmException {
        HttpRequest.Builder b = HttpRequest.newBuilder(baseUrl.resolve(path)).timeout(requestTimeout).GET();
        if (apiKey != null && !apiKey.isEmpty()) b.header("Authorization", "Bearer " + apiKey);
        try {
            HttpResponse<String> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) throw new LlmException("LLM error.", "HTTP " + resp.statusCode() + " on " + path);
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            throw new LlmException("LLM error.", "GET " + path + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM request cancelled.", "interrupted", e);
        }
    }

    /** GET returning only the status code (used for /health). */
    public int getStatus(String path, Duration timeout) {
        try {
            HttpRequest req = HttpRequest.newBuilder(baseUrl.resolve(path)).timeout(timeout).GET().build();
            return client.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            return -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    static JsonObject toJson(ChatRequest r) {
        JsonObject o = new JsonObject();
        JsonArray msgs = new JsonArray();
        for (ChatMessage m : r.messages()) {
            JsonObject jm = new JsonObject();
            jm.addProperty("role", m.role());
            jm.addProperty("content", m.content());
            msgs.add(jm);
        }
        o.add("messages", msgs);
        if (!r.tools().isEmpty()) {
            o.add("tools", r.tools());
            o.addProperty("tool_choice", r.toolChoice() == null ? "auto" : r.toolChoice());
        }
        o.addProperty("temperature", r.temperature());
        o.addProperty("max_tokens", r.maxTokens());
        if (r.seed() != null) o.addProperty("seed", r.seed());
        o.addProperty("stream", false);
        return o;
    }

    static ChatResponse parse(JsonObject body, long wallMillis) throws LlmException {
        JsonArray choices = body.has("choices") ? body.getAsJsonArray("choices") : null;
        if (choices == null || choices.isEmpty()) throw new LlmException("LLM error.", "response has no choices: " + truncate(body.toString()));
        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject msg = choice.getAsJsonObject("message");
        String content = str(msg, "content");
        String reasoning = str(msg, "reasoning_content");
        List<ToolCall> calls = new ArrayList<>();
        if (msg.has("tool_calls") && msg.get("tool_calls").isJsonArray()) {
            for (JsonElement e : msg.getAsJsonArray("tool_calls")) {
                JsonObject tc = e.getAsJsonObject();
                JsonObject fn = tc.has("function") ? tc.getAsJsonObject("function") : tc;
                JsonElement args = fn.get("arguments");
                String argsJson = args == null || args.isJsonNull() ? "{}" : args.isJsonPrimitive() ? args.getAsString() : args.toString();
                calls.add(new ToolCall(str(tc, "id"), str(fn, "name"), argsJson));
            }
        }
        ChatResponse.Timings timings = null;
        if (body.has("timings") && body.get("timings").isJsonObject()) {
            JsonObject t = body.getAsJsonObject("timings");
            timings = new ChatResponse.Timings(intOr(t, "cache_n"), intOr(t, "prompt_n"), dblOr(t, "prompt_ms"),
                    intOr(t, "predicted_n"), dblOr(t, "predicted_ms"));
        }
        return new ChatResponse(content, calls, str(choice, "finish_reason"), reasoning, timings, wallMillis);
    }

    private static boolean isLoopback(URI uri) {
        String h = uri.getHost();
        return "127.0.0.1".equals(h) || "localhost".equalsIgnoreCase(h) || "::1".equals(h) || "[::1]".equals(h);
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static int intOr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsInt();
    }

    private static double dblOr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
    }

    private static String truncate(String s) {
        return s == null ? "" : s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
