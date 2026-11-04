package com.seba.reverie.compat.create;

import com.seba.reverie.Reverie;
import com.seba.reverie.registry.ModCreativeTab;

import net.neoforged.bus.api.IEventBus;

/** Loaded only when Create is present (see Reverie constructor). */
public final class CreateCompat {
    private CreateCompat() {
    }

    public static void init(IEventBus modBus) {
        Reverie.LOGGER.info("Create detected: enabling Tuff Gearshifts and the Latch Remote");
        CreateContent.register(modBus);
        modBus.addListener(CreateNetwork::register);
        ModCreativeTab.add(CreateContent.TUFF_GEARSHIFT_ITEM);
        ModCreativeTab.add(CreateContent.SPORT_TUFF_GEARSHIFT_ITEM);
    }
}
