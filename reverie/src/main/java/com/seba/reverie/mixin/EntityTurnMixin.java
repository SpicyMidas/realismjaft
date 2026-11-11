package com.seba.reverie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.seba.reverie.client.dreamland.DreamMovementClient;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Upside down the view is turned over, so the mouse is too (both axes), and looking still goes where you push. */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double reverie$yaw(double yRot) {
        return flipped() ? -yRot : yRot;
    }

    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double reverie$pitch(double xRot) {
        return flipped() ? -xRot : xRot;
    }

    private boolean flipped() {
        return (Object) this == Minecraft.getInstance().player && DreamMovementClient.controlsFlipped();
    }
}
