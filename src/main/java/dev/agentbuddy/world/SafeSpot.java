package dev.agentbuddy.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds a standable spot (solid, harmless floor; two free, dry blocks above it) near a position, nearest first.
 * Used for teleports: following, fetch timeouts, waystones and death return.
 */
public final class SafeSpot {
    private SafeSpot() {
    }

    /** Nearest safe feet position within {@code radius} blocks of {@code around}, or null. */
    public static BlockPos find(ServerLevel level, BlockPos around, int radius) {
        return find(level, around, radius, 0);
    }

    /** As {@link #find(ServerLevel, BlockPos, int)}, but at least {@code minDistance} blocks away (e.g. not on top of a player). */
    public static BlockPos find(ServerLevel level, BlockPos around, int radius, double minDistance) {
        BlockPos center = around;
        // Died in the void or above the build limit: search from the surface at that x/z instead.
        if (center.getY() <= level.getMinBuildHeight() || center.getY() >= level.getMaxBuildHeight() - 2) {
            center = new BlockPos(center.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ()), center.getZ());
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    candidates.add(center.offset(dx, dy, dz));
                }
            }
        }
        BlockPos c = center;
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(c)));
        double minSqr = minDistance * minDistance;
        for (BlockPos p : candidates) {
            if (p.distSqr(c) >= minSqr && isSafe(level, p)) return p;
        }
        return null;
    }

    public static boolean isSafe(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 1 >= level.getMaxBuildHeight()) return false;
        BlockState floor = level.getBlockState(feet.below());
        if (!floor.isFaceSturdy(level, feet.below(), Direction.UP)) return false;
        if (floor.is(Blocks.MAGMA_BLOCK) || floor.is(BlockTags.FIRE) || floor.is(BlockTags.CAMPFIRES)) return false;
        return isFree(level, feet) && isFree(level, feet.above());
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty() && !s.is(BlockTags.FIRE)
                && !s.is(Blocks.SWEET_BERRY_BUSH) && !s.is(Blocks.POWDER_SNOW) && !s.is(Blocks.COBWEB);
    }
}
