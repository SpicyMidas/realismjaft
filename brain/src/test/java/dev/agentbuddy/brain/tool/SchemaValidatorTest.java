package dev.agentbuddy.brain.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class SchemaValidatorTest {
    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void acceptsValidEnum() {
        var r = SchemaValidator.validate(ToolSpecs.FIND_ORE.parameters(), json("{\"ore\":\"iron\"}"));
        assertTrue(r.valid());
        assertEquals("iron", r.args().get("ore").getAsString());
    }

    @Test
    void rejectsValueOutsideEnumAndListsChoices() {
        var r = SchemaValidator.validate(ToolSpecs.FIND_ORE.parameters(), json("{\"ore\":\"tin\"}"));
        assertFalse(r.valid());
        assertTrue(r.errors().get(0).startsWith("ore must be one of: coal, iron"), r.errors().toString());
    }

    @Test
    void reportsMissingRequired() {
        var r = SchemaValidator.validate(ToolSpecs.FIND_ITEM.parameters(), json("{}"));
        assertEquals("missing item", r.errors().get(0));
    }

    @Test
    void blankRequiredStringCountsAsMissing() {
        var r = SchemaValidator.validate(ToolSpecs.SET_WAYSTONE.parameters(), json("{\"name\":\"  \"}"));
        assertEquals("missing name", r.errors().get(0));
    }

    @Test
    void coercesNumericStringsAndDropsUnknownKeys() {
        var r = SchemaValidator.validate(ToolSpecs.FETCH_ITEM.parameters(), json("{\"item\":\"diamond\",\"count\":\"5\",\"speed\":\"fast\"}"));
        assertTrue(r.valid(), r.errors().toString());
        assertEquals(5, r.args().get("count").getAsInt());
        assertFalse(r.args().has("speed"));
    }

    @Test
    void enforcesIntegerBoundsAndType() {
        assertEquals("count must be <= 64", SchemaValidator.validate(ToolSpecs.FETCH_ITEM.parameters(),
                json("{\"item\":\"diamond\",\"count\":500}")).errors().get(0));
        assertEquals("count must be an integer", SchemaValidator.validate(ToolSpecs.FETCH_ITEM.parameters(),
                json("{\"item\":\"diamond\",\"count\":\"lots\"}")).errors().get(0));
        assertEquals("count must be an integer", SchemaValidator.validate(ToolSpecs.FETCH_ITEM.parameters(),
                json("{\"item\":\"diamond\",\"count\":2.5}")).errors().get(0));
    }

    @Test
    void nullIsTreatedAsAbsent() {
        var r = SchemaValidator.validate(ToolSpecs.LOOK_AT.parameters(), json("{\"claim\":null}"));
        assertTrue(r.valid());
        assertFalse(r.args().has("claim"));
    }

    @Test
    void oreNormalizationHandlesCommonModelOutputs() {
        assertEquals("iron", ToolSpecs.normalizeOre("minecraft:deepslate_iron_ore"));
        assertEquals("iron", ToolSpecs.normalizeOre("Iron Ore"));
        assertEquals("diamond", ToolSpecs.normalizeOre("diamonds"));
        assertEquals("gold", ToolSpecs.normalizeOre("nether_gold_ore"));
        assertEquals("quartz", ToolSpecs.normalizeOre("nether quartz ore"));
        assertEquals("lapis", ToolSpecs.normalizeOre("lapis lazuli"));
        assertEquals("ancient_debris", ToolSpecs.normalizeOre("netherite"));
        assertEquals("redstone", ToolSpecs.normalizeOre("redstone"));
    }
}
