package com.seba.reverie.content;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A Floating Lantern hangs on nothing: used toward empty air, it is set down where you point, a little ahead. */
public class FloatingLanternItem extends BlockItem {
    private static final double REACH = 3.0;

    public FloatingLanternItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(REACH));
        // Something solid in the way: that is a normal placement (handled by useOn), or nothing at all.
        if (level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)).getType() != HitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        BlockPos pos = BlockPos.containing(end);
        if (!level.getBlockState(pos).canBeReplaced() || !level.isInWorldBounds(pos) || !player.mayBuild()) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            var state = getBlock().defaultBlockState();
            if (!level.setBlock(pos, state, Block.UPDATE_ALL)) return InteractionResultHolder.fail(stack);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
            level.playSound(null, pos, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 1f, 1.2f);
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
