package com.seba.reverie.dreamland;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.seba.reverie.Reverie;
import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.dream.DreamSession;
import com.seba.reverie.dream.HomeIsland;
import com.seba.reverie.dreamland.block.LoneDoorBlock;
import com.seba.reverie.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * A warped echo of the room you fell asleep in, next to your Home Island: pieces of the room rotated, some
 * upside down, some floating off, doors become Lone Doors, ordinary blocks turn ghostly, and the bed sits in
 * the middle at twice its size. Fully inert. Replaced by the next dream's snapshot.
 */
public final class MemoryIsland implements DreamManager.Listener {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    @Override
    public void started(ServerPlayer player, DreamSession session) {
        if (session.snapshot().isEmpty()) return;
        try {
            ServerLevel dream = player.server.getLevel(DreamManager.DREAMLAND);
            if (dream == null) return;
            BlockPos home = HomeIsland.findOrBuild(dream, player.getUUID());
            build(dream, home.offset(18, 3, 0), session.snapshot(), player.getUUID().getLeastSignificantBits() ^ session.startTime());
        } catch (RuntimeException e) {
            Reverie.LOGGER.warn("Memory Island skipped for {}", player.getName().getString(), e);
        }
    }

    public static void build(ServerLevel level, BlockPos origin, List<DreamSession.SnapshotBlock> snapshot, long seed) {
        RandomSource rng = new XoroshiroRandomSource(seed);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) level.getChunk((origin.getX() >> 4) + dx, (origin.getZ() >> 4) + dz);
        // Clear the previous memory.
        for (int x = -10; x <= 10; x++)
            for (int y = -3; y <= 14; y++)
                for (int z = -10; z <= 10; z++) {
                    BlockPos p = origin.offset(x, y, z);
                    if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                }
        BlockState base = ModBlocks.LUCID_STONE.get().defaultBlockState();
        BlockState grass = ModBlocks.DREAM_GRASS_BLOCK.get().defaultBlockState();
        for (int x = -7; x <= 7; x++)
            for (int z = -7; z <= 7; z++) {
                int d2 = x * x + z * z;
                if (d2 > 50) continue;
                level.setBlock(origin.offset(x, -1, z), d2 > 40 ? grass : base, FLAGS);
                if (d2 < 20) level.setBlock(origin.offset(x, -2, z), base, FLAGS);
                if (d2 < 6) level.setBlock(origin.offset(x, -3, z), base, FLAGS);
            }
        // Each 3x3x3 piece of the room gets its own dream logic.
        Map<Long, int[]> pieces = new HashMap<>();
        BlockState[] ghosts = {Blocks.GLASS.defaultBlockState(), ModBlocks.PILLOW_CLOUD.get().defaultBlockState(),
                ModBlocks.LUCID_STONE.get().defaultBlockState(), Blocks.TINTED_GLASS.defaultBlockState()};
        for (DreamSession.SnapshotBlock sb : snapshot) {
            BlockState state = sb.state();
            if (state.getBlock() instanceof BedBlock) continue;
            BlockPos o = sb.offset();
            long key = BlockPos.asLong(Math.floorDiv(o.getX(), 3), Math.floorDiv(o.getY(), 3), Math.floorDiv(o.getZ(), 3));
            int[] t = pieces.computeIfAbsent(key, k -> new int[]{rng.nextInt(4), rng.nextInt(5) == 0 ? 1 : 0, rng.nextInt(4) == 0 ? 1 + rng.nextInt(3) : 0});
            int px = Math.floorDiv(o.getX(), 3) * 3 + 1;
            int pz = Math.floorDiv(o.getZ(), 3) * 3 + 1;
            int py = Math.floorDiv(o.getY(), 3) * 3 + 1;
            int rx = o.getX() - px;
            int rz = o.getZ() - pz;
            int ry = o.getY() - py;
            for (int r = 0; r < t[0]; r++) {
                int tmp = rx;
                rx = -rz;
                rz = tmp;
            }
            if (t[1] == 1) ry = -ry;
            BlockPos target = origin.offset(px + rx, py + ry + t[2] + 1, pz + rz);
            BlockState placed = transform(state, t[0], t[1] == 1, rng, ghosts);
            if (placed == null) continue;
            if (placed.getBlock() instanceof LoneDoorBlock) {
                if (level.getBlockState(target).isAir() && level.getBlockState(target.above()).isAir()) {
                    level.setBlock(target, placed.setValue(LoneDoorBlock.HALF, DoubleBlockHalf.LOWER), FLAGS);
                    level.setBlock(target.above(), placed.setValue(LoneDoorBlock.HALF, DoubleBlockHalf.UPPER), FLAGS);
                }
                continue;
            }
            if (Math.abs(target.getX() - origin.getX()) <= 10 && Math.abs(target.getZ() - origin.getZ()) <= 10 && target.getY() - origin.getY() < 14) {
                level.setBlock(target, placed, FLAGS);
            }
        }
        bigBed(level, origin.above());
    }

    private static BlockState transform(BlockState state, int rotations, boolean flipped, RandomSource rng, BlockState[] ghosts) {
        var block = state.getBlock();
        if (block instanceof DoorBlock) {
            if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) return null;
            Direction f = state.getValue(DoorBlock.FACING);
            return ModBlocks.LONE_DOOR.get().defaultBlockState().setValue(LoneDoorBlock.FACING, f);
        }
        // Inert: nothing that carries or makes a signal, nothing that moves or explodes.
        if (state.isSignalSource() || block instanceof PistonBaseBlock || block instanceof ObserverBlock || block instanceof TntBlock
                || state.is(Blocks.REDSTONE_WIRE) || state.hasBlockEntity()) {
            return ghosts[rng.nextInt(ghosts.length)];
        }
        if (rng.nextInt(6) == 0) return ghosts[rng.nextInt(ghosts.length)];
        Rotation rot = Rotation.values()[rotations & 3];
        BlockState out = state.rotate(rot);
        if (flipped) {
            if (out.hasProperty(BlockStateProperties.HALF)) {
                out = out.cycle(BlockStateProperties.HALF);
            } else if (out.hasProperty(BlockStateProperties.SLAB_TYPE)) {
                var type = out.getValue(BlockStateProperties.SLAB_TYPE);
                if (type != net.minecraft.world.level.block.state.properties.SlabType.DOUBLE) {
                    out = out.setValue(BlockStateProperties.SLAB_TYPE, type == net.minecraft.world.level.block.state.properties.SlabType.TOP
                            ? net.minecraft.world.level.block.state.properties.SlabType.BOTTOM : net.minecraft.world.level.block.state.properties.SlabType.TOP);
                }
            }
        }
        return out;
    }

    /** The bed, twice as big, in the middle of it all. */
    private static void bigBed(ServerLevel level, BlockPos c) {
        BlockState frame = ModBlocks.DREAMWOOD_PLANKS.get().defaultBlockState();
        BlockState white = Blocks.WHITE_WOOL.defaultBlockState();
        BlockState red = Blocks.RED_WOOL.defaultBlockState();
        for (int x = 0; x < 2; x++)
            for (int z = 0; z < 4; z++) {
                BlockPos p = c.offset(x - 1, 0, z - 2);
                level.setBlock(p, frame, FLAGS);
                level.setBlock(p.above(), z == 0 ? white : red, FLAGS);
            }
        level.setBlock(c.offset(-1, 1, -3), frame, FLAGS);
        level.setBlock(c.offset(0, 1, -3), frame, FLAGS);
        level.setBlock(c.offset(-1, 2, -3), frame, FLAGS);
        level.setBlock(c.offset(0, 2, -3), frame, FLAGS);
    }
}
