package com.seba.reverie.client.dreamland;

import java.util.Random;

import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.client.DreamClient;
import com.seba.reverie.client.DreamEffectsClient;
import com.seba.reverie.dream.DreamManager;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * A lucid dream is not a calm one. Now and then, while dreaming, reality slips: the horizon tilts and comes back,
 * footsteps follow right behind you while you stand still (and stop when you turn), a whisper over the hotbar
 * scrambles and unscrambles itself, or the view slowly breathes in and out. When a rushing Shade vanishes in your
 * face the view jolts and goes black for a moment. Reduce Motion skips the tilting and breathing.
 */
public final class LucidConfusionClient {
    private enum Slip { TILT, FOOTSTEPS, WHISPER, BREATHE }

    private static final int WHISPERS = 8;
    private static final Random RNG = new Random();
    private static Slip slip;
    private static int slipTicks;
    private static int slipLength = 1;
    private static int nextSlip = 1200;
    private static float tiltSign = 1;
    private static int whisper;
    private static Vec3 stepsLook = Vec3.ZERO;
    private static int jolt;
    private static final int JOLT_TICKS = 12;

    private LucidConfusionClient() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(LucidConfusionClient::tick);
        NeoForge.EVENT_BUS.addListener(LucidConfusionClient::camera);
        NeoForge.EVENT_BUS.addListener(LucidConfusionClient::fov);
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerBelowAll(Reverie.id("rush_blackout"), LucidConfusionClient::blackout));
    }

    /** A rushing Shade just vanished in the dreamer's face. */
    public static void jolt() {
        jolt = JOLT_TICKS;
    }

    private static boolean active(Minecraft mc) {
        return mc.player != null && mc.level != null && DreamClient.dreaming && mc.level.dimension().equals(DreamManager.DREAMLAND)
                && ReverieConfig.get(ReverieConfig.LUCID_CONFUSION);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (jolt > 0) jolt--;
        if (mc.isPaused()) return;
        if (!active(mc)) {
            slip = null;
            return;
        }
        if (slip == null) {
            if (--nextSlip > 0 || DreamEffectsClient.silenced()) return;
            start(mc);
            return;
        }
        slipTicks++;
        switch (slip) {
            case FOOTSTEPS -> footsteps(mc);
            case WHISPER -> mc.gui.setOverlayMessage(whisperText(), false);
            default -> {
            }
        }
        if (slipTicks >= slipLength) {
            slip = null;
            nextSlip = 700 + RNG.nextInt(1500);
        }
    }

    private static void start(Minecraft mc) {
        boolean calm = ReverieConfig.get(ReverieConfig.REDUCE_MOTION);
        Slip[] all = calm ? new Slip[]{Slip.FOOTSTEPS, Slip.WHISPER} : Slip.values();
        slip = all[RNG.nextInt(all.length)];
        slipTicks = 0;
        switch (slip) {
            case TILT -> {
                slipLength = 90 + RNG.nextInt(40);
                tiltSign = RNG.nextBoolean() ? 1 : -1;
            }
            case FOOTSTEPS -> {
                slipLength = 60;
                stepsLook = mc.player.getLookAngle();
            }
            case WHISPER -> {
                slipLength = 80;
                whisper = RNG.nextInt(WHISPERS);
            }
            case BREATHE -> slipLength = 120;
        }
    }

    /** 0..1..0 over the slip, eased. */
    private static float envelope(float partial) {
        float t = Mth.clamp((slipTicks + partial) / slipLength, 0, 1);
        float up = Mth.clamp(t / 0.35f, 0, 1);
        float down = Mth.clamp((1 - t) / 0.35f, 0, 1);
        float e = Math.min(up, down);
        return e * e * (3 - 2 * e);
    }

    private static void camera(ViewportEvent.ComputeCameraAngles event) {
        float pt = (float) event.getPartialTick();
        if (slip == Slip.TILT) event.setRoll(event.getRoll() + tiltSign * 24f * envelope(pt));
        if (jolt > 0) {
            float k = (jolt - pt) / JOLT_TICKS;
            event.setRoll(event.getRoll() + (RNG.nextFloat() - 0.5f) * 14f * k);
            event.setPitch(event.getPitch() - 6f * k);
        }
    }

    private static void fov(ViewportEvent.ComputeFov event) {
        float pt = (float) event.getPartialTick();
        if (slip == Slip.BREATHE) event.setFOV(event.getFOV() + 22.0 * Math.sin(envelope(pt) * Math.PI * 0.999) * (slipTicks > slipLength / 2 ? -1 : 1));
        if (jolt > 0) event.setFOV(event.getFOV() - 10.0 * (jolt - pt) / JOLT_TICKS);
    }

    /** Footsteps right behind you, only while you stand still; turn around and they stop. */
    private static void footsteps(Minecraft mc) {
        var p = mc.player;
        if (p.getLookAngle().dot(stepsLook) < 0.2) {
            slipTicks = slipLength;
            return;
        }
        if (slipTicks % 8 != 0 || p.getDeltaMovement().horizontalDistanceSqr() > 0.002) return;
        Vec3 behind = p.position().subtract(stepsLook.multiply(1, 0, 1).normalize().scale(1.8 - slipTicks * 0.01));
        BlockPos under = BlockPos.containing(behind.x, p.getY() - 0.5, behind.z);
        var sound = mc.level.getBlockState(under).getSoundType(mc.level, under, p).getStepSound();
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.PLAYERS, 0.35f, 0.9f + RNG.nextFloat() * 0.1f,
                net.minecraft.util.RandomSource.create(), behind.x, p.getY(), behind.z));
    }

    /** The whisper: readable in the middle of its time, scrambling at both ends. */
    private static Component whisperText() {
        String text = Component.translatable("reverie.whisper." + whisper).getString();
        float clarity = envelope(0);
        MutableComponent out = Component.empty();
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            boolean scrambled = ch.charAt(0) != ' ' && RNG.nextFloat() > clarity * 1.15f;
            out.append(Component.literal(ch).withStyle(scrambled ? new ChatFormatting[]{ChatFormatting.OBFUSCATED, ChatFormatting.GRAY}
                    : new ChatFormatting[]{ChatFormatting.ITALIC, ChatFormatting.WHITE}));
        }
        return out;
    }

    private static void blackout(GuiGraphics g, DeltaTracker delta) {
        if (jolt <= 0) return;
        float a = (jolt - delta.getGameTimeDeltaPartialTick(false)) / JOLT_TICKS;
        int alpha = (int) (Mth.clamp(a * 1.2f, 0, 1) * 235);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha << 24);
    }
}
