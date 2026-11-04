package dev.agentbuddy.brain.fake;

import dev.agentbuddy.brain.Pos;
import dev.agentbuddy.brain.tool.Cooldowns;
import dev.agentbuddy.brain.tool.ToolRegistry;
import dev.agentbuddy.brain.tool.ToolRunner;
import java.util.Map;

/** A small standard world: player at 0,64,0 looking at stone, one chest of diamonds, iron ore 23 blocks NE. */
public final class Fixtures {
    private Fixtures() {
    }

    public static FakeWorld standardWorld() {
        FakeWorld w = new FakeWorld();
        w.blocks.put(new Pos(2, 64, 0), "minecraft:stone");
        w.lookingAt = new Pos(2, 64, 0);
        w.blocks.put(new Pos(16, 14, -16), "minecraft:deepslate_iron_ore");
        w.containers.put(new Pos(-3, 64, 2), new FakeWorld.Container("minecraft:chest",
                Map.of("minecraft:diamond", 12, "minecraft:iron_ingot", 5)));
        w.credits.set(w.player, 20);
        return w;
    }

    public static ToolRunner runner(FakeWorld w, MutableClock clock) {
        return new ToolRunner(new ToolRegistry(FakeTools.all(w)), w.credits, new Cooldowns(clock));
    }
}
