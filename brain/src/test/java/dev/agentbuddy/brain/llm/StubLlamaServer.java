package dev.agentbuddy.brain.llm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Stand-in for llama-server in ManagedServer tests. Writes its argv to ./stub-args.txt, answers /health with 503
 * for 300 ms then 200, and /v1/chat/completions with a fixed reply (401 without the right bearer key).
 * A model path containing "crash" makes it exit with code 3 while "loading".
 */
public final class StubLlamaServer {
    public static void main(String[] args) throws Exception {
        Files.write(Path.of("stub-args.txt"), List.of(args));
        List<String> a = List.of(args);
        int port = Integer.parseInt(a.get(a.indexOf("--port") + 1));
        String model = a.get(a.indexOf("-m") + 1);
        if (model.contains("crash")) {
            Thread.sleep(200);
            System.exit(3);
        }
        String key = System.getenv("LLAMA_API_KEY");
        long readyAt = System.currentTimeMillis() + 300;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", ex -> respond(ex, System.currentTimeMillis() < readyAt ? 503 : 200, "{}"));
        server.createContext("/v1/chat/completions", ex -> {
            ex.getRequestBody().readAllBytes();
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if (key != null && !("Bearer " + key).equals(auth)) {
                respond(ex, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            respond(ex, 200, "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"stub\"}}],"
                    + "\"timings\":{\"cache_n\":0,\"prompt_n\":10,\"prompt_ms\":5.0,\"predicted_n\":2,\"predicted_ms\":4.0}}");
        });
        server.start();
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }
}
