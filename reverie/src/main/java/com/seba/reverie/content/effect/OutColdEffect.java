package com.seba.reverie.content.effect;

import com.seba.reverie.Reverie;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Out Cold: knocked down by a laced hit, lying on the ground (the sleeping pose, which every client renders lying
 * flat) without moving, jumping or targeting anything until it wears off. Content/Collapse puts it on and takes it off.
 */
public class OutColdEffect extends MobEffect {
    public OutColdEffect() {
        super(MobEffectCategory.HARMFUL, 0x3B2A5C);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, Reverie.id("out_cold_speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, Reverie.id("out_cold_jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        if (entity instanceof Player player) {
            if (player.getForcedPose() != Pose.SLEEPING) player.setForcedPose(Pose.SLEEPING);
        } else if (!entity.hasPose(Pose.SLEEPING)) {
            entity.setPose(Pose.SLEEPING);
        }
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.setAggressive(false);
        }
        // Flying things drop too.
        var v = entity.getDeltaMovement();
        entity.setDeltaMovement(v.x * 0.5, Math.min(v.y, entity.onGround() ? 0 : -0.25), v.z * 0.5);
        return true;
    }

    /** Back on its feet (the effect ended, ran out, or was cleared). */
    public static void stand(LivingEntity entity) {
        if (entity instanceof Player player) {
            if (player.getForcedPose() == Pose.SLEEPING) player.setForcedPose(null);
        } else if (entity.hasPose(Pose.SLEEPING) && !entity.isSleeping()) {
            entity.setPose(Pose.STANDING);
        }
        entity.refreshDimensions();
    }
}
