package com.seba.reverie.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.seba.reverie.Reverie;
import com.seba.reverie.content.melatonia.ConfigRarityFilter;
import com.seba.reverie.registry.ModBlocks;
import com.seba.reverie.registry.ModTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.worldgen.features.FeatureUtils;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleBlockConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Datapack registry entries: features, biome modifiers (and, later, the Dreamland). */
final class WorldGen {
    static final ResourceKey<ConfiguredFeature<?, ?>> WILD_MELATONIA_PATCH = ResourceKey.create(Registries.CONFIGURED_FEATURE, Reverie.id("wild_melatonia_patch"));
    static final ResourceKey<PlacedFeature> WILD_MELATONIA_PLACED = ResourceKey.create(Registries.PLACED_FEATURE, Reverie.id("wild_melatonia"));
    static final ResourceKey<BiomeModifier> ADD_WILD_MELATONIA = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, Reverie.id("add_wild_melatonia"));

    static final ResourceKey<net.minecraft.world.level.dimension.DimensionType> DREAMLAND_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, Reverie.id("dreamland"));
    static final ResourceKey<net.minecraft.world.level.dimension.LevelStem> DREAMLAND_STEM =
            ResourceKey.create(Registries.LEVEL_STEM, Reverie.id("dreamland"));

    private WorldGen() {
    }

    private static net.minecraft.world.level.dimension.DimensionType dreamlandType() {
        return new net.minecraft.world.level.dimension.DimensionType(
                java.util.OptionalLong.of(13000), true, false, false, false, 1.0, true, false,
                com.seba.reverie.dreamland.DreamIslandsChunkGenerator.MIN_Y, com.seba.reverie.dreamland.DreamIslandsChunkGenerator.HEIGHT,
                com.seba.reverie.dreamland.DreamIslandsChunkGenerator.HEIGHT,
                net.minecraft.tags.BlockTags.INFINIBURN_OVERWORLD, Reverie.id("dreamland"), 0.2f,
                new net.minecraft.world.level.dimension.DimensionType.MonsterSettings(true, false,
                        net.minecraft.util.valueproviders.ConstantInt.of(0), 0));
    }

    static DatapackBuiltinEntriesProvider create(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        RegistrySetBuilder builder = new RegistrySetBuilder()
                .add(Registries.DIMENSION_TYPE, ctx -> ctx.register(DREAMLAND_TYPE, dreamlandType()))
                .add(Registries.BIOME, DreamlandBiomes::bootstrap)
                .add(Registries.LEVEL_STEM, ctx -> ctx.register(DREAMLAND_STEM, new net.minecraft.world.level.dimension.LevelStem(
                        ctx.lookup(Registries.DIMENSION_TYPE).getOrThrow(DREAMLAND_TYPE),
                        new com.seba.reverie.dreamland.DreamIslandsChunkGenerator(DreamlandBiomes.biomeSource(ctx.lookup(Registries.BIOME))))))
                .add(Registries.CONFIGURED_FEATURE, ctx -> ctx.register(WILD_MELATONIA_PATCH, new ConfiguredFeature<>(Feature.FLOWER,
                        FeatureUtils.simpleRandomPatchConfiguration(20, PlacementUtils.onlyWhenEmpty(Feature.SIMPLE_BLOCK,
                                new SimpleBlockConfiguration(BlockStateProvider.simple(ModBlocks.WILD_MELATONIA.get())))))))
                .add(Registries.PLACED_FEATURE, ctx -> ctx.register(WILD_MELATONIA_PLACED, new PlacedFeature(
                        ctx.lookup(Registries.CONFIGURED_FEATURE).getOrThrow(WILD_MELATONIA_PATCH),
                        List.of(ConfigRarityFilter.INSTANCE, InSquarePlacement.spread(), PlacementUtils.HEIGHTMAP, BiomeFilter.biome()))))
                .add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, ctx -> ctx.register(ADD_WILD_MELATONIA,
                        new BiomeModifiers.AddFeaturesBiomeModifier(
                                ctx.lookup(Registries.BIOME).getOrThrow(ModTags.Biomes.HAS_WILD_MELATONIA),
                                HolderSet.direct(ctx.lookup(Registries.PLACED_FEATURE).getOrThrow(WILD_MELATONIA_PLACED)),
                                GenerationStep.Decoration.VEGETAL_DECORATION)));
        return new DatapackBuiltinEntriesProvider(output, lookup, builder, Set.of(Reverie.MOD_ID));
    }
}
