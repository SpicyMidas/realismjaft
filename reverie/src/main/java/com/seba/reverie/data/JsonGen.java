package com.seba.reverie.data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * Writes raw JSON for data that has no convenient builder (other mods' recipe types, loot tables of optional
 * blocks) and stamps NeoForge load conditions on it so nothing errors when the optional mod is missing.
 */
public final class JsonGen implements DataProvider {
    private final PackOutput output;
    private final String name;
    private final Consumer<JsonGen> contents;
    private final Map<Path, JsonElement> files = new LinkedHashMap<>();

    public JsonGen(PackOutput output, String name, Consumer<JsonGen> contents) {
        this.output = output;
        this.name = name;
        this.contents = contents;
    }

    /** {@code path} relative to the pack root, e.g. "data/reverie/recipe/x.json". */
    public void put(String path, JsonObject json, String... requiredMods) {
        if (requiredMods.length > 0) json.add("neoforge:conditions", modLoaded(requiredMods));
        files.put(output.getOutputFolder().resolve(path), json);
    }

    public static JsonArray modLoaded(String... mods) {
        JsonArray conditions = new JsonArray();
        for (String mod : mods) {
            JsonObject c = new JsonObject();
            c.addProperty("type", "neoforge:mod_loaded");
            c.addProperty("modid", mod);
            conditions.add(c);
        }
        return conditions;
    }

    public static JsonObject item(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("item", id);
        return o;
    }

    public static JsonObject tag(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("tag", id);
        return o;
    }

    public static JsonObject result(String id, int count) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        if (count != 1) o.addProperty("count", count);
        return o;
    }

    public static JsonArray array(JsonElement... elements) {
        JsonArray a = new JsonArray();
        for (JsonElement e : elements) a.add(e);
        return a;
    }

    /** Loot table that drops the block itself if it survives the explosion. */
    public static JsonObject dropSelf(String blockId) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", blockId);
        JsonObject cond = new JsonObject();
        cond.addProperty("condition", "minecraft:survives_explosion");
        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1.0);
        pool.addProperty("bonus_rolls", 0.0);
        pool.add("entries", array(entry));
        pool.add("conditions", array(cond));
        JsonObject table = new JsonObject();
        table.addProperty("type", "minecraft:block");
        table.add("pools", array(pool));
        table.addProperty("random_sequence", blockId.replaceFirst(":", ":blocks/"));
        return table;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        files.clear();
        contents.accept(this);
        List<CompletableFuture<?>> futures = new ArrayList<>();
        files.forEach((path, json) -> futures.add(DataProvider.saveStable(cache, json, path)));
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Reverie JSON: " + name;
    }
}
