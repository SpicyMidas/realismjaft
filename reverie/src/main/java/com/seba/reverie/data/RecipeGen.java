package com.seba.reverie.data;

import java.util.concurrent.CompletableFuture;

import com.seba.reverie.compat.create.CreateDataGen;
import com.seba.reverie.registry.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;

final class RecipeGen extends RecipeProvider {
    RecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.TUFF_ALLOY.get())
                .requires(Items.TUFF).requires(Items.IRON_NUGGET).requires(Items.GOLD_NUGGET)
                .unlockedBy("has_tuff", has(Items.TUFF))
                .save(out);
        CreateDataGen.recipes(out);
    }
}
