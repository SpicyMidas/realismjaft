package dev.agentbuddy.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.agentbuddy.brain.fake.FakeTools;
import dev.agentbuddy.brain.fake.FakeWorld;
import dev.agentbuddy.brain.tool.Category;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouterTest {
    private final Router router = new Router(new ToolRegistry(FakeTools.all(new FakeWorld())), 8);

    private List<String> tools(String text) {
        return router.select(router.route(text)).stream().map(Tool::spec).map(s -> s.name()).toList();
    }

    @Test
    void lookAtIsAlwaysFirstAndAtMostEightTools() {
        for (String s : List.of("find iron", "bring me 5 diamonds from the chest", "take me home", "follow me",
                "pick up this chest and bring me back to where I died")) {
            List<String> t = tools(s);
            assertEquals("look_at", t.get(0), s);
            assertTrue(t.size() <= 8, s + " -> " + t);
        }
    }

    @Test
    void routesByCategory() {
        assertEquals(Category.WORLD, router.route("where is the nearest iron?").categories().get(0));
        assertEquals(Category.INVENTORY, router.route("what's in this chest?").categories().get(0));
        assertEquals(Category.TRAVEL, router.route("take me home").categories().get(0));
        assertEquals(Category.SETTINGS, router.route("follow me").categories().get(0));
        assertTrue(tools("bring me 5 diamonds").contains("fetch_item"));
        assertTrue(tools("take me back to where I died").contains("death_return"));
        assertTrue(tools("call yourself Lens").contains("set_name"));
    }

    @Test
    void worldQuestionsExpectATool() {
        assertTrue(router.route("this is a wood block, right?").toolExpected());
        assertTrue(router.route("there's iron nearby, isn't there?").toolExpected());
        assertTrue(router.route("what's that over there?").toolExpected());
    }

    @Test
    void smallTalkAndContextQuestionsDoNot() {
        assertFalse(router.route("hi").toolExpected());
        assertFalse(router.route("what can you do?").toolExpected());
        assertFalse(router.route("I'm in the nether, right?").toolExpected());
        assertFalse(router.route("what's your name?").toolExpected());
        assertFalse(router.route("thanks for finding the iron").toolExpected());
    }

    @Test
    void unmatchedGetsNoTools() {
        assertEquals(List.of(), tools("hi"));
        assertEquals(List.of(), tools("I'm in the nether, right?"));
    }
}
