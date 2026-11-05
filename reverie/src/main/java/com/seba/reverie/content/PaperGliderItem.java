package com.seba.reverie.content;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A big folded paper glider. Hold use while in the air: you sink slowly and drift forward where you look, and the
 * fall does not hurt. In Dreamland it is how you cross from island to island without falling awake.
 */
public class PaperGliderItem extends Item {
    private static final double SINK = -0.07;
    private static final double CRUISE = 0.34;

    public PaperGliderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.onGround() || player.isInWater() || player.isFallFlying() || player.getAbilities().flying) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 0.7f);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (entity.onGround() || entity.isInWater() || entity.isFallFlying()) {
            entity.stopUsingItem();
            return;
        }
        Vec3 v = entity.getDeltaMovement();
        Vec3 look = entity.getLookAngle();
        Vec3 ahead = new Vec3(look.x, 0, look.z);
        ahead = ahead.lengthSqr() < 1e-4 ? Vec3.ZERO : ahead.normalize().scale(CRUISE);
        // Ease the horizontal speed toward a steady cruise where you look; never sink faster than SINK.
        double hx = v.x + (ahead.x - v.x) * 0.08;
        double hz = v.z + (ahead.z - v.z) * 0.08;
        entity.setDeltaMovement(hx, Math.max(v.y, SINK), hz);
        entity.resetFallDistance();
        int used = getUseDuration(stack, entity) - remaining;
        if (level.isClientSide) {
            if (used % 6 == 0) level.addParticle(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 2.3, entity.getZ(), -hx * 0.2, 0.0, -hz * 0.2);
        } else {
            if (used % 40 == 0) level.playSound(null, entity.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.4f, 0.6f);
            if (used % 20 == 19) stack.hurtAndBreak(1, entity, LivingEntity.getSlotForHand(entity.getUsedItemHand()));
        }
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
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
    }
}
