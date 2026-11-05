package com.seba.reverie.dreamland.block;

import com.seba.reverie.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Purely cosmetic anchor for the floating lantern, melted clock and eyebloom renderers. */
public class DecorBlockEntity extends BlockEntity {
    public DecorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECOR.get(), pos, state);
    }
}
