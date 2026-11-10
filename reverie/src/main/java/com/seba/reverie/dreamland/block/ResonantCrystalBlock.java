package com.seba.reverie.dreamland.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Lucid Geodes: a crystal that rings when touched, walked on or merely approached, and lights up. The ring
 * travels on through the crystals next to it, losing strength each step, so a struck geode lights up in a
 * ripple and sings a little pentatonic chord.
 */
public class ResonantCrystalBlock extends Block {
    public static final IntegerProperty RESONANCE = IntegerProperty.create("resonance", 0, 7);
    private static final float[] SCALE = {0, 2, 4, 7, 9, 12, 14};

    public ResonantCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RESONANCE, 0));
    }

    public static int light(BlockState state) {
        return Math.min(15, 3 + state.getValue(RESONANCE) * 2);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RESONANCE);
    }

    /** Rings this crystal with {@code power} (1..7) unless it is already ringing at least as strongly. */
    public static void resonate(Level level, BlockPos pos, int power, boolean sound) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ResonantCrystalBlock) || state.getValue(RESONANCE) >= power) return;
        level.setBlock(pos, state.setValue(RESONANCE, power), Block.UPDATE_CLIENTS);
        if (sound) {
            int note = Math.floorMod(pos.getX() * 3 + pos.getY() * 5 + pos.getZ() * 7, SCALE.length);
            float pitch = (float) Math.pow(2, (SCALE[note] - 12) / 12.0);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.25f + power * 0.08f, pitch);
        }
        level.scheduleTick(pos, state.getBlock(), 4);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int r = state.getValue(RESONANCE);
        if (r <= 0) return;
        if (r >= 2) {
            for (Direction d : Direction.values()) resonate(level, pos.relative(d), r - 1, random.nextInt(4) == 0);
        }
        level.setBlock(pos, state.setValue(RESONANCE, r - 1), Block.UPDATE_CLIENTS);
        if (r > 1) level.scheduleTick(pos, this, 8);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && state.getValue(RESONANCE) == 0) resonate(level, pos, 3, true);
        super.stepOn(level, pos, state, entity);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) resonate(level, pos, 7, true);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) resonate(level, pos, 6, true);
    }
}
