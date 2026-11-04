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

    private ModEffects() {
    }
}
