package com.seba.reverie.client.dreamland;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.client.DreamEffectsClient;
import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.dreamland.BiomeMechanics;
import com.seba.reverie.dreamland.FallLoop;
import com.seba.reverie.dreamland.IslandField.Kind;
import com.seba.reverie.dreamland.block.BubbleVentBlock;
import com.seba.reverie.registry.ModBlocks;
import com.seba.reverie.shade.ShadeEntity;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The client half of the biome rules. Movement belongs to the client, so the swimming through the Skyreef air
 * and the lift of the bubble vents happen here; the rest is what you see and hear: the region weights the sky
 * and fog blend by (like the Nether's biome fog), the heartbeat of The Maw, the echo in the Geodes, your breath
 * in the frost, the lights going out, being swallowed, the dropped frames of Signal Lost and the fog that hides
 * the endless fall.
 */
public final class BiomeMechanicsClient {
    private static final ResourceLocation STATIC = Reverie.id("textures/environment/sky_static.png");
    public static Kind region = Kind.VOID;
    private static final float[] WEIGHTS = new float[Kind.values().length];
    private static boolean fresh = true;
    /** 0..1: the mist at the bottom of the dream. Rises as you sink, then lingers through the fall loop. */
    private static float haze;
    /** Ticks left of the slow drift down after coming out on top. */
    private static int gentle;
    private static double prevY = Double.NaN;
    public static int lightsOut;
    private static int lightsTotal = 1;
    public static int swallow;
    public static int glitch;
    /** 0..1, jumps to 1 on each beat of The Maw. */
    public static float heartbeat;
    private static int beatTimer = 20;
    private static int breathTimer = 60;
    private static int minorGlitchTimer = 200;
    private static final List<Echo> ECHOES = new ArrayList<>();

    private record Echo(SoundInstance sound, int at) {
    }

    private static final class EchoSound extends SimpleSoundInstance {
        EchoSound(ResourceLocation id, SoundSource source, float volume, float pitch, RandomSource rng, double x, double y, double z) {
            super(id, source, volume, pitch, rng, false, 0, SoundInstance.Attenuation.LINEAR, x, y, z, false);
        }
    }

    private BiomeMechanicsClient() {
    }

    public static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(BiomeMechanicsClient::tick);
        NeoForge.EVENT_BUS.addListener(BiomeMechanicsClient::playerTick);
        NeoForge.EVENT_BUS.addListener(BiomeMechanicsClient::sound);
        NeoForge.EVENT_BUS.addListener(BiomeMechanicsClient::fov);
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerBelowAll(Reverie.id("biome_overlay"), BiomeMechanicsClient::overlay));
    }

    // ------------------------------------------------------------------ events from the server

    /** Returns true if the event was one of ours. */
    public static boolean onEvent(int event, int value) {
        switch (event) {
            case BiomeMechanics.REGION -> {
                region = Kind.byOrdinal(value);
                if (fresh) {
                    Arrays.fill(WEIGHTS, 0);
                    WEIGHTS[region.ordinal()] = 1;
                    fresh = false;
                }
            }
            case FallLoop.EVENT -> {
                haze = 1;
                gentle = value;
            }
            case BiomeMechanics.LIGHTS_OUT -> {
                lightsOut = value;
                lightsTotal = Math.max(1, value);
            }
            case BiomeMechanics.SWALLOW -> swallow = value;
            case BiomeMechanics.GLITCH -> glitch = value;
            case BiomeMechanics.TILT -> DreamMovementClient.onTilt(value);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** How much of the sky and fog of this biome there is right now (0..1); all weights add up to 1. */
    public static float weight(Kind kind) {
        return WEIGHTS[kind.ordinal()];
    }

    private static boolean inDream(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.level.dimension().equals(DreamManager.DREAMLAND);
    }

    private static boolean on() {
        return ReverieConfig.get(ReverieConfig.BIOME_MECHANICS);
    }

    /** Blackwood: you cannot run (see LocalPlayerMixin). */
    public static boolean heavyLegs() {
        Minecraft mc = Minecraft.getInstance();
        return inDream(mc) && region == Kind.BLACKWOOD && on();
    }

    // ------------------------------------------------------------------ ticks

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!inDream(mc)) {
            fresh = true;
            region = Kind.VOID;
            lightsOut = swallow = glitch = gentle = 0;
            haze = 0;
            prevY = Double.NaN;
            heartbeat = 0;
            ECHOES.clear();
            return;
        }
        if (mc.isPaused()) return;
        for (Kind k : Kind.values()) {
            float target = k == region ? 1 : 0;
            WEIGHTS[k.ordinal()] += Mth.clamp(target - WEIGHTS[k.ordinal()], -0.015f, 0.015f);
        }
        LocalPlayer self = mc.player;
        // The mist: follows how deep you are, and once thick it fades only slowly (about 3.5 s), so it is still
        // there after the fall loop has moved you up. A sudden rise of many blocks means the loop just happened.
        if (!Double.isNaN(prevY) && self.getY() - prevY > 30) {
            haze = 1;
            gentle = Math.max(gentle, 90);
        }
        prevY = self.getY();
        haze = Math.max(depth(mc, self.getY()), haze - 1f / 70f);
        if (gentle > 0 && (--gentle == 0 || self.onGround())) gentle = 0;
        if (haze > 0.15f && !mc.isPaused()) {
            RandomSource r = self.getRandom();
            // Wisps going past: drifting up beside you, so you feel yourself sinking through them.
            for (int i = 0; i < 2; i++) {
                mc.level.addParticle(ParticleTypes.CLOUD, self.getX() + (r.nextDouble() - 0.5) * 9, self.getY() - 3 + r.nextDouble() * 5,
                        self.getZ() + (r.nextDouble() - 0.5) * 9, 0, 0.12 + r.nextDouble() * 0.1, 0);
            }
        }
        if (lightsOut > 0) lightsOut--;
        if (swallow > 0) swallow--;
        if (glitch > 0) glitch--;
        heartbeat *= 0.86f;
        LocalPlayer p = mc.player;
        RandomSource rng = p.getRandom();
        boolean quiet = DreamEffectsClient.silenced();
        if (weight(Kind.THE_MAW) > 0.5f && swallow <= 0 && --beatTimer <= 0) {
            boolean near = !mc.level.getEntitiesOfClass(ShadeEntity.class, new AABB(p.blockPosition()).inflate(20)).isEmpty();
            beatTimer = near ? 13 : 23;
            heartbeat = 1;
            if (!quiet) mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT,
                    0.55f * DreamAmbienceClient.ambient(), near ? 1.0f : 0.8f, rng, p.getX(), p.getEyeY(), p.getZ()));
        }
        if (weight(Kind.FROSTLIGHT_TUNDRA) > 0.5f && --breathTimer <= 0) {
            breathTimer = 50 + rng.nextInt(40);
            Vec3 look = p.getLookAngle();
            Vec3 mouth = p.getEyePosition().add(look.scale(0.45)).add(0, -0.15, 0);
            for (int i = 0; i < 3; i++) {
                mc.level.addParticle(ParticleTypes.WHITE_ASH, mouth.x, mouth.y, mouth.z, look.x * 0.02, 0.01, look.z * 0.02);
                mc.level.addParticle(ParticleTypes.POOF, mouth.x, mouth.y, mouth.z, look.x * 0.015, 0.005, look.z * 0.015);
            }
        }
        if (region == Kind.SIGNAL_LOST && --minorGlitchTimer <= 0) {
            minorGlitchTimer = 160 + rng.nextInt(360);
            if (glitch <= 0) glitch = 2 + rng.nextInt(3);
        }
        for (int i = ECHOES.size() - 1; i >= 0; i--) {
            Echo e = ECHOES.get(i);
            if (p.tickCount >= e.at()) {
                mc.getSoundManager().play(e.sound());
                ECHOES.remove(i);
            }
        }
    }

    /** Skyreef swimming and the bubble vents, for the local player only (the client moves itself). */
    private static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer p) || !inDream(Minecraft.getInstance())) return;
        Vec3 v = p.getDeltaMovement();
        double vy = v.y;
        boolean changed = false;
        AttributeInstance gravity = p.getAttribute(Attributes.GRAVITY);
        boolean swimming = gravity != null && gravity.hasModifier(BiomeMechanics.SWIM) && !p.onGround() && !p.isInWater()
                && !p.getAbilities().flying && !p.isPassenger();
        if (swimming) {
            if (p.input.jumping) vy = Math.min(vy + 0.032, 0.3);
            else if (p.input.shiftKeyDown) vy = Math.max(vy - 0.04, -0.38);
            else vy += (-0.035 - vy) * 0.08; // a slow sink
            double hx = v.x * 1.015;
            double hz = v.z * 1.015;
            double h = Math.hypot(hx, hz);
            if (h > 0.34) {
                hx *= 0.34 / h;
                hz *= 0.34 / h;
            }
            v = new Vec3(hx, vy, hz);
            changed = true;
        }
        BlockPos feet = p.blockPosition();
        for (int dy = 0; dy <= BubbleVentBlock.REACH; dy++) {
            BlockPos q = feet.below(dy);
            var state = p.level().getBlockState(q);
            if (state.is(ModBlocks.BUBBLE_VENT.get())) {
                v = new Vec3(v.x, Math.min(Math.max(v.y, 0) + 0.09, 0.55), v.z);
                p.resetFallDistance();
                changed = true;
                break;
            }
            if (dy > 0 && !state.isAir() && !state.is(ModBlocks.SKY_KELP.get()) && !state.is(ModBlocks.SKY_CORAL.get())) break;
        }
        // The endless fall: slow down into the mist near the bottom, and drift gently down after coming out on top.
        float depth = depth(Minecraft.getInstance(), p.getY());
        if (v.y < 0 && (depth > 0 || gentle > 0)) {
            double cap = gentle > 0 ? -0.45 : Mth.lerp(depth, -1.4, -0.3);
            if (v.y < cap) {
                v = new Vec3(v.x * 0.96, Mth.lerp(0.25, v.y, cap), v.z * 0.96);
                changed = true;
            }
            p.resetFallDistance();
        }
        if (changed) p.setDeltaMovement(v);
    }

    /** 0 above the bottom stretch of the dream, 1 at the line where the fall loops you back up. */
    private static float depth(Minecraft mc, double y) {
        if (mc.level == null) return 0;
        double bottom = mc.level.getMinBuildHeight() + FallLoop.DEPTH;
        float t = (float) Mth.clamp((bottom + 70 - y) / 70.0, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** The mist right now (render time: also covers the frame before the next tick after a move). */
    static float mist(Minecraft mc, double cameraY) {
        return Math.max(haze, depth(mc, cameraY));
    }

    /** Lucid Geodes: every footstep comes back a moment later, softer, from the crystal. */
    private static void sound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        SoundInstance s = event.getSound();
        if (s == null || s instanceof EchoSound || !inDream(mc) || weight(Kind.LUCID_GEODES) < 0.5f) return;
        if (!s.getLocation().getPath().contains("step") || ECHOES.size() > 12) return;
        RandomSource rng = mc.player.getRandom();
        ECHOES.add(new Echo(new EchoSound(s.getLocation(), s.getSource(), 0.16f, 1.06f, rng, s.getX(), s.getY() + 1, s.getZ()), mc.player.tickCount + 7));
        ECHOES.add(new Echo(new EchoSound(s.getLocation(), s.getSource(), 0.07f, 1.1f, rng, s.getX(), s.getY() + 2, s.getZ()), mc.player.tickCount + 15));
    }

    private static void fov(ViewportEvent.ComputeFov event) {
        if (heartbeat <= 0.01f || ReverieConfig.get(ReverieConfig.REDUCE_MOTION)) return;
        event.setFOV(event.getFOV() * (1 + 0.012 * heartbeat));
    }

    // ------------------------------------------------------------------ fog

    /** Multiplies the fog distance: per-biome thickness, the heartbeat, the bottom of the fall, lights out. */
    static float fogFactor(Minecraft mc, double cameraY) {
        float f = 0;
        for (Kind k : Kind.values()) f += WEIGHTS[k.ordinal()] * fogOf(k);
        if (f <= 0) f = 1;
        f *= 1 - 0.12f * heartbeat;
        // The bottom of the dream: a mist that closes in as you sink, and thins again as you drift back down.
        float m = mist(mc, cameraY);
        f = Mth.lerp(m * m * (3 - 2 * m), f, 0.05f);
        if (lightsOut > 0) f = Math.min(f, 0.12f + 0.88f * (1 - Math.min(1, lightsOut / (float) lightsTotal * 1.3f)));
        if (swallow > 0) f = Math.min(f, 0.05f);
        return f;
    }

    private static float fogOf(Kind k) {
        return switch (k) {
            case FROSTLIGHT_TUNDRA -> 1.15f;
            case SKYREEF -> 0.78f;
            case THE_NURSERY -> 0.9f;
            case BLACKWOOD -> 0.42f;
            case WAITING_ROOMS -> 0.55f;
            case THE_MAW -> 0.6f;
            case SIGNAL_LOST -> 0.72f;
            default -> 1f;
        };
    }

    // ------------------------------------------------------------------ overlay

    private static void overlay(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!inDream(mc) || mc.options.hideGui && swallow <= 0) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float m = mist(mc, mc.gameRenderer.getMainCamera().getPosition().y);
        if (m > 0.01f) {
            // A pale veil over everything at the deepest point, so the move up is never seen; it thins smoothly.
            float a = (float) Math.pow(m, 1.6) * 0.9f;
            g.fill(0, 0, w, h, (int) (a * 255) << 24 | 0xD6D0EA);
        }
        if (swallow > 0) {
            float a = Mth.clamp(swallow / 30f, 0, 1);
            g.fill(0, 0, w, h, (int) (a * 255) << 24 | 0x3A0008);
        }
        if (lightsOut > 0) {
            float t = lightsOut / (float) lightsTotal;
            float a = Mth.clamp(Math.min(t * 4, (1 - t) * 8 + 0.2f), 0, 1) * 0.7f;
            g.fill(0, 0, w, h, (int) (a * 255) << 24);
        }
        if (glitch > 0) {
            // Dropped frames: torn bars of static across the picture.
            RandomSource rng = mc.player.getRandom();
            boolean calm = ReverieConfig.get(ReverieConfig.REDUCE_MOTION);
            int bars = calm ? 2 : 5 + rng.nextInt(6);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, calm ? 0.25f : 0.55f);
            for (int i = 0; i < bars; i++) {
                int y = rng.nextInt(h);
                int bh = 2 + rng.nextInt(calm ? 4 : 14);
                int shift = calm ? 0 : rng.nextInt(40) - 20;
                int v = rng.nextInt(64 - Math.min(63, bh));
                for (int x = shift - 64; x < w; x += 64) g.blit(STATIC, x, y, rng.nextInt(2) * 0f, v, 64, Math.min(bh, 64), 64, 64);
                if (!calm) g.fill(0, y, w, y + 1, 0x40FF0040);
            }
            g.setColor(1f, 1f, 1f, 1f);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        }
    }

}
