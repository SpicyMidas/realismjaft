package com.seba.reverie.compat.fd;

import com.seba.reverie.Reverie;

import net.neoforged.bus.api.IEventBus;

/** Loaded only when Farmer's Delight is present (see Reverie constructor). */
public final class FdCompat {
    private FdCompat() {
    }

    public static void init(IEventBus modBus) {
        Reverie.LOGGER.info("Farmer's Delight detected: enabling dreamy dishes");
    }
}
