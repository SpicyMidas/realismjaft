package com.seba.reverie.data;

import java.util.concurrent.CompletableFuture;

import com.seba.reverie.compat.Compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public final class ReverieDataGen {
    private ReverieDataGen() {
    }

    public static void gather(GatherDataEvent event) {
        if (!Compat.create() || !Compat.farmersDelight()) {
            // Recipes and models for optional content are generated from the real registries.
            throw new IllegalStateException("Run datagen with -Preverie.compat=all (Create and Farmer's Delight on the classpath)");
        }
        DataGenerator gen = event.getGenerator();
        PackOutput out = gen.getPackOutput();
        ExistingFileHelper files = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookup = event.getLookupProvider();

        gen.addProvider(event.includeClient(), new LangGen(out, "en_us"));
        gen.addProvider(event.includeClient(), new LangGen(out, "it_it"));
        gen.addProvider(event.includeClient(), new ModelGen.Items(out, files));
        gen.addProvider(event.includeClient(), new ModelGen.BlockStates(out, files));

        var worldgen = gen.addProvider(event.includeServer(), WorldGen.create(out, lookup));
        lookup = worldgen.getRegistryProvider();
        gen.addProvider(event.includeServer(), new RecipeGen(out, lookup));
        gen.addProvider(event.includeServer(), LootGen.create(out, lookup));
        gen.addProvider(event.includeServer(), new DataMapGen(out, lookup));
        gen.addProvider(event.includeServer(), new TagGen.Biomes(out, lookup, files));
        gen.addProvider(event.includeServer(), new JsonGen(out, "compat", json -> {
            com.seba.reverie.compat.create.CreateDataGen.json(json);
            com.seba.reverie.compat.fd.FdDataGen.json(json);
            CoreJson.json(json);
        }));
        BlockTagsProvider blockTags = new TagGen.Blocks(out, lookup, files);
        gen.addProvider(event.includeServer(), blockTags);
        gen.addProvider(event.includeServer(), new TagGen.Items(out, lookup, blockTags.contentsGetter(), files));
    }
}
