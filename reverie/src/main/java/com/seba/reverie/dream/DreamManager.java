package com.seba.reverie.dream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.network.DreamStatePayload;
import com.seba.reverie.network.DreamTransitionPayload;
import com.seba.reverie.registry.ModAttachments;
import com.seba.reverie.registry.ModComponents;
import com.seba.reverie.registry.ModEffects;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import com.seba.reverie.network.Net;

/**
 * The dream session: falling asleep (capture, backup, strip, teleport), the dream rules, and waking up
 * (keepsakes, restore, return). Server side only. Holds no Level/Player references in static state.
 */
public final class DreamManager {
    public static final ResourceKey<Level> DREAMLAND = ResourceKey.create(Registries.DIMENSION, Reverie.id("dreamland"));

    private static final net.minecraft.resources.ResourceLocation GRAVITY_ID = Reverie.id("dream_gravity");
    private static final net.minecraft.resources.ResourceLocation SAFE_FALL_ID = Reverie.id("dream_safe_fall");
    private static final int SNAPSHOT_MAX_BLOCKS = 500;

    public enum Reason { TIMEOUT, BED, DEATH, VOID, COMMAND, LOGIN_EXPIRED, LEFT_DREAMLAND, ERROR }

    /** Held-item snapshot taken when the player lay down. */
    private record Held(int slot, ItemStack stack) {
    }

    private static final Map<UUID, Held> HELD_AT_BED = new HashMap<>();
    private static final Map<UUID, Reason> PENDING_WAKES = new HashMap<>();
    private static final Map<UUID, Integer> WAKE_POSE = new HashMap<>();
    private static final Map<UUID, Integer> OUTSIDE_TICKS = new HashMap<>();
    private static final Set<UUID> WAKING = new HashSet<>();

    /** Teleport step, replaceable so tests can run without a real dimension change. */
    public interface Teleporter {
        void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yRot, float xRot);
    }

    public static volatile Teleporter teleporter = (p, l, pos, y, x) -> p.teleportTo(l, pos.x, pos.y, pos.z, y, x);

    /** Hooks for later modules (shades, memory island, sky): called after a dream starts / before it ends. */
    public interface Listener {
        default void started(ServerPlayer player, DreamSession session) {
        }

        /** The player logged back in during a dream. */
        default void resumed(ServerPlayer player, DreamSession session) {
        }

        default void ending(ServerPlayer player, DreamSession session, Reason reason) {
        }
    }

    private static final List<Listener> LISTENERS = new ArrayList<>();

    private DreamManager() {
    }

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static DreamData data(Player player) {
        return player.getData(ModAttachments.DREAM.get());
    }

    public static boolean isDreaming(Player player) {
        return data(player).dreaming();
    }

    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static boolean onCooldown(ServerPlayer player) {
        return now(player.server) < data(player).cooldownUntil;
    }

    /** Sleepyness lets this player dream right now (otherwise Sleepyness only helps day sleeping if... it doesn't). */
    public static boolean canDream(ServerPlayer player) {
        return ModEffects.sleepy(player) && !onCooldown(player) && !isDreaming(player)
                && player.server.getLevel(DREAMLAND) != null && !player.level().dimension().equals(DREAMLAND);
    }

    /** Clears the post-dream cooldown (admin/testing). */
    public static void resetCooldown(Player player) {
        data(player).cooldownUntil = 0;
    }

    public static void rememberHeldItem(ServerPlayer player) {
        Inventory inv = player.getInventory();
        HELD_AT_BED.put(player.getUUID(), new Held(inv.selected, inv.getSelected().copy()));
    }

    public static void forget(UUID id) {
        com.seba.reverie.dreamland.FallLoop.forget(id);
        HELD_AT_BED.remove(id);
        WAKE_POSE.remove(id);
        OUTSIDE_TICKS.remove(id);
        PENDING_WAKES.remove(id);
    }

    public static void clearAll() {
        HELD_AT_BED.clear();
        PENDING_WAKES.clear();
        WAKE_POSE.clear();
        OUTSIDE_TICKS.clear();
        WAKING.clear();
    }

    public static void scheduleWake(ServerPlayer player, Reason reason) {
        PENDING_WAKES.putIfAbsent(player.getUUID(), reason);
    }

    public static boolean inWakePose(UUID id) {
        return WAKE_POSE.containsKey(id);
    }

    // ------------------------------------------------------------------ falling asleep

    /** Starts a dream. {@code bed} is null when started by command. Returns false if nothing happened. */
    public static boolean startDream(ServerPlayer player, BlockPos bed) {
        // Never from inside Dreamland: the wake-up point would be the dream itself.
        if (isDreaming(player) || !player.isAlive() || player.level().dimension().equals(DREAMLAND)) return false;
        MinecraftServer server = player.server;
        ServerLevel dream = server.getLevel(DREAMLAND);
        if (dream == null) {
            Reverie.LOGGER.error("Dreamland dimension is missing; {} cannot dream", player.getName().getString());
            return false;
        }
        DreamData data = data(player);
        player.closeContainer();
        ServerLevel origin = player.serverLevel();
        if (player.isSleeping()) player.stopSleepInBed(true, true);

        Inventory inv = player.getInventory();
        int heldSlot = findHeldSlot(player);
        // A Dream Catcher stays home even in hand: it works from your inventory.
        if (heldSlot >= 0 && inv.getItem(heldSlot).getItem() instanceof com.seba.reverie.content.DreamCatcherItem) heldSlot = -1;
        ItemStack held = heldSlot >= 0 ? inv.getItem(heldSlot).copy() : ItemStack.EMPTY;
        List<ItemStack> items = new ArrayList<>(DreamSession.SLOTS);
        for (int i = 0; i < DreamSession.SLOTS; i++) items.add(i == heldSlot ? ItemStack.EMPTY : inv.getItem(i).copy());

        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance e : player.getActiveEffects()) {
            if (!e.getEffect().is(ModEffects.SLEEPYNESS) && !e.getEffect().is(ModEffects.EVIL_SLEEPYNESS)) effects.add(new MobEffectInstance(e));
        }
        var vitals = new DreamSession.Vitals(player.getHealth(), player.getFoodData().getFoodLevel(),
                player.getFoodData().getSaturationLevel(), player.experienceLevel, player.experienceProgress,
                player.totalExperience, effects);
        var ret = new DreamSession.ReturnPoint(Optional.ofNullable(bed).map(BlockPos::immutable), origin.dimension(),
                player.position(), player.getYRot());
        long now = now(server);
        long end = now + ReverieConfig.get(ReverieConfig.DREAM_LENGTH_TICKS);
        List<DreamSession.SnapshotBlock> snapshot = bed != null && ReverieConfig.get(ReverieConfig.MEMORY_ISLAND)
                ? snapshot(origin, bed) : List.of();
        boolean catcher = items.stream().anyMatch(com.seba.reverie.content.DreamCatcherItem::isOpen);
        boolean nightmare = player.hasEffect(ModEffects.EVIL_SLEEPYNESS);
        DreamSession session = new DreamSession(new DreamSession.Stash(items, inv.selected, heldSlot, held), vitals, ret,
                now, end, snapshot, catcher, nightmare);

        // Nothing is taken from the player before the backup exists on disk.
        if (DreamBackup.write(player, session, now) == null) {
            player.displayClientMessage(Component.translatable("reverie.dream.backup_failed").withStyle(ChatFormatting.RED), false);
            return false;
        }
        data.session = session;
        data.lastBackupTime = now;
        HELD_AT_BED.remove(player.getUUID());

        try {
            inv.clearContent();
            if (heldSlot >= 0 && !held.isEmpty()) {
                ItemStack carried = held.copy();
                carried.set(ModComponents.DREAM_ORIGIN.get(), Unit.INSTANCE);
                inv.setItem(heldSlot, carried);
                inv.selected = heldSlot;
            }
            player.removeAllEffects();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(5f);
            player.experienceLevel = 0;
            player.experienceProgress = 0;
            player.totalExperience = 0;
            player.clearFire();
            player.fallDistance = 0;
            applyDreamAttributes(player);
            Net.send(player, new DreamTransitionPayload(DreamTransitionPayload.ENTER));

            BlockPos home = arrival(dream, player.getUUID(), nightmare);
            teleporter.teleport(player, dream, Vec3.atBottomCenterOf(home), player.getYRot(), 0);
            syncState(player);
            player.inventoryMenu.broadcastChanges();

            for (Listener l : LISTENERS) safely(() -> l.started(player, session));
            return true;
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Starting the dream failed for {}; restoring everything", player.getName().getString(), e);
            emergencyRestore(player, session);
            return false;
        }
    }

    /** Where a dreamer arrives: Home, or for Evil Sleepyness the nearest nightmare in Deep Sleep. */
    public static BlockPos arrival(ServerLevel dream, UUID id, boolean nightmare) {
        if (nightmare) {
            BlockPos spot = com.seba.reverie.dreamland.IslandSearch.nightmareSpot(dream, HomeIsland.homeColumn(id));
            if (spot != null) return spot;
        }
        return HomeIsland.findOrBuild(dream, id);
    }

    private static int findHeldSlot(ServerPlayer player) {
        Inventory inv = player.getInventory();
        Held held = HELD_AT_BED.get(player.getUUID());
        if (held == null) return inv.getSelected().isEmpty() ? -1 : inv.selected;
        ItemStack inSlot = inv.getItem(held.slot());
        if (!held.stack().isEmpty() && ItemStack.isSameItemSameComponents(inSlot, held.stack())) return held.slot();
        if (held.stack().isEmpty()) return -1;
        for (int i = 0; i < 9; i++) if (ItemStack.isSameItemSameComponents(inv.getItem(i), held.stack())) return i;
        return -1; // it moved somewhere unexpected: take nothing rather than risk a duplicate
    }

    /** Block states only (no block entities, no inventories, no text) of a 9x6x9 box around the bed. */
    private static List<DreamSession.SnapshotBlock> snapshot(ServerLevel level, BlockPos bed) {
        List<DreamSession.SnapshotBlock> out = new ArrayList<>();
        try {
            for (int y = -1; y <= 4 && out.size() < SNAPSHOT_MAX_BLOCKS; y++) {
                for (int x = -4; x <= 4 && out.size() < SNAPSHOT_MAX_BLOCKS; x++) {
                    for (int z = -4; z <= 4 && out.size() < SNAPSHOT_MAX_BLOCKS; z++) {
                        BlockPos p = bed.offset(x, y, z);
                        if (!level.isLoaded(p)) continue;
                        BlockState state = level.getBlockState(p);
                        if (state.isAir() || state.hasBlockEntity() && !(state.getBlock() instanceof BedBlock)) continue;
                        if (!state.getFluidState().isEmpty() && state.getFluidState().isSource()) continue;
                        out.add(new DreamSession.SnapshotBlock(new BlockPos(x, y, z), state));
                    }
                }
            }
        } catch (RuntimeException e) {
            Reverie.LOGGER.warn("Bed snapshot failed; the Memory Island will be skipped", e);
            return List.of();
        }
        return out;
    }

    public static void applyDreamAttributes(ServerPlayer player) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity != null && !gravity.hasModifier(GRAVITY_ID)) {
            gravity.addTransientModifier(new AttributeModifier(GRAVITY_ID, -0.45, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance safeFall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (safeFall != null && !safeFall.hasModifier(SAFE_FALL_ID)) {
            safeFall.addTransientModifier(new AttributeModifier(SAFE_FALL_ID, 64, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    public static void removeDreamAttributes(ServerPlayer player) {
        com.seba.reverie.dreamland.BiomeMechanics.clear(player);
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity != null) gravity.removeModifier(GRAVITY_ID);
        AttributeInstance safeFall = player.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (safeFall != null) safeFall.removeModifier(SAFE_FALL_ID);
    }

    // ------------------------------------------------------------------ while dreaming

    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        Integer pose = WAKE_POSE.get(id);
        if (pose != null) {
            if (pose <= 0 || !player.isSleeping()) {
                WAKE_POSE.remove(id);
                try {
                    if (player.isSleeping()) player.stopSleepInBed(false, true);
                } catch (RuntimeException e) {
                    Reverie.LOGGER.warn("Could not end the wake-up pose for {}", player.getName().getString(), e);
                }
            } else {
                WAKE_POSE.put(id, pose - 1);
            }
        }
        Reason pending = PENDING_WAKES.remove(id);
        if (pending != null) {
            wake(player, pending);
            return;
        }
        DreamData data = data(player);
        if (data.session == null) return;
        if (!player.level().dimension().equals(DREAMLAND)) {
            int outside = OUTSIDE_TICKS.merge(id, 1, Integer::sum);
            if (outside > 40) wake(player, Reason.LEFT_DREAMLAND);
            return;
        }
        OUTSIDE_TICKS.remove(id);
        var food = player.getFoodData();
        food.setFoodLevel(20);
        food.setSaturation(Math.max(food.getSaturationLevel(), 5f));
        food.setExhaustion(0);
        com.seba.reverie.dreamland.FallLoop.track(player);
        if (player.getY() < player.level().getMinBuildHeight() + com.seba.reverie.dreamland.FallLoop.DEPTH) {
            // Falling never wakes you: you come out of the bottom of the dream above where you fell from.
            if (ReverieConfig.get(ReverieConfig.FALL_LOOP)) {
                com.seba.reverie.dreamland.FallLoop.loop(player, data.session);
            } else if (player.getY() < player.level().getMinBuildHeight() + 4) {
                wake(player, Reason.VOID);
                return;
            }
        }
        com.seba.reverie.dreamland.BiomeMechanics.tick(player, data.session);
        long left = data.session.endTime() - now(player.server);
        if (left <= 0) {
            wake(player, Reason.TIMEOUT);
            return;
        }
        if (player.tickCount % 20 == 0) syncState(player);
    }

    public static void syncState(ServerPlayer player) {
        DreamSession s = data(player).session;
        if (s == null) {
            Net.send(player, new DreamStatePayload(false, 0, 0));
            return;
        }
        long left = Math.max(0, s.endTime() - now(player.server));
        Net.send(player, new DreamStatePayload(true, (int) Math.min(Integer.MAX_VALUE, left),
                (int) Math.max(1, s.endTime() - s.startTime())));
    }

    /** Player logged in: resume a dream with time left, or wake them now (also after a server crash). */
    public static void onLogin(ServerPlayer player) {
        DreamData data = data(player);
        if (data.session == null) return;
        com.seba.reverie.dreamland.BiomeMechanics.clear(player); // stale biome rules from before the logout
        if (now(player.server) >= data.session.endTime()) {
            scheduleWake(player, Reason.LOGIN_EXPIRED);
            return;
        }
        applyDreamAttributes(player);
        ServerLevel dream = player.server.getLevel(DREAMLAND);
        if (dream != null && !player.level().dimension().equals(DREAMLAND)) {
            try {
                BlockPos home = arrival(dream, player.getUUID(), data.session.nightmare());
                teleporter.teleport(player, dream, Vec3.atBottomCenterOf(home), player.getYRot(), 0);
            } catch (RuntimeException e) {
                Reverie.LOGGER.error("Could not return {} to their dream; waking them", player.getName().getString(), e);
                scheduleWake(player, Reason.ERROR);
                return;
            }
        }
        syncState(player);
        for (Listener l : LISTENERS) safely(() -> l.resumed(player, data.session));
    }

    // ------------------------------------------------------------------ waking up

    public static void wake(ServerPlayer player, Reason reason) {
        DreamData data = data(player);
        DreamSession session = data.session;
        if (session == null || !WAKING.add(player.getUUID())) return;
        boolean restored = false;
        try {
            for (Listener l : LISTENERS) safely(() -> l.ending(player, session, reason));
            Net.send(player, new DreamTransitionPayload(DreamTransitionPayload.WAKE));
            player.closeContainer();
            Inventory inv = player.getInventory();
            List<ItemStack> dreamItems = new ArrayList<>();
            for (int i = 0; i < inv.getContainerSize(); i++) dreamItems.add(inv.getItem(i));
            dreamItems.add(player.containerMenu.getCarried());
            ItemStack caught = session.dreamCatcher() ? Keepsakes.catchOne(dreamItems, inv.getSelected(), player.getRandom()) : ItemStack.EMPTY;
            List<ItemStack> keepsakes = Keepsakes.roll(dreamItems, player.getRandom(), caught);
            caught = caught.isEmpty() ? caught : Keepsakes.clean(caught);

            restoreInventory(player, session);
            restored = true;
            data.session = null;
            data.cooldownUntil = now(player.server) + ReverieConfig.get(ReverieConfig.DREAM_COOLDOWN_TICKS);
            data.dreamsCompleted++;
            if (player.isSleeping()) player.stopSleepInBed(true, false);
            removeDreamAttributes(player);
            restoreVitals(player, session);
            player.fallDistance = 0;

            returnToWorld(player, session);
            for (ItemStack k : keepsakes) {
                if (!inv.add(k)) player.drop(k, false);
            }
            if (!caught.isEmpty()) giveCaught(player, caught);
            if (!keepsakes.isEmpty()) {
                player.displayClientMessage(Component.translatable("reverie.dream.keepsake", keepsakes.get(0).getHoverName())
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 0, false, false, true));
            syncState(player);
            player.inventoryMenu.broadcastChanges();
            com.seba.reverie.dream.DreamStats.record(player, reason, keepsakes);
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Waking {} failed ({}); falling back to a plain restore", player.getName().getString(), reason, e);
            if (!restored) emergencyRestore(player, session);
            else plainReturn(player, session);
        } finally {
            WAKING.remove(player.getUUID());
            OUTSIDE_TICKS.remove(player.getUUID());
        }
    }

    /** Closes the first open Dream Catcher in the inventory around the catch (or just gives it, if there is none). */
    private static void giveCaught(ServerPlayer player, ItemStack caught) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (com.seba.reverie.content.DreamCatcherItem.isOpen(stack)) {
                com.seba.reverie.content.DreamCatcherItem.close(stack, caught);
                player.displayClientMessage(Component.translatable("reverie.dream.caught", caught.getHoverName())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC), false);
                return;
            }
        }
        if (!inv.add(caught)) player.drop(caught, false);
    }

    private static void restoreInventory(ServerPlayer player, DreamSession session) {
        Inventory inv = player.getInventory();
        inv.clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        List<ItemStack> items = session.stash().items();
        for (int i = 0; i < Math.min(items.size(), inv.getContainerSize()); i++) inv.setItem(i, items.get(i).copy());
        inv.selected = Math.max(0, Math.min(8, session.stash().selectedSlot()));
    }

    private static void restoreVitals(ServerPlayer player, DreamSession session) {
        var v = session.vitals();
        player.removeAllEffects();
        for (MobEffectInstance e : v.effects()) player.addEffect(new MobEffectInstance(e));
        player.setHealth(Math.max(1f, Math.min(player.getMaxHealth(), v.health())));
        player.getFoodData().setFoodLevel(v.food());
        player.getFoodData().setSaturation(v.saturation());
        player.experienceLevel = v.xpLevel();
        player.experienceProgress = v.xpProgress();
        player.totalExperience = v.xpTotal();
    }

    /** Last resort: items and vitals back, session cleared, player put somewhere safe. Never throws. */
    private static void emergencyRestore(ServerPlayer player, DreamSession session) {
        try {
            restoreInventory(player, session);
            DreamData data = data(player);
            data.session = null;
            removeDreamAttributes(player);
            restoreVitals(player, session);
            player.inventoryMenu.broadcastChanges();
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Emergency restore failed for {}; their items are in reverie_backups", player.getName().getString(), e);
        }
        plainReturn(player, session);
    }

    private static void plainReturn(ServerPlayer player, DreamSession session) {
        try {
            ServerLevel level = player.server.getLevel(session.ret().dimension());
            if (level == null) level = player.server.overworld();
            Vec3 pos = session.ret().pos().equals(Vec3.ZERO) ? Vec3.atBottomCenterOf(worldSpawn(level)) : session.ret().pos();
            teleporter.teleport(player, level, pos, session.ret().yRot(), 0);
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Could not move {} back from the dream", player.getName().getString(), e);
        }
    }

    private static void returnToWorld(ServerPlayer player, DreamSession session) {
        MinecraftServer server = player.server;
        ServerLevel level = server.getLevel(session.ret().dimension());
        if (level == null) level = server.overworld();
        var ret = session.ret();
        if (ret.bed().isPresent()) {
            BlockPos bed = ret.bed().get();
            level.getChunk(bed.getX() >> 4, bed.getZ() >> 4);
            BlockState state = level.getBlockState(bed);
            if (state.getBlock() instanceof BedBlock) {
                Vec3 stand = BedBlock.findStandUpPosition(EntityType.PLAYER, level, bed, state.getValue(BedBlock.FACING), ret.yRot())
                        .orElse(Vec3.atBottomCenterOf(bed.above()));
                teleporter.teleport(player, level, stand, ret.yRot(), 0);
                try {
                    if (player.level() == level && !state.getValue(BedBlock.OCCUPIED)) {
                        player.startSleeping(bed);
                        WAKE_POSE.put(player.getUUID(), 30);
                    }
                } catch (RuntimeException e) {
                    Reverie.LOGGER.warn("Wake-up pose failed for {}", player.getName().getString(), e);
                }
                return;
            }
        }
        if (!ret.pos().equals(Vec3.ZERO) && ret.bed().isEmpty()) {
            teleporter.teleport(player, level, ret.pos(), ret.yRot(), 0);
            return;
        }
        // Bed destroyed: respawn point, then world spawn (vanilla's own respawn search; anchors are not used up).
        try {
            var transition = player.findRespawnPositionAndUseSpawnBlock(true, net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING);
            teleporter.teleport(player, transition.newLevel(), transition.pos(), transition.yRot(), transition.xRot());
        } catch (RuntimeException e) {
            ServerLevel overworld = server.overworld();
            teleporter.teleport(player, overworld, Vec3.atBottomCenterOf(worldSpawn(overworld)), 0, 0);
        }
    }

    private static BlockPos worldSpawn(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        level.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
    }

    private static void safely(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Dream listener failed", e);
        }
    }
}
