package com.seba.reverie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.seba.reverie.client.dreamland.DreamMovementClient;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/** Upside down the eyes belong near the underside you stand on: the camera drops to where they would be. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    public abstract Vec3 getPosition();

    @Inject(method = "setup", at = @At("TAIL"))
    private void reverie$upsideDown(BlockGetter level, Entity entity, boolean detached, boolean reverse, float partial, CallbackInfo ci) {
        if (!(entity instanceof LivingEntity living)) return;
        double drop = DreamMovementClient.cameraDrop(living, partial);
        if (drop > 0) setPosition(getPosition().subtract(0, drop, 0));
    }
}
