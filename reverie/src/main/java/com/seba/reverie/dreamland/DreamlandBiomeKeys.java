package com.seba.reverie.dreamland;

import com.seba.reverie.Reverie;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public final class DreamlandBiomeKeys {
    public static final ResourceKey<Biome> VOID_BETWEEN = key("the_void_between");

    private DreamlandBiomeKeys() {
    }

    static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, Reverie.id(name));
    }
}
