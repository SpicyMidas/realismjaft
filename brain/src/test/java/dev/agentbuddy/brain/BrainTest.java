package dev.agentbuddy.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.agentbuddy.brain.fake.FakeWorld;
import dev.agentbuddy.brain.fake.Fixtures;
import dev.agentbuddy.brain.fake.MutableClock;
import dev.agentbuddy.brain.fake.ScriptedBackend;
import dev.agentbuddy.brain.llm.ChatMessage;
import dev.agentbuddy.brain.llm.LlmException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BrainTest {
    private FakeWorld w;
    private ScriptedBackend llm;
    private final AtomicReference<String> toolThread = new AtomicReference<>();
    private Brain brain;

    @BeforeEach
    void setUp() {
        w = Fixtures.standardWorld();
        llm = new ScriptedBackend();
        Executor serverThread = r -> {
            toolThread.set(Thread.currentThread().getName());
            r.run();
        };
        brain = new Brain(llm, Fixtures.runner(w, new MutableClock()), serverThread, Brain.Config.defaults(0.2, 80));
    }

    private Reply say(String text) {
        return brain.handle(w.player, text, w.context());
    }

    @Test
    void toolCallBecomesTemplateReplyWithOneLlmCall() {
        llm.call("find_ore", "{\"ore\":\"iron\"}");
        Reply r = say("where is the nearest iron?");
        assertEquals("Iron: 55 blocks NE, y=14.", r.text());
        assertEquals("find_ore", r.tool());
        assertEquals(1, llm.requests.size());
        assertTrue(toolThread.get() != null, "tool must run through the server-thread executor");
    }

    @Test
    void requestCarriesSystemPromptContextAndRoutedTools() {
        llm.call("look_at", "{\"claim\":\"wood\"}");
        say("this is a wood block, right?");
        var req = llm.requests.get(0);
        assertEquals("system", req.messages().get(0).role());
        String user = req.messages().get(req.messages().size() - 1).content();
        assertTrue(user.contains("looking_at: stone @ 2,64,0"), user);
        assertTrue(user.endsWith("Player: this is a wood block, right?"), user);
        assertTrue(req.tools().size() <= 8);
        assertEquals("look_at", req.tools().get(0).getAsJsonObject().getAsJsonObject("function").get("name").getAsString());
        assertEquals(0.2, req.temperature());
        assertEquals(80, req.maxTokens());
    }

    @Test
    void falsePremiseIsCorrectedByTheTemplate() {
        llm.call("look_at", "{\"claim\":\"wood\"}");
        assertEquals("No. That's stone.", say("this is a wood block, right?").text());
    }

    @Test
    void invalidCallGetsOneCorrectiveRetry() {
        llm.call("find_ore", "{\"ore\":\"tin\"}").call("find_ore", "{\"ore\":\"iron\"}");
        Reply r = say("find tin ore");
        assertEquals("Iron: 55 blocks NE, y=14.", r.text());
        String retryUser = llm.requests.get(1).messages().get(llm.requests.get(1).messages().size() - 1).content();
        assertTrue(retryUser.contains("Your last tool call was invalid: find_ore: ore must be one of"), retryUser);
    }

    @Test
    void secondInvalidCallFallsBack() {
        llm.call("find_ore", "{\"ore\":\"tin\"}").call("dig", "{}");
        Reply r = say("find tin ore");
        assertEquals("Didn't understand.", r.text());
        assertEquals(2, llm.requests.size());
    }

    @Test
    void malformedMarkupIsRetriedThenRecovered() {
        llm.text("<tool_call>{broken").text("<tool_call>\n<function=find_ore>\n<parameter=ore>\niron\n</parameter>\n</function>\n</tool_call>");
        Reply r = say("find iron");
        assertEquals("Iron: 55 blocks NE, y=14.", r.text());
        assertEquals(ToolCallParser.Source.MALFORMED, r.steps().get(0).source());
        assertEquals(ToolCallParser.Source.RECOVERED, r.steps().get(1).source());
    }

    @Test
    void worldQuestionAnsweredInTextIsRejectedAndToolForced() {
        llm.text("No, that is a stone block, not wood.").call("look_at", "{\"claim\":\"wood\"}");
        Reply r = say("this is a wood block, right?");
        assertEquals("No. That's stone.", r.text());
        assertEquals("auto", llm.requests.get(0).toolChoice());
        assertEquals("required", llm.requests.get(1).toolChoice());
    }

    @Test
    void worldQuestionWithoutToolEvenWhenForcedIsNotAnswered() {
        llm.text("Yes, it's wood.").text("Yes, it's wood.");
        assertEquals("Didn't understand.", say("this is a wood block, right?").text());
    }

    @Test
    void smallTalkIsFilteredFreeText() {
        llm.text("Sure! Hi there, I'm your helper. I can do lots of things for you!");
        Reply r = say("hi");
        assertEquals("Hi there, I'm your helper.", r.text());
        assertNull(r.tool());
        assertEquals(1, llm.requests.size());
    }

    @Test
    void textCallInTextOnlyRequestIsNotRunButAnsweredFromContext() {
        llm.text("look_at {\"claim\":\"bow\"}").text("No, you're holding a stone pickaxe.");
        Reply r = say("I'm holding a bow, right?");
        assertEquals("No, you're holding a stone pickaxe.", r.text());
        assertNull(r.tool());
        assertTrue(llm.requests.get(0).tools().isEmpty());
        String retryUser = llm.requests.get(1).messages().get(llm.requests.get(1).messages().size() - 1).content();
        assertTrue(retryUser.endsWith("Answer in one short sentence using only the context."), retryUser);
    }

    @Test
    void llmUnavailableGivesOneShortLine() {
        llm.fail(new LlmException("LLM model missing.", "model not found at /x"));
        Reply r = say("find iron");
        assertEquals("LLM model missing.", r.text());
        assertEquals("model not found at /x", r.failure());
    }

    @Test
    void historyKeepsLastFourTurns() {
        for (int i = 0; i < 6; i++) {
            llm.text("Hi " + i + ".");
            say("hello " + i);
        }
        llm.text("Hi.");
        say("hello again");
        List<ChatMessage> msgs = llm.requests.get(6).messages();
        // system + 4 turns * 2 + current user
        assertEquals(1 + 8 + 1, msgs.size());
        assertEquals("Player: hello 2", msgs.get(1).content());
        assertEquals("Hi 5.", msgs.get(8).content());
    }
}
