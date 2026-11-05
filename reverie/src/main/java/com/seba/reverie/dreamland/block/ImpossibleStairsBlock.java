package com.seba.reverie.dreamland.block;

import com.mojang.serialization.MapCodec;
import com.seba.reverie.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The invisible anchor of the Impossible Stairs. Nothing here is solid: the stairs are drawn by the block entity
 * renderer, which bends the last flight toward the viewer's eye so the loop closes from wherever you stand.
 */
public class ImpossibleStairsBlock extends BaseEntityBlock {
    public static final MapCodec<ImpossibleStairsBlock> CODEC = simpleCodec(ImpossibleStairsBlock::new);

    public ImpossibleStairsBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntity(ModBlockEntities.IMPOSSIBLE_STAIRS.get(), pos, state) {
        };
    }
}
