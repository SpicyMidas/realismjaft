package com.seba.reverie.dream;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Every player has a fixed Home column in Dreamland (from their UUID, within ~3000 blocks). Arriving, they land
 * on the nearest safe ground there, or on a small Home platform that is built once and then persists.
 */
public final class HomeIsland {
    public static final int RANGE = 3000;
    public static final int PREFERRED_Y = 96;

    /** Builds the 7x7 platform; replaced by the Dreamland palette once that exists. */
    public interface Builder {
        void build(ServerLevel level, BlockPos center);
    }

    public static volatile Builder builder = HomeIsland::dreamPlatform;

    private HomeIsland() {
    }

    public static BlockPos homeColumn(UUID id) {
        long h = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ id.getLeastSignificantBits();
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        int x = (int) Math.floorMod(h, 2L * RANGE) - RANGE;
        int z = (int) Math.floorMod(h >>> 20, 2L * RANGE) - RANGE;
        return new BlockPos(x, PREFERRED_Y, z);
    }

    /** A standing position (feet) at the player's Home, building the platform if needed. Loads the chunks. */
    public static BlockPos findOrBuild(ServerLevel level, UUID id) {
        BlockPos column = homeColumn(id);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                level.getChunk((column.getX() >> 4) + dx, (column.getZ() >> 4) + dz);
        BlockPos island = com.seba.reverie.dreamland.IslandSearch.homeSpot(level, column);
        if (island != null) return island;
        BlockPos safe = findSafe(level, column, 8, PREFERRED_Y - 40, PREFERRED_Y + 220);
        if (safe != null) return safe;
        BlockPos center = column.atY(Mth.clamp(PREFERRED_Y, level.getMinBuildHeight() + 8, level.getMaxBuildHeight() - 16));
        builder.build(level, center);
        return center.above();
    }

    /** Puts the Lantern Stand a couple of steps from where the dreamer arrives. False if there was no room for it. */
    public static boolean lanternStand(ServerLevel level, BlockPos arrival) {
        int[][] around = {{2, 0}, {0, 2}, {-2, 0}, {0, -2}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}, {3, 1}, {1, 3}, {-3, -1}, {-1, -3}};
        for (int dy = 0; dy <= 2; dy++) {
            for (int[] o : around) {
                for (int sign : new int[]{0, 1, -1}) {
                    BlockPos p = arrival.offset(o[0], sign * dy, o[1]);
                    if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) continue;
                    if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)) continue;
                    level.setBlock(p, com.seba.reverie.registry.ModBlocks.LANTERN_STAND.get().defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                    return true;
                }
            }
        }
        return false;
    }

    public static BlockPos findSafe(ServerLevel level, BlockPos column, int radius, int minY, int maxY) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = column.getX() + dx;
                    int z = column.getZ() + dz;
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (top <= level.getMinBuildHeight() + 1 || top < minY || top > maxY) continue;
                    BlockPos feet = new BlockPos(x, top, z);
                    if (isSafeStand(level, feet)) return feet;
                }
            }
        }
        return null;
    }

    public static boolean isSafeStand(ServerLevel level, BlockPos feet) {
        BlockState below = level.getBlockState(feet.below());
        return below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty();
    }

    /** 7x7 Dream Grass disc with one small dreamwood tree and a floating lantern. Only fills air. */
    private static void dreamPlatform(ServerLevel level, BlockPos center) {
        BlockState grass = com.seba.reverie.registry.ModBlocks.DREAM_GRASS_BLOCK.get().defaultBlockState();
        BlockState soil = com.seba.reverie.registry.ModBlocks.DREAM_SOIL.get().defaultBlockState();
        BlockState stone = com.seba.reverie.registry.ModBlocks.LUCID_STONE.get().defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > 12) continue;
                setIfAir(level, center.offset(dx, 0, dz), grass);
                setIfAir(level, center.offset(dx, -1, dz), soil);
                if (d2 <= 6) setIfAir(level, center.offset(dx, -2, dz), stone);
                if (d2 <= 2) setIfAir(level, center.offset(dx, -3, dz), stone);
            }
        }
        BlockState log = com.seba.reverie.registry.ModBlocks.DREAMWOOD_LOG.get().defaultBlockState();
        BlockState leaves = com.seba.reverie.registry.ModBlocks.DREAMWOOD_LEAVES.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        BlockPos trunk = center.offset(-2, 1, -1);
        for (int i = 0; i < 4; i++) setIfAir(level, trunk.above(i), log);
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = 3; dy <= 5; dy++)
                for (int dz = -2; dz <= 2; dz++)
                    if (dx * dx + (dy - 4) * (dy - 4) * 2 + dz * dz <= 5) setIfAir(level, trunk.offset(dx, dy, dz), leaves);
        setIfAir(level, center.offset(2, 3, 2), com.seba.reverie.registry.ModBlocks.FLOATING_LANTERN.get().defaultBlockState());
    }

    private static void setIfAir(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    @SuppressWarnings("unused")
    private static void basicPlatform(ServerLevel level, BlockPos center) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 12) continue;
                BlockPos p = center.offset(dx, 0, dz);
                if (level.getBlockState(p).isAir()) level.setBlockAndUpdate(p, Blocks.MOSS_BLOCK.defaultBlockState());
            }
        }
        BlockPos lantern = center.offset(2, 1, 2);
        if (level.getBlockState(lantern).isAir()) level.setBlockAndUpdate(lantern, Blocks.LANTERN.defaultBlockState());
    }
}
