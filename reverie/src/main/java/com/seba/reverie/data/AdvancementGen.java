package com.seba.reverie.data;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.seba.reverie.Reverie;
import com.seba.reverie.compat.Compat;
import com.seba.reverie.registry.ModItems;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Reverie advancements. The dream ones are granted from code (DreamAdvancements); Gear Head needs Create. */
final class AdvancementGen {
    private AdvancementGen() {
    }

    static AdvancementProvider create(PackOutput out, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
        return new AdvancementProvider(out, lookup, files, List.of(AdvancementGen::generate));
    }

    private static Component title(String id) {
        return Component.translatable("advancements.reverie." + id + ".title");
    }

    private static Component desc(String id) {
        return Component.translatable("advancements.reverie." + id + ".description");
    }

    private static void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver, ExistingFileHelper files) {
        AdvancementHolder root = Advancement.Builder.advancement()
                .display(ModItems.MELATONIA_PETALS.get(), title("root"), desc("root"), Reverie.id("textures/block/lucid_stone.png"),
                        AdvancementType.TASK, false, false, false)
                .addCriterion("petals", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.MELATONIA_PETALS.get()))
                .save(saver, Reverie.id("root").toString());
        AdvancementHolder sheep = Advancement.Builder.advancement().parent(root)
                .display(ModItems.CONCENTRATED_MELATONIA_EXTRACT.get(), title("counting_sheep"), desc("counting_sheep"), null,
                        AdvancementType.TASK, true, true, false)
                .addCriterion("dreamed", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
                .save(saver, Reverie.id("counting_sheep").toString());
        Advancement.Builder.advancement().parent(sheep)
                .display(Items.MAGENTA_BED, title("wide_awake"), desc("wide_awake"), null, AdvancementType.GOAL, true, true, false)
                .addCriterion("woke_in_a_dream_bed", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
                .save(saver, Reverie.id("wide_awake").toString());
        Advancement.Builder.advancement().parent(sheep)
                .display(ModItems.LUCID_SHARD.get(), title("souvenir"), desc("souvenir"), null, AdvancementType.GOAL, true, true, false)
                .addCriterion("kept_something", CriteriaTriggers.IMPOSSIBLE.createCriterion(new ImpossibleTrigger.TriggerInstance()))
                .save(saver, Reverie.id("souvenir").toString());
    }

    /** Gear Head, as raw JSON with a Create mod_loaded condition (its blocks only exist with Create). */
    static void json(JsonGen gen) {
        JsonObject icon = new JsonObject();
        icon.addProperty("id", "reverie:tuff_gearshift");
        JsonObject display = new JsonObject();
        display.add("icon", icon);
        display.add("title", translatable("advancements.reverie.gear_head.title"));
        display.add("description", translatable("advancements.reverie.gear_head.description"));
        display.addProperty("frame", "task");
        JsonObject blocks = new JsonObject();
        blocks.add("blocks", JsonGen.array(new com.google.gson.JsonPrimitive("reverie:tuff_gearshift"),
                new com.google.gson.JsonPrimitive("reverie:sport_tuff_gearshift")));
        JsonObject predicate = new JsonObject();
        predicate.add("block", blocks);
        JsonObject check = new JsonObject();
        check.addProperty("condition", "minecraft:location_check");
        check.add("predicate", predicate);
        JsonObject conditions = new JsonObject();
        conditions.add("location", JsonGen.array(check));
        JsonObject placed = new JsonObject();
        placed.addProperty("trigger", "minecraft:placed_block");
        placed.add("conditions", conditions);
        JsonObject criteria = new JsonObject();
        criteria.add("placed", placed);
        JsonObject adv = new JsonObject();
        adv.addProperty("parent", "reverie:root");
        adv.add("display", display);
        adv.add("criteria", criteria);
        adv.add("requirements", JsonGen.array(JsonGen.array(new com.google.gson.JsonPrimitive("placed"))));
        gen.put("data/reverie/advancement/gear_head.json", adv, Compat.CREATE);
    }

    /** Master Chef: eat every Reverie dish (needs Farmer's Delight, which adds most of them). */
    static void masterChef(JsonGen gen) {
        String[] dishes = {"melatonia_cookie", "drowsy_tea", "sleepy_stew", "lullaby_stew", "dozing_dumplings", "dreamy_pie_slice",
                "night_bloom_roll", "melatonia_syrup"};
        JsonObject criteria = new JsonObject();
        com.google.gson.JsonArray requirements = new com.google.gson.JsonArray();
        for (String dish : dishes) {
            JsonObject item = new JsonObject();
            item.addProperty("items", "reverie:" + dish);
            JsonObject conditions = new JsonObject();
            conditions.add("item", item);
            JsonObject c = new JsonObject();
            c.addProperty("trigger", "minecraft:consume_item");
            c.add("conditions", conditions);
            criteria.add(dish, c);
            requirements.add(JsonGen.array(new com.google.gson.JsonPrimitive(dish)));
        }
        JsonObject icon = new JsonObject();
        icon.addProperty("id", "reverie:lullaby_stew");
        JsonObject display = new JsonObject();
        display.add("icon", icon);
        display.add("title", translatable("advancements.reverie.master_chef.title"));
        display.add("description", translatable("advancements.reverie.master_chef.description"));
        display.addProperty("frame", "challenge");
        JsonObject adv = new JsonObject();
        adv.addProperty("parent", "reverie:root");
        adv.add("display", display);
        adv.add("criteria", criteria);
        adv.add("requirements", requirements);
        gen.put("data/reverie/advancement/master_chef.json", adv, Compat.FARMERS_DELIGHT);
    }

    private static JsonObject translatable(String key) {
        JsonObject o = new JsonObject();
        o.addProperty("translate", key);
        return o;
    }
}
