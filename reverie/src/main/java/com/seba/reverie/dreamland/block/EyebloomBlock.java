package com.seba.reverie.dreamland.block;

import com.mojang.serialization.MapCodec;
import com.seba.reverie.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A flower with a single eye. Its pupil (drawn by a renderer) follows the nearest player. Cosmetic only. */
public class EyebloomBlock extends BushBlock implements EntityBlock {
    public static final MapCodec<EyebloomBlock> CODEC = simpleCodec(EyebloomBlock::new);
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 13, 12);

    public EyebloomBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModTags.Blocks.WILD_MELATONIA_PLACEABLE) || super.mayPlaceOn(state, level, pos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DecorBlockEntity(pos, state);
    }
}
