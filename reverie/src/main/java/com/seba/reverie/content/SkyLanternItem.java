package com.seba.reverie.content;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A paper sky lantern on a bamboo stick, the Paper Glider's opposite: hold use and raise it, and its hot air lifts you,
 * slowly, drifting a little where you look. Let go and it stops (and you fall: that is what the glider is for).
 * The lift is vanilla Levitation, hidden, so servers do not take it for flying. Dropped, it floats away upward and
 * burns out near the top of the sky.
 */
public class SkyLanternItem extends Item {
    /** Levitation III: up at about 3 blocks a second. */
    private static final int LIFT = 2;
    private static final double DRIFT = 0.1;

    public SkyLanternItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isInWater() || player.isFallFlying() || player.getAbilities().flying) return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35f, 1.6f);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity.isInWater() || entity.isFallFlying()) {
            entity.stopUsingItem();
            return;
        }
        int used = getUseDuration(stack, entity) - remaining;
        if (!level.isClientSide) {
            MobEffectInstance lift = entity.getEffect(MobEffects.LEVITATION);
            if (lift == null || lift.getAmplifier() != LIFT || lift.getDuration() < 4) {
                entity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 8, LIFT, true, false, false));
            }
            if (used % 50 == 0) level.playSound(null, entity.blockPosition(), SoundEvents.CAMPFIRE_CRACKLE, SoundSource.PLAYERS, 0.5f, 1.3f);
            if (used % 20 == 19) stack.hurtAndBreak(1, entity, LivingEntity.getSlotForHand(entity.getUsedItemHand()));
        } else if (used % 3 == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, entity.getX(), entity.getY() + entity.getBbHeight() + 0.9, entity.getZ(), 0, 0.02, 0);
        }
        // A slow drift where you look, as a lantern goes with the wind.
        Vec3 v = entity.getDeltaMovement();
        Vec3 look = entity.getLookAngle();
        Vec3 ahead = new Vec3(look.x, 0, look.z);
        ahead = ahead.lengthSqr() < 1e-4 ? Vec3.ZERO : ahead.normalize().scale(DRIFT);
        entity.setDeltaMovement(v.x + (ahead.x - v.x) * 0.05, v.y, v.z + (ahead.z - v.z) * 0.05);
        entity.resetFallDistance();
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        MobEffectInstance lift = entity.getEffect(MobEffects.LEVITATION);
        if (!level.isClientSide && lift != null && lift.isAmbient() && !lift.isVisible()) entity.removeEffect(MobEffects.LEVITATION);
    }

    /** Let go of it and it goes up and away, and burns out a little under the top of the sky. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.setNoGravity(true);
        Vec3 v = entity.getDeltaMovement();
        entity.setDeltaMovement(v.x * 0.92, Math.min(0.09, v.y + 0.006), v.z * 0.92);
        Level level = entity.level();
        if (level.isClientSide) {
            if (entity.tickCount % 4 == 0) level.addParticle(ParticleTypes.SMALL_FLAME, entity.getX(), entity.getY() + 0.15, entity.getZ(), 0, -0.01, 0);
        } else if (entity.getY() > level.getMaxBuildHeight() - 6) {
            ((ServerLevel) level).sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 0.3, entity.getZ(), 8, 0.1, 0.1, 0.1, 0.01);
            entity.discard();
            return true;
        }
        return false;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
    }
}
