package com.seba.reverie.compat.create.remote;

import com.seba.reverie.compat.create.CreateContent;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Frequency menu: two ghost slots like Create's Linked Controller menu (clicking copies one item in, never
 * moves real items). The remote in the main hand cannot be moved while the menu is open.
 */
public class LatchRemoteMenu extends AbstractContainerMenu {
    public static final int GHOST_X = 62;
    public static final int GHOST_Y = 30;
    public static final int GHOST_SPACING = 36;

    private final Inventory playerInventory;
    private final ItemStack remote;
    final SimpleContainer ghosts = new SimpleContainer(2);

    public LatchRemoteMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, ItemStack.STREAM_CODEC.decode(buf));
    }

    public LatchRemoteMenu(int id, Inventory inv, ItemStack remote) {
        super(CreateContent.LATCH_REMOTE_MENU.get(), id);
        this.playerInventory = inv;
        this.remote = remote;
        var items = LatchRemoteItem.frequencyItems(remote);
        ghosts.setItem(0, items.get(0));
        ghosts.setItem(1, items.get(1));
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 142));
        for (int i = 0; i < 2; i++) {
            addSlot(new Slot(ghosts, i, GHOST_X + i * GHOST_SPACING, GHOST_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }
    }

    private boolean isRemoteSlot(int index) {
        // Hotbar slots are menu indices 27..35.
        return index >= 27 && index < 36 && index - 27 == playerInventory.selected;
    }

    @Override
    public void clicked(int index, int button, ClickType type, Player player) {
        if (index >= 36 && index < 38) {
            if (type == ClickType.THROW || type == ClickType.CLONE) return;
            ItemStack carried = getCarried();
            ghosts.setItem(index - 36, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            broadcastChanges();
            return;
        }
        if (isRemoteSlot(index)) return;
        if (type == ClickType.SWAP && button == playerInventory.selected) return;
        super.clicked(index, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 36) {
            ItemStack stack = slots.get(index).getItem();
            for (int i = 0; i < 2; i++) {
                if (ghosts.getItem(i).isEmpty()) {
                    ghosts.setItem(i, stack.copyWithCount(1));
                    break;
                }
            }
        } else if (index < 38) {
            ghosts.setItem(index - 36, ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container == playerInventory && !isRemoteSlot(slot.index);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getMainHandItem() == remote && remote.getItem() instanceof LatchRemoteItem;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide) return;
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof LatchRemoteItem) {
            LatchRemoteItem.setFrequencyItems(held, ghosts.getItem(0), ghosts.getItem(1));
            if (player instanceof ServerPlayer sp) LatchRemoteServer.refresh(sp);
        }
    }
}
