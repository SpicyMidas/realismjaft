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

    public static final class Items {
        public static final TagKey<Item> INGOTS_TUFF_ALLOY = common("ingots/tuff_alloy");
        public static final TagKey<Item> INGOTS_BRASS = common("ingots/brass");

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
        static TagKey<Block> mod(String path) {
            return TagKey.create(Registries.BLOCK, Reverie.id(path));
        }

        private Blocks() {
        }
    }
}
