package com.seba.reverie.content;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Hold it when you fall asleep: one thing found in that dream has at least an even chance to stay when you wake.
 * Like anything carried into a dream, the catcher itself does not come back. (The effect lives in DreamManager.)
 */
public class DreamCatcherItem extends Item {
    public DreamCatcherItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
    }
}
