package dev.agentbuddy.brain;

import com.google.gson.JsonArray;
import dev.agentbuddy.brain.llm.ChatMessage;
import dev.agentbuddy.brain.llm.ChatRequest;
import dev.agentbuddy.brain.llm.ChatResponse;
import dev.agentbuddy.brain.llm.LlmBackend;
import dev.agentbuddy.brain.llm.LlmException;
import dev.agentbuddy.brain.reply.ReplyFilter;
import dev.agentbuddy.brain.reply.Templates;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolRegistry;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * One player message in, one short reply out. The LLM only picks a tool and fills arguments; the tool runs on the
 * server thread and the reply comes from a template.
 *
 * <p>{@link #handle} blocks on HTTP; call it from a worker thread. Per turn: at most {@code maxSteps} LLM calls,
 * one corrective retry for an invalid call, and one forced re-run ({@code tool_choice=required}) when the message is
 * about the world but the model answered in free text.
 */
public final class Brain {
    public record Config(double temperature, int maxTokens, Integer seed, int maxSteps, int maxTools, int historyTurns) {
        public static Config defaults(double temperature, int maxTokens) {
            return new Config(temperature, maxTokens, null, 4, 8, 4);
        }
    }

    private final LlmBackend llm;
    private final ToolRunner runner;
    private final Executor serverThread;
    private final Config config;
    private final Router router;
    private final History history;

    public Brain(LlmBackend llm, ToolRunner runner, Executor serverThread, Config config) {
        this.llm = llm;
        this.runner = runner;
        this.serverThread = serverThread;
        this.config = config;
        this.router = new Router(runner.registry(), config.maxTools());
        this.history = new History(config.historyTurns());
    }

    public History history() {
        return history;
    }

    public Reply handle(String player, String text, ContextBlock context) {
        Router.Route route = router.route(text);
        List<Tool> tools = router.select(route);
        JsonArray toolsJson = ToolRegistry.toOpenAi(tools);
        Set<String> knownNames = runner.registry().all().stream().map(t -> t.spec().name()).collect(Collectors.toSet());

        List<ChatMessage> base = new ArrayList<>();
        base.add(ChatMessage.system(Prompts.SYSTEM));
        base.addAll(history.messages(player));
        String userMessage = Prompts.userMessage(context, text);

        List<Reply.Step> steps = new ArrayList<>();
        String toolChoice = "auto";
        String correction = null;
        boolean corrected = false;
        boolean forced = false;

        for (int step = 0; step < config.maxSteps(); step++) {
            List<ChatMessage> messages = new ArrayList<>(base);
            messages.add(ChatMessage.user(correction == null ? userMessage : userMessage + "\n\n" + correction));
            ChatRequest request = new ChatRequest(messages, toolsJson, toolChoice, config.temperature(), config.maxTokens(), config.seed());
            ChatResponse response;
            try {
                response = llm.chat(request);
            } catch (LlmException e) {
                return finish(player, text, e.userMessage(), null, null, steps, e.getMessage());
            }
            ToolCallParser.Parsed parsed = ToolCallParser.parse(response, knownNames);

            if (parsed.source() == ToolCallParser.Source.MALFORMED) {
                steps.add(new Reply.Step(toolChoice, response, parsed.source(), parsed.error()));
                if (corrected) return finish(player, text, Templates.DIDNT_UNDERSTAND, null, null, steps, parsed.error());
                corrected = true;
                correction = corrective(parsed.error());
                continue;
            }

            if (!parsed.calls().isEmpty()) {
                ToolCallParser.RawCall call = parsed.calls().get(0);
                ToolRunner.Validation v = runner.validate(call.name(), call.args());
                steps.add(new Reply.Step(toolChoice, response, parsed.source(), v.valid() ? null : v.error()));
                if (!v.valid()) {
                    if (corrected) return finish(player, text, Templates.DIDNT_UNDERSTAND, null, null, steps, v.error());
                    corrected = true;
                    correction = corrective(v.error());
                    continue;
                }
                ToolResult result;
                try {
                    result = CompletableFuture.supplyAsync(() -> runner.execute(player, v.call()), serverThread)
                            .get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return finish(player, text, "Can't do that.", null, null, steps, "tool execution failed: " + e);
                }
                String reply = Templates.render(call.name(), result);
                return new Reply(remember(player, text, reply), call.name(), v.call().args(), result, steps, null);
            }

            steps.add(new Reply.Step(toolChoice, response, parsed.source(), null));
            if (route.toolExpected()) {
                // A world question answered without checking: reject the text and force a tool once.
                if (forced) return finish(player, text, Templates.DIDNT_UNDERSTAND, null, null, steps, "no tool call even when forced");
                forced = true;
                toolChoice = "required";
                continue;
            }
            return finish(player, text, ReplyFilter.clean(response.content()), null, null, steps, null);
        }
        return finish(player, text, Templates.DIDNT_UNDERSTAND, null, null, steps, "step limit reached");
    }

    private static String corrective(String error) {
        return "Your last tool call was invalid: " + error + ". Call one tool with valid arguments.";
    }

    private Reply finish(String player, String text, String reply, String tool, ToolResult result,
                         List<Reply.Step> steps, String failure) {
        if (failure == null) history.add(player, text, reply);
        return new Reply(reply, tool, null, result, steps, failure);
    }

    private String remember(String player, String text, String reply) {
        history.add(player, text, reply);
        return reply;
    }
}
