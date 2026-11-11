package com.seba.reverie.dreamland.block;

import com.seba.reverie.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A bamboo post with a lit Sky Lantern on top, by your first arrival in the dream. Use it to take the lantern. */
public class LanternStandBlock extends Block {
    public static final BooleanProperty LANTERN = BooleanProperty.create("lantern");
    private static final VoxelShape POST = Block.box(7, 0, 7, 9, 9, 9);
    private static final VoxelShape WITH_LANTERN = Shapes.or(POST, Block.box(4, 9, 4, 12, 16, 12));

    public LanternStandBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LANTERN, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LANTERN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(LANTERN) ? WITH_LANTERN : POST;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return POST;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(LANTERN)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            ItemStack lantern = new ItemStack(ModItems.SKY_LANTERN.get());
            if (!player.getInventory().add(lantern)) player.drop(lantern, false);
            level.setBlock(pos, state.setValue(LANTERN, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LANTERN_BREAK, SoundSource.BLOCKS, 0.7f, 1.3f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
