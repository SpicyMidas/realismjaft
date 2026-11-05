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

        // Melatonia
        s.add("block.reverie.melatonia", "Melatonia Bloom", "Fioritura di Melatonia");
        s.add("block.reverie.wild_melatonia", "Wild Melatonia", "Melatonia Selvatica");
        s.add("item.reverie.melatonia_seeds", "Melatonia Seeds", "Semi di Melatonia");
        s.add("item.reverie.melatonia_petals", "Melatonia Petals", "Petali di Melatonia");
        s.add("item.reverie.melatonia_extract", "Melatonia Extract", "Estratto di Melatonia");
        s.add("item.reverie.melatonia_extract.desc", "The room tilts, gently.", "La stanza si inclina, dolcemente.");
        s.add("item.reverie.concentrated_melatonia_extract", "Concentrated Melatonia Extract", "Estratto Concentrato di Melatonia");
        s.add("item.reverie.concentrated_melatonia_extract.desc", "Sleep comes whenever you ask it to.", "Il sonno arriva quando lo chiami.");
        s.add("item.reverie.melatonia_cookie", "Melatonia Cookie", "Biscotto alla Melatonia");
        s.add("item.reverie.melatonia_cookie.desc", "Sometimes the floor moves.", "A volte il pavimento si muove.");
        s.add("item.reverie.drowsy_tea", "Drowsy Tea", "Tisana Sonnolenta");
        s.add("item.reverie.drowsy_tea.desc", "Warm, and a little dizzy.", "Calda, e un po' vertiginosa.");
        s.add("item.reverie.sleepy_stew", "Sleepy Stew", "Stufato Assonnato");
        s.add("item.reverie.sleepy_stew.desc", "Heavy eyelids, full belly.", "Palpebre pesanti, pancia piena.");
        s.add("effect.reverie.dizziness", "Dizziness", "Vertigine");
        s.add("effect.reverie.sleepyness", "Sleepyness", "Sonnolenza");
        s.add("reverie.laced.normal", "Laced with Melatonia", "Corretto con Melatonia");
        s.add("reverie.laced.strong", "Strongly laced", "Fortemente corretto");

        // Dreams
        s.add("reverie.dream.backup_failed", "You cannot fall asleep: the dream backup could not be written. Tell the server admin.",
                "Non riesci ad addormentarti: il backup del sogno non e' stato scritto. Avvisa l'amministratore.");
        s.add("reverie.dream.keepsake", "You woke up holding %s.", "Ti sei svegliato stringendo %s.");
        s.add("reverie.dream.bed_not_dreaming", "This bed is only a dream.", "Questo letto e' solo un sogno.");
        s.add("reverie.command.not_dreaming", "%s is not dreaming.", "%s non sta sognando.");
        s.add("reverie.command.woke", "%s woke up.", "%s si e' svegliato.");
        s.add("reverie.command.dreaming", "%s is dreaming.", "%s sta sognando.");
        s.add("reverie.command.cannot_dream", "%s cannot dream right now.", "%s non puo' sognare ora.");
        s.add("reverie.command.home", "Dream home of %s: %s %s %s", "Casa onirica di %s: %s %s %s");
        s.add(entity(com.seba.reverie.registry.ModEntities.SHADE), "Shade", "Ombra");
        s.add("subtitles.reverie.caption.counting", "Someone is counting", "Qualcuno sta contando");
        s.add("subtitles.reverie.caption.door_far", "A door closes far away", "Una porta si chiude lontano");
        s.add("subtitles.reverie.caption.bed_creak", "A bed creaks", "Un letto scricchiola");
        s.add("subtitles.reverie.caption.footsteps_stop", "Footsteps stop", "Dei passi si fermano");
        s.add("subtitles.reverie.caption.folding", "Something is being folded", "Qualcosa viene piegato");
        s.add("subtitles.reverie.caption.clock_backwards", "A clock ticks backwards", "Un orologio ticchetta all'indietro");
        s.add("item.reverie.dream_catcher", "Dream Catcher", "Acchiappasogni");
        s.add(entity(com.seba.reverie.registry.ModEntities.SLEEPWALKING_SHEEP), "Sleepwalking Sheep", "Pecora Sonnambula");
        s.add(entity(com.seba.reverie.registry.ModEntities.LUCID_MOTH), "Lucid Moth", "Falena Lucida");
        s.add("item.reverie.sleepwalking_sheep_spawn_egg", "Sleepwalking Sheep Spawn Egg", "Uovo Generatore di Pecora Sonnambula");
        s.add("item.reverie.lucid_moth_spawn_egg", "Lucid Moth Spawn Egg", "Uovo Generatore di Falena Lucida");
        s.add("block.reverie.impossible_stairs", "Impossible Stairs", "Scale Impossibili");
        s.add("item.reverie.dream_catcher.desc", "Hold it as you fall asleep: one thing from the dream is likelier to stay.",
                "Tienilo in mano mentre ti addormenti: una cosa del sogno ha piu' probabilita' di restare.");
        s.add("advancements.reverie.master_chef.title", "Sweet Dreams, Chef", "Sogni d'Oro, Chef");
        s.add("advancements.reverie.master_chef.description", "Eat every Reverie dish.", "Mangia ogni piatto di Reverie.");
        s.add("reverie.recurring.dandelion", "The Same Dandelion", "Lo Stesso Dente di Leone");
        s.add("reverie.recurring.key", "A Brass Key", "Una Chiave d'Ottone");
        s.add("reverie.recurring.boat", "A Paper Boat", "Una Barchetta di Carta");
        s.add("reverie.recurring.lore", "You have seen this before.", "L'hai gia' visto.");
        s.add("advancements.reverie.root.title", "Reverie", "Reverie");
        s.add("advancements.reverie.root.description", "Pick a Melatonia petal. It only opens at night.", "Raccogli un petalo di Melatonia. Si apre solo di notte.");
        s.add("advancements.reverie.counting_sheep.title", "Counting Sheep", "Contare le Pecore");
        s.add("advancements.reverie.counting_sheep.description", "Fall asleep and dream.", "Addormentati e sogna.");
        s.add("advancements.reverie.wide_awake.title", "Wide Awake", "Sveglio");
        s.add("advancements.reverie.wide_awake.description", "Lie down in a bed inside a dream.", "Sdraiati in un letto dentro un sogno.");
        s.add("advancements.reverie.souvenir.title", "Souvenir", "Souvenir");
        s.add("advancements.reverie.souvenir.description", "Wake up still holding something from a dream.", "Svegliati stringendo ancora qualcosa di un sogno.");
        s.add("advancements.reverie.gear_head.title", "Gear Head", "Testa d'Ingranaggio");
        s.add("advancements.reverie.gear_head.description", "Place a Tuff Gearshift.", "Piazza un Cambio di Tufo.");
        // Farmer's Delight dishes (keys are harmless without FD)
        s.add("item.reverie.lullaby_stew", "Lullaby Stew", "Stufato Ninnananna");
        s.add("item.reverie.dozing_dumplings", "Dozing Dumplings", "Ravioli Sonnecchiosi");
        s.add("block.reverie.dreamy_pie", "Dreamy Pie", "Torta Sognante");
        s.add("item.reverie.dreamy_pie_slice", "Slice of Dreamy Pie", "Fetta di Torta Sognante");
        s.add("item.reverie.night_bloom_roll", "Night-Bloom Roll", "Involtino Fiordinotte");
        s.add("item.reverie.melatonia_syrup", "Melatonia Syrup", "Sciroppo di Melatonia");
        s.add("reverie.command.backup.count", "%s has %s dream backups (newest first):", "%s ha %s backup dei sogni (dal piu' recente):");
        s.add("reverie.command.backup.dreaming", "%s is dreaming; wake them first.", "%s sta sognando; prima sveglialo.");
        s.add("reverie.command.backup.none", "There is no backup number %s.", "Non esiste il backup numero %s.");
        s.add("reverie.command.backup.unreadable", "Backup %s could not be read.", "Il backup %s non si puo' leggere.");
        s.add("reverie.command.backup.restored", "Gave back %s stacks to %s.", "Restituiti %s stack a %s.");
        s.add("reverie.command.shades_cleared", "Removed %s shades.", "Rimosse %s ombre.");
        s.add("reverie.command.shades_count", "%s shades are watching.", "%s ombre stanno guardando.");
        s.add("reverie.command.unknown_event", "Unknown event: %s", "Evento sconosciuto: %s");
        s.add("biome.reverie.the_void_between", "The Void Between", "Il Vuoto Frammezzo");
        s.add("biome.reverie.pillow_fields", "Pillow Fields", "Campi di Cuscini");
        s.add("biome.reverie.hollow_spires", "Hollow Spires", "Guglie Cave");
        s.add("biome.reverie.mirror_marsh", "Mirror Marsh", "Palude Specchio");
        s.add("biome.reverie.sandglass_dunes", "Sandglass Dunes", "Dune di Clessidra");
        s.add("biome.reverie.eye_meadow", "Eye Meadow", "Prato degli Occhi");
        s.add("biome.reverie.upside_down_town", "Upside-Down Town", "Paese Capovolto");
        s.add("biome.reverie.cloudreach", "Cloudreach", "Cimanuvola");
        s.add("effect.reverie.drift", "Drift", "Deriva");
        s.add("block.reverie.dream_grass_block", "Dream Grass Block", "Blocco d'Erba Onirica");
        s.add("block.reverie.dream_soil", "Dream Soil", "Terra Onirica");
        s.add("block.reverie.lucid_stone", "Lucid Stone", "Pietra Lucida");
        s.add("block.reverie.cobbled_lucid_stone", "Cobbled Lucid Stone", "Pietrisco Lucido");
        s.add("block.reverie.lucid_stone_bricks", "Lucid Stone Bricks", "Mattoni di Pietra Lucida");
        s.add("block.reverie.dreamwood_log", "Dreamwood Log", "Tronco di Legnosogno");
        s.add("block.reverie.dreamwood_planks", "Dreamwood Planks", "Assi di Legnosogno");
        s.add("block.reverie.dreamwood_leaves", "Dreamwood Leaves", "Foglie di Legnosogno");
        s.add("block.reverie.lucid_crystal_ore", "Lucid Crystal Ore", "Minerale di Cristallo Lucido");
        s.add("block.reverie.lucid_crystal_cluster", "Lucid Crystal Cluster", "Grappolo di Cristallo Lucido");
        s.add("block.reverie.pillow_cloud", "Pillow Cloud", "Nuvola Cuscino");
        s.add("block.reverie.mirror_water", "Mirror Water", "Acqua Specchio");
        s.add("block.reverie.hourglass_sand", "Hourglass Sand", "Sabbia di Clessidra");
        s.add("block.reverie.floating_lantern", "Floating Lantern", "Lanterna Fluttuante");
        s.add("block.reverie.melted_clock", "Melted Clock", "Orologio Sciolto");
        s.add("block.reverie.eyebloom", "Eyebloom", "Fiordocchio");
        s.add("block.reverie.sleep_bell", "Sleep Bell", "Campana del Sonno");
        s.add("block.reverie.sandglass_core", "Sandglass Core", "Cuore di Clessidra");
        s.add("block.reverie.lone_door", "Lone Door", "Porta Solitaria");
        s.add("item.reverie.lucid_shard", "Lucid Shard", "Scheggia Lucida");
        s.add("item.reverie.dream_dust", "Dream Dust", "Polvere di Sogno");

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

        // Create: latch remote
        s.add("item.reverie.latch_remote", "Latch Remote", "Telecomando a Ritenuta");
        s.add("reverie.latch_remote.on", "Remote ON", "Telecomando ACCESO");
        s.add("reverie.latch_remote.off", "Remote OFF", "Telecomando SPENTO");
        s.add("reverie.latch_remote.copied", "Frequency copied from the Redstone Link", "Frequenza copiata dal Collegamento di Pietrarossa");
        s.add("reverie.latch_remote.tooltip.on", "Transmitting", "In trasmissione");
        s.add("reverie.latch_remote.tooltip.off", "Idle", "Inattivo");
        s.add("reverie.latch_remote.tooltip.frequency", "Frequency: %s + %s", "Frequenza: %s + %s");
        s.add("reverie.latch_remote.tooltip.hint", "Right-click: on/off. Sneak: set frequency", "Clic destro: acceso/spento. Accovacciato: frequenza");
        s.add("reverie.latch_remote.menu.hint", "Frequency pair", "Coppia di frequenza");
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
