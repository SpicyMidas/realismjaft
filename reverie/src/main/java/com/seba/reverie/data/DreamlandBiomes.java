package com.seba.reverie.data;

import com.seba.reverie.dreamland.DreamBiomeSource;
import com.seba.reverie.dreamland.DreamlandBiomeKeys;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.biome.AmbientAdditionsSettings;
import net.minecraft.world.level.biome.AmbientMoodSettings;
import net.minecraft.world.level.biome.AmbientParticleSettings;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;

/** Dreamland biomes: a fixed twilight palette, each with its own tint, air and sound. No rain, no vanilla mobs. */
final class DreamlandBiomes {
    private DreamlandBiomes() {
    }

    static void bootstrap(BootstrapContext<Biome> ctx) {
        reg(ctx, DreamlandBiomeKeys.PILLOW_FIELDS, 0x9B8BD6, 0x6E5BB8, 0x9FB6FF, ParticleTypes.END_ROD, 0.0006f,
                com.seba.reverie.registry.ModSounds.DREAM_HUM, SoundEvents.AMETHYST_BLOCK_CHIME, 0.0012);
        reg(ctx, DreamlandBiomeKeys.HOLLOW_SPIRES, 0x5A5878, 0x4A4570, 0x7C7AB8, ParticleTypes.ASH, 0.002f,
                com.seba.reverie.registry.ModSounds.SPIRE_DRONE, com.seba.reverie.registry.ModSounds.GLASS_HUM, 0.0015);
        reg(ctx, DreamlandBiomeKeys.MIRROR_MARSH, 0x6FA4B8, 0x4D6E98, 0x7FD0E0, ParticleTypes.GLOW, 0.006f,
                com.seba.reverie.registry.ModSounds.MARSH_HUM, SoundEvents.FROG_AMBIENT, 0.0008);
        reg(ctx, DreamlandBiomeKeys.SANDGLASS_DUNES, 0xD6C49B, 0x9E8A6A, 0xC8D6E8, ParticleTypes.WHITE_ASH, 0.004f,
                com.seba.reverie.registry.ModSounds.DREAM_HUM, SoundEvents.NOTE_BLOCK_HAT.value(), 0.0009);
        reg(ctx, DreamlandBiomeKeys.EYE_MEADOW, 0xB58BC4, 0x7B5A9A, 0xB59AE0, ParticleTypes.END_ROD, 0.0012f,
                com.seba.reverie.registry.ModSounds.DREAM_HUM, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 0.0004);
        reg(ctx, DreamlandBiomeKeys.UPSIDE_DOWN_TOWN, 0x8C7FA8, 0x5D4F7C, 0x9C90D0, ParticleTypes.END_ROD, 0.0004f,
                com.seba.reverie.registry.ModSounds.TOWN_HUM, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.0014);
        reg(ctx, DreamlandBiomeKeys.CLOUDREACH, 0xC9CFF2, 0x8E9CEB, 0xD0DCFF, ParticleTypes.END_ROD, 0.0008f,
                com.seba.reverie.registry.ModSounds.DREAM_HUM, SoundEvents.AMETHYST_BLOCK_CHIME, 0.0006);
        reg(ctx, DreamlandBiomeKeys.VOID_BETWEEN, 0x544A9C, 0x3F3A88, 0x5E58C8, ParticleTypes.END_ROD, 0.0004f,
                com.seba.reverie.registry.ModSounds.VOID_WIND, com.seba.reverie.registry.ModSounds.FAR_BELL, 0.0005);
    }

    /** Sleepwalking Sheep drift over the soft biomes. */
    private static net.minecraft.world.level.biome.MobSpawnSettings spawns(ResourceKey<Biome> key) {
        var b = new net.minecraft.world.level.biome.MobSpawnSettings.Builder();
        if (key == DreamlandBiomeKeys.PILLOW_FIELDS || key == DreamlandBiomeKeys.EYE_MEADOW || key == DreamlandBiomeKeys.CLOUDREACH) {
            b.addSpawn(net.minecraft.world.entity.MobCategory.CREATURE, new net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData(
                    com.seba.reverie.registry.ModEntities.SLEEPWALKING_SHEEP.get(), key == DreamlandBiomeKeys.PILLOW_FIELDS ? 12 : 6, 1, 3));
        }
        return b.build();
    }

    private static void reg(BootstrapContext<Biome> ctx, ResourceKey<Biome> key, int fog, int sky, int water, ParticleOptions particle,
                            float particleChance, Object loop, Object additions, double additionChance) {
        var features = ctx.lookup(Registries.PLACED_FEATURE);
        var carvers = ctx.lookup(Registries.CONFIGURED_CARVER);
        BiomeSpecialEffects.Builder effects = new BiomeSpecialEffects.Builder()
                .fogColor(fog).skyColor(sky).waterColor(water).waterFogColor(fog)
                .ambientParticle(new AmbientParticleSettings(particle, particleChance))
                // In the dark a far bell, not cave noises.
                .ambientMoodSound(new AmbientMoodSettings(com.seba.reverie.registry.ModSounds.FAR_BELL, 6000, 8, 2.0));
        if (loop != null) effects.ambientLoopSound(holder(loop));
        if (additions != null) effects.ambientAdditionsSound(new AmbientAdditionsSettings(holder(additions), additionChance));
        ctx.register(key, new Biome.BiomeBuilder()
                .hasPrecipitation(false).temperature(0.6f).downfall(0f)
                .specialEffects(effects.build())
                .mobSpawnSettings(spawns(key))
                .generationSettings(new BiomeGenerationSettings.Builder(features, carvers).build())
                .build());
    }

    @SuppressWarnings("unchecked")
    private static Holder<SoundEvent> holder(Object sound) {
        if (sound instanceof Holder<?> h) return (Holder<SoundEvent>) h;
        return Holder.direct((SoundEvent) sound);
    }

    static BiomeSource biomeSource(HolderGetter<Biome> biomes) {
        var b = DreamlandBiomeKeys.ALL.stream().map(biomes::getOrThrow).toList();
        return new DreamBiomeSource(b.get(0), b.get(1), b.get(2), b.get(3), b.get(4), b.get(5), b.get(6), b.get(7));
    }
}
