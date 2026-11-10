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
    /** Sleepyness from laced food: dreams that start with it always start in a nightmare. */
    public static final DeferredHolder<MobEffect, MobEffect> EVIL_SLEEPYNESS = EFFECTS.register("evil_sleepyness",
            () -> new SleepynessEffect(net.minecraft.world.effect.MobEffectCategory.HARMFUL, 0x8E2F5A));

    /** Inside a Sandglass time bubble: slow and floaty, harmless, gone a moment after you leave. */
    public static final DeferredHolder<MobEffect, MobEffect> DRIFT = EFFECTS.register("drift", () -> new MobEffect(
            net.minecraft.world.effect.MobEffectCategory.NEUTRAL, 0xE8D9A8) {
    }.addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, Reverie.id("drift_speed"), -0.35,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY, Reverie.id("drift_gravity"), -0.5,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Out Cold: knocked flat by a laced arrow or snowball. */
    public static final DeferredHolder<MobEffect, MobEffect> OUT_COLD = EFFECTS.register("out_cold", com.seba.reverie.content.effect.OutColdEffect::new);
    /** Lucid Sight: a short glimpse of the ores through the stone (client/LucidSightClient draws it). */
    public static final DeferredHolder<MobEffect, MobEffect> LUCID_SIGHT = EFFECTS.register("lucid_sight", () -> new MobEffect(
            net.minecraft.world.effect.MobEffectCategory.BENEFICIAL, 0x9FE8FF) {
    });

    /** Either Sleepyness: both let you sleep at any time and dream. */
    public static boolean sleepy(net.minecraft.world.entity.LivingEntity entity) {
        return entity.hasEffect(SLEEPYNESS) || entity.hasEffect(EVIL_SLEEPYNESS);
    }

    private ModEffects() {
    }
}
