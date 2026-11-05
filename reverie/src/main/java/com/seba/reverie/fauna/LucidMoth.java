package com.seba.reverie.fauna;

import javax.annotation.Nullable;

import com.seba.reverie.Reverie;
import com.seba.reverie.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * A small glowing moth circling a Floating Lantern. Lanterns in Dreamland call them (see FloatingLanternBlock); they
 * are never saved, pass through blocks, and fade away a little while after their lantern is gone. Drops Dream Dust.
 */
public class LucidMoth extends AmbientCreature {
    private static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, Reverie.id("entities/lucid_moth"));

    @Nullable
    private BlockPos home;
    private float angle;
    private float radius = 1.6f;
    private float speed = 0.06f;
    private int orphanTicks;

    public LucidMoth(EntityType<? extends AmbientCreature> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0);
    }

    /** Called by the lantern that summoned it. */
    public void circle(BlockPos lantern, float startAngle) {
        this.home = lantern;
        this.angle = startAngle;
        this.radius = 1.2f + random.nextFloat() * 1.3f;
        this.speed = (0.04f + random.nextFloat() * 0.05f) * (random.nextBoolean() ? 1 : -1);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (home == null || !level().getBlockState(home).is(ModBlocks.FLOATING_LANTERN.get())) {
            // Lantern gone: wander a little, then fade out.
            setDeltaMovement(getDeltaMovement().scale(0.9).add((random.nextDouble() - 0.5) * 0.02, 0.003, (random.nextDouble() - 0.5) * 0.02));
            if (++orphanTicks > 200) discard();
            return;
        }
        angle += speed;
        Vec3 c = Vec3.atCenterOf(home);
        double y = c.y + 0.2 + Mth.sin(tickCount * 0.11f + getId()) * 0.35;
        Vec3 target = new Vec3(c.x + Mth.cos(angle) * radius, y, c.z + Mth.sin(angle) * radius);
        setDeltaMovement(target.subtract(position()).scale(0.35));
        setYRot((float) (Mth.atan2(getDeltaMovement().z, getDeltaMovement().x) * (180 / Math.PI)) - 90f);
    }

    @Override
    public void move(net.minecraft.world.entity.MoverType type, Vec3 movement) {
        setPos(getX() + movement.x, getY() + movement.y, getZ() + movement.z);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public ResourceKey<LootTable> getDefaultLootTable() {
        return LOOT;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
    }
}
