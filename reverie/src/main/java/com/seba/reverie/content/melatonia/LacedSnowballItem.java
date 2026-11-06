package com.seba.reverie.content.melatonia;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;

/** Thrown like a snowball (and from dispensers). */
public class LacedSnowballItem extends Item implements ProjectileItem {
    public LacedSnowballItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.NEUTRAL, 0.5f,
                0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
        if (!level.isClientSide) {
            LacedSnowball ball = new LacedSnowball(level, player);
            ball.setItem(stack);
            ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 1.5f, 1f);
            level.addFreshEntity(ball);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        LacedSnowball ball = new LacedSnowball(level, pos.x(), pos.y(), pos.z());
        ball.setItem(stack);
        return ball;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        int level = Math.max(1, Lacing.level(stack));
        tooltip.add(net.minecraft.network.chat.Component.translatable(level >= 2 ? "item.reverie.concentrated_melatonia_extract" : "item.reverie.melatonia_extract")
                .withColor(Lacing.tagColor(level)));
    }
}
