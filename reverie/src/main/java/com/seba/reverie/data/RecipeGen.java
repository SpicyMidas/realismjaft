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

        ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, ModItems.MELATONIA_EXTRACT.get())
                .requires(ModItems.MELATONIA_PETALS.get(), 3).requires(Items.GLASS_BOTTLE)
                .unlockedBy("has_petals", has(ModItems.MELATONIA_PETALS.get())).save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, ModItems.MELATONIA_COOKIE.get(), 4)
                .requires(Items.WHEAT).requires(Items.COCOA_BEANS).requires(ModItems.MELATONIA_PETALS.get())
                .unlockedBy("has_petals", has(ModItems.MELATONIA_PETALS.get())).save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, ModItems.DROWSY_TEA.get())
                .requires(ModItems.MELATONIA_EXTRACT.get()).requires(Items.SUGAR)
                .unlockedBy("has_extract", has(ModItems.MELATONIA_EXTRACT.get())).save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, ModItems.SLEEPY_STEW.get())
                .requires(Items.BOWL).requires(ModItems.MELATONIA_PETALS.get(), 2).requires(Items.POTATO).requires(Items.CARROT)
                .unlockedBy("has_petals", has(ModItems.MELATONIA_PETALS.get())).save(out);
        net.minecraft.data.recipes.SpecialRecipeBuilder.special(com.seba.reverie.content.melatonia.LacingRecipe::new)
                .save(out, com.seba.reverie.Reverie.id("lacing").toString());
    }
}
