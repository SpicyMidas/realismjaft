package com.seba.reverie.shade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A Shade: a flat black silhouette that watches the dreamer from just too far away. Harmless, untargetable,
 * unhittable, invulnerable, never saved, no physics, no AI. Everything it does is decided by the crowd manager.
 */
public class ShadeEntity extends Entity {
    public enum Variant {
        ONLOOKER(1.55), TALL_ONE(3.75), STOOPED_ONE(1.05), WIDE_ARMED(1.55), HEADLESS(1.05), HANGING(0.3), DOORFRAME(1.85);

        /** Height of the head (or what passes for one) above the entity's feet, in blocks. */
        public final double headHeight;

        Variant(double headHeight) {
            this.headHeight = headHeight;
        }
    }

    /** How the shade orients itself (the client turns it smoothly). */
    public static final int FACE = 0;
    public static final int AWAY = 1;
    public static final int TURNS = 2;   // Everyone Turns: away while looked at, toward when not
    public static final int FIXED = 3;   // Counting: everyone points the same way

    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(ShadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(ShadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(ShadeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FADING = SynchedEntityData.defineId(ShadeEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> FIXED_YAW = SynchedEntityData.defineId(ShadeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> INSTANT = SynchedEntityData.defineId(ShadeEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int FADE_TICKS = 10;

    /** Set by the crowd manager; a shade without it (loaded from somewhere) removes itself. */
    boolean managed;
    private int fadeStartTick = -1;
    /** Level time when the fade started, so the crowd manager can clean up a shade that is not ticking. */
    long fadeGameTime;
    private Vec3 drift = Vec3.ZERO;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;

    public ShadeEntity(EntityType<? extends ShadeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
        setSilent(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VARIANT, 0);
        builder.define(OWNER, -1);
        builder.define(MODE, FACE);
        builder.define(FADING, false);
        builder.define(FIXED_YAW, 0f);
        builder.define(INSTANT, false);
    }

    public Variant variant() {
        int v = entityData.get(VARIANT);
        return Variant.values()[Mth.clamp(v, 0, Variant.values().length - 1)];
    }

    public void setVariant(Variant v) {
        entityData.set(VARIANT, v.ordinal());
    }

    public int ownerId() {
        return entityData.get(OWNER);
    }

    public void setOwner(Player owner) {
        entityData.set(OWNER, owner.getId());
    }

    public int mode() {
        return entityData.get(MODE);
    }

    public void setMode(int mode) {
        entityData.set(MODE, mode);
    }

    public void setFixedYaw(float yaw) {
        entityData.set(FIXED_YAW, yaw);
    }

    public float fixedYaw() {
        return entityData.get(FIXED_YAW);
    }

    /** Appears without fading in (Behind You). */
    public void setInstant() {
        entityData.set(INSTANT, true);
    }

    public boolean fading() {
        return entityData.get(FADING);
    }

    public void fadeOut() {
        if (!fading()) {
            entityData.set(FADING, true);
            fadeStartTick = tickCount;
            fadeGameTime = level().getGameTime();
        }
    }

    public void setDrift(Vec3 v) {
        drift = v;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == FADING && fading()) fadeStartTick = tickCount;
    }

    /** 0..1 visibility (fade in when spawned, fade out when vanishing). */
    public float alpha(float partial) {
        float in = entityData.get(INSTANT) ? 1f : Mth.clamp((tickCount + partial) / FADE_TICKS, 0f, 1f);
        if (!fading() || fadeStartTick < 0) return in;
        return in * Mth.clamp(1f - (tickCount - fadeStartTick + partial) / FADE_TICKS, 0f, 1f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, getYRot(), getXRot());
                lerpSteps--;
            }
            clientTurn();
            return;
        }
        if (!managed) {
            discard();
            return;
        }
        if (!drift.equals(Vec3.ZERO)) setPos(getX() + drift.x, getY() + drift.y, getZ() + drift.z);
        if (fading() && tickCount - fadeStartTick > FADE_TICKS + 2) discard();
        if (fading() && tickCount - fadeStartTick == 1 && level() instanceof ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, getX(), getY() + 1.0, getZ(), 6, 0.15, 0.4, 0.15, 0.005);
        }
    }

    private void clientTurn() {
        Entity owner = level().getEntity(ownerId());
        float target = getYRot();
        int mode = mode();
        if (mode == FIXED) {
            target = fixedYaw();
        } else if (owner != null) {
            double dx = owner.getX() - getX();
            double dz = owner.getZ() - getZ();
            float toward = (float) (Mth.atan2(dz, dx) * (180 / Math.PI)) - 90f;
            boolean away = mode == AWAY;
            if (mode == TURNS && ShadeLook.inView(this)) {
                // Seen: already facing away, instantly.
                yRotO = toward + 180f;
                setYRot(toward + 180f);
                return;
            }
            target = away ? toward + 180f : toward;
        }
        yRotO = getYRot();
        setYRot(Mth.approachDegrees(getYRot(), target, mode == TURNS ? 40f : mode == FIXED ? 30f : 6f));
    }

    /** Smooth position updates; the rotation is the client's own (it turns toward its owner locally). */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = Math.max(1, steps);
    }

    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    // ------------------------------------------------------------------ inert

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    /** The Tall One and the Wide-Armed reach well outside the hitbox; keep them from being culled early. */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return switch (variant()) {
            case TALL_ONE -> getBoundingBox().expandTowards(0, 2.6, 0).inflate(0.3);
            case WIDE_ARMED -> getBoundingBox().inflate(1.4, 0.2, 1.4);
            default -> getBoundingBox().inflate(0.4);
        };
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        managed = false; // a shade from disk is a mistake: it vanishes on its first tick
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
