package com.seba.reverie.dreamland;

import com.seba.reverie.Reverie;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public final class DreamlandBiomeKeys {
    public static final ResourceKey<Biome> PILLOW_FIELDS = key("pillow_fields");
    public static final ResourceKey<Biome> HOLLOW_SPIRES = key("hollow_spires");
    public static final ResourceKey<Biome> MIRROR_MARSH = key("mirror_marsh");
    public static final ResourceKey<Biome> SANDGLASS_DUNES = key("sandglass_dunes");
    public static final ResourceKey<Biome> EYE_MEADOW = key("eye_meadow");
    public static final ResourceKey<Biome> UPSIDE_DOWN_TOWN = key("upside_down_town");
    public static final ResourceKey<Biome> CLOUDREACH = key("cloudreach");
    public static final ResourceKey<Biome> VOID_BETWEEN = key("the_void_between");

    // Fog colours, one per biome and all different: client/dreamland/DreamColors tells biomes apart by them.
    public static final int FOG_PILLOW = 0x8A93A3;
    public static final int FOG_SPIRES = 0x55525F;
    public static final int FOG_MARSH = 0x648A84;
    public static final int FOG_DUNES = 0xA8977A;
    public static final int FOG_EYE = 0x8A6A7C;
    public static final int FOG_TOWN = 0x6E6A78;
    public static final int FOG_CLOUD = 0xA4ACBC;
    public static final int FOG_VOID = 0x2E2A48;

    /** In {@link IslandField.Kind} order. */
    public static final java.util.List<ResourceKey<Biome>> ALL = java.util.List.of(PILLOW_FIELDS, HOLLOW_SPIRES, MIRROR_MARSH,
            SANDGLASS_DUNES, EYE_MEADOW, UPSIDE_DOWN_TOWN, CLOUDREACH, VOID_BETWEEN);

    private DreamlandBiomeKeys() {
    }

    static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, Reverie.id(name));
    }
}
