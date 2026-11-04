package com.seba.reverie.data;

import java.util.function.Supplier;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/** One table for both locales so en_us and it_it can never drift apart. */
final class Lang {
    interface Sink {
        void add(String key, String en, String it);
    }

    private Lang() {
    }

    static void all(Sink s) {
        s.add("itemGroup.reverie", "Reverie", "Reverie");
        s.add("item.reverie.tuff_alloy", "Tuff Alloy", "Lega di Tufo");
    }

    static String item(Supplier<? extends ItemLike> item) {
        return item.get().asItem().getDescriptionId();
    }

    static String block(Supplier<? extends Block> block) {
        return block.get().getDescriptionId();
    }

    static String effect(Supplier<? extends MobEffect> effect) {
        return effect.get().getDescriptionId();
    }

    static String entity(Supplier<? extends EntityType<?>> type) {
        return type.get().getDescriptionId();
    }
}
