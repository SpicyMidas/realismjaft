package com.seba.reverie.dreamland;

import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * reverie:dream_islands biome source. Vanilla's multi_noise cannot be used: a non-noise generator gets a dummy
 * (all-zero) climate sampler. Instead each island has one biome from large seeded regions, and every position
 * not on an island is The Void Between.
 */
public class DreamBiomeSource extends BiomeSource {
    public static final MapCodec<DreamBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("pillow_fields").forGetter(s -> s.biomes[0]),
            Biome.CODEC.fieldOf("hollow_spires").forGetter(s -> s.biomes[1]),
            Biome.CODEC.fieldOf("mirror_marsh").forGetter(s -> s.biomes[2]),
            Biome.CODEC.fieldOf("sandglass_dunes").forGetter(s -> s.biomes[3]),
            Biome.CODEC.fieldOf("eye_meadow").forGetter(s -> s.biomes[4]),
            Biome.CODEC.fieldOf("upside_down_town").forGetter(s -> s.biomes[5]),
            Biome.CODEC.fieldOf("cloudreach").forGetter(s -> s.biomes[6]),
            Biome.CODEC.fieldOf("void_between").forGetter(s -> s.biomes[7])
    ).apply(i, DreamBiomeSource::new));

    @SuppressWarnings("unchecked")
    private final Holder<Biome>[] biomes = new Holder[8];
    private volatile IslandField field;

    public DreamBiomeSource(Holder<Biome> pillow, Holder<Biome> spires, Holder<Biome> marsh, Holder<Biome> dunes,
                            Holder<Biome> eye, Holder<Biome> town, Holder<Biome> cloud, Holder<Biome> voidBetween) {
        biomes[0] = pillow;
        biomes[1] = spires;
        biomes[2] = marsh;
        biomes[3] = dunes;
        biomes[4] = eye;
        biomes[5] = town;
        biomes[6] = cloud;
        biomes[7] = voidBetween;
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
        if (f == null) return biomes[7];
        IslandField.Island island = f.islandAt((qx << 2) + 2, (qy << 2) + 2, (qz << 2) + 2, 6);
        return island == null ? biomes[7] : biomes[island.kind.ordinal()];
    }
}
