package dev.agentbuddy.gametest;

import com.google.gson.JsonObject;
import dev.agentbuddy.AgentBuddy;
import dev.agentbuddy.ModRegistries;
import dev.agentbuddy.data.AgentStore;
import dev.agentbuddy.data.CreditStore;
import dev.agentbuddy.entity.AgentEntity;
import dev.agentbuddy.server.AgentService;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M5: find_ore, waystones, death_return. Neighbouring test areas are within 64 blocks, so each ore test uses
 * an ore type no other test places.
 */
@GameTestHolder(AgentBuddy.MODID)
@PrefixGameTestTemplate(false)
public class TravelGameTests {
    /** Mock player at relative (2,1,2) looking +Z at eye level. */
    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(new Vec3(2.5, 1, 2.5));
        p.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return p;
    }

    private static AgentEntity agentFor(GameTestHelper h, ServerPlayer owner) {
        AgentEntity a = h.spawn(ModRegistries.AGENT.get(), new Vec3(1.5, 1, 2.5));
        UUID id = UUID.randomUUID();
        AgentStore.get(h.getLevel().getServer()).create(id, owner.getUUID());
        a.bind(id, owner.getUUID(), "Agent");
        return a;
    }

    private static void credits(GameTestHelper h, ServerPlayer p, int n) {
        CreditStore.get(h.getLevel().getServer()).credits().set(p.getUUID().toString(), n);
    }

    private static JsonObject args(String key, String value) {
        JsonObject o = new JsonObject();
        if (key != null) o.addProperty(key, value);
        return o;
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    @GameTest(template = "corridor")
    public static void findOreReportsNearestWithDirectionAndHeight(GameTestHelper h) {
        ServerPlayer p = player(h);
        agentFor(h, p);
        credits(h, p, 10);
        h.setBlock(new BlockPos(2, 1, 12), Blocks.IRON_ORE);
        h.setBlock(new BlockPos(4, 3, 17), Blocks.DEEPSLATE_IRON_ORE);
        int y = h.absolutePos(new BlockPos(2, 1, 12)).getY();
        h.assertValueEqual(AgentService.get().runTool(p, "find_ore", args("ore", "iron ore")), "Iron: 10 blocks S, y=" + y + ".", "nearest");
        done(h, p);
    }

    @GameTest(template = "corridor")
    public static void findOreFindsTheNearOneThenCoolsDown(GameTestHelper h) {
        ServerPlayer p = player(h);
        agentFor(h, p);
        credits(h, p, 10);
        h.setBlock(new BlockPos(2, 2, 5), Blocks.GOLD_ORE);   // under the crosshair
        h.setBlock(new BlockPos(2, 1, 75), Blocks.GOLD_ORE);  // ~72 blocks away: beyond 64
        AgentService s = AgentService.get();
        h.assertTrue(s.runTool(p, "find_ore", args("ore", "gold")).startsWith("Gold: 3 blocks S"), "near gold found");
        h.assertTrue(s.runTool(p, "ignore_block", new JsonObject()).startsWith("Ignoring gold ore"), "ignore it");
        credits(h, p, 10);
        h.assertValueEqual(s.runTool(p, "find_ore", args("ore", "gold")), "Wait 5 s.", "cooldown after success");
        done(h, p);
    }

    @GameTest(template = "corridor")
    public static void ignoredOreIsInvisibleAndFarOreIsOutOfRange(GameTestHelper h) {
        ServerPlayer p = player(h);
        agentFor(h, p);
        credits(h, p, 10);
        h.setBlock(new BlockPos(2, 2, 5), Blocks.LAPIS_ORE);
        h.setBlock(new BlockPos(2, 1, 75), Blocks.LAPIS_ORE);
        AgentService s = AgentService.get();
        h.assertTrue(s.runTool(p, "ignore_block", new JsonObject()).startsWith("Ignoring lapis ore"), "ignore near lapis");
        h.assertValueEqual(s.runTool(p, "find_ore", args("ore", "lapis lazuli")), "No lapis within 64.", "ignored + out of range");
        h.assertValueEqual(s.credits(p), 10, "absence is free");
        done(h, p);
    }

    @GameTest(template = "corridor")
    public static void findOreNeedsCredits(GameTestHelper h) {
        ServerPlayer p = player(h);
        agentFor(h, p);
        h.setBlock(new BlockPos(2, 1, 9), Blocks.EMERALD_ORE);
        AgentService s = AgentService.get();
        h.assertValueEqual(s.runTool(p, "find_ore", args("ore", "emeralds")), "Need 1 credits, have 0.", "broke");
        credits(h, p, 5);
        h.assertTrue(s.runTool(p, "find_ore", args("ore", "emeralds")).startsWith("Emerald: 7 blocks S"), "found");
        h.assertValueEqual(s.credits(p), 4, "costs 1");
        done(h, p);
    }

    @GameTest(template = "corridor")
    public static void waystonesSetDuplicateLimitAndRemove(GameTestHelper h) {
        ServerPlayer p = player(h);
        agentFor(h, p);
        AgentService s = AgentService.get();
        h.assertValueEqual(s.runTool(p, "set_waystone", args("name", "home")), "Waystone home set.", "set");
        h.assertValueEqual(s.runTool(p, "set_waystone", args("name", "Home")), "Waystone Home already exists.", "duplicate (case-insensitive)");
        for (String n : new String[] {"a", "b", "c", "d"}) s.runTool(p, "set_waystone", args("name", n));
        h.assertValueEqual(s.runTool(p, "set_waystone", args("name", "e")), "Max 5 waystones.", "limit");
        h.assertValueEqual(s.runTool(p, "remove_waystone", args("name", "HOME")), "Waystone home removed.", "remove keeps typed name");
        h.assertValueEqual(s.runTool(p, "remove_waystone", args("name", "nope")), "No waystone named nope.", "remove unknown");
        credits(h, p, 5); // teleport checks credits (1) before looking the name up
        h.assertValueEqual(s.runTool(p, "teleport_waystone", args("name", "nope")), "No waystone named nope.", "teleport unknown");
        h.assertValueEqual(s.credits(p), 5, "failed teleport is free");
        done(h, p);
    }

    @GameTest(template = "corridor")
    public static void teleportCostsByDistanceAndHasCooldown(GameTestHelper h) {
        ServerPlayer p = player(h);
        agentFor(h, p);
        credits(h, p, 10);
        AgentService s = AgentService.get();
        s.runTool(p, "set_waystone", args("name", "base"));
        BlockPos base = p.blockPosition();
        p.teleportTo(h.getLevel(), p.getX() + 250, p.getY(), p.getZ(), 0.0F, 0.0F);
        h.assertValueEqual(s.runTool(p, "teleport_waystone", args("name", "base")), "Teleporting to base.", "teleport");
        h.assertTrue(p.blockPosition().closerThan(base, 5.0), "player is back near the waystone, at " + p.blockPosition());
        h.assertValueEqual(s.credits(p), 7, "1 + 250/100 = 3 credits");
        h.assertValueEqual(s.runTool(p, "teleport_waystone", args("name", "base")), "Wait 60 s.", "cooldown");
        done(h, p);
    }

    @GameTest(template = "corridor")
    public static void waystoneInAnotherDimensionIsRefused(GameTestHelper h) {
        ServerPlayer p = player(h);
        AgentEntity a = agentFor(h, p);
        credits(h, p, 10);
        AgentStore.get(h.getLevel().getServer()).addWaystone(a.agentId(),
                new AgentStore.Waystone("hell", GlobalPos.of(Level.NETHER, new BlockPos(0, 64, 0)), 0.0F));
        h.assertValueEqual(AgentService.get().runTool(p, "teleport_waystone", args("name", "hell")),
                "Waystone hell is in another dimension.", "cross-dimension");
        h.assertValueEqual(AgentService.get().credits(p), 10, "refusal is free");
        done(h, p);
    }

    @GameTest(template = "corridor")
    public static void deathReturnOncePerDeathWithoutAnAgent(GameTestHelper h) {
        ServerPlayer p = player(h); // no agent: after a death the agent is usually far away
        AgentService s = AgentService.get();
        credits(h, p, 30);
        h.assertValueEqual(s.runTool(p, "death_return", new JsonObject()), "No death position recorded.", "no death yet");
        BlockPos death = h.absolutePos(new BlockPos(2, 0, 12));
        p.setLastDeathLocation(Optional.of(GlobalPos.of(h.getLevel().dimension(), death)));
        h.assertValueEqual(s.runTool(p, "death_return", new JsonObject()), "Teleporting near your death spot.", "return");
        h.assertTrue(p.blockPosition().closerThan(death, 9.0), "near the death spot, at " + p.blockPosition());
        h.assertValueEqual(s.credits(p), 20, "costs 10");
        h.assertValueEqual(s.runTool(p, "death_return", new JsonObject()), "Already used for this death.", "once per death");
        // Fell into the void: the search starts from the surface instead.
        p.setLastDeathLocation(Optional.of(GlobalPos.of(h.getLevel().dimension(), new BlockPos(death.getX(), -200, death.getZ()))));
        h.assertValueEqual(s.runTool(p, "death_return", new JsonObject()), "Teleporting near your death spot.", "void death");
        h.assertTrue(p.getY() > h.getLevel().getMinBuildHeight(), "landed above the void, y=" + p.getY());
        done(h, p);
    }

    @GameTest(template = "empty")
    public static void waystonesSurviveSaveAndLoad(GameTestHelper h) {
        AgentStore s = AgentStore.get(h.getLevel().getServer());
        UUID id = UUID.randomUUID();
        s.create(id, UUID.randomUUID());
        s.addWaystone(id, new AgentStore.Waystone("Mine", GlobalPos.of(Level.NETHER, new BlockPos(5, 6, 7)), 90.0F));
        var registries = h.getLevel().registryAccess();
        AgentStore loaded = AgentStore.load(s.save(new CompoundTag(), registries), registries);
        var w = loaded.get(id).waystone("mine").orElseThrow();
        h.assertTrue(w.name().equals("Mine") && w.pos().dimension().equals(Level.NETHER) && w.pos().pos().equals(new BlockPos(5, 6, 7))
                && w.yaw() == 90.0F, "waystone round trip");
        h.succeed();
    }
}
