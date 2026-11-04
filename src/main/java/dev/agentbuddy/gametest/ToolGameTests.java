package dev.agentbuddy.gametest;

import com.google.gson.JsonObject;
import dev.agentbuddy.AgentBuddy;
import dev.agentbuddy.ModRegistries;
import dev.agentbuddy.data.AgentStore;
import dev.agentbuddy.data.CreditStore;
import dev.agentbuddy.entity.AgentEntity;
import dev.agentbuddy.server.AgentService;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** M3: real tools through AgentService (the same path GUI packets take), credits from XP. */
@GameTestHolder(AgentBuddy.MODID)
@PrefixGameTestTemplate(false)
public class ToolGameTests {
    /** A mock player standing at relative (2,1,2), looking along +Z (south) at eye level. */
    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(new Vec3(2.5, 1, 2.5));
        p.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return p;
    }

    private static void remove(GameTestHelper h, ServerPlayer p) {
        h.getLevel().getServer().getPlayerList().remove(p);
    }

    private static AgentEntity spawnAgentFor(GameTestHelper h, ServerPlayer owner) {
        AgentEntity a = h.spawn(ModRegistries.AGENT.get(), new Vec3(1.5, 2, 1.5));
        UUID id = UUID.randomUUID();
        AgentStore.get(h.getLevel().getServer()).create(id, owner.getUUID());
        a.bind(id, owner.getUUID(), "Agent");
        return a;
    }

    private static JsonObject args(String key, String value) {
        JsonObject o = new JsonObject();
        o.addProperty(key, value);
        return o;
    }

    @GameTest(template = "corridor")
    public static void lookAtStatesTheRealBlockAndRejectsFalseClaim(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.setBlock(new BlockPos(2, 2, 5), Blocks.STONE);
        AgentService s = AgentService.get();
        h.assertValueEqual(s.runTool(p, "look_at", args("claim", "wood")), "No. That's stone.", "false claim");
        h.assertValueEqual(s.runTool(p, "look_at", args("claim", "stone")), "That's stone.", "true claim");
        h.assertValueEqual(s.runTool(p, "look_at", new JsonObject()), "That's stone.", "no claim");
        h.setBlock(new BlockPos(2, 2, 5), Blocks.OAK_LOG);
        h.assertValueEqual(s.runTool(p, "look_at", args("claim", "wood")), "That's oak log.", "wood matches #logs");
        remove(h, p);
        h.succeed();
    }

    @GameTest(template = "corridor")
    public static void lookAtWithNothingInReach(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.assertValueEqual(AgentService.get().runTool(p, "look_at", args("claim", "wood")), "Can't see anything there.", "empty view");
        remove(h, p);
        h.succeed();
    }

    @GameTest(template = "corridor")
    public static void stayAndFollowChangeTheAgentMode(GameTestHelper h) {
        ServerPlayer p = player(h);
        AgentEntity a = spawnAgentFor(h, p);
        AgentService s = AgentService.get();
        h.assertValueEqual(s.runTool(p, "stay", new JsonObject()), "Staying here.", "stay reply");
        h.assertFalse(a.isFollowing(), "agent should stay");
        h.assertValueEqual(s.runTool(p, "follow", new JsonObject()), "Following.", "follow reply");
        h.assertTrue(a.isFollowing(), "agent should follow");
        remove(h, p);
        h.succeed();
    }

    @GameTest(template = "corridor")
    public static void setNameUpdatesEntityAndStoreAndStripsFormatting(GameTestHelper h) {
        ServerPlayer p = player(h);
        AgentEntity a = spawnAgentFor(h, p);
        h.assertValueEqual(AgentService.get().runTool(p, "set_name", args("name", "§4Lens  the  Great and Very Long Name")),
                "Name set to Lens the Great and Very.", "sanitized and capped at 24 chars");
        h.assertValueEqual(a.getCustomName().getString(), "Lens the Great and Very", "entity name");
        h.assertValueEqual(AgentStore.get(h.getLevel().getServer()).get(a.agentId()).name(), "Lens the Great and Very", "store name");
        remove(h, p);
        h.succeed();
    }

    @GameTest(template = "corridor")
    public static void otherPlayersAgentCannotBeCommanded(GameTestHelper h) {
        ServerPlayer owner = player(h);
        ServerPlayer other = player(h);
        AgentEntity a = spawnAgentFor(h, owner);
        h.assertValueEqual(AgentService.get().runTool(other, "stay", new JsonObject()), "No agent nearby.", "not theirs");
        h.assertTrue(a.isFollowing(), "agent unchanged");
        remove(h, owner);
        remove(h, other);
        h.succeed();
    }

    @GameTest(template = "corridor")
    public static void unknownToolOrBadArgsIsNotRun(GameTestHelper h) {
        ServerPlayer p = player(h);
        AgentService s = AgentService.get();
        h.assertValueEqual(s.runTool(p, "give_diamonds", new JsonObject()), "Didn't understand.", "unknown tool");
        h.assertValueEqual(s.runTool(p, "set_name", new JsonObject()), "Didn't understand.", "missing argument");
        remove(h, p);
        h.succeed();
    }

    @GameTest(template = "corridor")
    public static void collectedXpEarnsCreditsAndIsCapped(GameTestHelper h) {
        ServerPlayer p = player(h);
        AgentService s = AgentService.get();
        int before = s.credits(p);
        // The real pickup path: ExperienceOrb.playerTouch fires PlayerXpEvent.PickupXp.
        ExperienceOrb orb = new ExperienceOrb(h.getLevel(), p.getX(), p.getY(), p.getZ(), 9);
        h.getLevel().addFreshEntity(orb);
        orb.playerTouch(p);
        h.assertValueEqual(s.credits(p), before + 2, "9 XP at 4 XP/credit = 2 credits (1 XP carried over)");
        s.onXpPickup(p, 3);
        h.assertValueEqual(s.credits(p), before + 3, "carried XP counts toward the next credit");
        s.onXpPickup(p, 100_000);
        h.assertValueEqual(s.credits(p), AgentService.MAX_CREDITS, "capped");
        remove(h, p);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creditsSurviveSaveAndLoad(GameTestHelper h) {
        CreditStore store = new CreditStore();
        store.credits().set("player-a", 42);
        var registries = h.getLevel().registryAccess();
        CreditStore loaded = CreditStore.load(store.save(new CompoundTag(), registries), registries);
        h.assertValueEqual(loaded.credits().balance("player-a"), 42, "credits round trip");
        h.succeed();
    }
}
