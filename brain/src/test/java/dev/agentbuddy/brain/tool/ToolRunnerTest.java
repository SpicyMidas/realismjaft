package dev.agentbuddy.brain.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.agentbuddy.brain.Pos;
import dev.agentbuddy.brain.fake.FakeWorld;
import dev.agentbuddy.brain.fake.Fixtures;
import dev.agentbuddy.brain.fake.MutableClock;
import dev.agentbuddy.brain.reply.Templates;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** ToolRunner checks (schema, cooldown, credits) and per-tool range/ignore behavior on the fake world. */
class ToolRunnerTest {
    private FakeWorld w;
    private MutableClock clock;
    private ToolRunner runner;

    @BeforeEach
    void setUp() {
        w = Fixtures.standardWorld();
        clock = new MutableClock();
        runner = Fixtures.runner(w, clock);
    }

    private String say(String tool, String args) {
        JsonObject a = JsonParser.parseString(args).getAsJsonObject();
        ToolRunner.Validation v = runner.validate(tool, a);
        if (!v.valid()) return "INVALID: " + v.error();
        return Templates.render(tool, runner.execute(w.player, v.call()));
    }

    @Test
    void unknownToolAndBadArgsAreInvalid() {
        assertEquals("INVALID: unknown tool dig", say("dig", "{}"));
        assertTrue(say("find_ore", "{\"ore\":\"tin\"}").startsWith("INVALID: find_ore: ore must be one of"));
        assertEquals("INVALID: find_ore: missing ore", say("find_ore", "{}"));
    }

    @Test
    void normalizesOreBeforeValidation() {
        assertEquals("Iron: 55 blocks NE, y=14.", say("find_ore", "{\"ore\":\"minecraft:iron_ore\"}"));
    }

    @Test
    void creditsAreCheckedAndOnlyTakenOnSuccess() {
        w.credits.set(w.player, 1);
        assertEquals("No gold within 64.", say("find_ore", "{\"ore\":\"gold\"}"));
        assertEquals(1, w.credits.balance(w.player), "failed scan must not cost credits");
        assertEquals("Iron: 55 blocks NE, y=14.", say("find_ore", "{\"ore\":\"iron\"}"));
        assertEquals(0, w.credits.balance(w.player));
        clock.advance(Duration.ofSeconds(10));
        assertEquals("Need 1 credits, have 0.", say("find_ore", "{\"ore\":\"iron\"}"));
    }

    @Test
    void pickupNeedsFiveCredits() {
        w.credits.set(w.player, 2);
        w.lookingAt = new Pos(-3, 64, 2);
        assertEquals("Need 5 credits, have 2.", say("pickup_container", "{}"));
    }

    @Test
    void cooldownBlocksUntilElapsed() {
        assertEquals("Iron: 55 blocks NE, y=14.", say("find_ore", "{\"ore\":\"iron\"}"));
        assertEquals("Wait 5 s.", say("find_ore", "{\"ore\":\"iron\"}"));
        clock.advance(Duration.ofMillis(4200));
        assertEquals("Wait 1 s.", say("find_ore", "{\"ore\":\"iron\"}"));
        clock.advance(Duration.ofMillis(900));
        assertEquals("Iron: 55 blocks NE, y=14.", say("find_ore", "{\"ore\":\"iron\"}"));
    }

    @Test
    void failedCallDoesNotStartCooldown() {
        assertEquals("No waystone named castle.", say("teleport_waystone", "{\"name\":\"castle\"}"));
        w.waystones.put("castle", new Pos(100, 70, 0));
        assertEquals("Teleporting to castle.", say("teleport_waystone", "{\"name\":\"castle\"}"));
    }

    @Test
    void teleportCostScalesWithDistance() {
        w.waystones.put("far", new Pos(1000, 64, 0));
        w.credits.set(w.player, 5);
        assertEquals("Need 11 credits, have 5.", say("teleport_waystone", "{\"name\":\"far\"}"));
    }

    @Test
    void oreScanRespectsRange() {
        w.blocks.clear();
        w.blocks.put(new Pos(70, 64, 0), "minecraft:diamond_ore");
        assertEquals("No diamond within 64.", say("find_ore", "{\"ore\":\"diamond\"}"));
        w.blocks.put(new Pos(0, 12, 30), "minecraft:deepslate_diamond_ore");
        assertEquals("Diamond: 60 blocks S, y=12.", say("find_ore", "{\"ore\":\"diamonds\"}"));
    }

    @Test
    void containerToolsRespectRange() {
        w.containers.put(new Pos(40, 64, 0), new FakeWorld.Container("minecraft:barrel", java.util.Map.of("minecraft:coal", 3)));
        assertEquals("Too far away.", say("list_container", "{\"x\":40,\"y\":64,\"z\":0}"));
        // Targets come from the crosshair, so give the player a long reach to reach past the pickup range (8).
        w.reach = 16;
        w.lookingAt = new Pos(-3, 64, 2);
        w.playerPos = new Pos(-3, 64, 12);
        assertEquals("Too far away.", say("pickup_container", "{}"));
    }

    @Test
    void lookAtClaimIsDecidedInJava() {
        assertEquals("No. That's stone.", say("look_at", "{\"claim\":\"wood\"}"));
        assertEquals("That's stone.", say("look_at", "{\"claim\":\"stone\"}"));
        // Model copied the true block into the claim: still no "Yes".
        assertEquals("That's stone.", say("look_at", "{\"claim\":\"minecraft:stone\"}"));
        assertEquals("That's stone.", say("look_at", "{}"));
        w.lookingAt = null;
        assertEquals("Can't see anything there.", say("look_at", "{\"claim\":\"wood\"}"));
    }

    @Test
    void containerTemplates() {
        w.lookingAt = new Pos(-3, 64, 2);
        assertEquals("Chest: 12 diamonds, 5 iron ingots.", say("list_container", "{}"));
        assertEquals("12 diamonds in chest at -3, 64, 2.", say("find_item", "{\"item\":\"Diamonds\"}"));
        clock.advance(Duration.ofSeconds(6));
        assertEquals("No emerald in known containers.", say("find_item", "{\"item\":\"emerald\"}"));
        assertEquals("Bringing 5 diamonds from chest at -3, 64, 2.", say("fetch_item", "{\"item\":\"diamond\",\"count\":5}"));
        assertEquals(7, w.containers.get(new Pos(-3, 64, 2)).items.get("minecraft:diamond"));
        w.lookingAt = new Pos(2, 64, 0);
        assertEquals("No container there.", say("list_container", "{}"));
    }

    @Test
    void ignoredBlocksDoNotExistForPerception() {
        Pos chest = new Pos(-3, 64, 2);
        w.lookingAt = chest;
        assertEquals("Ignoring chest at -3, 64, 2.", say("ignore_block", "{}"));
        assertEquals("Can't see anything there.", say("look_at", "{}"));
        assertEquals("I don't see any containers.", say("find_item", "{\"item\":\"diamond\"}"));
        assertEquals("I don't see that chest.", say("fetch_item", "{\"item\":\"diamond\"}"));
        assertEquals("No container there.", say("list_container", "{\"x\":-3,\"y\":64,\"z\":2}"));
        assertEquals("Not looking at a container.", say("pickup_container", "{}"));
        String ctx = w.context().render();
        assertTrue(ctx.contains("looking_at: nothing in reach"), ctx);
        assertTrue(ctx.contains("nearby_containers: 0"), ctx);
        assertFalse(ctx.contains("-3,64,2"), ctx);
        assertEquals("No longer ignoring chest at -3, 64, 2.", say("unignore_block", "{}"));
        assertEquals("12 diamonds in chest at -3, 64, 2.", say("find_item", "{\"item\":\"diamond\"}"));
    }

    @Test
    void pickupAndPlaceRoundTrip() {
        w.credits.set(w.player, 10);
        Pos chest = new Pos(-3, 64, 2);
        w.lookingAt = chest;
        assertEquals("Carrying chest.", say("pickup_container", "{}"));
        assertFalse(w.containers.containsKey(chest));
        clock.advance(Duration.ofSeconds(31));
        w.lookingAt = new Pos(2, 64, 0);
        assertEquals("Placed chest at 2, 65, 0.", say("place_container", "{}"));
        assertEquals(12, w.containers.get(new Pos(2, 65, 0)).items.get("minecraft:diamond"));
        assertEquals("Not carrying anything.", say("place_container", "{}"));
    }

    @Test
    void waystonesAndDeathReturn() {
        assertEquals("Waystone home set.", say("set_waystone", "{\"name\":\"home\"}"));
        assertEquals("Waystone Home already exists.", say("set_waystone", "{\"name\":\"Home\"}"));
        assertEquals("No death position recorded.", say("death_return", "{}"));
        w.lastDeath = new Pos(50, 30, 50);
        assertEquals("Teleporting near your death spot.", say("death_return", "{}"));
        assertEquals("Already used for this death.", say("death_return", "{}"));
        assertEquals("Waystone home removed.", say("remove_waystone", "{\"name\":\"home\"}"));
        w.maxWaystones = 0;
        assertEquals("Max 0 waystones.", say("set_waystone", "{\"name\":\"x\"}"));
    }

    @Test
    void settings() {
        assertEquals("Name set to Lens.", say("set_name", "{\"name\":\"Lens\"}"));
        assertEquals("Staying here.", say("stay", "{}"));
        assertEquals("Following.", say("follow", "{}"));
    }
}
