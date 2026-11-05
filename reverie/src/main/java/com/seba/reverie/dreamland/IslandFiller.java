package com.seba.reverie.dreamland;

import java.util.List;

import com.seba.reverie.dreamland.IslandField.Island;
import com.seba.reverie.dreamland.IslandField.Kind;
import com.seba.reverie.dreamland.IslandField.Shape;
import com.seba.reverie.registry.ModBlocks;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/** Writes the islands overlapping one chunk, column by column. Chunk-local and thread-safe. */
final class IslandFiller {
    record Palette(BlockState surface, BlockState soil, BlockState stone, BlockState lake) {
    }

    private IslandFiller() {
    }

    static Palette palette(Kind kind) {
        BlockState grass = ModBlocks.DREAM_GRASS_BLOCK.get().defaultBlockState();
        BlockState soil = ModBlocks.DREAM_SOIL.get().defaultBlockState();
        BlockState stone = ModBlocks.LUCID_STONE.get().defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState mirror = ModBlocks.MIRROR_WATER.get().defaultBlockState();
        return switch (kind) {
            case HOLLOW_SPIRES -> new Palette(ModBlocks.COBBLED_LUCID_STONE.get().defaultBlockState(), stone, stone, mirror);
            case MIRROR_MARSH -> new Palette(grass, soil, stone, mirror);
            case SANDGLASS_DUNES -> {
                BlockState sand = ModBlocks.HOURGLASS_SAND.get().defaultBlockState();
                yield new Palette(sand, sand, stone, water);
            }
            case CLOUDREACH -> {
                BlockState cloud = ModBlocks.PILLOW_CLOUD.get().defaultBlockState();
                yield new Palette(cloud, cloud, cloud, cloud);
            }
            default -> new Palette(grass, soil, stone, water);
        };
    }

    /** Receives blocks for the chunk being generated. */
    interface Sink {
        void set(int x, int y, int z, BlockState state);

        BlockState get(int x, int y, int z);
    }

    static void fill(IslandField field, ChunkAccess chunk) {
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        List<Island> islands = field.islandsNear(x0 - 64, z0 - 64, x0 + 15 + 64, z0 + 15 + 64);
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight() - 1;
        Heightmap ocean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        LevelChunkSection[] sections = chunk.getSections();
        for (LevelChunkSection s : sections) s.acquire();
        try {
            Sink sink = new Sink() {
                @Override
                public void set(int x, int y, int z, BlockState state) {
                    if (y < minY || y > maxY) return;
                    int lx = x - x0;
                    int lz = z - z0;
                    if (lx < 0 || lx > 15 || lz < 0 || lz > 15) return;
                    LevelChunkSection section = sections[chunk.getSectionIndex(y)];
                    section.setBlockState(lx, y & 15, lz, state, false);
                    ocean.update(lx, y, lz, state);
                    surface.update(lx, y, lz, state);
                }

                @Override
                public BlockState get(int x, int y, int z) {
                    if (y < minY || y > maxY) return Blocks.AIR.defaultBlockState();
                    return sections[chunk.getSectionIndex(y)].getBlockState(x - x0, y & 15, z - z0);
                }
            };
            for (Island is : islands) {
                if (!is.touchesColumnRange(x0, z0, x0 + 15, z0 + 15)) continue;
                Palette p = palette(is.kind);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = x0 + lx;
                        int z = z0 + lz;
                        column(field, is, p, x, z, sink);
                    }
                }
            }
            bridges(field, islands, x0, z0, sink);
            dustMotes(field, x0, z0, sink);
        } finally {
            for (LevelChunkSection s : sections) s.release();
        }
    }

    private static void column(IslandField field, Island is, Palette p, int x, int z, Sink sink) {
        switch (is.shape) {
            case INVERTED -> inverted(field, is, p, x, z, sink);
            case CLOUD -> cloud(field, is, x, z, sink);
            default -> plateau(field, is, p, x, z, sink);
        }
        for (IslandField.Islet islet : is.islets) islet(field, is, islet, p, x, z, sink);
        for (IslandField.Spire spire : is.spires) spire(is, spire, x, z, sink);
        for (IslandField.Blob rock : is.rocks) blob(rock, x, z, ModBlocks.COBBLED_LUCID_STONE.get().defaultBlockState(), sink);
        if (is.shape == Shape.STAIRS) stairs(is, x, z, sink);
        if (is.shape == Shape.GREAT_BED) greatBed(is, x, z, sink);
    }

    private static void plateau(IslandField field, Island is, Palette p, int x, int z, Sink sink) {
        if (is.shape == Shape.SPIRES && field.normDist(is, x, z) > 0.62) return;
        int top = field.plateauTop(is, x, z);
        if (top == Integer.MIN_VALUE) return;
        int bot = field.plateauBottom(is, x, z);
        if (bot > top) bot = top;
        boolean hole = field.ringHole(is, x, z);
        int lakeLevel = is.cy - 2;
        boolean pool = is.kind == Kind.MIRROR_MARSH && field.normDist(is, x, z) < 0.8
                && Math.abs(Math.sin(x * 0.21 + Math.cos(z * 0.17) * 2.1) + Math.sin(z * 0.19 + x * 0.05)) < 0.32;
        boolean patchy = is.kind == Kind.HOLLOW_SPIRES && Math.sin(x * 0.13) + Math.cos(z * 0.11) > 1.1;
        for (int y = bot; y <= top; y++) {
            int depth = top - y;
            BlockState state;
            if (hole && y > lakeLevel) continue;
            if (hole && y > lakeLevel - 3) {
                state = p.lake();
            } else if (depth == 0) {
                state = pool ? ModBlocks.MIRROR_WATER.get().defaultBlockState()
                        : patchy ? ModBlocks.DREAM_GRASS_BLOCK.get().defaultBlockState() : p.surface();
            } else if (depth <= 3) {
                state = p.soil();
            } else if (depth > 4 && y > bot + 1 && field.isOre(x, y, z)) {
                state = ModBlocks.LUCID_CRYSTAL_ORE.get().defaultBlockState();
            } else {
                state = p.stone();
            }
            sink.set(x, y, z, state);
        }
    }

    /** Upside down: grass on the flat underside, a bare stone cone pointing up with a flat top. */
    private static void inverted(IslandField field, Island is, Palette p, int x, int z, Sink sink) {
        double d = field.normDist(is, x, z);
        if (d >= 1) return;
        int base = is.cy - 2;
        double peak = is.radius * is.bottomFactor * 0.8;
        int top = base + 2 + (int) Math.round(Math.min(Math.pow(1 - d, 1.35) * peak, peak * 0.75));
        for (int y = base; y <= top; y++) {
            BlockState state = y == base ? ModBlocks.DREAM_GRASS_BLOCK.get().defaultBlockState()
                    : y <= base + 2 ? ModBlocks.DREAM_SOIL.get().defaultBlockState()
                    : field.isOre(x, y, z) && y < top - 1 ? ModBlocks.LUCID_CRYSTAL_ORE.get().defaultBlockState()
                    : p.stone();
            sink.set(x, y, z, state);
        }
    }

    private static void cloud(IslandField field, Island is, int x, int z, Sink sink) {
        double d = field.normDist(is, x, z);
        if (d >= 1) return;
        int top = is.cy + (d < 0.5 ? 1 : 0);
        int bot = is.cy - is.topThick - (int) Math.round((1 - d) * is.radius * 0.35);
        BlockState cloud = ModBlocks.PILLOW_CLOUD.get().defaultBlockState();
        for (int y = bot; y <= top; y++) sink.set(x, y, z, cloud);
    }

    private static void islet(IslandField field, Island is, IslandField.Islet i, Palette p, int x, int z, Sink sink) {
        double dx = x - i.x();
        double dz = z - i.z();
        double d = Math.sqrt(dx * dx + dz * dz) / i.radius();
        if (d >= 1) return;
        int top = i.topY() - (d > 0.75 ? 1 : 0);
        int bot = top - i.topThick() - (int) Math.round(Math.pow(1 - d, 1.2) * i.radius() * 0.9);
        for (int y = bot; y <= top; y++) {
            int depth = top - y;
            sink.set(x, y, z, depth == 0 ? p.surface() : depth <= 2 ? p.soil() : p.stone());
        }
    }

    /** Twisted pillars; in Hollow Spires they are hollow, with a spiral stair and windows every few turns. */
    private static void spire(Island is, IslandField.Spire s, int x, int z, Sink sink) {
        BlockState wall = s.down() ? ModBlocks.LUCID_STONE.get().defaultBlockState() : ModBlocks.LUCID_STONE_BRICKS.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        int y0 = s.down() ? s.baseY() - s.height() : s.baseY();
        int y1 = s.down() ? s.baseY() : s.baseY() + s.height();
        for (int y = y0; y <= y1; y++) {
            double r = IslandField.spireRadiusAt(s, y);
            if (r < 0) continue;
            double cx = s.x() + IslandField.spireCenterOffsetX(s, y);
            double cz = s.z() + IslandField.spireCenterOffsetZ(s, y);
            double dx = x + 0.5 - cx;
            double dz = z + 0.5 - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > r) continue;
            int rel = y - s.baseY();
            if (s.hollow() && rel >= 1 && rel < s.height() - 3 && dist < r - 1.1) {
                double angle = Math.atan2(dz, dx);
                double stair = rel * (Math.PI / 4) + s.phase();
                double diff = Math.abs(Math.atan2(Math.sin(angle - stair), Math.cos(angle - stair)));
                sink.set(x, y, z, diff < 0.55 && dist > r - 2.9 ? wall : air);
                continue;
            }
            if (s.hollow() && rel >= 1 && rel < s.height() - 3) {
                double angle = Math.atan2(dz, dx);
                // The door at the bottom and a window every 9 blocks.
                double doorAngle = s.phase() + Math.PI / 4;
                double door = Math.abs(Math.atan2(Math.sin(angle - doorAngle), Math.cos(angle - doorAngle)));
                if (rel <= 2 && door < 0.45) {
                    sink.set(x, y, z, air);
                    continue;
                }
                double windowAngle = rel * (Math.PI / 4) + s.phase() + Math.PI;
                double win = Math.abs(Math.atan2(Math.sin(angle - windowAngle), Math.cos(angle - windowAngle)));
                if (rel % 9 >= 4 && rel % 9 <= 5 && win < 0.4 && rel > 5) {
                    sink.set(x, y, z, air);
                    continue;
                }
            }
            sink.set(x, y, z, wall);
        }
    }

    private static void blob(IslandField.Blob b, int x, int z, BlockState state, Sink sink) {
        double dx = x + 0.5 - b.x();
        double dz = z + 0.5 - b.z();
        double h2 = b.radius() * b.radius() - dx * dx - dz * dz;
        if (h2 < 0) return;
        double h = Math.sqrt(h2);
        for (int y = (int) Math.floor(b.y() - h); y <= (int) Math.ceil(b.y() + h * 0.7); y++) sink.set(x, y, z, state);
    }

    /** A straight staircase climbing from the island edge into the void and ending nowhere. */
    private static void stairs(Island is, int x, int z, Sink sink) {
        double c = Math.cos(is.stairAngle);
        double s = Math.sin(is.stairAngle);
        double rx = x + 0.5 - is.cx;
        double rz = z + 0.5 - is.cz;
        double t = rx * c + rz * s;
        double u = -rx * s + rz * c;
        double start = is.radius * 0.8;
        if (t < start || t > start + is.stairLength || Math.abs(u) > 1.1) return;
        int k = (int) Math.floor(t - start);
        sink.set(x, is.cy + 1 + k, z, ModBlocks.LUCID_STONE_BRICKS.get().defaultBlockState());
    }

    /** The Great Bed: a gigantic bed on a flat island, with Pillow Cloud pillows. Real beds are added later. */
    private static void greatBed(Island is, int x, int z, Sink sink) {
        int lx = x - is.cx;
        int lz = z - is.cz;
        if (lx < -7 || lx > 6 || lz < -12 || lz > 10) return;
        int y = is.cy;
        BlockState log = ModBlocks.DREAMWOOD_LOG.get().defaultBlockState();
        BlockState planks = ModBlocks.DREAMWOOD_PLANKS.get().defaultBlockState();
        BlockState white = Blocks.WHITE_WOOL.defaultBlockState();
        BlockState blanket = Blocks.MAGENTA_WOOL.defaultBlockState();
        BlockState cloud = ModBlocks.PILLOW_CLOUD.get().defaultBlockState();
        boolean edgeX = lx == -7 || lx == 6;
        boolean edgeZ = lz == -11 || lz == 10;
        if (lz == -12) {
            int arch = 9 - (int) Math.round(Math.abs(lx + 0.5) * 0.45);
            for (int dy = 1; dy <= arch; dy++) sink.set(x, y + dy, z, log);
            return;
        }
        if ((edgeX && edgeZ)) for (int dy = 1; dy <= 3; dy++) sink.set(x, y + dy, z, log);
        if (edgeX || edgeZ) sink.set(x, y + 3, z, planks);
        else {
            sink.set(x, y + 3, z, white);
            sink.set(x, y + 4, z, lz > -3 ? blanket : white);
        }
        boolean pillowL = lx >= -6 && lx <= -1 && lz >= -10 && lz <= -6;
        boolean pillowR = lx >= 0 && lx <= 5 && lz >= -10 && lz <= -6;
        if (pillowL || pillowR) {
            int px = pillowL ? lx + 3 : lx - 3;
            int pz = lz + 8;
            int h = (Math.abs(px) <= 1 && Math.abs(pz) <= 1) ? 3 : 2;
            for (int dy = 5; dy < 5 + h; dy++) sink.set(x, y + dy, z, cloud);
        }
    }

    /** Pillow Cloud strands between near cloud islets (Cloudreach bounce nets). */
    private static void bridges(IslandField field, List<Island> islands, int x0, int z0, Sink sink) {
        BlockState cloud = ModBlocks.PILLOW_CLOUD.get().defaultBlockState();
        for (Island a : islands) {
            if (a.shape != Shape.CLOUD) continue;
            Island partner = null;
            double best = 60 * 60;
            for (Island b : islands) {
                if (b == a || b.shape != Shape.CLOUD || b.id <= a.id) continue;
                double d = (double) (a.cx - b.cx) * (a.cx - b.cx) + (double) (a.cz - b.cz) * (a.cz - b.cz);
                if (d < best) {
                    best = d;
                    partner = b;
                }
            }
            if (partner == null) continue;
            double ax = a.cx + 0.5;
            double az = a.cz + 0.5;
            double bx = partner.cx + 0.5;
            double bz = partner.cz + 0.5;
            double len2 = (bx - ax) * (bx - ax) + (bz - az) * (bz - az);
            if (len2 < 1) continue;
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    double px = x0 + lx + 0.5;
                    double pz = z0 + lz + 0.5;
                    double t = ((px - ax) * (bx - ax) + (pz - az) * (bz - az)) / len2;
                    if (t < 0 || t > 1) continue;
                    double qx = ax + t * (bx - ax);
                    double qz = az + t * (bz - az);
                    if ((px - qx) * (px - qx) + (pz - qz) * (pz - qz) > 0.6) continue;
                    double sag = Math.sin(t * Math.PI) * Math.sqrt(len2) * 0.08;
                    int y = (int) Math.round(a.cy + t * (partner.cy - a.cy) - sag);
                    if (sink.get(x0 + lx, y, z0 + lz).isAir()) sink.set(x0 + lx, y, z0 + lz, cloud);
                }
            }
        }
    }

    /** Single floating blocks drifting in the void. */
    private static void dustMotes(IslandField field, int x0, int z0, Sink sink) {
        long h = field.hash(x0 >> 4, 4242, z0 >> 4);
        if (IslandField.unit(h) > 0.4) return;
        int x = x0 + (int) (IslandField.unit(IslandField.mix(h)) * 16);
        int z = z0 + (int) (IslandField.unit(IslandField.mix(h ^ 1)) * 16);
        int y = 60 + (int) (IslandField.unit(IslandField.mix(h ^ 2)) * 250);
        if (!sink.get(x, y, z).isAir() || !sink.get(x, y - 1, z).isAir() || !sink.get(x, y + 1, z).isAir()) return;
        double kind = IslandField.unit(IslandField.mix(h ^ 3));
        BlockState state = kind < 0.5 ? ModBlocks.LUCID_STONE.get().defaultBlockState()
                : kind < 0.8 ? ModBlocks.PILLOW_CLOUD.get().defaultBlockState()
                : ModBlocks.DREAM_SOIL.get().defaultBlockState();
        sink.set(x, y, z, state);
    }
}
