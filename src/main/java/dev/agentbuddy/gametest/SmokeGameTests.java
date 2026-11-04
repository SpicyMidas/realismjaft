package dev.agentbuddy.gametest;

import dev.agentbuddy.AgentBuddy;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// Template agentbuddy:empty is data/agentbuddy/structure/empty.nbt (3x3x3 air).
@GameTestHolder(AgentBuddy.MODID)
@PrefixGameTestTemplate(false)
public class SmokeGameTests {
    @GameTest(template = "empty")
    public static void modIsLoaded(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(AgentBuddy.MODID), "agentbuddy is not loaded");
        helper.succeed();
    }
}
