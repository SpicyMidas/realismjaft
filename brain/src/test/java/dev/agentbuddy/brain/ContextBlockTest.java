package dev.agentbuddy.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.agentbuddy.brain.fake.Fixtures;
import org.junit.jupiter.api.Test;

class ContextBlockTest {
    @Test
    void rendersCompactBlock() {
        assertEquals("""
                player: Steve, hp 20/20, holding stone_pickaxe
                looking_at: stone @ 2,64,0
                dimension: overworld, biome: plains, time: day
                agent: Agent @ 1,65,1, following, credits 20
                nearby_containers: 1""", Fixtures.standardWorld().context().render());
    }
}
