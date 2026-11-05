package com.seba.reverie.dreamland.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A still pool you can walk on. It reflects a sky that is not quite this one. Ripples where you step. */
public class MirrorWaterBlock extends Block {
    public MirrorWaterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level.isClientSide && entity.getDeltaMovement().horizontalDistanceSqr() > 1e-4 && level.random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SPLASH, entity.getX() + (level.random.nextDouble() - 0.5) * 0.6, pos.getY() + 1.02,
                    entity.getZ() + (level.random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            if (level.random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.BUBBLE_POP, entity.getX(), pos.getY() + 1.01, entity.getZ(), 0, 0, 0);
            }
        }
        super.stepOn(level, pos, state, entity);
    }
}
