package com.seba.reverie.dreamland.block;

import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.registry.ModBlockEntities;
import com.seba.reverie.registry.ModBlocks;
import com.seba.reverie.registry.ModEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server tick for the Sleep Bell (rings near dreamers) and the Sandglass Core (time bubble, Drift). */
public class ResonatorBlockEntity extends BlockEntity {
    public static final double BUBBLE_RADIUS = 12.0;
    private int cooldown;

    public ResonatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESONATOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ResonatorBlockEntity be) {
        if (!(level instanceof ServerLevel server) || (level.getGameTime() + pos.asLong()) % 10 != 0) return;
        if (be.cooldown > 0) be.cooldown -= 10;
        if (state.is(ModBlocks.SLEEP_BELL.get())) {
            if (be.cooldown > 0) return;
            for (ServerPlayer p : server.players()) {
                if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 36 && DreamManager.isDreaming(p)) {
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.25f,
                            0.5f + level.random.nextFloat() * 0.15f);
                    be.cooldown = 120 + level.random.nextInt(120);
                    break;
                }
            }
        } else if (state.is(ModBlocks.SANDGLASS_CORE.get()) && com.seba.reverie.ReverieConfig.get(com.seba.reverie.ReverieConfig.TIME_BUBBLES)) {
            double r2 = BUBBLE_RADIUS * BUBBLE_RADIUS;
            for (ServerPlayer p : server.players()) {
                if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < r2) {
                    p.addEffect(new MobEffectInstance(ModEffects.DRIFT, 30, 0, true, false, true));
                }
            }
        }
    }
}
