package com.seba.reverie.dreamland.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** A Waiting Rooms ceiling light: buzzes, and now and then flickers off for a moment. */
public class FlickerLightBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public FlickerLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && random.nextInt(3) == 0) darken(level, pos, 2 + random.nextInt(6));
    }

    /** Off for {@code ticks}, then on again. */
    public static void darken(Level level, BlockPos pos, int ticks) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FlickerLightBlock) || !state.getValue(LIT)) return;
        level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, state.getBlock(), ticks);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && random.nextInt(200) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS,
                    0.12f, 1.9f, false);
        }
    }
}
