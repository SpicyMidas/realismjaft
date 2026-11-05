package com.seba.reverie.compat.fd;

import com.seba.reverie.Reverie;
import com.seba.reverie.registry.ModCreativeTab;

import net.neoforged.bus.api.IEventBus;

/**
 * Loaded only when Farmer's Delight is present (see Reverie constructor). Melatonia on Rich Soil Farmland needs no
 * code: FD's farmland sustains every CropBlock and boosts any BonemealableBlock above it.
 */
public final class FdCompat {
    private FdCompat() {
    }

    public static void init(IEventBus modBus) {
        FdContent.BLOCKS.register(modBus);
        FdContent.ITEMS.register(modBus);
        for (var item : FdContent.TAB) ModCreativeTab.add(item);
        Reverie.LOGGER.info("Farmer's Delight detected: enabling dreamy dishes");
    }
}
