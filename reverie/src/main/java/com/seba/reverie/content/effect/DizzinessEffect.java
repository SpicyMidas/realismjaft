package com.seba.reverie.content.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** The world sways: the camera and input drift are client-side (see client.DizzinessClient). */
public class DizzinessEffect extends MobEffect {
    public DizzinessEffect() {
        super(MobEffectCategory.HARMFUL, 0x9C7BD8);
    }
}
