package com.seba.reverie.compat.create.remote;

import java.util.List;
import java.util.UUID;

import com.seba.reverie.compat.create.CreateContent;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Couple;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Like Create's Linked Controller, but latching: one click starts transmitting on its single frequency pair,
 * the next click stops. Transmission itself is handled by {@link LatchRemoteServer} while the remote is carried.
 */
public class LatchRemoteItem extends Item {
    public LatchRemoteItem(Properties properties) {
        super(properties);
    }

    public static boolean isActive(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(CreateContent.REMOTE_ACTIVE.get()));
    }

    public static UUID remoteId(ItemStack stack) {
        return stack.get(CreateContent.REMOTE_ID.get());
    }

    public static UUID assignNewId(ItemStack stack) {
        UUID id = UUID.randomUUID();
        stack.set(CreateContent.REMOTE_ID.get(), id);
        return id;
    }

    public static NonNullList<ItemStack> frequencyItems(ItemStack stack) {
        NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
        ItemContainerContents contents = stack.get(CreateContent.REMOTE_FREQUENCY.get());
        if (contents != null) contents.copyInto(items);
        return items;
    }

    public static void setFrequencyItems(ItemStack stack, ItemStack first, ItemStack second) {
        stack.set(CreateContent.REMOTE_FREQUENCY.get(), ItemContainerContents.fromItems(List.of(first.copyWithCount(1), second.copyWithCount(1))));
    }

    public static Couple<Frequency> networkKey(ItemStack stack) {
        NonNullList<ItemStack> items = frequencyItems(stack);
        return Couple.create(Frequency.of(items.get(0)), Frequency.of(items.get(1)));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            // Sneak-click a Redstone Link: copy its frequency. Other blocks: let them handle the click.
            if (AllBlocks.REDSTONE_LINK.has(ctx.getLevel().getBlockState(ctx.getClickedPos()))) {
                if (!ctx.getLevel().isClientSide) copyFrequency(stack, player, ctx.getLevel(), ctx.getClickedPos());
                return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (ctx.getHand() != InteractionHand.MAIN_HAND && !player.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!ctx.getLevel().isClientSide) toggle(stack, player);
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                sp.openMenu(new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return stack.getHoverName();
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                        return new LatchRemoteMenu(id, inv, p.getMainHandItem());
                    }
                }, buf -> ItemStack.STREAM_CODEC.encode(buf, stack));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!level.isClientSide) toggle(stack, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void toggle(ItemStack stack, Player player) {
        boolean on = !isActive(stack);
        stack.set(CreateContent.REMOTE_ACTIVE.get(), on);
        if (on && remoteId(stack) == null) assignNewId(stack);
        player.displayClientMessage(Component.translatable(on ? "reverie.latch_remote.on" : "reverie.latch_remote.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.4f, on ? 0.75f : 0.55f);
        player.getCooldowns().addCooldown(stack.getItem(), 4);
        if (player instanceof ServerPlayer sp) LatchRemoteServer.refresh(sp);
    }

    private static void copyFrequency(ItemStack stack, Player player, Level level, BlockPos pos) {
        LinkBehaviour link = BlockEntityBehaviour.get(level, pos, LinkBehaviour.TYPE);
        if (link == null) return;
        Couple<Frequency> key = link.getNetworkKey();
        setFrequencyItems(stack, key.getFirst().getStack(), key.getSecond().getStack());
        player.displayClientMessage(Component.translatable("reverie.latch_remote.copied").withStyle(ChatFormatting.AQUA), true);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 0.5f, 1.2f);
        if (player instanceof ServerPlayer sp) LatchRemoteServer.refresh(sp);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        boolean on = isActive(stack);
        tooltip.add(Component.translatable(on ? "reverie.latch_remote.tooltip.on" : "reverie.latch_remote.tooltip.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        NonNullList<ItemStack> f = frequencyItems(stack);
        tooltip.add(Component.translatable("reverie.latch_remote.tooltip.frequency",
                f.get(0).isEmpty() ? Component.literal("-") : f.get(0).getHoverName(),
                f.get(1).isEmpty() ? Component.literal("-") : f.get(1).getHoverName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("reverie.latch_remote.tooltip.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
