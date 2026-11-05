package com.seba.reverie.registry;

import com.seba.reverie.Reverie;
import com.seba.reverie.content.effect.DizzinessEffect;
import com.seba.reverie.content.effect.SleepynessEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Reverie.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> DIZZINESS = EFFECTS.register("dizziness", DizzinessEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> SLEEPYNESS = EFFECTS.register("sleepyness", SleepynessEffect::new);

    /** Inside a Sandglass time bubble: slow and floaty, harmless, gone a moment after you leave. */
    public static final DeferredHolder<MobEffect, MobEffect> DRIFT = EFFECTS.register("drift", () -> new MobEffect(
            net.minecraft.world.effect.MobEffectCategory.NEUTRAL, 0xE8D9A8) {
    }.addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, Reverie.id("drift_speed"), -0.35,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY, Reverie.id("drift_gravity"), -0.5,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    private ModEffects() {
    }
}
