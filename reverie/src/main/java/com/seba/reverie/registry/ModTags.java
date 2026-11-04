package com.seba.reverie.registry;

import com.seba.reverie.Reverie;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    private ModTags() {
    }

    public static final class Biomes {
        public static final TagKey<net.minecraft.world.level.biome.Biome> HAS_WILD_MELATONIA =
                TagKey.create(Registries.BIOME, Reverie.id("has_wild_melatonia"));

        private Biomes() {
        }
    }

    public static final class Items {
        public static final TagKey<Item> INGOTS_TUFF_ALLOY = common("ingots/tuff_alloy");
        public static final TagKey<Item> INGOTS_BRASS = common("ingots/brass");
        public static final TagKey<Item> LACING_AGENTS = mod("lacing_agents");
        public static final TagKey<Item> LUCID_KEEPSAKES = mod("lucid_keepsakes");

        private static TagKey<Item> common(String path) {
            return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
        }

        static TagKey<Item> mod(String path) {
            return TagKey.create(Registries.ITEM, Reverie.id(path));
        }

        private Items() {
        }
    }

    public static final class Blocks {
        public static final TagKey<Block> MELATONIA_PLANTABLE = mod("melatonia_plantable");
        public static final TagKey<Block> WILD_MELATONIA_PLACEABLE = mod("wild_melatonia_placeable");

        static TagKey<Block> mod(String path) {
            return TagKey.create(Registries.BLOCK, Reverie.id(path));
        }

        private Blocks() {
        }
    }
}
