package com.seba.reverie.content.melatonia;

import java.util.function.Supplier;

import com.mojang.serialization.MapCodec;
import com.seba.reverie.registry.ModEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Melatonia Cake, placed like a Farmer's Delight pie: four slices. Right-click eats one; a knife (c:tools/knife,
 * e.g. Farmer's Delight's) cuts one off as an item instead. The quarters go in Farmer's Delight's order: south-east,
 * then the rest of the south half, then the north-west, leaving the north-east quarter last (relative to facing).
 */
public class MelatoniaCakeBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MelatoniaCakeBlock> CODEC = simpleCodec(p -> new MelatoniaCakeBlock(p, () -> net.minecraft.world.item.Items.AIR));
    public static final IntegerProperty BITES = IntegerProperty.create("bites", 0, 3);
    public static final TagKey<Item> KNIVES = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/knife"));
    public static final int NUTRITION = 3;
    public static final float SATURATION = 0.25f;
    /** Dizziness from one slice, in ticks. */
    public static final int DIZZY_TICKS = 300;

    private static final VoxelShape[] SHAPES = new VoxelShape[4 * 4];

    static {
        // Quarters in model space (facing north): NW, NE, SE, SW; bites remove SE, then SW, then NW.
        double[][] quarters = {{1, 1, 8, 8}, {8, 1, 15, 8}, {8, 8, 15, 15}, {1, 8, 8, 15}};
        int[][] left = {{0, 1, 2, 3}, {0, 1, 3}, {0, 1}, {1}};
        Direction[] dirs = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int f = 0; f < 4; f++) {
            for (int bites = 0; bites < 4; bites++) {
                VoxelShape shape = Shapes.empty();
                for (int q : left[bites]) {
                    double[] r = rotate(quarters[q], f);
                    shape = Shapes.or(shape, Block.box(r[0], 0, r[1], r[2], 8, r[3]));
                }
                SHAPES[dirs[f].get2DDataValue() * 4 + bites] = shape;
            }
        }
    }

    /** Rotates an x/z rectangle (pixels) by {@code quarterTurns} clockwise seen from above (north -> east). */
    private static double[] rotate(double[] r, int quarterTurns) {
        double x0 = r[0], z0 = r[1], x1 = r[2], z1 = r[3];
        for (int i = 0; i < quarterTurns; i++) {
            double nx0 = 16 - z1, nx1 = 16 - z0, nz0 = x0, nz1 = x1;
            x0 = nx0;
            x1 = nx1;
            z0 = nz0;
            z1 = nz1;
        }
        return new double[]{x0, z0, x1, z1};
    }

    private final Supplier<Item> slice;

    public MelatoniaCakeBlock(Properties properties, Supplier<Item> slice) {
        super(properties);
        this.slice = slice;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BITES, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue() * 4 + state.getValue(BITES)];
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(KNIVES)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            Block.popResourceFromFace(level, pos, hit.getDirection(), new ItemStack(slice.get()));
            level.playSound(null, pos, SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);
            takeSlice(level, pos, state, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.canEat(false)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            player.awardStat(Stats.EAT_CAKE_SLICE);
            player.getFoodData().eat(NUTRITION, SATURATION);
            sliceEffects(player);
            level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8f, 0.8f + level.random.nextFloat() * 0.4f);
            level.gameEvent(player, GameEvent.EAT, pos);
            takeSlice(level, pos, state, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** What one slice does, whether eaten off the block or as a slice item. */
    public static void sliceEffects(net.minecraft.world.entity.LivingEntity eater) {
        eater.addEffect(new MobEffectInstance(ModEffects.DIZZINESS, DIZZY_TICKS));
    }

    private static void takeSlice(LevelAccessor level, BlockPos pos, BlockState state, Player player) {
        int bites = state.getValue(BITES);
        if (bites < 3) {
            level.setBlock(pos, state.setValue(BITES, bites + 1), Block.UPDATE_ALL);
        } else {
            level.removeBlock(pos, false);
            level.gameEvent(player, GameEvent.BLOCK_DESTROY, pos);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level,
                                     BlockPos pos, BlockPos facingPos) {
        return facing == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, facing, facingState, level, pos, facingPos);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isSolid();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BITES);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return (4 - state.getValue(BITES)) * 3;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
