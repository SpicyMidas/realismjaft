package com.seba.reverie.dreamland.block;

import com.mojang.serialization.MapCodec;
import com.seba.reverie.dreamland.LoneDoors;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A freestanding doorframe. Walk through it and you are somewhere else. */
public class LoneDoorBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<LoneDoorBlock> CODEC = simpleCodec(LoneDoorBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    private static final VoxelShape FRAME_NS = Shapes.or(Block.box(0, 0, 6, 2, 16, 10), Block.box(14, 0, 6, 16, 16, 10));
    private static final VoxelShape FRAME_EW = Shapes.or(Block.box(6, 0, 0, 10, 16, 2), Block.box(6, 0, 14, 10, 16, 16));
    private static final VoxelShape TOP_NS = Shapes.or(FRAME_NS, Block.box(0, 14, 6, 16, 16, 10));
    private static final VoxelShape TOP_EW = Shapes.or(FRAME_EW, Block.box(6, 14, 0, 10, 16, 16));

    public LoneDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        if (pos.getY() >= ctx.getLevel().getMaxBuildHeight() - 1 || !ctx.getLevel().getBlockState(pos.above()).canBeReplaced(ctx)) return null;
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (dir == Direction.UP)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return level.getBlockState(pos.below()).is(this);
        return true;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean ns = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return ns ? TOP_NS : TOP_EW;
        return ns ? FRAME_NS : FRAME_EW;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && entity instanceof ServerPlayer player) {
            LoneDoors.walkThrough(player, pos);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && random.nextInt(3) == 0) {
            boolean ns = state.getValue(FACING).getAxis() == Direction.Axis.Z;
            double along = 0.2 + random.nextDouble() * 0.6;
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + (ns ? along : 0.5), pos.getY() + random.nextDouble() * 1.9,
                    pos.getZ() + (ns ? 0.5 : along), 0, 0.01, 0);
        }
    }
}
