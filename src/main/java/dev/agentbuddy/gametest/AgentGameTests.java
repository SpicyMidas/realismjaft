package dev.agentbuddy.gametest;

import dev.agentbuddy.AgentBuddy;
import dev.agentbuddy.ModRegistries;
import dev.agentbuddy.data.AgentStore;
import dev.agentbuddy.entity.AgentEntity;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** M2: agent item, entity, pickup/place, persistence. Templates: agentbuddy:empty (3x3x3 air), agentbuddy:corridor (5x6x20 air). */
@GameTestHolder(AgentBuddy.MODID)
@PrefixGameTestTemplate(false)
public class AgentGameTests {
    private static AgentStore store(GameTestHelper h) {
        return AgentStore.get(h.getLevel().getServer());
    }

    /** Uses the stack in the player's main hand on the top face of the given relative block. */
    private static InteractionResult useItemOn(GameTestHelper h, Player player, ItemStack stack, BlockPos relative) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = h.absolutePos(relative);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
    }

    private static AgentEntity onlyAgent(GameTestHelper h) {
        List<AgentEntity> agents = h.getEntities(ModRegistries.AGENT.get());
        h.assertTrue(agents.size() == 1, "expected exactly 1 agent, found " + agents.size());
        return agents.get(0);
    }

    @GameTest(template = "empty")
    public static void freshItemCreatesAgentOwnedByUser(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ModRegistries.AGENT_ITEM.get());
        h.assertTrue(useItemOn(h, p, stack, new BlockPos(1, 0, 1)) == InteractionResult.CONSUME, "use should succeed");
        h.assertTrue(stack.isEmpty(), "item should be consumed");
        AgentEntity a = onlyAgent(h);
        h.assertTrue(p.getUUID().equals(a.owner()), "owner should be the user");
        AgentStore.Record r = store(h).get(a.agentId());
        h.assertTrue(r != null && r.deployed() && r.owner.equals(p.getUUID()), "store record should exist and be deployed");
        h.assertTrue("Agent".equals(a.getCustomName().getString()), "default name");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftClickPicksUpAndPlacingRestoresSameAgent(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        useItemOn(h, p, new ItemStack(ModRegistries.AGENT_ITEM.get()), new BlockPos(1, 0, 1));
        AgentEntity a = onlyAgent(h);
        UUID id = a.agentId();
        store(h).setName(id, "Lens");

        p.setShiftKeyDown(true);
        h.assertTrue(a.interact(p, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME, "pickup should succeed");
        h.assertTrue(a.isRemoved(), "entity should be gone");
        h.assertFalse(store(h).get(id).deployed(), "record should be marked stored");
        ItemStack carried = p.getInventory().items.stream().filter(s -> id.equals(s.get(ModRegistries.AGENT_ID.get()))).findFirst().orElse(ItemStack.EMPTY);
        h.assertFalse(carried.isEmpty(), "player should hold an item with the agent id");

        p.setShiftKeyDown(false);
        useItemOn(h, p, carried, new BlockPos(1, 0, 1));
        AgentEntity back = h.getEntities(ModRegistries.AGENT.get()).stream().filter(e -> !e.isRemoved()).findFirst().orElseThrow();
        h.assertTrue(id.equals(back.agentId()), "same agent id after placing");
        h.assertTrue("Lens".equals(back.getCustomName().getString()), "name restored from the store");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void duplicatedItemIsRefusedWhileAgentIsOut(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.CREATIVE);
        useItemOn(h, p, new ItemStack(ModRegistries.AGENT_ITEM.get()), new BlockPos(1, 0, 1));
        UUID id = onlyAgent(h).agentId();
        ItemStack copy = dev.agentbuddy.item.AgentItem.stackFor(id);
        h.assertTrue(useItemOn(h, p, copy, new BlockPos(1, 0, 1)) == InteractionResult.FAIL, "second deploy must fail");
        h.assertFalse(copy.isEmpty(), "refused item is not consumed");
        onlyAgent(h);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void onlyOwnerCanPickUp(GameTestHelper h) {
        Player owner = h.makeMockPlayer(GameType.SURVIVAL);
        Player other = h.makeMockPlayer(GameType.SURVIVAL);
        useItemOn(h, owner, new ItemStack(ModRegistries.AGENT_ITEM.get()), new BlockPos(1, 0, 1));
        AgentEntity a = onlyAgent(h);
        other.setShiftKeyDown(true);
        // Mob.interact maps a non-consuming FAIL from mobInteract to PASS; what matters is nothing happened.
        h.assertFalse(a.interact(other, InteractionHand.MAIN_HAND).consumesAction(), "non-owner pickup must not succeed");
        h.assertFalse(a.isRemoved(), "agent stays");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void deathDropsAgentItemEvenWithoutMobLoot(GameTestHelper h) {
        GameRules.BooleanValue mobLoot = h.getLevel().getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        boolean before = mobLoot.get();
        mobLoot.set(false, h.getLevel().getServer());
        try {
            Player p = h.makeMockPlayer(GameType.SURVIVAL);
            useItemOn(h, p, new ItemStack(ModRegistries.AGENT_ITEM.get()), new BlockPos(1, 0, 1));
            AgentEntity a = onlyAgent(h);
            UUID id = a.agentId();
            a.kill();
            List<ItemEntity> drops = h.getEntities(EntityType.ITEM);
            h.assertTrue(drops.stream().anyMatch(e -> id.equals(e.getItem().get(ModRegistries.AGENT_ID.get()))), "agent item should drop");
            h.assertFalse(store(h).get(id).deployed(), "record should be freed for redeploy");
        } finally {
            mobLoot.set(before, h.getLevel().getServer());
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void agentHoversInsteadOfFalling(GameTestHelper h) {
        AgentEntity a = h.spawn(ModRegistries.AGENT.get(), new Vec3(1.5, 2.0, 1.5));
        double startY = a.getY();
        h.runAfterDelay(60, () -> {
            h.assertTrue(a.getY() > startY - 0.3, "agent fell from " + startY + " to " + a.getY());
            h.succeed();
        });
    }

    // Test areas are walled with barriers, so the owner must be inside the (5x6x20) template.
    @GameTest(template = "corridor", timeoutTicks = 300)
    public static void agentFliesToOwner(GameTestHelper h) {
        @SuppressWarnings("removal")
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos far = h.absolutePos(new BlockPos(2, 1, 17));
        p.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        useItemOn(h, p, new ItemStack(ModRegistries.AGENT_ITEM.get()), new BlockPos(2, 0, 2));
        AgentEntity a = onlyAgent(h);
        double start = a.distanceTo(p);
        h.succeedWhen(() -> {
            h.assertTrue(a.distanceTo(p) < 6.0, "still " + a.distanceTo(p) + " blocks away (started " + start + ")");
            h.getLevel().getServer().getPlayerList().remove(p);
        });
    }

    @GameTest(template = "empty")
    public static void entityNbtKeepsIdOwnerAndMode(GameTestHelper h) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        useItemOn(h, p, new ItemStack(ModRegistries.AGENT_ITEM.get()), new BlockPos(1, 0, 1));
        AgentEntity a = onlyAgent(h);
        a.setFollowing(false);
        CompoundTag tag = a.saveWithoutId(new CompoundTag());
        AgentEntity copy = ModRegistries.AGENT.get().create(h.getLevel());
        copy.load(tag);
        h.assertTrue(a.agentId().equals(copy.agentId()) && a.owner().equals(copy.owner()), "id and owner survive NBT");
        h.assertFalse(copy.isFollowing(), "stay mode survives NBT");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void storeSurvivesSaveAndLoad(GameTestHelper h) {
        AgentStore s = store(h);
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        s.create(id, owner);
        s.setName(id, "Lens");
        s.setDeployed(id, true);
        var registries = h.getLevel().registryAccess();
        AgentStore loaded = AgentStore.load(s.save(new CompoundTag(), registries), registries);
        AgentStore.Record r = loaded.get(id);
        h.assertTrue(r != null && r.owner.equals(owner) && r.name().equals("Lens") && r.deployed(), "record round trip");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void craftingRecipeIsLoaded(GameTestHelper h) {
        h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(AgentBuddy.MODID, "agent")).isPresent(),
                "recipe agentbuddy:agent missing");
        h.succeed();
    }
}
