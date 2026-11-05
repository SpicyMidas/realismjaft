package com.seba.reverie.data;

import com.seba.reverie.dreamland.DreamlandBiomeKeys;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;

/** Dreamland biomes. Phase 4 only needs the open void; the island biomes are added with the generator. */
final class DreamlandBiomes {
    private DreamlandBiomes() {
    }

    static void bootstrap(BootstrapContext<Biome> ctx) {
        var features = ctx.lookup(Registries.PLACED_FEATURE);
        var carvers = ctx.lookup(Registries.CONFIGURED_CARVER);
        ctx.register(DreamlandBiomeKeys.VOID_BETWEEN, biome(features, carvers, 0x1E1B44, 0x2C2766, 0x5E58C8,
                new AmbientParticleSettings(ParticleTypes.WHITE_ASH, 0.0015f)));
    }

    static BiomeSource biomeSource(HolderGetter<Biome> biomes) {
        return new FixedBiomeSource(biomes.getOrThrow(DreamlandBiomeKeys.VOID_BETWEEN));
    }

    static Biome biome(HolderGetter<net.minecraft.world.level.levelgen.placement.PlacedFeature> features,
                       HolderGetter<net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver<?>> carvers,
                       int fog, int sky, int water, AmbientParticleSettings particles) {
        BiomeSpecialEffects.Builder effects = new BiomeSpecialEffects.Builder()
                .fogColor(fog).skyColor(sky).waterColor(water).waterFogColor(fog)
                .ambientMoodSound(AmbientMoodSettings.LEGACY_CAVE_SETTINGS);
        if (particles != null) effects.ambientParticle(particles);
        return new Biome.BiomeBuilder()
                .hasPrecipitation(false).temperature(0.6f).downfall(0f)
                .specialEffects(effects.build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(new BiomeGenerationSettings.Builder(features, carvers).build())
                .build();
    }
}
