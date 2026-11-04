package com.seba.reverie.compat.create.remote;

import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class LatchRemoteEvents {
    private LatchRemoteEvents() {
    }

    /**
     * Create's link handler would otherwise take a plain right-click on a link's frequency slot and set the remote
     * itself as that link's frequency. A Latch Remote in hand toggles instead (sneak-click copies, see the item).
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!(held.getItem() instanceof LatchRemoteItem) || event.getEntity().isShiftKeyDown()) return;
        if (BlockEntityBehaviour.get(event.getLevel(), event.getPos(), LinkBehaviour.TYPE) == null) return;
        if (!event.getLevel().isClientSide) LatchRemoteItem.toggle(held, event.getEntity());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
