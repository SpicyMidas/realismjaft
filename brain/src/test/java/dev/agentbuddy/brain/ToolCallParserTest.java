package dev.agentbuddy.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.agentbuddy.brain.ToolCallParser.Source;
import dev.agentbuddy.brain.llm.ChatResponse;
import dev.agentbuddy.brain.llm.ToolCall;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolCallParserTest {
    private static final Set<String> KNOWN = Set.of("find_ore", "look_at", "list_container");

    private static ToolCallParser.Parsed text(String content) {
        return ToolCallParser.parse(new ChatResponse(content, List.of(), "stop", "", null, 0), KNOWN);
    }

    @Test
    void structuredCall() {
        var p = ToolCallParser.parse(new ChatResponse("", List.of(new ToolCall("1", "find_ore", "{\"ore\":\"iron\"}")), "tool_calls", "", null, 0), KNOWN);
        assertEquals(Source.STRUCTURED, p.source());
        assertEquals("find_ore", p.calls().get(0).name());
        assertEquals("iron", p.calls().get(0).args().get("ore").getAsString());
    }

    @Test
    void structuredDoubleEncodedArguments() {
        var p = ToolCallParser.parse(new ChatResponse("", List.of(new ToolCall("1", "find_ore", "\"{\\\"ore\\\":\\\"gold\\\"}\"")), "tool_calls", "", null, 0), KNOWN);
        assertEquals(Source.STRUCTURED, p.source());
        assertEquals("gold", p.calls().get(0).args().get("ore").getAsString());
    }

    @Test
    void structuredWithBrokenJsonIsMalformed() {
        var p = ToolCallParser.parse(new ChatResponse("", List.of(new ToolCall("1", "find_ore", "{ore: iron")), "tool_calls", "", null, 0), KNOWN);
        assertEquals(Source.MALFORMED, p.source());
    }

    @Test
    void hermesStyleTaggedJsonInText() {
        var p = text("<tool_call>\n{\"name\": \"find_ore\", \"arguments\": {\"ore\": \"coal\"}}\n</tool_call>");
        assertEquals(Source.RECOVERED, p.source());
        assertEquals("coal", p.calls().get(0).args().get("ore").getAsString());
    }

    @Test
    void qwenXmlInText() {
        var p = text("<tool_call>\n<function=find_ore>\n<parameter=ore>\ndiamond\n</parameter>\n</function>\n</tool_call>");
        assertEquals(Source.RECOVERED, p.source());
        assertEquals("find_ore", p.calls().get(0).name());
        assertEquals("diamond", p.calls().get(0).args().get("ore").getAsString());
    }

    @Test
    void qwenXmlNumericParameterStaysNumeric() {
        var p = text("<function=list_container><parameter=x>-120</parameter><parameter=y>64</parameter></function>");
        assertEquals(-120, p.calls().get(0).args().get("x").getAsInt());
        assertTrue(p.calls().get(0).args().get("y").getAsJsonPrimitive().isNumber());
    }

    @Test
    void bareJsonInFence() {
        var p = text("```json\n{\"name\":\"look_at\",\"parameters\":{\"claim\":\"wood\"}}\n```");
        assertEquals(Source.RECOVERED, p.source());
        assertEquals("wood", p.calls().get(0).args().get("claim").getAsString());
    }

    @Test
    void fewShotArrowStyle() {
        var p = text("look_at {\"claim\":\"iron ore\"}");
        assertEquals(Source.RECOVERED, p.source());
        assertEquals("iron ore", p.calls().get(0).args().get("claim").getAsString());
    }

    @Test
    void pythonStyleKwargs() {
        var p = text("find_ore(ore=\"emerald\")");
        assertEquals("emerald", p.calls().get(0).args().get("ore").getAsString());
    }

    @Test
    void plainTextIsNone() {
        assertEquals(Source.NONE, text("Hi.").source());
        assertEquals(Source.NONE, text("").source());
    }

    @Test
    void unparseableMarkupIsMalformed() {
        assertEquals(Source.MALFORMED, text("<tool_call>{oops</tool_call>").source());
    }
}
