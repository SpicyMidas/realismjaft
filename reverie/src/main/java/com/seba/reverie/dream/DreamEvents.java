package com.seba.reverie.dream;

import com.seba.reverie.Reverie;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Wires the dream session into vanilla sleeping, damage, login and interaction. No Mixins needed. */
public final class DreamEvents {
    private DreamEvents() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, DreamEvents::allowDaySleep);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DreamEvents::snapshotHeldItem);
        NeoForge.EVENT_BUS.addListener(DreamEvents::continueSleeping);
        NeoForge.EVENT_BUS.addListener(DreamEvents::playerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DreamEvents::lethalDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, DreamEvents::death);
        NeoForge.EVENT_BUS.addListener(DreamEvents::fall);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, DreamEvents::bedInDream);
        NeoForge.EVENT_BUS.addListener(DreamEvents::inception);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) DreamManager.onLogin(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> DreamManager.forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            // Should never happen (dreamers cannot die), but if it did the attachment survived the respawn: wake now.
            if (e.getEntity() instanceof ServerPlayer p && DreamManager.isDreaming(p)) DreamManager.scheduleWake(p, DreamManager.Reason.DEATH);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> DreamManager.clearAll());
        DreamCommands.init();
    }

    /** Sleepyness lets you lie down in daylight. Every other vanilla restriction stays. */
    private static void allowDaySleep(CanPlayerSleepEvent event) {
        ServerPlayer player = event.getEntity();
        if (event.getProblem() == Player.BedSleepingProblem.NOT_POSSIBLE_NOW && DreamManager.canDream(player)) {
            event.setProblem(null);
        }
        // Sleepy but just back from a dream: say so, instead of a plain "you can only sleep at night".
        if (com.seba.reverie.registry.ModEffects.sleepy(player) && DreamManager.onCooldown(player)) {
            long left = Math.max(1, (DreamManager.cooldownLeft(player) + 19) / 20);
            player.displayClientMessage(Component.translatable("reverie.dream.too_awake", left).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** The item in hand when lying down is the one taken into the dream. */
    private static void snapshotHeldItem(CanPlayerSleepEvent event) {
        if (event.getProblem() == null) DreamManager.rememberHeldItem(event.getEntity());
    }

    private static void continueSleeping(CanContinueSleepingEvent event) {
        if (event.getProblem() != Player.BedSleepingProblem.NOT_POSSIBLE_NOW) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (DreamManager.canDream(player) || DreamManager.inWakePose(player.getUUID())) event.setContinueSleeping(true);
    }

    private static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        try {
            // Player ticks run after the level's night-skip check, so a dreamer is gone before it could count.
            if (player.isSleepingLongEnough() && !DreamManager.inWakePose(player.getUUID()) && DreamManager.canDream(player)) {
                DreamManager.startDream(player, player.getSleepingPos().orElse(null));
                return;
            }
            DreamManager.tick(player);
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Dream tick failed for {}", player.getName().getString(), e);
        }
    }

    /** Lethal damage while dreaming wakes you instead (runs last, on the final damage amount). */
    private static void lethalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DreamManager.isDreaming(player)) return;
        if (event.getNewDamage() >= player.getHealth() + player.getAbsorptionAmount()) {
            event.setNewDamage(0);
            DreamManager.scheduleWake(player, DreamManager.Reason.DEATH);
        }
    }

    /** Safety net for anything that kills without going through damage (and /kill). */
    private static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DreamManager.isDreaming(player)) return;
        event.setCanceled(true);
        player.setHealth(1f);
        DreamManager.scheduleWake(player, DreamManager.Reason.DEATH);
    }

    private static void fall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DreamManager.isDreaming(player)) event.setDamageMultiplier(0);
    }

    /**
     * Inception: Concentrated Extract drunk inside the dream. The island you are on fills with Shades, all staring,
     * and a few seconds later the dream inside the dream folds up and you wake.
     */
    private static void inception(net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DreamManager.isDreaming(player)) return;
        if (!event.getItem().is(com.seba.reverie.registry.ModItems.CONCENTRATED_MELATONIA_EXTRACT.get())) return;
        DreamAdvancements.award(player, DreamAdvancements.INCEPTION, "dreamed_in_a_dream");
        com.seba.reverie.shade.ShadeCrowdManager.inception(player);
        DreamManager.wakeLater(player, 150);
        player.displayClientMessage(Component.translatable("reverie.inception").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), true);
    }

    /** Dreaming inside a dream resolves to waking. Beds in Dreamland never set a respawn point. */
    private static void bedInDream(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().dimension().equals(DreamManager.DREAMLAND)) return;
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BedBlock)) return;
        if (event.getEntity().isSecondaryUseActive() && !event.getItemStack().isEmpty()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player) {
            if (DreamManager.isDreaming(player)) DreamManager.scheduleWake(player, DreamManager.Reason.BED);
            else player.displayClientMessage(Component.translatable("reverie.dream.bed_not_dreaming").withStyle(ChatFormatting.GRAY), true);
        }
    }
}
