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
            // A null lake leaves a ring island's hole open all the way through.
            case FROSTLIGHT_TUNDRA -> new Palette(st(ModBlocks.DREAMSNOW), st(ModBlocks.RIME_STONE), st(ModBlocks.RIME_STONE), st(ModBlocks.STARLIGHT_ICE));
            case SKYREEF -> new Palette(st(ModBlocks.REEF_STONE), st(ModBlocks.REEF_STONE), st(ModBlocks.REEF_STONE), null);
            case LUCID_GEODES -> new Palette(st(ModBlocks.VIOLET_MOSS), st(ModBlocks.LILAC_CALCITE), st(ModBlocks.GEODE_SHELL), st(ModBlocks.RESONANT_CRYSTAL));
            // A torn-off piece of a child's floor: rug, stuffing, floorboards.
            case THE_NURSERY -> new Palette(st(ModBlocks.NURSERY_RUG), st(ModBlocks.STUFFING), st(ModBlocks.DREAMWOOD_PLANKS), null);
            case BLACKWOOD -> new Palette(st(ModBlocks.ASHEN_SOIL), st(ModBlocks.ASHEN_SOIL), stone, mirror);
            case WAITING_ROOMS -> new Palette(st(ModBlocks.STALE_CARPET), st(ModBlocks.CEILING_TILE), stone, null);
            case THE_MAW -> new Palette(st(ModBlocks.DREAM_FLESH), st(ModBlocks.SINEW), st(ModBlocks.SINEW), null);
            case SIGNAL_LOST -> new Palette(st(ModBlocks.DEAD_PIXEL), st(ModBlocks.STATIC_BLOCK), stone, null);
            default -> new Palette(grass, soil, stone, water);
        };
    }

    private static BlockState st(net.neoforged.neoforge.registries.DeferredBlock<?> block) {
        return block.get().defaultBlockState();
    }

    /** What the small floating rocks around an island are made of: in the new biomes, something that belongs there. */
    private static BlockState rock(Kind kind) {
        return switch (kind) {
            case FROSTLIGHT_TUNDRA -> st(ModBlocks.STARLIGHT_ICE);
            case SKYREEF -> st(ModBlocks.TIDE_CORAL);
            case LUCID_GEODES -> st(ModBlocks.GEODE_SHELL);
            case THE_NURSERY -> st(ModBlocks.TOY_BLOCK_YELLOW);
            case BLACKWOOD -> st(ModBlocks.ASHEN_SOIL);
            case WAITING_ROOMS -> st(ModBlocks.CEILING_TILE);
            case THE_MAW -> st(ModBlocks.ENAMEL);
            case SIGNAL_LOST -> st(ModBlocks.TEST_CARD);
            default -> ModBlocks.COBBLED_LUCID_STONE.get().defaultBlockState();
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
            case GEODE -> geode(field, is, x, z, sink);
            default -> plateau(field, is, p, x, z, sink);
        }
        if (is.kind == Kind.WAITING_ROOMS) rooms(field, is, x, z, sink);
        for (IslandField.Islet islet : is.islets) islet(field, is, islet, p, x, z, sink);
        for (IslandField.Spire spire : is.spires) spire(is, spire, x, z, sink);
        BlockState rock = rock(is.kind);
        for (IslandField.Blob b : is.rocks) blob(b, x, z, rock, sink);
        if (is.shape == Shape.STAIRS) stairs(is, x, z, sink);
        if (is.shape == Shape.GREAT_BED) greatBed(is, x, z, sink);
    }

    private static void plateau(IslandField field, Island is, Palette p, int x, int z, Sink sink) {
        if (is.shape == Shape.SPIRES && field.normDist(is, x, z) > 0.62) return;
        int top = field.plateauTop(is, x, z);
        if (top == Integer.MIN_VALUE) return;
        int bot = field.plateauBottom(is, x, z);
        if (bot > top) bot = top;
        int shift = 0;
        if (is.kind == Kind.SIGNAL_LOST) {
            // Corrupted data: missing blocks of columns, columns slipped up or down, patches drawn from another dream.
            long g = field.hash(Math.floorDiv(x, 4), 5150 + is.layer, Math.floorDiv(z, 4));
            double u = IslandField.unit(g);
            if (u < 0.10 && field.normDist(is, x, z) > 0.18) return;
            if (u < 0.24) shift = (int) Math.round((IslandField.unit(IslandField.mix(g)) - 0.5) * 14);
            if (u > 0.9) p = palette(new Kind[]{Kind.PILLOW_FIELDS, Kind.SANDGLASS_DUNES, Kind.FROSTLIGHT_TUNDRA, Kind.SKYREEF,
                    Kind.THE_NURSERY, Kind.LUCID_GEODES}[(int) (IslandField.unit(IslandField.mix(g ^ 7)) * 6)]);
        }
        boolean hole = field.ringHole(is, x, z);
        int lakeLevel = is.cy - 2;
        double d = field.normDist(is, x, z);
        boolean pool = (is.kind == Kind.MIRROR_MARSH || is.kind == Kind.FROSTLIGHT_TUNDRA) && d < 0.8
                && Math.abs(Math.sin(x * 0.21 + Math.cos(z * 0.17) * 2.1) + Math.sin(z * 0.19 + x * 0.05)) < (is.kind == Kind.FROSTLIGHT_TUNDRA ? 0.22 : 0.32);
        BlockState poolBlock = is.kind == Kind.FROSTLIGHT_TUNDRA ? st(ModBlocks.STARLIGHT_ICE) : ModBlocks.MIRROR_WATER.get().defaultBlockState();
        boolean patchy = is.kind == Kind.HOLLOW_SPIRES && Math.sin(x * 0.13) + Math.cos(z * 0.11) > 1.1;
        BlockState surface = surfacePatch(field, is, p, x, z, patchy);
        if (is.kind == Kind.THE_MAW && is.shape == Shape.RING && !hole) teeth(field, is, x, z, top, d, sink);
        for (int y = bot; y <= top; y++) {
            int depth = top - y;
            BlockState state;
            if (hole && y > lakeLevel) continue;
            if (hole && y > lakeLevel - 3) {
                if (p.lake() == null) continue;
                state = p.lake();
            } else if (hole && p.lake() == null) {
                continue;
            } else if (depth == 0) {
                state = pool ? poolBlock : surface;
            } else if (depth == 1 && pool && is.kind == Kind.FROSTLIGHT_TUNDRA) {
                state = poolBlock;
            } else if (depth <= 3) {
                state = p.soil();
            } else if (depth > 4 && y > bot + 1 && field.isOre(x, y, z)) {
                state = ModBlocks.LUCID_CRYSTAL_ORE.get().defaultBlockState();
            } else {
                state = p.stone();
            }
            sink.set(x, y + shift, z, state);
        }
    }

    /** The surface block of a column: the biome's own, with patches (coral beds, crystal, static, gums). */
    private static BlockState surfacePatch(IslandField field, Island is, Palette p, int x, int z, boolean patchy) {
        if (patchy) return ModBlocks.DREAM_GRASS_BLOCK.get().defaultBlockState();
        double n = field.patch(x, z, 0.09, is.kind.ordinal());
        return switch (is.kind) {
            case SKYREEF -> n > 0.32 ? st(ModBlocks.TIDE_CORAL) : n < -0.36 ? st(ModBlocks.DUSK_CORAL) : p.surface();
            case LUCID_GEODES -> n > 0.48 ? st(ModBlocks.RESONANT_CRYSTAL) : n < -0.45 ? st(ModBlocks.LILAC_CALCITE) : p.surface();
            case SIGNAL_LOST -> n > 0.3 ? st(ModBlocks.STATIC_BLOCK) : p.surface();
            case THE_MAW -> n > 0.45 ? st(ModBlocks.SINEW) : p.surface();
            case FROSTLIGHT_TUNDRA -> n > 0.5 ? st(ModBlocks.RIME_STONE) : p.surface();
            default -> p.surface();
        };
    }

    /** The Maw: a ring of teeth around the throat, the inner ones longest. */
    private static void teeth(IslandField field, Island is, int x, int z, int top, double d, Sink sink) {
        double rim = field.ringHoleEdge(x, z);
        if (d < rim || d > rim + 0.08) return;
        double angle = Math.atan2(z - is.cz, x - is.cx);
        double t = (angle + Math.PI) / (Math.PI * 2) * 22;
        double within = t - Math.floor(t);
        if (within < 0.2 || within > 0.8) return; // the gaps between teeth
        int tooth = (int) Math.floor(t);
        int height = (d < rim + 0.035 ? 3 : 1) + (int) (IslandField.unit(field.hash(is.cx + tooth, 7070, is.cz)) * 3);
        for (int dy = 1; dy <= height; dy++) sink.set(x, top + dy, z, st(ModBlocks.ENAMEL));
    }

    /** A geode: shell, calcite, crystal lining, hollow inside; the top broken off along a jagged rim. */
    private static void geode(IslandField field, Island is, int x, int z, Sink sink) {
        double dx = x + 0.5 - is.cx;
        double dz = z + 0.5 - is.cz;
        double d2 = dx * dx + dz * dz;
        double r = is.radius;
        if (d2 >= r * r) return;
        int gy = IslandField.geodeCenterY(is);
        double h = Math.sqrt(r * r - d2);
        double cut = field.geodeCut(is, x, z);
        int y0 = (int) Math.floor(gy - h);
        int y1 = (int) Math.floor(Math.min(gy + h, cut));
        BlockState shell = st(ModBlocks.GEODE_SHELL);
        BlockState calcite = st(ModBlocks.LILAC_CALCITE);
        BlockState crystal = st(ModBlocks.RESONANT_CRYSTAL);
        BlockState ore = ModBlocks.LUCID_CRYSTAL_ORE.get().defaultBlockState();
        for (int y = y0; y <= y1; y++) {
            double dy = y + 0.5 - gy;
            double d = Math.sqrt(d2 + dy * dy);
            BlockState state;
            if (d > r - 1.3) state = shell;
            else if (d > r - 2.3) state = calcite;
            else if (d > r - 3.2) state = field.isOre(x, y, z) ? ore : crystal;
            else continue;
            if (y == y1 && gy + h > cut && state != crystal) state = st(ModBlocks.VIOLET_MOSS); // moss along the broken rim
            sink.set(x, y, z, state);
        }
    }

    private static final int ROOM = 11;

    /**
     * The Waiting Rooms: the island is a piece of an endless office floor. Wallpapered walls on an 11-block
     * grid with a doorway in each, some walls missing, a ceiling of tiles with a light panel in every room, holes
     * where the ceiling has fallen through. Near the edge it all just stops.
     */
    private static void rooms(IslandField field, Island is, int x, int z, Sink sink) {
        if (field.plateauTop(is, x, z) != is.cy || field.normDist(is, x, z) > 0.9) return;
        int top = is.cy;
        int ox = x - is.cx + 5;
        int oz = z - is.cz + 5;
        int gx = Math.floorMod(ox, ROOM);
        int gz = Math.floorMod(oz, ROOM);
        int cellX = Math.floorDiv(ox, ROOM);
        int cellZ = Math.floorDiv(oz, ROOM);
        if (gx == 0 || gz == 0) {
            boolean alongZ = gx == 0;
            long seg = alongZ ? field.hash(cellX * 2, 6200 + is.layer, cellZ) : field.hash(cellX * 2 + 1, 6200 + is.layer, cellZ);
            boolean missing = IslandField.unit(seg) < 0.22 && !(gx == 0 && gz == 0);
            int along = alongZ ? gz : gx;
            boolean door = along >= 4 && along <= 6 && !(gx == 0 && gz == 0);
            if (!missing) {
                for (int dy = 1; dy <= 4; dy++) {
                    if (door && dy <= 3) continue;
                    sink.set(x, top + dy, z, st(ModBlocks.YELLOWED_WALLPAPER));
                }
            }
        }
        long tile = field.hash(Math.floorDiv(x, 3), 6300 + is.layer, Math.floorDiv(z, 3));
        if (IslandField.unit(tile) < 0.13) return; // the ceiling fell in here
        boolean light = (gx == 5 || gx == 6) && gz >= 4 && gz <= 6;
        sink.set(x, top + 5, z, light ? st(ModBlocks.FLICKER_LIGHT) : st(ModBlocks.CEILING_TILE));
    }

    /** Upside down: grass on the flat underside, a bare stone cone pointing up with a flat top. */
    private static void inverted(IslandField field, Island is, Palette p, int x, int z, Sink sink) {
        double d = field.normDist(is, x, z);
        if (d >= 1) return;
        int base = is.cy - 2;
        double peak = is.radius * is.bottomFactor * 0.8;
        int top = base + 2 + (int) Math.round(Math.min(Math.pow(1 - d, 1.35) * peak, peak * 0.75));
        boolean own = is.kind.ordinal() > Kind.VOID.ordinal(); // the new biomes use their own ground
        for (int y = base; y <= top; y++) {
            BlockState state = y == base ? (own ? p.surface() : ModBlocks.DREAM_GRASS_BLOCK.get().defaultBlockState())
                    : y <= base + 2 ? (own ? p.soil() : ModBlocks.DREAM_SOIL.get().defaultBlockState())
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
        // Frostlight grows spires of starlight ice, the Geodes spires of singing crystal.
        if (is.kind == Kind.FROSTLIGHT_TUNDRA) wall = st(ModBlocks.STARLIGHT_ICE);
        else if (is.kind == Kind.LUCID_GEODES) wall = st(ModBlocks.RESONANT_CRYSTAL);
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
