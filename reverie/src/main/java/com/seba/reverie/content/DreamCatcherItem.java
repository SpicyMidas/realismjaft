package com.seba.reverie.content;

import java.util.List;

import com.seba.reverie.registry.ModComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * Keep it in your inventory (you need not hold it, and it never goes into the dream): while you dream, it almost
 * surely catches one thing from your dream inventory, most likely what you hold as you leave. You wake with it closed
 * around the catch; using it opens it and gives you the thing. A closed catcher catches nothing more until opened.
 * (The catching lives in DreamManager and Keepsakes.)
 */
public class DreamCatcherItem extends Item {
    public DreamCatcherItem(Properties properties) {
        super(properties);
    }

    public static boolean isOpen(ItemStack stack) {
        return stack.getItem() instanceof DreamCatcherItem && !stack.has(ModComponents.CAUGHT.get());
    }

    /** Closes an open catcher around {@code caught}. */
    public static void close(ItemStack catcher, ItemStack caught) {
        catcher.set(ModComponents.CAUGHT.get(), ItemContainerContents.fromItems(List.of(caught.copy())));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemContainerContents caught = stack.get(ModComponents.CAUGHT.get());
        if (caught == null) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            stack.remove(ModComponents.CAUGHT.get());
            caught.nonEmptyItemsCopy().forEach(item -> {
                if (!player.getInventory().add(item)) player.drop(item, false);
            });
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.6f, 1.4f);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getEyeY() - 0.3, player.getZ(), 8, 0.3, 0.2, 0.3, 0.02);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        ItemContainerContents caught = stack.get(ModComponents.CAUGHT.get());
        if (caught == null) {
            tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            return;
        }
        caught.nonEmptyItems().forEach(item -> tooltip.add(Component.translatable("item.reverie.dream_catcher.caught", item.getHoverName())
                .withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.translatable("item.reverie.dream_catcher.open").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
