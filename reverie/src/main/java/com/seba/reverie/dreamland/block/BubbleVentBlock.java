package com.seba.reverie.dreamland.block;

import com.seba.reverie.registry.ModParticles;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Skyreef vent breathing a column of bubbles into the open air. Whoever stands in the column is carried up
 * (BiomeMechanicsClient lifts the local player: movement is the client's).
 */
public class BubbleVentBlock extends Block {
    public static final int REACH = 36;

    public BubbleVentBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 3; i++) {
            level.addParticle(ModParticles.DREAM_BUBBLE.get(), pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.0 + random.nextDouble() * 10,
                    pos.getZ() + 0.2 + random.nextDouble() * 0.6, (random.nextDouble() - 0.5) * 0.02, 0.18 + random.nextDouble() * 0.12,
                    (random.nextDouble() - 0.5) * 0.02);
        }
        if (random.nextInt(60) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                    SoundSource.BLOCKS, 0.4f, 0.8f + random.nextFloat() * 0.3f, false);
        }
    }
}
