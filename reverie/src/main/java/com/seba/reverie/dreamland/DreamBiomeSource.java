package com.seba.reverie.dreamland;

import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * reverie:dream_islands biome source. Vanilla's multi_noise cannot be used: a non-noise generator gets a dummy
 * (all-zero) climate sampler. Instead each island has one biome from large seeded regions, and every position
 * not on an island is The Void Between. The biomes are looked up in the registry, not stored: older saves that
 * still list the first eight by name decode the same way.
 */
public class DreamBiomeSource extends BiomeSource {
    public static final MapCodec<DreamBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME)
    ).apply(i, DreamBiomeSource::new));

    @SuppressWarnings("unchecked")
    private final Holder<Biome>[] biomes = new Holder[IslandField.Kind.values().length];
    private volatile IslandField field;

    public DreamBiomeSource(HolderGetter<Biome> lookup) {
        for (IslandField.Kind kind : IslandField.Kind.values()) biomes[kind.ordinal()] = lookup.getOrThrow(DreamlandBiomeKeys.of(kind));
    }

    void attach(IslandField field) {
        this.field = field;
    }

    public Holder<Biome> biome(IslandField.Kind kind) {
        return biomes[kind.ordinal()];
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(biomes);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        IslandField f = field;
        int voidIndex = IslandField.Kind.VOID.ordinal();
        if (f == null) return biomes[voidIndex];
        IslandField.Island island = f.islandAt((qx << 2) + 2, (qy << 2) + 2, (qz << 2) + 2, 6);
        return island == null ? biomes[voidIndex] : biomes[island.kind.ordinal()];
    }
}
