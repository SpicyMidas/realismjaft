package com.seba.reverie.data;

import com.google.gson.JsonObject;

/** Hand-written JSON for recipe types without a vanilla builder. */
final class CoreJson {
    private CoreJson() {
    }

    /** Entity loot: Dream Dust from the dream fauna. */
    private static com.google.gson.JsonObject dustLoot(String id, double min, double max) {
        com.google.gson.JsonObject count = new com.google.gson.JsonObject();
        count.addProperty("type", "minecraft:uniform");
        count.addProperty("min", min);
        count.addProperty("max", max);
        com.google.gson.JsonObject setCount = new com.google.gson.JsonObject();
        setCount.addProperty("function", "minecraft:set_count");
        setCount.add("count", count);
        com.google.gson.JsonObject entry = new com.google.gson.JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", "reverie:dream_dust");
        entry.add("functions", JsonGen.array(setCount));
        com.google.gson.JsonObject pool = new com.google.gson.JsonObject();
        pool.addProperty("rolls", 1.0);
        pool.addProperty("bonus_rolls", 0.0);
        pool.add("entries", JsonGen.array(entry));
        com.google.gson.JsonObject table = new com.google.gson.JsonObject();
        table.addProperty("type", "minecraft:entity");
        table.add("pools", JsonGen.array(pool));
        table.addProperty("random_sequence", "reverie:entities/" + id);
        return table;
    }

    static void json(JsonGen gen) {
        gen.put("data/reverie/loot_table/entities/sleepwalking_sheep.json", dustLoot("sleepwalking_sheep", 1, 2));
        gen.put("data/reverie/loot_table/entities/lucid_moth.json", dustLoot("lucid_moth", 0, 1));
        JsonObject concentrated = new JsonObject();
        concentrated.addProperty("type", "reverie:bottle_returning_shapeless");
        concentrated.addProperty("category", "misc");
        concentrated.add("ingredients", JsonGen.array(JsonGen.item("reverie:melatonia_extract"), JsonGen.item("reverie:melatonia_extract"),
                JsonGen.item("reverie:melatonia_extract"), JsonGen.tag("c:dusts/glowstone")));
        concentrated.add("result", JsonGen.result("reverie:concentrated_melatonia_extract", 1));
        concentrated.addProperty("returned_bottles", 2);
        gen.put("data/reverie/recipe/concentrated_melatonia_extract.json", concentrated);
        // Golden String: 1% from a spider or cave spider a player kills inside Trial Chambers.
        JsonObject spider = new JsonObject();
        spider.addProperty("condition", "minecraft:entity_properties");
        spider.addProperty("entity", "this");
        JsonObject predicate = new JsonObject();
        predicate.add("type", JsonGen.array(new com.google.gson.JsonPrimitive("minecraft:spider"), new com.google.gson.JsonPrimitive("minecraft:cave_spider")));
        JsonObject where = new JsonObject();
        where.addProperty("structures", "minecraft:trial_chambers");
        predicate.add("location", where);
        spider.add("predicate", predicate);
        JsonObject byPlayer = new JsonObject();
        byPlayer.addProperty("condition", "minecraft:killed_by_player");
        JsonObject chance = new JsonObject();
        chance.addProperty("condition", "minecraft:random_chance");
        chance.addProperty("chance", 0.01);
        JsonObject modifier = new JsonObject();
        modifier.addProperty("type", "neoforge:add_table");
        modifier.add("conditions", JsonGen.array(spider, byPlayer, chance));
        modifier.addProperty("table", "reverie:gameplay/golden_string");
        gen.put("data/reverie/loot_modifiers/golden_string_from_trial_spiders.json", modifier);
        JsonObject glm = new JsonObject();
        glm.addProperty("replace", false);
        glm.add("entries", JsonGen.array(new com.google.gson.JsonPrimitive("reverie:golden_string_from_trial_spiders")));
        gen.put("data/neoforge/loot_modifiers/global_loot_modifiers.json", glm);
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", "reverie:golden_string");
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1.0);
        pool.add("entries", JsonGen.array(entry));
        JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:generic");
        table.add("pools", JsonGen.array(pool));
        gen.put("data/reverie/loot_table/gameplay/golden_string.json", table);
        // Eight snowballs or arrows around one Extract: eight laced ones, the bottle comes back.
        for (String ammo : new String[]{"snowball", "arrow"}) {
            for (int level = 1; level <= 2; level++) {
                JsonObject recipe = new JsonObject();
                recipe.addProperty("type", "reverie:bottle_returning_shapeless");
                recipe.addProperty("category", "equipment");
                com.google.gson.JsonArray ingredients = new com.google.gson.JsonArray();
                for (int i = 0; i < 8; i++) ingredients.add(JsonGen.item("minecraft:" + ammo));
                ingredients.add(JsonGen.item(level == 2 ? "reverie:concentrated_melatonia_extract" : "reverie:melatonia_extract"));
                recipe.add("ingredients", ingredients);
                JsonObject result = JsonGen.result("reverie:laced_" + ammo, 8);
                JsonObject laced = new JsonObject();
                laced.addProperty("level", level);
                JsonObject components = new JsonObject();
                components.add("reverie:laced", laced);
                result.add("components", components);
                recipe.add("result", result);
                recipe.addProperty("returned_bottles", 1);
                gen.put("data/reverie/recipe/laced_" + ammo + (level == 2 ? "_concentrated" : "") + ".json", recipe);
            }
        }
    }
}
