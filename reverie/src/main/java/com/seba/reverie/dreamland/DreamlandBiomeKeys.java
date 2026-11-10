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
    public static final ResourceKey<Biome> FROSTLIGHT_TUNDRA = key("frostlight_tundra");
    public static final ResourceKey<Biome> SKYREEF = key("skyreef");
    public static final ResourceKey<Biome> LUCID_GEODES = key("lucid_geodes");
    public static final ResourceKey<Biome> THE_NURSERY = key("the_nursery");
    public static final ResourceKey<Biome> BLACKWOOD = key("blackwood");
    public static final ResourceKey<Biome> WAITING_ROOMS = key("the_waiting_rooms");
    public static final ResourceKey<Biome> THE_MAW = key("the_maw");
    public static final ResourceKey<Biome> SIGNAL_LOST = key("signal_lost");

    // Fog colours, one per biome and all different: client/dreamland/DreamColors tells biomes apart by them.
    public static final int FOG_PILLOW = 0x8A93A3;
    public static final int FOG_SPIRES = 0x55525F;
    public static final int FOG_MARSH = 0x648A84;
    public static final int FOG_DUNES = 0xA8977A;
    public static final int FOG_EYE = 0x8A6A7C;
    public static final int FOG_TOWN = 0x6E6A78;
    public static final int FOG_CLOUD = 0xA4ACBC;
    public static final int FOG_VOID = 0x2E2A48;
    public static final int FOG_FROST = 0x2C4A66;
    public static final int FOG_REEF = 0x2B7A86;
    public static final int FOG_GEODE = 0x5C3E7E;
    public static final int FOG_NURSERY = 0x3A3F66;
    public static final int FOG_BLACKWOOD = 0x2A1C1C;
    public static final int FOG_WAITING = 0x7A6E48;
    public static final int FOG_MAW = 0x4A0E16;
    public static final int FOG_SIGNAL = 0x3A3A40;

    /** In {@link IslandField.Kind} order. */
    public static final java.util.List<ResourceKey<Biome>> ALL = java.util.List.of(PILLOW_FIELDS, HOLLOW_SPIRES, MIRROR_MARSH,
            SANDGLASS_DUNES, EYE_MEADOW, UPSIDE_DOWN_TOWN, CLOUDREACH, VOID_BETWEEN, FROSTLIGHT_TUNDRA, SKYREEF, LUCID_GEODES,
            THE_NURSERY, BLACKWOOD, WAITING_ROOMS, THE_MAW, SIGNAL_LOST);

    public static ResourceKey<Biome> of(IslandField.Kind kind) {
        return ALL.get(kind.ordinal());
    }

    private DreamlandBiomeKeys() {
    }

    static ResourceKey<Biome> key(String name) {
        return ResourceKey.create(Registries.BIOME, Reverie.id(name));
    }
}
