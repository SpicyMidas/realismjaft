package com.seba.reverie.client.dreamland;

import java.util.HashSet;
import java.util.Set;

import com.mojang.math.Axis;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.dreamland.BiomeMechanics;
import com.seba.reverie.network.UpsideDownPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Moving through the dream, for the local player (movement is the client's):
 * <ul>
 * <li>the long dash: a second jump in mid-air flings you far ahead, in the dream and lucid biomes (not in the
 * nightmares, where it is easy to fall);</li>
 * <li>the tilt: for a few seconds the whole dream leans and you slide; crouching at an edge or a wall stops you;</li>
 * <li>upside down: under an upside-down island gravity points up, the view turns over, and you walk on its underside
 * (controls and camera turned over with it, a jump pushes off "down"); round its rim it pulls you back under.</li>
 * </ul>
 */
public final class DreamMovementClient {
    private static final double DASH = 1.2;
    private static boolean jumpWasDown;
    private static boolean dashed;
    private static int tiltTicks;
    private static int tiltTotal = 1;
    private static float tiltYaw;
    /** 0 upright .. 1 turned over. */
    private static float flip;
    private static float flipO;
    private static int underX;
    private static int underZ;
    private static int underRadius;
    private static int ceilingJump;
    private static final Set<Integer> FLIPPED = new HashSet<>();

    private DreamMovementClient() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(DreamMovementClient::tick);
        NeoForge.EVENT_BUS.addListener(DreamMovementClient::playerTick);
        NeoForge.EVENT_BUS.addListener(DreamMovementClient::input);
        NeoForge.EVENT_BUS.addListener(DreamMovementClient::camera);
        // Lowest, and never for a cancelled render: whatever is pushed here is popped in renderPost.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, DreamMovementClient::renderPre);
        NeoForge.EVENT_BUS.addListener(DreamMovementClient::renderPost);
    }

    private static boolean inDream(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.level.dimension().equals(DreamManager.DREAMLAND);
    }

    public static void onUpsideDown(UpsideDownPayload p) {
        underX = p.centerX();
        underZ = p.centerZ();
        underRadius = p.radius();
    }

    /** A tilt: value = ticks * 360 + the direction it slides you (degrees, world yaw). */
    static void onTilt(int value) {
        tiltTotal = Math.max(1, value / 360);
        tiltTicks = tiltTotal;
        tiltYaw = value % 360;
    }

    static boolean upsideDown(LivingEntity e) {
        AttributeInstance g = e.getAttribute(Attributes.GRAVITY);
        return g != null && g.hasModifier(BiomeMechanics.INVERT);
    }

    /** True while the view is turned over far enough that mouse and strafing are turned over too. */
    public static boolean controlsFlipped() {
        return flip > 0.5f;
    }

    /** How far the camera sits below the eyes while upside down (the eyes belong near the underside you stand on). */
    public static double cameraDrop(LivingEntity e, float partial) {
        float f = eased(Mth.lerp(partial, flipO, flip));
        return f <= 0 ? 0 : (2 * e.getEyeHeight() - e.getBbHeight()) * f;
    }

    private static float eased(float t) {
        return t * t * (3 - 2 * t);
    }

    private static float tiltStrength(float partial) {
        if (tiltTicks <= 0) return 0;
        float elapsed = tiltTotal - tiltTicks + partial;
        return Mth.clamp(Math.min(elapsed / 25f, (tiltTicks - partial) / 25f), 0, 1);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        flipO = flip;
        if (!inDream(mc)) {
            tiltTicks = 0;
            flip = flipO = 0;
            underRadius = 0;
            return;
        }
        if (mc.isPaused()) return;
        if (tiltTicks > 0) tiltTicks--;
        flip = Mth.approach(flip, upsideDown(mc.player) ? 1 : 0, 0.06f);
    }

    private static void playerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer p) || !inDream(Minecraft.getInstance())) return;
        Vec3 v = p.getDeltaMovement();
        boolean upside = upsideDown(p);
        boolean jump = p.input.jumping;
        if (p.onGround() || p.isInWater() || p.getAbilities().flying || upside) dashed = false;

        // The long dash.
        if (jump && !jumpWasDown && !p.onGround() && !dashed && canDash(p)) {
            dashed = true;
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            double fx = -Mth.sin(yaw);
            double fz = Mth.cos(yaw);
            v = new Vec3(fx * DASH, Math.max(v.y, 0) + 0.5, fz * DASH);
            p.resetFallDistance();
            p.playSound(SoundEvents.PHANTOM_FLAP, 0.6f, 1.4f);
            for (int i = 0; i < 6; i++) {
                p.level().addParticle(ParticleTypes.CLOUD, p.getX() - fx * 0.6, p.getY() + 0.4, p.getZ() - fz * 0.6,
                        -fx * 0.05 + (p.getRandom().nextDouble() - 0.5) * 0.05, 0.02, -fz * 0.05);
            }
        }
        jumpWasDown = jump;

        // The tilt: everything slides one way.
        float tilt = tiltStrength(0);
        if (tilt > 0 && !upside) {
            float yaw = tiltYaw * Mth.DEG_TO_RAD;
            double push = (p.onGround() ? 0.11 : 0.025) * tilt;
            v = v.add(-Mth.sin(yaw) * push, 0, Mth.cos(yaw) * push);
        }

        // Upside down: walking on the underside.
        if (upside) {
            boolean ceiling = p.verticalCollision && !p.onGround();
            if (ceilingJump > 0) ceilingJump--;
            if (ceiling) {
                float yaw = p.getYRot() * Mth.DEG_TO_RAD;
                // Controls are turned over with the view: strafing was already flipped in input().
                double fwd = p.input.forwardImpulse;
                double side = p.input.leftImpulse;
                double ax = (-Mth.sin(yaw) * fwd + Mth.cos(yaw) * side) * 0.055;
                double az = (Mth.cos(yaw) * fwd + Mth.sin(yaw) * side) * 0.055;
                v = new Vec3((v.x + ax) * 0.75, v.y, (v.z + az) * 0.75);
                if (jump && ceilingJump == 0) {
                    v = new Vec3(v.x, -0.42, v.z); // push off "down", gravity brings you back up
                    ceilingJump = 10;
                }
            }
            if (underRadius > 0) {
                // Round the rim it pulls you back under, like the far side of a small planet.
                double dx = underX - p.getX();
                double dz = underZ - p.getZ();
                double d = Math.hypot(dx, dz);
                if (d > underRadius * 0.85 && d > 1) v = v.add(dx / d * 0.035, 0, dz / d * 0.035);
            }
        }
        if (!v.equals(p.getDeltaMovement())) p.setDeltaMovement(v);
    }

    private static boolean canDash(LocalPlayer p) {
        if (BiomeMechanicsClient.region.nightmare()) return false;
        if (p.isUsingItem() || p.isFallFlying() || p.isPassenger() || p.onClimbable()) return false;
        AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
        return g == null || !g.hasModifier(BiomeMechanics.SWIM); // in the Skyreef air, jump swims
    }

    /** With the view turned over, left is right: flip strafing so the keys still go the way they look. */
    private static void input(MovementInputUpdateEvent event) {
        if (!controlsFlipped()) return;
        var in = event.getInput();
        in.leftImpulse = -in.leftImpulse;
        boolean left = in.left;
        in.left = in.right;
        in.right = left;
    }

    private static void camera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!inDream(mc)) return;
        float pt = (float) event.getPartialTick();
        float f = eased(Mth.lerp(pt, flipO, flip));
        float tilt = tiltStrength(pt);
        float roll = 180f * f;
        if (tilt > 0) {
            // Lean the horizon toward the way you slide.
            float rel = Mth.wrapDegrees(tiltYaw - mc.player.getYRot()) * Mth.DEG_TO_RAD;
            float amount = ReverieConfig.get(ReverieConfig.REDUCE_MOTION) ? 7f : 18f;
            roll += -Mth.sin(rel) * amount * tilt;
            event.setPitch(event.getPitch() + Mth.cos(rel) * amount * 0.4f * tilt);
        }
        if (roll != 0) event.setRoll(event.getRoll() + roll);
    }

    /** Players held by an upside-down island are drawn upside down (yourself in third person, and others). */
    private static void renderPre(RenderPlayerEvent.Pre event) {
        var player = event.getEntity();
        if (!upsideDown(player)) return;
        var ps = event.getPoseStack();
        ps.pushPose();
        float h = player.getBbHeight() / 2;
        ps.translate(0, h, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(180));
        ps.translate(0, -h, 0);
        FLIPPED.add(player.getId());
    }

    private static void renderPost(RenderPlayerEvent.Post event) {
        if (FLIPPED.remove(event.getEntity().getId())) event.getPoseStack().popPose();
    }
}
