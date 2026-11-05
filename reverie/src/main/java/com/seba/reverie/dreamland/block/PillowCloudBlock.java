package com.seba.reverie.dreamland.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Soft cloud: no fall damage, a mild bounce, translucent, never suffocates. */
public class PillowCloudBlock extends HalfTransparentBlock {
    public PillowCloudBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.resetFallDistance();
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        if (entity.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(level, entity);
            return;
        }
        Vec3 v = entity.getDeltaMovement();
        if (v.y < -0.1) {
            double factor = entity instanceof LivingEntity ? 0.55 : 0.4;
            entity.setDeltaMovement(v.x, -v.y * factor, v.z);
        } else {
            entity.setDeltaMovement(v.x, 0, v.z);
        }
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
