package com.seba.reverie.dreamland.block;

import com.mojang.serialization.MapCodec;
import com.seba.reverie.registry.ModBlockEntities;
import com.seba.reverie.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/** Sleep Bell and Sandglass Core: decorative blocks with a small server behaviour. */
public class ResonatorBlock extends BaseEntityBlock {
    public static final MapCodec<ResonatorBlock> CODEC = simpleCodec(ResonatorBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);
    private static final DustParticleOptions SAND = new DustParticleOptions(new Vector3f(0.93f, 0.86f, 0.66f), 0.7f);

    public ResonatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResonatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.RESONATOR.get(), ResonatorBlockEntity::serverTick);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.is(ModBlocks.SANDGLASS_CORE.get())) return;
        // Time bubble: sand grains drifting *up* inside a sphere around the hourglass.
        for (int i = 0; i < 3; i++) {
            double r = random.nextDouble() * ResonatorBlockEntity.BUBBLE_RADIUS;
            double a = random.nextDouble() * Math.PI * 2;
            double b = (random.nextDouble() - 0.5) * Math.PI;
            double x = pos.getX() + 0.5 + r * Math.cos(a) * Math.cos(b);
            double y = pos.getY() + 0.5 + r * Math.sin(b);
            double z = pos.getZ() + 0.5 + r * Math.sin(a) * Math.cos(b);
            level.addParticle(SAND, x, y, z, 0, 0.8, 0);
        }
    }
}
