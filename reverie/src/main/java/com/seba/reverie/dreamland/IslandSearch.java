package com.seba.reverie.dreamland;

import com.seba.reverie.dream.HomeIsland;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/** Finding spots on islands from server code (Lone Doors, Home Island). */
public final class IslandSearch {
    private IslandSearch() {
    }

    public static IslandField field(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof DreamIslandsChunkGenerator g ? g.field() : null;
    }

    /** A safe standing spot near the middle of the island, or null. Loads (generates) the chunk. */
    public static BlockPos spotOn(ServerLevel level, IslandField field, IslandField.Island is, double fraction, double angle) {
        int x = is.cx + (int) Math.round(Math.cos(angle) * is.radius * fraction);
        int z = is.cz + (int) Math.round(Math.sin(angle) * is.radius * fraction);
        int top = field.topOf(is, x, z);
        if (top == Integer.MIN_VALUE) return null;
        level.getChunk(x >> 4, z >> 4);
        return HomeIsland.findSafe(level, new BlockPos(x, top, z), 6, top - 6, top + 12);
    }

    public static BlockPos randomIslandSpot(ServerLevel level, BlockPos from, int range, RandomSource random) {
        IslandField field = field(level);
        if (field == null) return null;
        IslandField.Island here = field.islandAt(from.getX(), from.getY(), from.getZ(), 6);
        for (int attempt = 0; attempt < 16; attempt++) {
            int x = from.getX() + random.nextInt(2 * range + 1) - range;
            int z = from.getZ() + random.nextInt(2 * range + 1) - range;
            int layer = random.nextInt(10) == 0 ? IslandField.layerCount() - 1 : random.nextInt(IslandField.layerCount() - 1);
            IslandField.Island is = field.island(layer, Math.floorDiv(x, IslandField.CELL), Math.floorDiv(z, IslandField.CELL));
            if (is == null || here != null && is.id == here.id) continue;
            BlockPos spot = spotOn(level, field, is, random.nextDouble() * 0.4, random.nextDouble() * Math.PI * 2);
            if (spot != null) return spot;
        }
        return null;
    }

    /** For a door standing in the sky (top of a Staircase to Nowhere): the island the stairs started from. */
    public static BlockPos skyDoorReturn(ServerLevel level, BlockPos door) {
        IslandField field = field(level);
        if (field == null) return null;
        int below = field.highestTop(door.getX(), door.getZ());
        if (below != Integer.MIN_VALUE && door.getY() - below < 6) return null;
        IslandField.Island best = null;
        double bestD = Double.MAX_VALUE;
        for (IslandField.Island is : field.islandsNear(door.getX() - 80, door.getZ() - 80, door.getX() + 80, door.getZ() + 80)) {
            if (is.shape != IslandField.Shape.STAIRS || is.cy >= door.getY()) continue;
            double d = (is.cx - door.getX()) * (double) (is.cx - door.getX()) + (is.cz - door.getZ()) * (double) (is.cz - door.getZ());
            if (d < bestD) {
                bestD = d;
                best = is;
            }
        }
        return best == null ? null : spotOn(level, field, best, 0.1, 0);
    }

    /** The island nearest to the player's Home column, in the two lowest layers. */
    public static BlockPos homeSpot(ServerLevel level, BlockPos column) {
        IslandField field = field(level);
        if (field == null) return null;
        IslandField.Island best = null;
        double bestD = Double.MAX_VALUE;
        for (IslandField.Island is : field.islandsNear(column.getX() - 60, column.getZ() - 60, column.getX() + 60, column.getZ() + 60)) {
            if (is.layer > 1 || is.shape == IslandField.Shape.INVERTED || is.shape == IslandField.Shape.CLOUD
                    || is.shape == IslandField.Shape.SEA_OF_DOORS || is.shape == IslandField.Shape.GREAT_BED) continue;
            double d = (is.cx - column.getX()) * (double) (is.cx - column.getX()) + (is.cz - column.getZ()) * (double) (is.cz - column.getZ());
            if (d < bestD) {
                bestD = d;
                best = is;
            }
        }
        if (best == null) return null;
        for (int i = 0; i < 6; i++) {
            BlockPos spot = spotOn(level, field, best, 0.08 * i, i * 1.7);
            if (spot != null) return spot;
        }
        return null;
    }
}
