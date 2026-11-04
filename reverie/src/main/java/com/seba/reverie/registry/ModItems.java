package com.seba.reverie.registry;

import com.seba.reverie.Reverie;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Reverie.MOD_ID);

    public static final DeferredItem<Item> TUFF_ALLOY = ITEMS.registerSimpleItem("tuff_alloy", new Item.Properties());

    private ModItems() {
    }
}
