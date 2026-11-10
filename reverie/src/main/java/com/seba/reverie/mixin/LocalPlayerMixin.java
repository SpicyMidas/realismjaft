package com.seba.reverie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.seba.reverie.client.dreamland.BiomeMechanicsClient;

import net.minecraft.client.player.LocalPlayer;

/** Blackwood: like in a nightmare, you cannot run. Starting and keeping a sprint both ask this. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "hasEnoughFoodToStartSprinting", at = @At("HEAD"), cancellable = true)
    private void reverie$heavyLegs(CallbackInfoReturnable<Boolean> cir) {
        if (BiomeMechanicsClient.heavyLegs()) cir.setReturnValue(false);
    }
}
