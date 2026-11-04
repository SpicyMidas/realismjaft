package dev.agentbuddy.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PosTest {
    @Test
    void compassDirectionsUseMinecraftAxes() {
        Pos o = new Pos(0, 64, 0);
        assertEquals("N", o.directionTo(new Pos(0, 64, -10)));
        assertEquals("S", o.directionTo(new Pos(0, 64, 10)));
        assertEquals("E", o.directionTo(new Pos(10, 64, 0)));
        assertEquals("W", o.directionTo(new Pos(-10, 64, 0)));
        assertEquals("NE", o.directionTo(new Pos(10, 64, -10)));
        assertEquals("SW", o.directionTo(new Pos(-10, 64, 10)));
        assertEquals("down", o.directionTo(new Pos(0, 10, 0)));
    }
}
