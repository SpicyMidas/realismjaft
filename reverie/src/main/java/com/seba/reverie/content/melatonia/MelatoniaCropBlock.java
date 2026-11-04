package com.seba.reverie.content.melatonia;

import com.mojang.serialization.MapCodec;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.registry.ModItems;
import com.seba.reverie.registry.ModTags;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * Four-stage night-blooming crop. Grows faster at night or in the dark, slower in full daylight, and never
 * needs light to grow at all (unlike vanilla crops).
 */
public class MelatoniaCropBlock extends CropBlock {
    public static final MapCodec<MelatoniaCropBlock> CODEC = simpleCodec(MelatoniaCropBlock::new);
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    private static final VoxelShape[] SHAPES = {
            Block.box(0, 0, 0, 16, 3, 16), Block.box(0, 0, 0, 16, 6, 16),
            Block.box(0, 0, 0, 16, 10, 16), Block.box(0, 0, 0, 16, 13, 16)};

    public MelatoniaCropBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<MelatoniaCropBlock> codec() {
        return CODEC;
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return MAX_AGE;
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModItems.MELATONIA_SEEDS.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[Math.min(getAge(state), MAX_AGE)];
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(ModTags.Blocks.MELATONIA_PLANTABLE);
    }

    @Override
    protected int getBonemealAgeIncrease(Level level) {
        return 1 + (level.random.nextInt(3) == 0 ? 1 : 0);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1)) return;
        int age = getAge(state);
        if (age >= getMaxAge()) return;
        float speed = getGrowthSpeed(state, level, pos) * nightMultiplier(level, pos);
        boolean roll = random.nextInt((int) (25.0F / speed) + 1) == 0;
        if (CommonHooks.canCropGrow(level, pos, state, roll)) {
            level.setBlock(pos, getStateForAge(age + 1), Block.UPDATE_CLIENTS);
            CommonHooks.fireCropGrowPost(level, pos, state);
        }
    }

    /** x2.5 at night or when dark (light <= 7), x0.5 in full daylight, x1 otherwise. */
    public static float nightMultiplier(Level level, BlockPos pos) {
        if (!ReverieConfig.get(ReverieConfig.NIGHT_BLOOMER_GROWTH_BOOST)) return 1f;
        int light = level.getMaxLocalRawBrightness(pos);
        boolean night = level.dimensionType().hasSkyLight() && !level.isDay();
        if (night || light <= 7) return 2.5f;
        if (level.isDay() && level.canSeeSky(pos) && light >= 14) return 0.5f;
        return 1f;
    }
}
