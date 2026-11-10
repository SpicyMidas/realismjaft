package com.seba.reverie.dreamland;

import java.util.List;

import com.seba.reverie.dreamland.IslandField.Island;
import com.seba.reverie.dreamland.IslandField.Kind;
import com.seba.reverie.dreamland.IslandField.Shape;
import com.seba.reverie.dreamland.block.LoneDoorBlock;
import com.seba.reverie.dreamland.block.MeltedClockBlock;
import com.seba.reverie.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * Plants, trees and landmarks. Runs in the features step for one chunk: scatter is per column of this chunk,
 * landmarks are anchored at a point and only built by the chunk that contains the anchor (extent < 16 blocks).
 */
final class IslandDecorator {
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private IslandDecorator() {
    }

    static void decorate(WorldGenLevel level, net.minecraft.world.level.chunk.ChunkAccess chunk, IslandField field) {
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();
        RandomSource rng = new XoroshiroRandomSource(field.hash(chunk.getPos().x, 777, chunk.getPos().z));
        List<Island> islands = field.islandsNear(x0, z0, x0 + 15, z0 + 15);
        for (Island is : islands) {
            if (is.shape != Shape.CLOUD) scatter(level, field, is, x0, z0, rng);
            landmarks(level, field, is, x0, z0);
        }
    }

    private static boolean inChunk(int x, int z, int x0, int z0) {
        return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16;
    }

    // ------------------------------------------------------------------ scatter

    private static void scatter(WorldGenLevel level, IslandField field, Island is, int x0, int z0, RandomSource rng) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx;
                int z = z0 + lz;
                double d = field.normDist(is, x, z);
                if (d >= 1) continue;
                if (is.shape == Shape.INVERTED) {
                    if (is.kind != Kind.HOLLOW_SPIRES && rng.nextFloat() < 0.012f) hangingTree(level, new BlockPos(x, is.cy - 3, z), rng);
                    continue;
                }
                int top = field.topOf(is, x, z);
                if (top == Integer.MIN_VALUE) continue;
                BlockPos ground = new BlockPos(x, top, z);
                BlockState g = level.getBlockState(ground);
                BlockPos above = ground.above();
                if (g.isAir() || !level.getBlockState(above).isAir()) continue;
                surfaceFeature(level, is, ground, g, d, rng);
                if (is.shape != Shape.SPIRES && (is.kind == Kind.PILLOW_FIELDS || is.kind == Kind.UPSIDE_DOWN_TOWN) && rng.nextFloat() < 0.004f) {
                    int bot = field.plateauBottom(is, x, z);
                    BlockPos under = new BlockPos(x, bot - 1, z);
                    if (level.getBlockState(under).isAir() && !level.getBlockState(under.above()).isAir()) hangingTree(level, under, rng);
                }
                underside(level, field, is, x, z, rng);
            }
        }
    }

    private static void surfaceFeature(WorldGenLevel level, Island is, BlockPos ground, BlockState g, double d, RandomSource rng) {
        BlockPos above = ground.above();
        float r = rng.nextFloat();
        if (is.shape == Shape.SEA_OF_DOORS) {
            if (r < 0.88f && g.is(ModBlocks.DREAM_GRASS_BLOCK.get())) {
                Block[] doors = {Blocks.OAK_TRAPDOOR, Blocks.SPRUCE_TRAPDOOR, Blocks.DARK_OAK_TRAPDOOR, Blocks.CHERRY_TRAPDOOR, Blocks.BIRCH_TRAPDOOR};
                level.setBlock(above, doors[rng.nextInt(doors.length)].defaultBlockState()
                        .setValue(TrapDoorBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(rng))
                        .setValue(TrapDoorBlock.HALF, Half.BOTTOM).setValue(TrapDoorBlock.OPEN, false), FLAGS);
            }
            return;
        }
        if (is.shape == Shape.GREAT_BED && Math.abs(ground.getX() - is.cx) <= 8 && ground.getZ() - is.cz >= -13 && ground.getZ() - is.cz <= 11) return;
        boolean grass = g.is(ModBlocks.DREAM_GRASS_BLOCK.get());
        switch (is.kind) {
            case PILLOW_FIELDS -> {
                if (grass && r < 0.022f) place(level, above, ModBlocks.WILD_MELATONIA.get().defaultBlockState());
                else if (grass && r < 0.030f && d < 0.7) tree(level, above, rng);
                else if (r < 0.033f) place(level, above.above(3 + rng.nextInt(4)), ModBlocks.FLOATING_LANTERN.get().defaultBlockState());
            }
            case HOLLOW_SPIRES -> {
                if (r < 0.035f) place(level, above, ModBlocks.LUCID_CRYSTAL_CLUSTER.get().defaultBlockState()
                        .setValue(AmethystClusterBlock.FACING, Direction.UP));
            }
            case MIRROR_MARSH -> {
                if (grass && r < 0.015f) place(level, above, ModBlocks.WILD_MELATONIA.get().defaultBlockState());
                else if (grass && r < 0.03f) place(level, above, Blocks.SHORT_GRASS.defaultBlockState());
                else if (r < 0.033f) place(level, above.above(1 + rng.nextInt(2)), ModBlocks.FLOATING_LANTERN.get().defaultBlockState());
            }
            case SANDGLASS_DUNES -> {
                if (r < 0.05f) place(level, above, ModBlocks.HOURGLASS_SAND.get().defaultBlockState());
                else if (r < 0.054f && d > 0.6) {
                    Direction outward = Direction.getNearest(ground.getX() - is.cx, 0, ground.getZ() - is.cz);
                    if (outward.getAxis().isHorizontal()) place(level, above, ModBlocks.MELTED_CLOCK.get().defaultBlockState()
                            .setValue(MeltedClockBlock.FACING, outward));
                }
            }
            case EYE_MEADOW -> {
                if (grass && r < 0.10f) place(level, above, ModBlocks.EYEBLOOM.get().defaultBlockState());
                else if (grass && r < 0.12f) place(level, above, ModBlocks.WILD_MELATONIA.get().defaultBlockState());
                else if (grass && r < 0.16f) place(level, above, Blocks.SHORT_GRASS.defaultBlockState());
            }
            case UPSIDE_DOWN_TOWN -> {
                if (grass && r < 0.02f) place(level, above, Blocks.SHORT_GRASS.defaultBlockState());
                else if (r < 0.023f) place(level, above.above(4), ModBlocks.FLOATING_LANTERN.get().defaultBlockState());
            }
            case FROSTLIGHT_TUNDRA -> {
                if (!g.is(ModBlocks.DREAMSNOW.get())) return;
                if (r < 0.011f && d < 0.78) pine(level, above, rng);
                else if (r < 0.04f) place(level, above, ModBlocks.FROSTBLOOM.get().defaultBlockState());
                else if (r < 0.11f) place(level, above, Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 1 + rng.nextInt(2)));
            }
            case SKYREEF -> {
                if (r < 0.07f) place(level, above, ModBlocks.SKY_CORAL.get().defaultBlockState());
                else if (r < 0.0715f && d < 0.8 && g.is(ModBlocks.REEF_STONE.get())) level.setBlock(ground, ModBlocks.BUBBLE_VENT.get().defaultBlockState(), FLAGS);
            }
            case LUCID_GEODES -> {
                if (r < 0.05f) place(level, above, ModBlocks.LUCID_CRYSTAL_CLUSTER.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
                else if (r < 0.062f) {
                    int h = 2 + rng.nextInt(3);
                    for (int i = 0; i < h && level.getBlockState(above.above(i)).isAir(); i++) level.setBlock(above.above(i), ModBlocks.RESONANT_CRYSTAL.get().defaultBlockState(), FLAGS);
                } else if (r < 0.08f && g.is(ModBlocks.VIOLET_MOSS.get())) place(level, above, ModBlocks.WILD_MELATONIA.get().defaultBlockState());
            }
            case THE_NURSERY -> {
                if (r < 0.012f) {
                    var toys = new net.neoforged.neoforge.registries.DeferredBlock<?>[]{ModBlocks.TOY_BLOCK_RED, ModBlocks.TOY_BLOCK_YELLOW,
                            ModBlocks.TOY_BLOCK_BLUE, ModBlocks.TOY_BLOCK_GREEN};
                    place(level, above, toys[rng.nextInt(toys.length)].get().defaultBlockState());
                } else if (r < 0.0135f) place(level, above, ModBlocks.NIGHT_LIGHT.get().defaultBlockState());
            }
            case BLACKWOOD -> {
                if (r < 0.034f && d < 0.9) deadTree(level, above, rng);
                else if (r < 0.15f) place(level, above, ModBlocks.WITHERED_GRASS.get().defaultBlockState());
            }
            case WAITING_ROOMS -> furniture(level, above, r);
            case THE_MAW -> {
                if (r < 0.012f) {
                    int h = 1 + rng.nextInt(3);
                    for (int i = 0; i < h && level.getBlockState(above.above(i)).isAir(); i++) level.setBlock(above.above(i), ModBlocks.ENAMEL.get().defaultBlockState(), FLAGS);
                }
            }
            case SIGNAL_LOST -> {
                if (r < 0.006f) {
                    int h = 2 + rng.nextInt(4);
                    for (int i = 0; i < h && level.getBlockState(above.above(i)).isAir(); i++) level.setBlock(above.above(i), ModBlocks.STATIC_BLOCK.get().defaultBlockState(), FLAGS);
                } else if (r < 0.009f) place(level, above.above(rng.nextInt(4)), ModBlocks.TEST_CARD.get().defaultBlockState());
            }
            default -> {
            }
        }
    }

    /** Things that hang off the bottom of an island: kelp in the reef, veins under the Maw, chimes under the ice. */
    private static void underside(WorldGenLevel level, IslandField field, Island is, int x, int z, RandomSource rng) {
        BlockState hang;
        float chance;
        int max;
        switch (is.kind) {
            case SKYREEF -> { hang = ModBlocks.SKY_KELP.get().defaultBlockState(); chance = 0.03f; max = 9; }
            case THE_MAW -> { hang = ModBlocks.VEIN_VINES.get().defaultBlockState(); chance = 0.025f; max = 7; }
            case FROSTLIGHT_TUNDRA -> { hang = ModBlocks.ICE_CHIME.get().defaultBlockState(); chance = 0.012f; max = 2; }
            case LUCID_GEODES -> {
                hang = ModBlocks.LUCID_CRYSTAL_CLUSTER.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN);
                chance = 0.02f;
                max = 1;
            }
            default -> { return; }
        }
        if (rng.nextFloat() >= chance || is.shape == Shape.INVERTED) return;
        int bot;
        if (is.shape == Shape.GEODE) {
            double dx = x + 0.5 - is.cx;
            double dz = z + 0.5 - is.cz;
            double h2 = is.radius * (double) is.radius - dx * dx - dz * dz;
            if (h2 <= 0) return;
            bot = (int) Math.floor(IslandField.geodeCenterY(is) - Math.sqrt(h2));
        } else {
            bot = field.plateauBottom(is, x, z);
            if (bot == Integer.MAX_VALUE) return;
        }
        BlockPos p = new BlockPos(x, bot - 1, z);
        if (!level.getBlockState(p).isAir() || level.getBlockState(p.above()).isAir()) return;
        int n = 1 + rng.nextInt(max);
        for (int i = 0; i < n && level.getBlockState(p.below(i)).isAir(); i++) level.setBlock(p.below(i), hang, FLAGS);
    }

    /** Frostlight pines: a narrow cone of frosted leaves, snow on every ledge, ice chimes under the lowest boughs. */
    private static void pine(WorldGenLevel level, BlockPos base, RandomSource rng) {
        int h = 6 + rng.nextInt(4);
        for (int i = 0; i < h + 2; i++) if (!level.getBlockState(base.above(i)).isAir()) return;
        BlockState log = ModBlocks.DREAMWOOD_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int i = 0; i < h; i++) level.setBlock(base.above(i), log, FLAGS);
        for (int i = 2; i <= h + 1; i++) {
            double r = (h + 1 - i) * 0.42 + (i % 2 == 0 ? 0.6 : 0.1);
            int ri = (int) Math.ceil(r);
            for (int dx = -ri; dx <= ri; dx++)
                for (int dz = -ri; dz <= ri; dz++) {
                    if (dx * dx + dz * dz > r * r + 0.3) continue;
                    BlockPos p = base.offset(dx, i, dz);
                    place(level, p, leaves());
                }
        }
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int i = h + 2; i >= 2; i--) {
                    BlockPos p = base.offset(dx, i, dz);
                    if (level.getBlockState(p).is(ModBlocks.DREAMWOOD_LEAVES.get())) {
                        place(level, p.above(), Blocks.SNOW.defaultBlockState());
                        break;
                    }
                }
        for (int k = 0; k < 2; k++) {
            BlockPos under = base.offset(rng.nextInt(3) - 1, 1, rng.nextInt(3) - 1);
            if (level.getBlockState(under).isAir() && level.getBlockState(under.above()).is(ModBlocks.DREAMWOOD_LEAVES.get())) {
                level.setBlock(under, ModBlocks.ICE_CHIME.get().defaultBlockState(), FLAGS);
            }
        }
    }

    /** Blackwood trees: tall, dead, black, a few crooked branches near the top. */
    private static void deadTree(WorldGenLevel level, BlockPos base, RandomSource rng) {
        int h = 9 + rng.nextInt(10);
        for (int i = 0; i < h; i++) if (!level.getBlockState(base.above(i)).isAir()) return;
        BlockState log = ModBlocks.BLACKWOOD_LOG.get().defaultBlockState();
        for (int i = 0; i < h; i++) level.setBlock(base.above(i), log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), FLAGS);
        int branches = 1 + rng.nextInt(3);
        for (int b = 0; b < branches; b++) {
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(rng);
            BlockPos p = base.above(h - 2 - rng.nextInt(Math.max(1, h / 3)));
            int len = 2 + rng.nextInt(3);
            for (int k = 1; k <= len; k++) {
                p = p.relative(d);
                if (k == len - 1 || rng.nextInt(3) == 0) p = p.above();
                if (!level.getBlockState(p).isAir()) break;
                level.setBlock(p, log.setValue(RotatedPillarBlock.AXIS, d.getAxis()), FLAGS);
            }
        }
    }

    /** Waiting Rooms: chairs along the walls, now and then a dead plant or a water cooler. */
    private static void furniture(WorldGenLevel level, BlockPos above, float r) {
        if (r > 0.3f || !level.getBlockState(above).isAir() || !level.getBlockState(above.above()).isAir()) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos wall = above.relative(d);
            if (!level.getBlockState(wall).is(ModBlocks.YELLOWED_WALLPAPER.get()) || !level.getBlockState(wall.above()).is(ModBlocks.YELLOWED_WALLPAPER.get())) continue;
            if (r < 0.012f) place(level, above, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
            else if (r < 0.02f) {
                place(level, above, Blocks.WHITE_CONCRETE.defaultBlockState());
                place(level, above.above(), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
            } else {
                place(level, above, Blocks.BIRCH_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, d));
            }
            return;
        }
    }

    private static void place(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir()) level.setBlock(pos, state, FLAGS);
    }

    private static BlockState leaves() {
        return ModBlocks.DREAMWOOD_LEAVES.get().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private static void tree(WorldGenLevel level, BlockPos base, RandomSource rng) {
        int h = 3 + rng.nextInt(3);
        for (int i = 0; i < h; i++) if (!level.getBlockState(base.above(i)).isAir()) return;
        BlockState log = ModBlocks.DREAMWOOD_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int i = 0; i < h; i++) level.setBlock(base.above(i), log, FLAGS);
        canopy(level, base.above(h), 2.4 + rng.nextDouble() * 0.6, rng);
    }

    private static void hangingTree(WorldGenLevel level, BlockPos top, RandomSource rng) {
        int h = 3 + rng.nextInt(3);
        for (int i = 0; i < h; i++) if (!level.getBlockState(top.below(i)).isAir()) return;
        BlockState log = ModBlocks.DREAMWOOD_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int i = 0; i < h; i++) level.setBlock(top.below(i), log, FLAGS);
        canopy(level, top.below(h), 2.2 + rng.nextDouble() * 0.6, rng);
        if (rng.nextInt(3) == 0) {
            BlockPos lamp = top.below(h + 3);
            if (level.getBlockState(lamp).isAir()) level.setBlock(lamp, ModBlocks.FLOATING_LANTERN.get().defaultBlockState(), FLAGS);
        }
    }

    private static void canopy(WorldGenLevel level, BlockPos c, double r, RandomSource rng) {
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++)
            for (int dy = -ri; dy <= ri; dy++)
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = dx * dx + dy * dy * 1.4 + dz * dz;
                    if (d > r * r || d > r * r - 1.2 && rng.nextInt(3) == 0) continue;
                    place(level, c.offset(dx, dy, dz), leaves());
                }
    }

    // ------------------------------------------------------------------ landmarks

    private static void landmarks(WorldGenLevel level, IslandField field, Island is, int x0, int z0) {
        long h = is.seed;
        if (is.shape == Shape.CLOUD) return;
        // Lone Door on about a quarter of the islands.
        if (IslandField.unit(IslandField.mix(h ^ 0xD00DL)) < 0.27 && is.shape != Shape.INVERTED && is.shape != Shape.SEA_OF_DOORS) {
            double a = IslandField.unit(IslandField.mix(h ^ 0xD01L)) * Math.PI * 2;
            int x = is.cx + (int) Math.round(Math.cos(a) * is.radius * 0.35);
            int z = is.cz + (int) Math.round(Math.sin(a) * is.radius * 0.35);
            if (inChunk(x, z, x0, z0)) loneDoor(level, field, is, x, z, Direction.from2DDataValue((int) (IslandField.mix(h ^ 0xD02L) & 3)));
        }
        if (is.shape == Shape.STAIRS) {
            double c = Math.cos(is.stairAngle);
            double s = Math.sin(is.stairAngle);
            double t = is.radius * 0.8 + is.stairLength - 0.5;
            int x = (int) Math.floor(is.cx + c * t);
            int z = (int) Math.floor(is.cz + s * t);
            int k = (int) Math.floor(t - is.radius * 0.8);
            if (inChunk(x, z, x0, z0)) {
                BlockPos floor = new BlockPos(x, is.cy + 1 + k, z);
                BlockPos door = floor.above();
                if (!level.getBlockState(floor).isAir() && level.getBlockState(door).isAir() && level.getBlockState(door.above()).isAir()) {
                    Direction facing = Direction.getNearest(c, 0, s);
                    setDoor(level, door, facing.getAxis().isHorizontal() ? facing : Direction.NORTH);
                }
            }
        }
        if (is.shape == Shape.GREAT_BED && inChunk(is.cx, is.cz, x0, z0)) {
            // Three real beds on the blanket: sleeping in the Great Bed ends the dream kindly.
            for (int dx : new int[]{-4, -1, 2}) {
                BlockPos foot = new BlockPos(is.cx + dx, is.cy + 5, is.cz + 4);
                BlockPos head = foot.north();
                if (level.getBlockState(foot).isAir() && level.getBlockState(head).isAir()) {
                    BlockState bed = Blocks.MAGENTA_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
                    level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), FLAGS);
                    level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), FLAGS);
                }
            }
        }
        if (is.kind == Kind.UPSIDE_DOWN_TOWN && is.shape == Shape.PLATEAU) {
            // The town: a few houses hanging from the underside, each anchored in its own chunk.
            int houses = 2 + (int) (IslandField.unit(IslandField.mix(h ^ 0x70A7L)) * 3);
            for (int i = 0; i < houses; i++) {
                double a = i * Math.PI * 2 / houses + IslandField.unit(IslandField.mix(h ^ (0x70B0L + i)));
                int hx = is.cx + (int) Math.round(Math.cos(a) * is.radius * 0.45);
                int hz = is.cz + (int) Math.round(Math.sin(a) * is.radius * 0.45);
                if (inChunk(hx, hz, x0, z0)) upsideDownHouse(level, field, is, hx, hz);
            }
        }
        double roll = IslandField.unit(IslandField.mix(h ^ 0x1A7DL));
        int ax = is.cx + (int) Math.round((IslandField.unit(IslandField.mix(h ^ 0xA1L)) - 0.5) * is.radius * 0.6);
        int az = is.cz + (int) Math.round((IslandField.unit(IslandField.mix(h ^ 0xA2L)) - 0.5) * is.radius * 0.6);
        if (!inChunk(ax, az, x0, z0)) return;
        int top = field.topOf(is, ax, az);
        if (top == Integer.MIN_VALUE) return;
        BlockPos ground = new BlockPos(ax, top, az);
        RandomSource lr = new XoroshiroRandomSource(IslandField.mix(h ^ 0x5EA5L));
        Direction facing = Direction.from2DDataValue((int) (IslandField.mix(h ^ 0xFACEL) & 3));
        if (is.kind == Kind.PILLOW_FIELDS && roll < 0.12) sleepBellGrove(level, field, is, ground);
        else if (is.kind == Kind.SANDGLASS_DUNES && roll < 0.7) hourglass(level, ground.above());
        else if (is.kind == Kind.FROSTLIGHT_TUNDRA && roll < 0.38 && is.shape == Shape.PLATEAU) DreamStructures.snowGlobe(level, ground);
        else if (is.kind == Kind.SKYREEF && roll < 0.34 && is.shape != Shape.INVERTED) DreamStructures.skyWreck(level, ground, lr);
        else if (is.kind == Kind.LUCID_GEODES && roll < 0.42 && is.shape != Shape.GEODE) DreamStructures.crystalChoir(level, field, is, ground);
        else if (is.kind == Kind.THE_NURSERY && roll < 0.3 && is.shape == Shape.PLATEAU) DreamStructures.teddy(level, ground, facing);
        else if (is.kind == Kind.THE_NURSERY && roll < 0.55 && is.shape == Shape.PLATEAU) DreamStructures.blockTower(level, ground, lr);
        else if (is.kind == Kind.THE_NURSERY && roll < 0.75) DreamStructures.crayons(level, ground, lr);
        else if (is.kind == Kind.BLACKWOOD && roll < 0.36 && is.shape == Shape.PLATEAU) DreamStructures.cabin(level, ground, facing);
        else if (is.kind == Kind.WAITING_ROOMS && roll < 0.55) {
            // Snap to the middle of a room.
            int rx = is.cx + Math.round((ax - is.cx) / 11f) * 11;
            int rz = is.cz + Math.round((az - is.cz) / 11f) * 11;
            if (inChunk(rx, rz, x0, z0) && field.plateauTop(is, rx, rz) == is.cy) DreamStructures.reception(level, new BlockPos(rx, is.cy, rz), facing);
        } else if (is.kind == Kind.THE_MAW && roll < 0.45 && is.shape == Shape.PLATEAU) DreamStructures.ribcage(level, ground, (h & 4) == 0, lr);
        else if (is.kind == Kind.SIGNAL_LOST && roll < 0.42 && is.shape != Shape.INVERTED) DreamStructures.television(level, ground, facing);
        else if (roll > 0.982 && is.shape != Shape.SPIRES) giantClock(level, ground.above(9), IslandField.mix(h ^ 0xC10CL));
        else if (roll > 0.94 && is.shape == Shape.PLATEAU && is.kind != Kind.UPSIDE_DOWN_TOWN) impossibleStairs(level, ground);
    }

    private static void loneDoor(WorldGenLevel level, IslandField field, Island is, int x, int z, Direction facing) {
        int top = field.topOf(is, x, z);
        if (top == Integer.MIN_VALUE) return;
        BlockPos pos = new BlockPos(x, top + 1, z);
        if (level.getBlockState(pos.below()).isAir() || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) return;
        setDoor(level, pos, facing);
    }

    private static void setDoor(WorldGenLevel level, BlockPos pos, Direction facing) {
        BlockState door = ModBlocks.LONE_DOOR.get().defaultBlockState().setValue(LoneDoorBlock.FACING, facing);
        level.setBlock(pos, door.setValue(LoneDoorBlock.HALF, DoubleBlockHalf.LOWER), FLAGS);
        level.setBlock(pos.above(), door.setValue(LoneDoorBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
    }

    private static void sleepBellGrove(WorldGenLevel level, IslandField field, Island is, BlockPos center) {
        BlockState post = ModBlocks.DREAMWOOD_LOG.get().defaultBlockState();
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5;
            int x = center.getX() + (int) Math.round(Math.cos(a) * 4);
            int z = center.getZ() + (int) Math.round(Math.sin(a) * 4);
            int top = field.topOf(is, x, z);
            if (top == Integer.MIN_VALUE) continue;
            BlockPos p = new BlockPos(x, top + 1, z);
            if (!level.getBlockState(p).isAir() || level.getBlockState(p.below()).isAir()) continue;
            level.setBlock(p, post, FLAGS);
            if (level.getBlockState(p.above()).isAir()) level.setBlock(p.above(), ModBlocks.SLEEP_BELL.get().defaultBlockState(), FLAGS);
        }
        place(level, center.above(), ModBlocks.WILD_MELATONIA.get().defaultBlockState());
    }

    /** A tall glass hourglass; its core is the centre of a time bubble. */
    private static void hourglass(WorldGenLevel level, BlockPos base) {
        for (int dy = 0; dy < 7; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    if (!level.getBlockState(base.offset(dx, dy, dz)).isAir()) return;
        BlockState frame = ModBlocks.DREAMWOOD_PLANKS.get().defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState sand = ModBlocks.HOURGLASS_SAND.get().defaultBlockState();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(base.offset(dx, 0, dz), frame, FLAGS);
                level.setBlock(base.offset(dx, 6, dz), frame, FLAGS);
                boolean corner = dx != 0 && dz != 0;
                for (int dy = 1; dy <= 5; dy++) {
                    if (dy == 3 && (dx != 0 || dz != 0)) continue; // the waist
                    BlockState s = corner ? frame : (dx == 0 && dz == 0) ? (dy == 1 || dy == 5 ? sand : Blocks.AIR.defaultBlockState()) : glass;
                    if (corner && (dy == 2 || dy == 4)) s = Blocks.AIR.defaultBlockState();
                    level.setBlock(base.offset(dx, dy, dz), s, FLAGS);
                }
            }
        level.setBlock(base.offset(0, 3, 0), ModBlocks.SANDGLASS_CORE.get().defaultBlockState(), FLAGS);
    }

    /**
     * Impossible Stairs: an invisible anchor whose renderer draws a loop of four climbing flights that never ends
     * (the last flight is re-solved toward each viewer's eye, see ImpossibleStairsRenderer). Next to it a slim
     * viewing tower with a ladder and a plaque on top: from up there you look down on the climb.
     */
    private static void impossibleStairs(WorldGenLevel level, BlockPos ground) {
        BlockPos anchor = ground.above(2);
        BlockPos tower = ground.offset(-8, 1, 8);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = 0; dy <= 12; dy++)
                    if (!level.getBlockState(anchor.offset(dx, dy, dz)).isAir()) return;
        if (!level.getBlockState(tower.below()).isSolid()) return;
        for (int dy = 0; dy <= 12; dy++) {
            if (!level.getBlockState(tower.above(dy)).isAir() || !level.getBlockState(tower.above(dy).east()).isAir()) return;
        }
        BlockState brick = ModBlocks.LUCID_STONE_BRICKS.get().defaultBlockState();
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST);
        for (int dy = 0; dy < 11; dy++) {
            level.setBlock(tower.above(dy), brick, FLAGS);
            level.setBlock(tower.above(dy).east(), ladder, FLAGS);
        }
        level.setBlock(tower.above(11), Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState(), FLAGS);
        level.setBlock(anchor, ModBlocks.IMPOSSIBLE_STAIRS.get().defaultBlockState(), FLAGS);
    }

    /** A little house hanging from the island's underside, furniture on its ceiling (which is its floor). */
    private static void upsideDownHouse(WorldGenLevel level, IslandField field, Island is, int cx, int cz) {
        int bottom = Integer.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                if (field.normDist(is, cx + dx, cz + dz) >= 0.85) return;
                bottom = Math.min(bottom, field.plateauBottom(is, cx + dx, cz + dz));
            }
        int y = bottom - 1; // the house's floor, attached to the island
        BlockState planks = ModBlocks.DREAMWOOD_PLANKS.get().defaultBlockState();
        BlockState log = ModBlocks.DREAMWOOD_LOG.get().defaultBlockState();
        BlockState roof = ModBlocks.LUCID_STONE_BRICKS.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                // Fill the gap up to the island so it really hangs.
                for (int up = y + 1; up < y + 6; up++) {
                    BlockPos p = new BlockPos(cx + dx, up, cz + dz);
                    if (!level.getBlockState(p).isAir()) break;
                    level.setBlock(p, ModBlocks.LUCID_STONE.get().defaultBlockState(), FLAGS);
                }
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), planks, FLAGS);
                boolean wall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                boolean corner = Math.abs(dx) == 3 && Math.abs(dz) == 3;
                for (int dy = 1; dy <= 4; dy++) {
                    BlockPos p = new BlockPos(cx + dx, y - dy, cz + dz);
                    BlockState s = corner ? log : wall ? planks : air;
                    if (wall && !corner && dy == 2 && (dx == 0 || dz == 0)) s = Blocks.GLASS_PANE.defaultBlockState();
                    level.setBlock(p, s, FLAGS);
                }
            }
        // Roof pointing down (a stepped pyramid) and a chimney hanging below it.
        for (int layer = 0; layer < 3; layer++)
            for (int dx = -3 + layer; dx <= 3 - layer; dx++)
                for (int dz = -3 + layer; dz <= 3 - layer; dz++)
                    level.setBlock(new BlockPos(cx + dx, y - 5 - layer, cz + dz), roof, FLAGS);
        for (int dy = 0; dy < 3; dy++) level.setBlock(new BlockPos(cx + 2, y - 6 - dy, cz + 1), Blocks.BRICKS.defaultBlockState(), FLAGS);
        // Furniture on the ceiling (= hanging from the floor above our heads).
        level.setBlock(new BlockPos(cx - 2, y - 1, cz - 2), Blocks.BOOKSHELF.defaultBlockState(), FLAGS);
        level.setBlock(new BlockPos(cx - 1, y - 1, cz - 2), Blocks.CRAFTING_TABLE.defaultBlockState(), FLAGS);
        level.setBlock(new BlockPos(cx + 2, y - 1, cz + 2), Blocks.FLOWER_POT.defaultBlockState(), FLAGS);
        level.setBlock(new BlockPos(cx, y - 1, cz), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), FLAGS);
        // A lamp post hanging from the island next to the house.
        for (int dy = 1; dy <= 5; dy++) level.setBlock(new BlockPos(cx + 5, y + 1 - dy, cz), Blocks.DARK_OAK_FENCE.defaultBlockState(), FLAGS);
        level.setBlock(new BlockPos(cx + 5, y - 5, cz), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), FLAGS);
    }

    /** A giant floating clock face with thirteen marks and its hands at a time it never is. */
    private static void giantClock(WorldGenLevel level, BlockPos center, long h) {
        boolean alongX = (h & 1) == 0;
        BlockState face = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState rim = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState hand = Blocks.BLACK_CONCRETE.defaultBlockState();
        int r = 6;
        for (int a = -r; a <= r; a++)
            for (int b = -r; b <= r; b++) {
                double d = Math.sqrt(a * a + b * b);
                if (d > r + 0.3) continue;
                BlockPos p = alongX ? center.offset(a, b, 0) : center.offset(0, b, a);
                if (!level.getBlockState(p).isAir()) continue;
                level.setBlock(p, d > r - 0.7 ? rim : face, FLAGS);
            }
        for (int m = 0; m < 13; m++) {
            double ang = m * Math.PI * 2 / 13;
            int a = (int) Math.round(Math.sin(ang) * (r - 1.5));
            int b = (int) Math.round(Math.cos(ang) * (r - 1.5));
            BlockPos p = alongX ? center.offset(a, b, alongX ? 1 : 0) : center.offset(1, b, a);
            if (level.getBlockState(p).isAir()) level.setBlock(p, rim, FLAGS);
        }
        // Hour hand toward the 13th mark, minute hand toward a minute that is not on the dial.
        double[] angles = {12.5 * Math.PI * 2 / 13, 0.37 * Math.PI * 2};
        int[] lengths = {3, 5};
        for (int k = 0; k < 2; k++)
            for (int i = 0; i <= lengths[k]; i++) {
                int a = (int) Math.round(Math.sin(angles[k]) * i);
                int b = (int) Math.round(Math.cos(angles[k]) * i);
                BlockPos p = alongX ? center.offset(a, b, 1) : center.offset(1, b, a);
                level.setBlock(p, hand, FLAGS);
            }
    }
}
