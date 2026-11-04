package com.seba.reverie.compat;

import net.neoforged.fml.ModList;

/** The only place that asks whether optional mods are present. */
public final class Compat {
    public static final String CREATE = "create";
    public static final String FARMERS_DELIGHT = "farmersdelight";

    private Compat() {
    }

    public static boolean create() {
        return isLoaded(CREATE);
    }

    public static boolean farmersDelight() {
        return isLoaded(FARMERS_DELIGHT);
    }

    private static boolean isLoaded(String id) {
        ModList list = ModList.get();
        return list != null && list.isLoaded(id);
    }
}
