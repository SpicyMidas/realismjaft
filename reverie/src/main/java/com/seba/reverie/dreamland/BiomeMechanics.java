package com.seba.reverie.dreamland;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.dream.DreamSession;
import com.seba.reverie.dreamland.IslandField.Kind;
import com.seba.reverie.dreamland.block.FlickerLightBlock;
import com.seba.reverie.dreamland.block.ResonantCrystalBlock;
import com.seba.reverie.network.Net;
import com.seba.reverie.network.ShadeEventPayload;
import com.seba.reverie.registry.ModBlocks;
import com.seba.reverie.shade.ShadeCrowdManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The rules of the new biomes, server side, per dreamer. They follow the region (the biome map of the nearest
 * layer) rather than the block underfoot, so a rule holds in the air between a biome's islands too.
 * Movement itself is the client's: BiomeMechanicsClient does the swimming and the vents.
 */
public final class BiomeMechanics {
    /** ShadeEventPayload ids (the duration field carries the value). */
    public static final int REGION = 30;
    public static final int LIGHTS_OUT = 33;
    public static final int SWALLOW = 34;
    public static final int GLITCH = 35;

    public static final ResourceLocation SWIM = Reverie.id("skyreef_swim");
    private static final ResourceLocation SMALL = Reverie.id("nursery_small");
    private static final ResourceLocation HEAVY = Reverie.id("blackwood_heavy");
    /** In The Nursery you are a little under half your size: the toys are enormous. */
    private static final double SMALL_SCALE = -0.55;
    private static final int MAW_WARN = 60;
    private static final int MAW_SWALLOW = 170;

    private static final class State {
        Kind region = Kind.VOID;
        int ticket;
        int serving;
        long nextCall;
        int still;
        Vec3 lastPos = Vec3.ZERO;
        final Vec3[] trail = new Vec3[60];
        int trailIndex;
        long nextSkip;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private BiomeMechanics() {
    }

    /** The region this dreamer is in (VOID until known). */
    public static Kind region(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? Kind.VOID : s.region;
    }

    public static void tick(ServerPlayer p, DreamSession session) {
        ServerLevel level = p.serverLevel();
        IslandField field = IslandSearch.field(level);
        if (field == null) return;
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        // A moment after arriving (not during the loading screen), a nightmare tells you what it is.
        if (session.nightmare() && com.seba.reverie.dream.DreamManager.now(p.server) - session.startTime() == 70) {
            p.displayClientMessage(Component.translatable("reverie.nightmare.arrive").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
        }
        if (p.tickCount % 10 == 0 || s.region == Kind.VOID) {
            Kind now = field.regionAt(p.getBlockX(), p.getBlockY(), p.getBlockZ());
            if (now != s.region) {
                s.region = now;
                Net.send(p, new ShadeEventPayload(REGION, now.ordinal()));
                if (now == Kind.WAITING_ROOMS) s.ticket = 0;
            }
        }
        boolean on = ReverieConfig.get(ReverieConfig.BIOME_MECHANICS);
        Kind r = on ? s.region : Kind.VOID;
        modifier(p, Attributes.GRAVITY, SWIM, r == Kind.SKYREEF ? -1.0 : 0);
        modifier(p, Attributes.MOVEMENT_SPEED, HEAVY, r == Kind.BLACKWOOD ? -0.15 : 0);
        easeScale(p, level, r == Kind.THE_NURSERY ? SMALL_SCALE : 0);
        if (r == Kind.SKYREEF) p.resetFallDistance(); // you swim down, you do not fall
        if (r != Kind.THE_MAW) s.still = 0;
        if (r != Kind.SIGNAL_LOST) s.nextSkip = 0;
        switch (r) {
            case WAITING_ROOMS -> waitingRooms(p, s, level);
            case THE_MAW -> maw(p, s, level, session);
            case SIGNAL_LOST -> signalLost(p, s, level);
            case LUCID_GEODES -> geodes(p, level);
            default -> {
            }
        }
    }

    /** Takes every biome rule off (waking, logging out). */
    public static void clear(ServerPlayer p) {
        for (var pair : new Object[][]{{Attributes.GRAVITY, SWIM}, {Attributes.MOVEMENT_SPEED, HEAVY}, {Attributes.SCALE, SMALL}}) {
            @SuppressWarnings("unchecked")
            AttributeInstance a = p.getAttribute((Holder<Attribute>) pair[0]);
            if (a != null) a.removeModifier((ResourceLocation) pair[1]);
        }
        STATES.remove(p.getUUID());
    }

    private static void modifier(ServerPlayer p, Holder<Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance a = p.getAttribute(attribute);
        if (a == null) return;
        AttributeModifier m = a.getModifier(id);
        if (amount == 0) {
            if (m != null) a.removeModifier(id);
        } else if (m == null || m.amount() != amount) {
            a.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Shrinks or grows a little each tick; never grows into a ceiling. */
    private static void easeScale(ServerPlayer p, ServerLevel level, double target) {
        AttributeInstance a = p.getAttribute(Attributes.SCALE);
        if (a == null) return;
        AttributeModifier m = a.getModifier(SMALL);
        double current = m == null ? 0 : m.amount();
        if (Math.abs(current - target) < 1e-4) return;
        double next = current + Mth.clamp(target - current, -0.02, 0.02);
        if (next > current) {
            double grown = p.getBbHeight() / (1 + current) * (1 + next);
            if (!level.noCollision(p, p.getBoundingBox().expandTowards(0, grown - p.getBbHeight() + 0.05, 0))) return;
        }
        if (Math.abs(next) < 1e-4) a.removeModifier(SMALL);
        else a.addOrUpdateTransientModifier(new AttributeModifier(SMALL, next, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    // ------------------------------------------------------------------ The Waiting Rooms

    /** You get a number. Now and then a chime calls the next ones. When yours is called the lights go out. */
    private static void waitingRooms(ServerPlayer p, State s, ServerLevel level) {
        long now = level.getGameTime();
        var rng = p.getRandom();
        if (s.ticket == 0) {
            s.ticket = 20 + rng.nextInt(80);
            s.serving = s.ticket - 6 - rng.nextInt(9);
            s.nextCall = now + 260 + rng.nextInt(200);
            p.displayClientMessage(Component.translatable("reverie.waiting.ticket", s.ticket).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (now < s.nextCall) return;
        s.serving = Math.min(s.ticket, s.serving + 1 + rng.nextInt(3));
        p.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.AMBIENT, 0.7f, 1.19f);
        p.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.AMBIENT, 0.6f, 0.94f);
        if (s.serving < s.ticket) {
            p.displayClientMessage(Component.translatable("reverie.waiting.serving", s.serving).withStyle(ChatFormatting.GRAY), true);
            s.nextCall = now + 300 + rng.nextInt(400);
            return;
        }
        p.displayClientMessage(Component.translatable("reverie.waiting.called", s.ticket).withStyle(ChatFormatting.YELLOW), true);
        lightsOut(level, p.blockPosition());
        Net.send(p, new ShadeEventPayload(LIGHTS_OUT, 120));
        ShadeCrowdManager.startEvent(p, ShadeCrowdManager.Event.RUSH);
        s.ticket = 0;
        s.nextCall = now + 900;
    }

    private static void lightsOut(ServerLevel level, BlockPos center) {
        var rng = level.getRandom();
        for (BlockPos q : BlockPos.betweenClosed(center.offset(-20, 1, -20), center.offset(20, 8, 20))) {
            if (level.getBlockState(q).getBlock() instanceof FlickerLightBlock) FlickerLightBlock.darken(level, q.immutable(), 110 + rng.nextInt(40));
        }
    }

    // ------------------------------------------------------------------ The Maw

    /** Stand still on the flesh and it takes hold; stay and it swallows you, and spits you out somewhere else. */
    private static void maw(ServerPlayer p, State s, ServerLevel level, DreamSession session) {
        Vec3 pos = p.position();
        boolean still = pos.distanceToSqr(s.lastPos) < 0.003;
        s.lastPos = pos;
        boolean onFlesh = p.onGround() && level.getBlockState(p.getOnPos()).is(ModBlocks.DREAM_FLESH.get());
        if (!onFlesh || !still) {
            s.still = Math.max(0, s.still - 4);
            return;
        }
        s.still++;
        if (s.still == MAW_WARN) {
            p.displayClientMessage(Component.translatable("reverie.maw.hold").withStyle(ChatFormatting.DARK_RED), true);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false));
        }
        if (s.still > MAW_WARN && s.still % 12 == 0) {
            level.playSound(null, p.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.AMBIENT, 0.7f, 0.5f + p.getRandom().nextFloat() * 0.2f);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.4, 0.1, 0.4, 0.01);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3, false, false));
        }
        if (s.still < MAW_SWALLOW) return;
        s.still = 0;
        double a = p.getRandom().nextDouble() * Math.PI * 2;
        BlockPos far = p.blockPosition().offset((int) (Math.cos(a) * 260), 0, (int) (Math.sin(a) * 260));
        BlockPos spot = IslandSearch.nightmareSpot(level, far);
        if (spot == null) spot = com.seba.reverie.dream.DreamManager.arrival(level, p.getUUID(), session.nightmare());
        level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 1.2f, 0.4f);
        Net.send(p, new ShadeEventPayload(SWALLOW, 50));
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
        p.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT),
                p.getYRot(), p.getXRot());
        p.resetFallDistance();
        p.displayClientMessage(Component.translatable("reverie.maw.swallowed").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
    }

    // ------------------------------------------------------------------ Signal Lost

    /** The dream drops frames: now and then you are put back where you were three seconds ago. */
    private static void signalLost(ServerPlayer p, State s, ServerLevel level) {
        s.trailIndex = (s.trailIndex + 1) % s.trail.length;
        s.trail[s.trailIndex] = p.position();
        long now = level.getGameTime();
        var rng = p.getRandom();
        if (s.nextSkip == 0) s.nextSkip = now + 240 + rng.nextInt(300);
        if (now < s.nextSkip) return;
        s.nextSkip = now + 360 + rng.nextInt(500);
        Vec3 back = s.trail[(s.trailIndex + 1) % s.trail.length];
        Net.send(p, new ShadeEventPayload(GLITCH, 10));
        p.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.AMBIENT, 0.8f, 0.5f);
        if (back == null || back.distanceToSqr(p.position()) < 4 || back.distanceToSqr(p.position()) > 30 * 30) return;
        if (!level.noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(back))) return;
        p.teleportTo(level, back.x, back.y, back.z, EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT), p.getYRot(), p.getXRot());
        p.resetFallDistance();
    }

    // ------------------------------------------------------------------ Lucid Geodes

    /** The crystals around you hum awake as you pass, so you walk in your own small light. */
    private static void geodes(ServerPlayer p, ServerLevel level) {
        if (p.tickCount % 8 != 0) return;
        BlockPos c = p.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-3, -2, -3), c.offset(3, 3, 3))) {
            var state = level.getBlockState(q);
            if (state.getBlock() instanceof ResonantCrystalBlock && state.getValue(ResonantCrystalBlock.RESONANCE) < 2 && q.distSqr(c) <= 12) {
                ResonantCrystalBlock.resonate(level, q.immutable(), 2, p.getRandom().nextInt(8) == 0);
            }
        }
    }
}
