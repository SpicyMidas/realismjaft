package com.seba.reverie.dreamland.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Lucid crystal cluster: hums very softly now and then. */
public class CrystalClusterBlock extends AmethystClusterBlock {
    public CrystalClusterBlock(Properties properties) {
        super(7, 3, properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(160) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.AMBIENT, 0.12f, 0.5f + random.nextFloat() * 0.2f, false);
        }
    }
}
