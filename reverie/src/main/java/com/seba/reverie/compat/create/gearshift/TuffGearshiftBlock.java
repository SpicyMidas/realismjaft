package com.seba.reverie.compat.create.gearshift;

import com.seba.reverie.compat.create.CreateContent;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.AbstractEncasedShaftBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.TickPriority;

/**
 * Multi-ratio gearbox modelled on Create's Gearshift: an encased split shaft whose output side is multiplied by
 * the ratio of the effective gear. {@link #GEAR} is the effective gear (0 = N, 1-6, 7 = R) and drives the gear
 * window model; the remembered player selection lives in the block entity.
 */
public class TuffGearshiftBlock extends AbstractEncasedShaftBlock implements IBE<TuffGearshiftBlockEntity> {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty GEAR = IntegerProperty.create("gear", 0, 7);

    /** Set by client setup; opens the gear-stick screen. No-op on a dedicated server. */
    public static volatile java.util.function.Consumer<BlockPos> screenOpener = pos -> { };

    public final GateLayout layout;

    public TuffGearshiftBlock(Properties properties, GateLayout layout) {
        super(properties);
        this.layout = layout;
        registerDefaultState(defaultBlockState().setValue(POWERED, false).setValue(GEAR, GateLayout.N));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED, GEAR);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return super.getStateForPlacement(context).setValue(POWERED, powered)
                .setValue(GEAR, powered ? GateLayout.R : GateLayout.N);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        // Let the wrench rotate / dismantle as usual.
        if (AllItems.WRENCH.isIn(stack)) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide) screenOpener.accept(pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) return;
        int selected = getBlockEntityOptional(level, pos).map(TuffGearshiftBlockEntity::selectedGear).orElse(GateLayout.N);
        applyState(level, pos, state.setValue(POWERED, powered).setValue(GEAR, powered ? GateLayout.R : selected));
    }

    /**
     * Swap in a new state whose effective gear may differ: detach the kinetic network first and re-attach next
     * tick, exactly like Create's Gearshift/Clutch do, so speeds are re-propagated with the new ratio.
     */
    public void applyState(Level level, BlockPos pos, BlockState newState) {
        BlockState old = level.getBlockState(pos);
        if (old == newState || !old.is(this)) return;
        boolean ratioChanges = old.getValue(GEAR) != newState.getValue(GEAR);
        if (ratioChanges) detachKinetics(level, pos, true);
        level.setBlock(pos, newState, Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof TuffGearshiftBlockEntity be) be.onEffectiveGearChanged(newState.getValue(GEAR));
    }

    public void detachKinetics(Level level, BlockPos pos, boolean reattachNextTick) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof KineticBlockEntity kbe)) return;
        RotationPropagator.handleRemoved(level, pos, kbe);
        if (reattachNextTick) level.scheduleTick(pos, this, 1, TickPriority.EXTREMELY_HIGH);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof KineticBlockEntity kbe) RotationPropagator.handleAdded(level, pos, kbe);
    }

    @Override
    public Class<TuffGearshiftBlockEntity> getBlockEntityClass() {
        return TuffGearshiftBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TuffGearshiftBlockEntity> getBlockEntityType() {
        return CreateContent.GEARSHIFT_BE.get();
    }
}
