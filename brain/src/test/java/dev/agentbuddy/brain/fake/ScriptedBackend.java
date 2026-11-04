package dev.agentbuddy.brain.fake;

import dev.agentbuddy.brain.llm.ChatRequest;
import dev.agentbuddy.brain.llm.ChatResponse;
import dev.agentbuddy.brain.llm.LlmBackend;
import dev.agentbuddy.brain.llm.LlmException;
import dev.agentbuddy.brain.llm.ToolCall;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Returns canned responses in order and records the requests it saw. */
public final class ScriptedBackend implements LlmBackend {
    private final Deque<Object> script = new ArrayDeque<>();
    public final List<ChatRequest> requests = new ArrayList<>();

    public ScriptedBackend text(String content) {
        script.add(new ChatResponse(content, List.of(), "stop", "", null, 1));
        return this;
    }

    public ScriptedBackend call(String name, String argsJson) {
        script.add(new ChatResponse("", List.of(new ToolCall("id" + script.size(), name, argsJson)), "tool_calls", "", null, 1));
        return this;
    }

    public ScriptedBackend fail(LlmException e) {
        script.add(e);
        return this;
    }

    @Override
    public ChatResponse chat(ChatRequest request) throws LlmException {
        requests.add(request);
        Object next = script.poll();
        if (next == null) throw new AssertionError("unexpected extra LLM call #" + requests.size());
        if (next instanceof LlmException e) throw e;
        return (ChatResponse) next;
    }
}
