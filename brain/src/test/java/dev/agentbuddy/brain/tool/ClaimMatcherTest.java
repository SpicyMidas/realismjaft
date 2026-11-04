package dev.agentbuddy.brain.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ClaimMatcherTest {
    @Test
    void woodMatchesLogsAndPlanksOnly() {
        assertTrue(ClaimMatcher.matches("wood", "minecraft:oak_log", Set.of("minecraft:logs")));
        assertTrue(ClaimMatcher.matches("a wood block", "minecraft:oak_planks", Set.of("minecraft:planks")));
        assertFalse(ClaimMatcher.matches("wood", "minecraft:stone", Set.of()));
        assertFalse(ClaimMatcher.matches("minecraft:wood", "minecraft:stone", Set.of()));
    }

    @Test
    void wordBasedNotSubstring() {
        assertTrue(ClaimMatcher.matches("iron ore", "minecraft:deepslate_iron_ore", Set.of()));
        assertFalse(ClaimMatcher.matches("diamond ore", "minecraft:redstone_ore", Set.of()));
        assertFalse(ClaimMatcher.matches("stone", "minecraft:redstone_ore", Set.of()));
        assertFalse(ClaimMatcher.matches("stone", "minecraft:cobblestone", Set.of()));
        assertTrue(ClaimMatcher.matches("Stone", "minecraft:stone", Set.of()));
        assertTrue(ClaimMatcher.matches("chests", "minecraft:chest", Set.of()));
    }

    @Test
    void noiseOnlyClaimIsEmpty() {
        assertTrue(ClaimMatcher.isEmpty("this block"));
        assertTrue(ClaimMatcher.isEmpty(""));
        assertFalse(ClaimMatcher.isEmpty("wood"));
    }
}
