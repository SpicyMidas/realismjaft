package com.seba.reverie.content.effect;

import com.seba.reverie.Reverie;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Heavy limbs while awake (-10% speed); lets you sleep at any time and dream (see dream package). */
public class SleepynessEffect extends MobEffect {
    public SleepynessEffect() {
        super(MobEffectCategory.NEUTRAL, 0x6F7FCF);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, Reverie.id("sleepyness_slowness"), -0.10,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
