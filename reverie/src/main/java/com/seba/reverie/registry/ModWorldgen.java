package com.seba.reverie.registry;

import com.seba.reverie.Reverie;
import com.seba.reverie.content.melatonia.ConfigRarityFilter;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorldgen {
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Reverie.MOD_ID);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ConfigRarityFilter>> CONFIG_RARITY =
            PLACEMENT_MODIFIERS.register("config_rarity", () -> () -> ConfigRarityFilter.CODEC);

    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Reverie.MOD_ID);
    public static final DeferredHolder<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.chunk.ChunkGenerator>,
            com.mojang.serialization.MapCodec<com.seba.reverie.dreamland.DreamIslandsChunkGenerator>> DREAM_ISLANDS =
            CHUNK_GENERATORS.register("dream_islands", () -> com.seba.reverie.dreamland.DreamIslandsChunkGenerator.CODEC);

    public static final DeferredRegister<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.biome.BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, Reverie.MOD_ID);
    public static final DeferredHolder<com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.biome.BiomeSource>,
            com.mojang.serialization.MapCodec<com.seba.reverie.dreamland.DreamBiomeSource>> DREAM_BIOMES =
            BIOME_SOURCES.register("dream_islands", () -> com.seba.reverie.dreamland.DreamBiomeSource.CODEC);

    private ModWorldgen() {
    }
}
