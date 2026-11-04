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
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M4: list/find/fetch/snapshot, ignore list, double chests, unopened loot chests. Uses agentbuddy:corridor.
 * Test areas sit 5 blocks apart and are never cleared, so neighbours' chests are within find_item's 32-block
 * range: every test uses an item no other test uses, and absence checks accept either absence reply.
 */
@GameTestHolder(AgentBuddy.MODID)
@PrefixGameTestTemplate(false)
public class ContainerGameTests {
    /** Mock player at relative (2,1,2) looking +Z at eye level (blocks at relative y=2 are in sight). */
    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Vec3 at = h.absoluteVec(new Vec3(2.5, 1, 2.5));
        p.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return p;
    }

    private static AgentEntity agentFor(GameTestHelper h, ServerPlayer owner) {
        AgentEntity a = h.spawn(ModRegistries.AGENT.get(), new Vec3(1.5, 3, 2.5));
        UUID id = UUID.randomUUID();
        AgentStore.get(h.getLevel().getServer()).create(id, owner.getUUID());
        a.bind(id, owner.getUUID(), "Agent");
        return a;
    }

    private static void credits(GameTestHelper h, ServerPlayer p, int n) {
        CreditStore.get(h.getLevel().getServer()).credits().set(p.getUUID().toString(), n);
    }

    private static Container chest(GameTestHelper h, BlockPos rel, ItemStack... items) {
        h.setBlock(rel, Blocks.CHEST);
        return fill(h, rel, items);
    }

    private static Container fill(GameTestHelper h, BlockPos rel, ItemStack... items) {
        Container c = (Container) h.getLevel().getBlockEntity(h.absolutePos(rel));
        for (int i = 0; i < items.length; i++) c.setItem(i, items[i]);
        return c;
    }

    private static String at(GameTestHelper h, BlockPos rel) {
        BlockPos a = h.absolutePos(rel);
        return a.getX() + ", " + a.getY() + ", " + a.getZ();
    }

    private static JsonObject args(String key, Object value) {
        JsonObject o = new JsonObject();
        if (value instanceof Number n) o.addProperty(key, n);
        else o.addProperty(key, value.toString());
        return o;
    }

    private static void assertOneOf(GameTestHelper h, String actual, String what, String... options) {
        h.assertTrue(java.util.Arrays.asList(options).contains(actual), what + ": got \"" + actual + "\"");
    }

    private static void done(GameTestHelper h, ServerPlayer... players) {
        for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    @GameTest(template = "corridor")
    public static void findItemPicksTheContainerWithTheMost(GameTestHelper h) {
        ServerPlayer p = player(h);
        agentFor(h, p);
        credits(h, p, 10);
        chest(h, new BlockPos(2, 1, 8), new ItemStack(Items.AMETHYST_SHARD, 12));
        h.setBlock(new BlockPos(3, 1, 10), Blocks.BARREL);
        fill(h, new BlockPos(3, 1, 10), new ItemStack(Items.AMETHYST_SHARD, 3));
        h.assertValueEqual(AgentService.get().runTool(p, "find_item", args("item", "Amethyst Shards")),
                "12 amethyst shards in chest at " + at(h, new BlockPos(2, 1, 8)) + " (+1 more).", "best container");
        done(h, p);
    }

    @GameTest(template = "corridor")
    public static void doubleChestIsOneContainer(GameTestHelper h) {
        ServerPlayer p = player(h);
        agentFor(h, p);
        credits(h, p, 10);
        // Facing north, the LEFT half connects east, so LEFT at x=1 and RIGHT at x=2.
        h.setBlock(new BlockPos(1, 2, 5), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.LEFT));
        h.setBlock(new BlockPos(2, 2, 5), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH).setValue(ChestBlock.TYPE, ChestType.RIGHT));
        fill(h, new BlockPos(1, 2, 5), new ItemStack(Items.EMERALD, 10));
        fill(h, new BlockPos(2, 2, 5), new ItemStack(Items.EMERALD, 5));
        AgentService s = AgentService.get();
        String canonical = at(h, new BlockPos(1, 2, 5));
        h.assertValueEqual(s.runTool(p, "find_item", args("item", "emerald")), "15 emeralds in chest at " + canonical + ".", "merged count");
        // The player looks at the right half (x=2); both halves are one container.
        h.assertValueEqual(s.runTool(p, "list_container", new JsonObject()), "Chest: 15 emeralds.", "list merged");
        h.assertValueEqual(s.runTool(p, "ignore_block", new JsonObject()), "Ignoring chest at " + canonical + ".", "ignore canonical half");
        h.assertValueEqual(s.runTool(p, "list_container", new JsonObject()), "No container there.", "looked-at half is ignored too");
        h.assertValueEqual(s.runTool(p, "look_at", new JsonObject()), "Can't see anything there.", "other half hidden too");
        done(h, p);
    }

    @GameTest(template = "corridor")
    public static void unopenedLootChestIsUnknownAndStaysUnopened(GameTestHelper h) {
        ServerPlayer p = player(h);
        agentFor(h, p);
        credits(h, p, 10);
        h.setBlock(new BlockPos(2, 2, 5), Blocks.CHEST);
        ChestBlockEntity loot = (ChestBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2, 2, 5)));
        loot.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON, 42L);
        AgentService s = AgentService.get();
        h.assertValueEqual(s.runTool(p, "list_container", new JsonObject()), "Contents unknown until opened.", "list");
        h.assertValueEqual(s.runTool(p, "find_item", args("item", "nautilus_shell")), "No nautilus shell in known containers.", "find skips unknown");
        h.assertTrue(loot.getLootTable() != null, "scanning must not generate the loot");
        done(h, p);
    }

    @GameTest(template = "corridor")
    public static void ignoredContainerDoesNotExistForTheAgent(GameTestHelper h) {
        ServerPlayer p = player(h);
        AgentEntity a = agentFor(h, p);
        credits(h, p, 10);
        BlockPos rel = new BlockPos(2, 2, 5);
        chest(h, rel, new ItemStack(Items.ECHO_SHARD, 12));
        AgentService s = AgentService.get();
        h.assertValueEqual(s.runTool(p, "ignore_block", new JsonObject()), "Ignoring chest at " + at(h, rel) + ".", "ignore");
        h.assertValueEqual(s.runTool(p, "ignore_block", new JsonObject()), "Already ignored.", "ignore twice");
        h.assertValueEqual(s.runTool(p, "look_at", new JsonObject()), "Can't see anything there.", "look_at");
        assertOneOf(h, s.runTool(p, "find_item", args("item", "echo_shard")), "find_item",
                "I don't see any containers.", "No echo shard in known containers.");
        assertOneOf(h, s.runTool(p, "fetch_item", args("item", "echo_shard")), "fetch_item",
                "I don't see that chest.", "No echo shard in known containers.");
        h.assertValueEqual(s.runTool(p, "list_container", new JsonObject()), "No container there.", "list_container");
        h.assertValueEqual(s.runTool(p, "snapshot_container", new JsonObject()), "No container there.", "snapshot");
        h.assertTrue(AgentStore.get(h.getLevel().getServer()).get(a.agentId())
                .isIgnored(GlobalPos.of(h.getLevel().dimension(), h.absolutePos(rel))), "stored in the agent record");
        BlockPos abs = h.absolutePos(rel);
        JsonObject xyz = new JsonObject();
        xyz.addProperty("x", abs.getX());
        xyz.addProperty("y", abs.getY());
        xyz.addProperty("z", abs.getZ());
        h.assertValueEqual(s.runTool(p, "unignore_block", xyz), "No longer ignoring chest at " + at(h, rel) + ".", "unignore by coords");
        h.assertValueEqual(s.runTool(p, "find_item", args("item", "echo_shard")), "12 echo shards in chest at " + at(h, rel) + ".", "visible again");
        done(h, p);
    }

    @GameTest(template = "corridor")
    public static void findItemCostsOneCreditOnlyOnSuccessAndHasCooldown(GameTestHelper h) {
        ServerPlayer p = player(h);
        agentFor(h, p);
        chest(h, new BlockPos(2, 1, 8), new ItemStack(Items.PRISMARINE_SHARD, 12));
        AgentService s = AgentService.get();
        h.assertValueEqual(s.runTool(p, "find_item", args("item", "prismarine_shard")), "Need 1 credits, have 0.", "broke");
        credits(h, p, 5);
        h.assertValueEqual(s.runTool(p, "find_item", args("item", "heart_of_the_sea")), "No heart of the sea in known containers.", "absent");
        h.assertValueEqual(s.credits(p), 5, "a failed search is free");
        h.assertTrue(s.runTool(p, "find_item", args("item", "prismarine_shard")).startsWith("12 prismarine shards"), "found");
        h.assertValueEqual(s.credits(p), 4, "success costs 1");
        h.assertValueEqual(s.runTool(p, "find_item", args("item", "prismarine_shard")), "Wait 5 s.", "cooldown");
        done(h, p);
    }

    @GameTest(template = "corridor", timeoutTicks = 500)
    public static void fetchFliesOverAndBringsTheRealStacks(GameTestHelper h) {
        ServerPlayer p = player(h);
        agentFor(h, p);
        credits(h, p, 10);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Lens Cutter"));
        Container c = chest(h, new BlockPos(2, 1, 14), new ItemStack(Items.DIAMOND, 12), sword);
        AgentService s = AgentService.get();
        h.assertValueEqual(s.runTool(p, "fetch_item", args("item", "diamond_sword")),
                "Bringing 1 diamond sword from chest at " + at(h, new BlockPos(2, 1, 14)) + ".", "reply now");
        h.assertValueEqual(s.runTool(p, "fetch_item", args("item", "diamond")), "Wait 10 s.", "fetch cooldown");
        h.succeedWhen(() -> {
            ItemStack got = p.getInventory().items.stream().filter(st -> st.is(Items.DIAMOND_SWORD)).findFirst().orElse(ItemStack.EMPTY);
            h.assertFalse(got.isEmpty(), "sword not delivered yet");
            h.assertValueEqual(got.getHoverName().getString(), "Lens Cutter", "custom name kept");
            h.assertTrue(c.getItem(1).isEmpty(), "sword left the chest");
            h.assertValueEqual(c.getItem(0).getCount(), 12, "diamonds untouched");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "corridor", timeoutTicks = 500)
    public static void agentKilledMidFetchDropsWhatItCarries(GameTestHelper h) {
        ServerPlayer p = player(h);
        AgentEntity a = agentFor(h, p);
        credits(h, p, 10);
        chest(h, new BlockPos(2, 1, 16), new ItemStack(Items.GOLD_INGOT, 12));
        AgentService.get().runTool(p, "fetch_item", args("item", "gold ingots"));
        h.startSequence()
                .thenWaitUntil(() -> h.assertFalse(a.carriedItems().isEmpty(), "not carrying yet"))
                .thenExecute(a::kill)
                .thenExecuteAfter(1, () -> h.assertTrue(h.getEntities(EntityType.ITEM).stream()
                        .anyMatch(e -> e.getItem().is(Items.GOLD_INGOT) && e.getItem().getCount() == 12), "carried gold dropped"))
                .thenExecute(() -> h.getLevel().getServer().getPlayerList().remove(p))
                .thenSucceed();
    }

    @GameTest(template = "corridor")
    public static void snapshotChargesAndRepliesWithoutChangingTheChest(GameTestHelper h) {
        ServerPlayer p = player(h);
        agentFor(h, p);
        credits(h, p, 3);
        Container c = chest(h, new BlockPos(2, 2, 5), new ItemStack(Items.DIAMOND, 12));
        h.assertValueEqual(AgentService.get().runTool(p, "snapshot_container", new JsonObject()),
                "Snapshot of chest at " + at(h, new BlockPos(2, 2, 5)) + ".", "reply");
        h.assertValueEqual(AgentService.get().credits(p), 2, "costs 1");
        h.assertValueEqual(c.getItem(0).getCount(), 12, "read-only");
        done(h, p);
    }

    @GameTest(template = "empty")
    public static void ignoreListSurvivesSaveAndLoad(GameTestHelper h) {
        AgentStore s = AgentStore.get(h.getLevel().getServer());
        UUID id = UUID.randomUUID();
        s.create(id, UUID.randomUUID());
        GlobalPos g = GlobalPos.of(h.getLevel().dimension(), new BlockPos(1, 2, 3));
        s.ignore(id, g);
        var registries = h.getLevel().registryAccess();
        AgentStore loaded = AgentStore.load(s.save(new CompoundTag(), registries), registries);
        h.assertTrue(loaded.get(id).isIgnored(g), "ignored position round trip");
        h.succeed();
    }
}
