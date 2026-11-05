package com.seba.reverie.dreamland.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Dreamwood leaves (two-tint via the colour handler) that shed slow glowing petals. */
public class DreamLeavesBlock extends LeavesBlock {
    public DreamLeavesBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (random.nextInt(40) == 0 && level.getBlockState(pos.below()).isAir()) {
            level.addParticle(ParticleTypes.CHERRY_LEAVES, pos.getX() + random.nextDouble(), pos.getY() - 0.05,
                    pos.getZ() + random.nextDouble(), 0, 0, 0);
        }
    }
}
