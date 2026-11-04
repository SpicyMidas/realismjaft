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

        // Create: gearshifts
        s.add("block.reverie.tuff_gearshift", "Tuff Gearshift", "Cambio di Tufo");
        s.add("block.reverie.sport_tuff_gearshift", "Sport Tuff Gearshift", "Cambio Sportivo di Tufo");
        s.add("reverie.gearshift.goggles.gear", "Gear: %s (%s)", "Marcia: %s (%s)");
        s.add("reverie.gearshift.goggles.speed", "In %s RPM → Out %s RPM", "Entrata %s RPM → Uscita %s RPM");
        s.add("reverie.gearshift.goggles.locked", "Locked in R by redstone", "Bloccato in R dalla pietrarossa");
        s.add("reverie.gearshift.goggles.selected", "Remembered gear: %s", "Marcia memorizzata: %s");
        s.add("reverie.gearshift.screen.title", "Gear Stick", "Leva del cambio");
        s.add("reverie.gearshift.screen.in", "IN", "ENTRA");
        s.add("reverie.gearshift.screen.out", "OUT", "ESCE");
        s.add("reverie.gearshift.screen.rpm", "rpm", "giri/min");
        s.add("reverie.gearshift.screen.redstone", "REDSTONE", "PIETRAROSSA");
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
