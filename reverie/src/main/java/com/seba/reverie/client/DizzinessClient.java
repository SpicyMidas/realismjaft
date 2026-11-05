package com.seba.reverie.client;

import com.seba.reverie.ReverieConfig;
import com.seba.reverie.content.melatonia.Lacing;
import com.seba.reverie.registry.ModEffects;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Dizziness, meant to be a real nuisance and never quite predictable: the horizon bends (post shader, see
 * ScreenEffects), the camera rolls and the FOV breathes, the walk drifts sideways, you walk a little slower than
 * normal with sudden deeper dips, and now and then the controls flip (left/right, sometimes forward/back) for a few
 * seconds. Everything eases in over the first second and out over the last. Also the laced-food tooltip line.
 */
public final class DizzinessClient {
    private static int activeTicks;
    private static final java.util.Random RNG = new java.util.Random();
    // the unpredictable part: each value drifts toward a target that changes at random moments
    private static float bend;
    private static float bendTarget;
    private static int bendTimer;
    private static float surge;
    private static int surgeTicks;
    private static float slow = 1f;
    private static float slowTarget = 0.88f;
    private static int slowTimer;
    private static int invertTicks;
    private static int invertCooldown = 100;
    private static boolean invertSide;
    private static boolean invertForward;

    private DizzinessClient() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(DizzinessClient::camera);
        NeoForge.EVENT_BUS.addListener(DizzinessClient::fov);
        NeoForge.EVENT_BUS.addListener(DizzinessClient::input);
        NeoForge.EVENT_BUS.addListener(DizzinessClient::tick);
        NeoForge.EVENT_BUS.addListener(DizzinessClient::tooltip);
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() != Minecraft.getInstance().player) return;
        if (!event.getEntity().hasEffect(ModEffects.DIZZINESS)) {
            activeTicks = 0;
            invertTicks = 0;
            slow = 1f;
            com.seba.reverie.client.shader.ScreenEffects.DIZZY.strength = 0;
            return;
        }
        activeTicks++;
        if (--bendTimer <= 0) {
            bendTarget = (RNG.nextFloat() * 2 - 1) * (RNG.nextInt(4) == 0 ? 1f : 0.6f);
            bendTimer = 30 + RNG.nextInt(110);
        }
        bend += (bendTarget - bend) * 0.04f;
        if (surgeTicks > 0) surgeTicks--;
        else if (RNG.nextInt(260) == 0) surgeTicks = 30 + RNG.nextInt(60);
        surge += ((surgeTicks > 0 ? 0.6f : 0f) - surge) * 0.08f;
        if (--slowTimer <= 0) {
            // A little slower than normal, then suddenly slower again, then not.
            slowTarget = RNG.nextInt(3) == 0 ? 0.5f + RNG.nextFloat() * 0.2f : 0.82f + RNG.nextFloat() * 0.12f;
            slowTimer = 20 + RNG.nextInt(80);
        }
        slow += (slowTarget - slow) * 0.15f;
        if (invertTicks > 0) {
            invertTicks--;
        } else if (--invertCooldown <= 0 && activeTicks > 40) {
            invertTicks = 30 + RNG.nextInt(60);
            invertSide = RNG.nextInt(4) != 0;
            invertForward = !invertSide || RNG.nextInt(3) == 0;
            invertCooldown = 100 + RNG.nextInt(220);
        }
    }

    /** 0..1 envelope times the configured intensity. */
    static float strength(float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        MobEffectInstance effect = player.getEffect(ModEffects.DIZZINESS);
        if (effect == null) return 0;
        float in = Mth.clamp((activeTicks + partialTick) / 20f, 0, 1);
        float out = effect.isInfiniteDuration() ? 1 : Mth.clamp((effect.getDuration() - partialTick) / 20f, 0, 1);
        float amp = Math.min(1.6f, 1f + 0.3f * effect.getAmplifier());
        return in * out * amp * (float) (double) ReverieConfig.get(ReverieConfig.DIZZINESS_INTENSITY);
    }

    private static float motionScale() {
        return ReverieConfig.get(ReverieConfig.REDUCE_MOTION) ? 0.5f : 1f;
    }

    private static float time(double partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0 : (float) (player.tickCount + partialTick);
    }

    private static void camera(ViewportEvent.ComputeCameraAngles event) {
        float s = strength((float) event.getPartialTick());
        // The post shader reads these this frame (it draws only if it loaded and no shader pack is active).
        com.seba.reverie.client.shader.ScreenEffects.DIZZY.strength = s * (1f + surge) * motionScale();
        com.seba.reverie.client.shader.ScreenEffects.DIZZY.bend = bend;
        if (s <= 0) return;
        s *= 1f + surge;
        float t = time(event.getPartialTick());
        float roll = 3f * s * motionScale() * (float) (Math.sin(t * 0.045) * 0.8 + Math.sin(t * 0.017 + 1.3) * 0.2);
        event.setRoll(event.getRoll() + roll);
        event.setYaw(event.getYaw() + 0.6f * s * motionScale() * (float) Math.sin(t * 0.023 + 0.5));
    }

    private static void fov(ViewportEvent.ComputeFov event) {
        float s = strength((float) event.getPartialTick());
        if (s <= 0) return;
        float t = time(event.getPartialTick());
        event.setFOV(event.getFOV() + 4.0 * s * motionScale() * Math.sin(t * 0.037 + 2.1));
    }

    private static void input(MovementInputUpdateEvent event) {
        float s = Math.min(1f, strength(0));
        if (s <= 0) return;
        var input = event.getInput();
        if (invertTicks > 0) {
            if (invertSide) input.leftImpulse = -input.leftImpulse;
            if (invertForward) input.forwardImpulse = -input.forwardImpulse;
        }
        float speed = 1f - (1f - slow) * s;
        input.forwardImpulse *= speed;
        input.leftImpulse *= speed;
        float t = time(0);
        double angle = Math.toRadians(10.0 * s * Math.sin(t * 0.031));
        float f = input.forwardImpulse;
        float l = input.leftImpulse;
        input.forwardImpulse = (float) (f * Math.cos(angle) - l * Math.sin(angle));
        input.leftImpulse = (float) (f * Math.sin(angle) + l * Math.cos(angle));
    }

    private static void tooltip(ItemTooltipEvent event) {
        int level = Lacing.level(event.getItemStack());
        if (level <= 0) return;
        event.getToolTip().add(1, Component.translatable(level >= 2 ? "reverie.laced.strong" : "reverie.laced.normal")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
