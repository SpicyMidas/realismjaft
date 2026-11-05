package com.seba.reverie.fauna;

import java.util.EnumSet;

import javax.annotation.Nullable;

import com.seba.reverie.Reverie;
import com.seba.reverie.registry.ModParticles;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * A pale sheep asleep on its feet, drifting a couple of blocks above the ground and snoring little Zs. Passive: it
 * does not flee, follow food or breed. Drops Dream Dust. Only spawns in Dreamland.
 */
public class SleepwalkingSheep extends Sheep {
    private static final double HOVER = 2.2;
    private static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, Reverie.id("entities/sleepwalking_sheep"));

    public SleepwalkingSheep(EntityType<? extends Sheep> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 10, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Sheep.createAttributes().add(Attributes.FLYING_SPEED, 0.18);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals(); // keeps the eat goal: Sheep's AI step reads it
        goalSelector.removeAllGoals(g -> g instanceof PanicGoal || g instanceof BreedGoal || g instanceof TemptGoal
                || g instanceof FollowParentGoal || g instanceof RandomStrollGoal);
        goalSelector.addGoal(5, new DriftGoal(this));
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public float getVoicePitch() {
        return 0.65f;
    }

    @Override
    public ResourceKey<LootTable> getDefaultLootTable() {
        return LOOT;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType type, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, type, data);
        setColor(DyeColor.WHITE);
        setPos(getX(), getY() + HOVER, getZ());
        return out;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    /** Height of the ground under the sheep, or NaN over the void. */
    double groundBelow() {
        int top = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, getBlockX(), getBlockZ());
        return top <= level().getMinBuildHeight() + 4 || top > getY() + 1 ? Double.NaN : top;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            if (random.nextInt(28) == 0) {
                level().addParticle(ModParticles.DREAM_Z.get(), getX() + (random.nextDouble() - 0.5) * 0.4, getY() + getBbHeight() + 0.2,
                        getZ() + (random.nextDouble() - 0.5) * 0.4, 0.004, 0.025, 0.004);
            }
            return;
        }
        // Keep a sleepy hover over the ground with a slow bob; over the void, sink back toward the last island.
        double ground = groundBelow();
        Vec3 v = getDeltaMovement();
        if (!Double.isNaN(ground) && getY() - ground < 24) {
            double target = ground + HOVER + Mth.sin(tickCount * 0.05f + getId()) * 0.25;
            setDeltaMovement(v.x, v.y * 0.85 + (target - getY()) * 0.02, v.z);
        } else {
            setDeltaMovement(v.x, v.y * 0.9 - 0.004, v.z);
        }
    }

    /** Every few seconds, drift a few blocks to a spot over ground at about the same height. */
    static final class DriftGoal extends Goal {
        private final SleepwalkingSheep sheep;

        DriftGoal(SleepwalkingSheep sheep) {
            this.sheep = sheep;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !sheep.isLeashed() && sheep.getRandom().nextInt(70) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return sheep.getMoveControl().hasWanted();
        }

        @Override
        public void start() {
            var rng = sheep.getRandom();
            double here = sheep.groundBelow();
            for (int attempt = 0; attempt < 8; attempt++) {
                double a = rng.nextDouble() * Math.PI * 2;
                double r = 2 + rng.nextDouble() * 5;
                int x = Mth.floor(sheep.getX() + Math.cos(a) * r);
                int z = Mth.floor(sheep.getZ() + Math.sin(a) * r);
                int top = sheep.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (top <= sheep.level().getMinBuildHeight() + 4) continue;
                if (!Double.isNaN(here) && Math.abs(top - here) > 4) continue;
                sheep.getMoveControl().setWantedPosition(x + 0.5, top + HOVER, z + 0.5, 0.6);
                return;
            }
        }
    }
}
