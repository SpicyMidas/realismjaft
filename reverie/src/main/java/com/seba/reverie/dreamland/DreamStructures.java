package com.seba.reverie.dreamland;

import com.seba.reverie.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/**
 * Landmarks of the new biomes. Each is built by the chunk holding its anchor and stays within 8 blocks of it
 * sideways (see IslandDecorator).
 */
final class DreamStructures {
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private DreamStructures() {
    }

    private static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, FLAGS);
    }

    private static void setIfAir(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) level.setBlock(pos, state, FLAGS);
    }

    private static BlockState st(net.neoforged.neoforge.registries.DeferredBlock<?> b) {
        return b.get().defaultBlockState();
    }

    // ------------------------------------------------------------------ Frostlight Tundra

    /** A snow globe as big as a house: a glass dome on a wooden base over a tiny snowy cottage with its light on. */
    static void snowGlobe(WorldGenLevel level, BlockPos ground) {
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState wood = ModBlocks.DREAMWOOD_PLANKS.get().defaultBlockState();
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState snow = st(ModBlocks.DREAMSNOW);
        int r = 6;
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 7.3) continue;
                for (int dy = 0; dy <= 1; dy++) set(level, ground.offset(dx, dy, dz), d > 6.6 && dy == 1 ? gold : wood);
                if (d <= r) set(level, ground.offset(dx, 1, dz), snow);
                for (int dy = 2; dy <= 2 + r; dy++) {
                    double dd = Math.sqrt(d * d + (dy - 1.5) * (dy - 1.5));
                    BlockPos p = ground.offset(dx, dy, dz);
                    if (dd > r - 0.5 && dd < r + 0.5) set(level, p, glass);
                    else if (dd <= r - 0.5) set(level, p, Blocks.AIR.defaultBlockState());
                }
            }
        // The cottage.
        BlockPos c = ground.offset(-1, 2, -1);
        for (int dx = 0; dx <= 2; dx++)
            for (int dz = 0; dz <= 2; dz++)
                for (int dy = 0; dy <= 1; dy++) set(level, c.offset(dx, dy, dz), wood);
        set(level, c.offset(1, 0, 2), Blocks.AIR.defaultBlockState());
        set(level, c.offset(1, 1, 0), st(ModBlocks.NIGHT_LIGHT)); // the lit window
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = 0; dz <= 2; dz++) set(level, c.offset(dx, 2, dz), Blocks.SPRUCE_SLAB.defaultBlockState());
        }
        for (int dz = 0; dz <= 2; dz++) set(level, c.offset(1, 3, dz), Blocks.SNOW_BLOCK.defaultBlockState());
        // A small pine beside it.
        BlockPos t = ground.offset(3, 2, 2);
        set(level, t, ModBlocks.DREAMWOOD_LOG.get().defaultBlockState());
        set(level, t.above(), ModBlocks.DREAMWOOD_LEAVES.get().defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
        set(level, t.above(2), Blocks.SNOW.defaultBlockState());
    }

    // ------------------------------------------------------------------ Skyreef

    /** A small ship that sank into the sky and grew coral: hull, deck, a broken mast, a torn sail, a chest. */
    static void skyWreck(WorldGenLevel level, BlockPos ground, RandomSource rng) {
        boolean alongX = rng.nextBoolean();
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState dark = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState wool = Blocks.WHITE_WOOL.defaultBlockState();
        BlockPos base = ground.above();
        for (int t = -6; t <= 6; t++) {
            int w = t <= -5 || t >= 6 ? 0 : t <= -3 || t >= 4 ? 1 : 2;
            for (int u = -w; u <= w; u++) {
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos p = base.offset(alongX ? t : u, dy, alongX ? u : t);
                    boolean side = Math.abs(u) == w;
                    if (dy == 0 && Math.abs(u) < w) set(level, p, dark);
                    else if (dy == 0 || side) set(level, p, rng.nextInt(9) == 0 ? st(ModBlocks.TIDE_CORAL) : planks);
                    else if (dy == 3 && t > -4 && t < 5) set(level, p, rng.nextInt(7) == 0 ? Blocks.AIR.defaultBlockState() : planks);
                    else set(level, p, Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos mast = base.above(1);
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int dy = 0; dy < 9; dy++) set(level, mast.above(dy), log);
        for (int u = -3; u <= 3; u++)
            for (int dy = 4; dy <= 8; dy++) {
                if (rng.nextInt(6) == 0) continue; // torn
                set(level, mast.offset(alongX ? 1 : u, dy, alongX ? u : 1), wool);
            }
        BlockPos chest = base.offset(alongX ? -3 : 0, 1, alongX ? 0 : -3);
        set(level, chest, Blocks.CHEST.defaultBlockState());
        net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, rng, chest, BuiltInLootTables.SHIPWRECK_SUPPLY);
        for (int i = 0; i < 5; i++) {
            BlockPos p = base.offset(rng.nextInt(9) - 4, 4, rng.nextInt(5) - 2);
            if (level.getBlockState(p.below()).isSolid()) setIfAir(level, p, st(ModBlocks.SKY_CORAL));
        }
    }

    // ------------------------------------------------------------------ Lucid Geodes

    /** Seven crystal pillars in a ring, each taller than the last: touch them in turn and they play a scale. */
    static void crystalChoir(WorldGenLevel level, IslandField field, IslandField.Island is, BlockPos center) {
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7;
            int x = center.getX() + (int) Math.round(Math.cos(a) * 5);
            int z = center.getZ() + (int) Math.round(Math.sin(a) * 5);
            int top = field.topOf(is, x, z);
            if (top == Integer.MIN_VALUE) continue;
            int h = 3 + i;
            for (int dy = 1; dy <= h; dy++) set(level, new BlockPos(x, top + dy, z), st(ModBlocks.RESONANT_CRYSTAL));
            set(level, new BlockPos(x, top + h + 1, z), ModBlocks.LUCID_CRYSTAL_CLUSTER.get().defaultBlockState());
        }
        setIfAir(level, center.above(), st(ModBlocks.FROSTBLOOM));
    }

    // ------------------------------------------------------------------ The Nursery

    private static void ball(WorldGenLevel level, BlockPos c, double rx, double ry, double rz, BlockState state) {
        int ix = (int) Math.ceil(rx);
        int iy = (int) Math.ceil(ry);
        int iz = (int) Math.ceil(rz);
        for (int dx = -ix; dx <= ix; dx++)
            for (int dy = -iy; dy <= iy; dy++)
                for (int dz = -iz; dz <= iz; dz++)
                    if ((dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz) <= 1.0) set(level, c.offset(dx, dy, dz), state);
    }

    /** A teddy bear sitting on the rug, three times your height when you are this small. Button eyes. */
    static void teddy(WorldGenLevel level, BlockPos ground, Direction facing) {
        BlockState fur = Blocks.BROWN_WOOL.defaultBlockState();
        BlockState light = Blocks.WHITE_WOOL.defaultBlockState();
        BlockState eye = Blocks.BLACK_CONCRETE.defaultBlockState();
        BlockState bow = Blocks.RED_WOOL.defaultBlockState();
        int fx = facing.getStepX();
        int fz = facing.getStepZ();
        int sx = -fz; // to the bear's side
        int sz = fx;
        BlockPos body = ground.above(4);
        ball(level, body, 3.4, 3.6, 3.4, fur);
        ball(level, body.offset(fx * 2, -1, fz * 2), 2.2, 2.0, 2.2, light);       // tummy
        ball(level, body.above(6), 3.0, 2.8, 3.0, fur);                               // head
        ball(level, body.above(9).offset(sx * 2, 0, sz * 2), 1.2, 1.2, 1.2, fur);    // ears
        ball(level, body.above(9).offset(-sx * 2, 0, -sz * 2), 1.2, 1.2, 1.2, fur);
        ball(level, body.above(5).offset(fx * 3, 0, fz * 3), 1.3, 1.0, 1.3, light); // snout
        set(level, body.above(6).offset(fx * 4, 0, fz * 4), eye);                    // nose
        set(level, body.above(7).offset(fx * 3 + sx, 0, fz * 3 + sz), eye);         // button eyes
        set(level, body.above(7).offset(fx * 3 - sx, 0, fz * 3 - sz), eye);
        for (int s = -1; s <= 1; s += 2) {
            ball(level, body.offset(sx * 3 * s + fx, 0, sz * 3 * s + fz), 1.4, 2.2, 1.4, fur);                 // arms
            ball(level, ground.above(1).offset(sx * 2 * s + fx * 3, 0, sz * 2 * s + fz * 3), 1.5, 1.2, 1.8, fur); // legs
        }
        for (int s = -2; s <= 2; s++) set(level, body.above(4).offset(fx * 3 + sx * s, 0, fz * 3 + sz * s), bow);
    }

    private static final String[][] LETTERS = {
            {"#####", "...#.", "..#..", ".#...", "#####"}, // Z
            {".###.", "#...#", "#####", "#...#", "#...#"}, // A
            {"####.", "#...#", "####.", "#...#", "####."}, // B
            {".####", "#....", "#....", "#....", ".####"}, // C
    };

    /** A tower of giant toy blocks, each with a big letter on every side: Z, A, B, C... */
    static void blockTower(WorldGenLevel level, BlockPos ground, RandomSource rng) {
        BlockState[] colors = {Blocks.RED_CONCRETE.defaultBlockState(), Blocks.YELLOW_CONCRETE.defaultBlockState(),
                Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(), Blocks.LIME_CONCRETE.defaultBlockState()};
        BlockState ink = Blocks.WHITE_CONCRETE.defaultBlockState();
        int cubes = 2 + rng.nextInt(2);
        int ox = 0;
        int oz = 0;
        for (int k = 0; k < cubes; k++) {
            BlockState color = colors[(k + rng.nextInt(4)) % 4];
            String[] letter = LETTERS[(k + rng.nextInt(4)) % LETTERS.length];
            BlockPos c = ground.offset(ox - 3, 1 + k * 7, oz - 3);
            for (int dx = 0; dx < 7; dx++)
                for (int dy = 0; dy < 7; dy++)
                    for (int dz = 0; dz < 7; dz++) {
                        boolean edgeX = dx == 0 || dx == 6;
                        boolean edgeZ = dz == 0 || dz == 6;
                        BlockState s = color;
                        // Letters on the four sides (rows top to bottom).
                        if ((edgeX ^ edgeZ) && dy >= 1 && dy <= 5) {
                            int col = edgeX ? dz : dx;
                            if (col >= 1 && col <= 5) {
                                int cc = edgeX ? (dx == 0 ? 5 - col : col - 1) : (dz == 6 ? 5 - col : col - 1);
                                if (letter[5 - dy].charAt(Math.max(0, Math.min(4, cc))) == '#') s = ink;
                            }
                        }
                        set(level, c.offset(dx, dy, dz), s);
                    }
            ox += rng.nextInt(3) - 1;
            oz += rng.nextInt(3) - 1;
        }
    }

    /** Giant crayons standing in the rug, wrapped in paper. */
    static void crayons(WorldGenLevel level, BlockPos ground, RandomSource rng) {
        BlockState[] colors = {Blocks.RED_CONCRETE.defaultBlockState(), Blocks.BLUE_CONCRETE.defaultBlockState(),
                Blocks.ORANGE_CONCRETE.defaultBlockState(), Blocks.PURPLE_CONCRETE.defaultBlockState(), Blocks.GREEN_CONCRETE.defaultBlockState()};
        for (int i = 0; i < 3; i++) {
            BlockPos base = ground.offset(rng.nextInt(9) - 4, 1, rng.nextInt(9) - 4);
            BlockState c = colors[rng.nextInt(colors.length)];
            int h = 8 + rng.nextInt(5);
            for (int dy = 0; dy < h; dy++) {
                BlockState band = dy >= 2 && dy <= h - 4 ? (dy == 3 || dy == h - 5 ? Blocks.BLACK_CONCRETE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState()) : c;
                for (Direction d : Direction.Plane.HORIZONTAL) set(level, base.above(dy).relative(d), band);
                set(level, base.above(dy), c);
            }
            set(level, base.above(h), c);
            set(level, base.above(h + 1), c);
        }
    }

    // ------------------------------------------------------------------ Blackwood

    /** A cabin in the trees with the light on in the windows. The light goes out when you come close. */
    static void cabin(WorldGenLevel level, BlockPos ground, Direction door) {
        BlockState log = ModBlocks.BLACKWOOD_LOG.get().defaultBlockState();
        BlockState wall = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState window = st(ModBlocks.WATCHING_WINDOW);
        BlockPos f = ground;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                set(level, f.offset(dx, 0, dz), Blocks.DARK_OAK_PLANKS.defaultBlockState());
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                for (int dy = 1; dy <= 4; dy++) {
                    BlockPos p = f.offset(dx, dy, dz);
                    if (corner) set(level, p, log);
                    else if (edge) set(level, p, dy == 2 && (dx == 0 || dz == 0) ? window : wall);
                    else set(level, p, Blocks.AIR.defaultBlockState());
                }
            }
        for (int layer = 0; layer < 4; layer++)
            for (int dx = -4 + layer; dx <= 4 - layer; dx++)
                for (int dz = -4 + layer; dz <= 4 - layer; dz++)
                    set(level, f.offset(dx, 5 + layer, dz), log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        BlockPos gap = f.relative(door, 3);
        set(level, gap.above(1), Blocks.AIR.defaultBlockState());
        set(level, gap.above(2), Blocks.AIR.defaultBlockState());
        // Somebody's chair, facing the wall.
        set(level, f.offset(0, 1, 0).relative(door.getOpposite(), 1), Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, door));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos w = f.relative(d, 3).above(2);
            if (level.getBlockState(w).is(ModBlocks.WATCHING_WINDOW.get())) level.scheduleTick(w, ModBlocks.WATCHING_WINDOW.get(), 20);
        }
    }

    // ------------------------------------------------------------------ The Waiting Rooms

    /** Reception: a desk, a bell nobody answers, a dead plant, and the chair behind the desk. */
    static void reception(WorldGenLevel level, BlockPos floor, Direction facing) {
        BlockState desk = Blocks.BIRCH_PLANKS.defaultBlockState();
        Direction side = facing.getClockWise();
        for (int i = -2; i <= 2; i++) {
            set(level, floor.above().relative(side, i), desk);
            setIfAir(level, floor.above(2).relative(side, i), Blocks.AIR.defaultBlockState());
        }
        set(level, floor.above().relative(side, 2).relative(facing.getOpposite()), desk);
        set(level, floor.above(2), Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, side));
        set(level, floor.above(2).relative(side, -2), Blocks.POTTED_DEAD_BUSH.defaultBlockState());
        set(level, floor.above().relative(facing.getOpposite(), 2), Blocks.BIRCH_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing.getOpposite()));
    }

    // ------------------------------------------------------------------ The Maw

    /** Something enormous died here: a spine and its ribs arching over the flesh, veins hanging off them. */
    static void ribcage(WorldGenLevel level, BlockPos ground, boolean alongX, RandomSource rng) {
        BlockState spine = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
        BlockState rib = Blocks.BONE_BLOCK.defaultBlockState();
        for (int t = -6; t <= 6; t++) set(level, ground.offset(alongX ? t : 0, 1, alongX ? 0 : t), spine);
        for (int t = -6; t <= 6; t += 3) {
            for (int step = 0; step <= 24; step++) {
                double a = step / 24.0 * Math.PI;
                int u = (int) Math.round(Math.cos(a) * 6);
                int dy = 1 + (int) Math.round(Math.sin(a) * 7);
                BlockPos p = ground.offset(alongX ? t : u, dy, alongX ? u : t);
                set(level, p, rib);
                if (dy > 5 && rng.nextInt(5) == 0) {
                    for (int k = 1; k <= 1 + rng.nextInt(4); k++) setIfAir(level, p.below(k), st(ModBlocks.VEIN_VINES));
                }
            }
        }
    }

    // ------------------------------------------------------------------ Signal Lost

    /** An old television the size of a room, showing static, and an armchair in front of it. */
    static void television(WorldGenLevel level, BlockPos ground, Direction facing) {
        BlockState body = Blocks.GRAY_CONCRETE.defaultBlockState();
        BlockState frame = Blocks.BLACK_CONCRETE.defaultBlockState();
        BlockState screen = st(ModBlocks.STATIC_BLOCK);
        Direction side = facing.getClockWise();
        for (int w = -4; w <= 4; w++)
            for (int h = 1; h <= 7; h++)
                for (int d = -2; d <= 2; d++) {
                    BlockPos p = ground.above(h).relative(side, w).relative(facing, d);
                    boolean front = d == 2;
                    boolean inScreen = front && Math.abs(w) <= 3 && h >= 2 && h <= 6;
                    set(level, p, inScreen ? screen : front ? frame : body);
                }
        // Rabbit ears.
        for (int k = 1; k <= 5; k++) {
            set(level, ground.above(7 + k).relative(side, k / 2 + 1), Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            set(level, ground.above(7 + k).relative(side, -(k / 2 + 1)), Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
        }
        // The armchair, facing the screen.
        BlockPos chair = ground.above().relative(facing, 6);
        BlockState seat = Blocks.RED_WOOL.defaultBlockState();
        set(level, chair, seat);
        set(level, chair.relative(facing).above(), seat);
        set(level, chair.relative(facing), seat);
        set(level, chair.relative(side), seat);
        set(level, chair.relative(side.getOpposite()), seat);
        set(level, chair.relative(side).above(), Blocks.RED_CARPET.defaultBlockState());
        set(level, chair.relative(side.getOpposite()).above(), Blocks.RED_CARPET.defaultBlockState());
    }
}
