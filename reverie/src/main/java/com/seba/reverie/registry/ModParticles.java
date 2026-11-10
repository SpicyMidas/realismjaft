package com.seba.reverie.registry;

import com.seba.reverie.Reverie;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Dream particles. They keep the velocity they are spawned with, so the same petal can fall, drift or rise. */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Reverie.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DREAM_PETAL = PARTICLES.register("dream_petal", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DREAM_PAGE = PARTICLES.register("dream_page", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DREAM_RAINDROP = PARTICLES.register("dream_raindrop", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DREAM_Z = PARTICLES.register("dream_z", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DREAM_SNOW = PARTICLES.register("dream_snow", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DREAM_BUBBLE = PARTICLES.register("dream_bubble", () -> new SimpleParticleType(false));

    private ModParticles() {
    }
}
