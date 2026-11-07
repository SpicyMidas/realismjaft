package com.seba.reverie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.seba.reverie.content.melatonia.LacedStacks;

import net.minecraft.world.item.ItemStack;

/**
 * A stack split in two (picking up half, dropping one, hoppers...) copies its components to both parts. For a stack
 * with laced items inside, the part split off must take the top ones and the rest keep the others.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "split", at = @At("RETURN"))
    private void reverie$splitLacing(int amount, CallbackInfoReturnable<ItemStack> cir) {
        LacedStacks.afterSplit((ItemStack) (Object) this, cir.getReturnValue());
    }
}
