package com.seba.reverie.dreamland;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seba.reverie.Reverie;
import com.seba.reverie.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * reverie:dream_islands. Floating islands written directly (no noise router), deterministic from the world seed,
 * chunk-local and thread-safe. Decorations and landmarks are placed in {@link #applyBiomeDecoration}.
 */
public class DreamIslandsChunkGenerator extends ChunkGenerator {
    public static final MapCodec<DreamIslandsChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)
    ).apply(i, DreamIslandsChunkGenerator::new));

    public static final int MIN_Y = -64;
    public static final int HEIGHT = 448;

    private volatile IslandField field;
    /** Generation cost: chunks filled and nanoseconds spent (for /reverie debug genstats). */
    public static final java.util.concurrent.atomic.AtomicLong FILLED = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong FILL_NANOS = new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong DECO_NANOS = new java.util.concurrent.atomic.AtomicLong();

    public DreamIslandsChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    /** The island field of this world (null until the server created the level). */
    public IslandField field() {
        return field;
    }

    private void init(long seed) {
        if (field != null && field.seed() == seed) return;
        field = new IslandField(seed);
        if (biomeSource instanceof DreamBiomeSource dbs) dbs.attach(field);
    }

    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        init(seed); // the only place a generator learns the world seed
        return super.createState(structureSets, randomState, seed);
    }

    private IslandField fieldOrDefault() {
        IslandField f = field;
        if (f == null) {
            Reverie.LOGGER.warn("Dreamland generator used before its seed was known; using seed 0");
            init(0L);
            f = field;
        }
        return f;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState random, BiomeManager biomeManager,
                             StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving step) {
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager, RandomState random, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
        // Like the noise generator, but the biome is read at the island surface (the top of the world is void).
        var chunk = level.getCenter();
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z);
        if (top <= level.getMinBuildHeight() + 4) return;
        var biome = level.getBiome(new net.minecraft.core.BlockPos(x, top, z));
        var random = new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.LegacyRandomSource(net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(level.getSeed(), chunk.getMinBlockX(), chunk.getMinBlockZ());
        net.minecraft.world.level.NaturalSpawner.spawnMobsForChunkGeneration(level, biome, chunk, random);
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        long t0 = System.nanoTime();
        try {
            IslandFiller.fill(fieldOrDefault(), chunk);
            FILLED.incrementAndGet();
            FILL_NANOS.addAndGet(System.nanoTime() - t0);
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Dreamland chunk {} failed to generate; leaving it empty", chunk.getPos(), e);
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        super.applyBiomeDecoration(level, chunk, structureManager);
        long t0 = System.nanoTime();
        try {
            IslandDecorator.decorate(level, chunk, fieldOrDefault());
            DECO_NANOS.addAndGet(System.nanoTime() - t0);
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Dreamland decoration failed in chunk {}", chunk.getPos(), e);
        }
    }

    @Override
    public int getSeaLevel() {
        return MIN_Y;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        int top = fieldOrDefault().highestTop(x, z);
        return top == Integer.MIN_VALUE ? level.getMinBuildHeight() : Math.min(level.getMaxBuildHeight(), top + 1);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        BlockState[] states = new BlockState[height.getHeight()];
        Arrays.fill(states, Blocks.AIR.defaultBlockState());
        IslandField f = fieldOrDefault();
        BlockState stone = ModBlocks.LUCID_STONE.get().defaultBlockState();
        for (IslandField.Island is : f.islandsNear(x, z, x, z)) {
            int top = f.topOf(is, x, z);
            if (top == Integer.MIN_VALUE) continue;
            int bottom = is.shape == IslandField.Shape.INVERTED ? is.cy - 2 : Math.min(top, f.plateauBottom(is, x, z));
            for (int y = Math.max(bottom, height.getMinBuildHeight()); y <= Math.min(top, height.getMaxBuildHeight() - 1); y++) {
                states[y - height.getMinBuildHeight()] = stone;
            }
        }
        return new NoiseColumn(height.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        IslandField f = field;
        if (f == null) return;
        IslandField.Island is = f.islandAt(pos.getX(), pos.getY(), pos.getZ(), 6);
        info.add(is == null ? "Dreamland: the void between" : String.format("Dreamland: %s %s r=%d layer=%d",
                is.kind.name().toLowerCase(java.util.Locale.ROOT), is.shape.name().toLowerCase(java.util.Locale.ROOT), is.radius, is.layer));
    }
}
