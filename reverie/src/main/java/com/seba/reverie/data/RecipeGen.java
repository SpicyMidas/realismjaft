package com.seba.reverie.data;

import java.util.concurrent.CompletableFuture;

import com.seba.reverie.compat.create.CreateDataGen;
import com.seba.reverie.registry.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;

final class RecipeGen extends RecipeProvider {
    RecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    private void dreamland(RecipeOutput out) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, com.seba.reverie.registry.ModBlocks.DREAMWOOD_PLANKS.get(), 4)
                .requires(com.seba.reverie.registry.ModBlocks.DREAMWOOD_LOG.get()).group("planks")
                .unlockedBy("has_log", has(com.seba.reverie.registry.ModBlocks.DREAMWOOD_LOG.get())).save(out);
        net.minecraft.data.recipes.SimpleCookingRecipeBuilder.smelting(net.minecraft.world.item.crafting.Ingredient.of(
                        com.seba.reverie.registry.ModBlocks.COBBLED_LUCID_STONE.get()), RecipeCategory.BUILDING_BLOCKS,
                        com.seba.reverie.registry.ModBlocks.LUCID_STONE.get(), 0.1f, 200)
                .unlockedBy("has_cobbled", has(com.seba.reverie.registry.ModBlocks.COBBLED_LUCID_STONE.get())).save(out);
        net.minecraft.data.recipes.ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, com.seba.reverie.registry.ModBlocks.LUCID_STONE_BRICKS.get(), 4)
                .pattern("SS").pattern("SS").define('S', com.seba.reverie.registry.ModBlocks.LUCID_STONE.get())
                .unlockedBy("has_lucid", has(com.seba.reverie.registry.ModBlocks.LUCID_STONE.get())).save(out);
        net.minecraft.data.recipes.ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, com.seba.reverie.registry.ModBlocks.PILLOW_CLOUD.get())
                .pattern("DD").pattern("DD").define('D', ModItems.DREAM_DUST.get())
                .unlockedBy("has_dust", has(ModItems.DREAM_DUST.get())).save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.DREAM_DUST.get(), 2).requires(ModItems.LUCID_SHARD.get())
                .unlockedBy("has_shard", has(ModItems.LUCID_SHARD.get())).save(out);
        net.minecraft.data.recipes.ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModItems.LONE_DOOR.get())
                .pattern("PP").pattern("PS").pattern("PP").define('P', com.seba.reverie.registry.ModBlocks.DREAMWOOD_PLANKS.get())
                .define('S', ModItems.LUCID_SHARD.get()).unlockedBy("has_shard", has(ModItems.LUCID_SHARD.get())).save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, com.seba.reverie.registry.ModBlocks.FLOATING_LANTERN.get())
                .requires(Items.LANTERN).requires(ModItems.DREAM_DUST.get()).unlockedBy("has_dust", has(ModItems.DREAM_DUST.get())).save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, com.seba.reverie.registry.ModBlocks.MELTED_CLOCK.get())
                .requires(Items.CLOCK).requires(ModItems.DREAM_DUST.get()).unlockedBy("has_dust", has(ModItems.DREAM_DUST.get())).save(out);
        net.minecraft.data.recipes.ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, com.seba.reverie.registry.ModBlocks.SLEEP_BELL.get())
                .pattern("PPP").pattern("PGP").pattern("P P").define('P', com.seba.reverie.registry.ModBlocks.DREAMWOOD_PLANKS.get())
                .define('G', Items.GOLD_INGOT).unlockedBy("has_planks", has(com.seba.reverie.registry.ModBlocks.DREAMWOOD_PLANKS.get())).save(out);
        net.minecraft.data.recipes.ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, com.seba.reverie.registry.ModBlocks.SANDGLASS_CORE.get())
                .pattern("PGP").pattern(" S ").pattern("PGP").define('P', com.seba.reverie.registry.ModBlocks.DREAMWOOD_PLANKS.get())
                .define('G', Items.GLASS).define('S', com.seba.reverie.registry.ModBlocks.HOURGLASS_SAND.get())
                .unlockedBy("has_sand", has(com.seba.reverie.registry.ModBlocks.HOURGLASS_SAND.get())).save(out);
    }

    @Override
    protected void buildRecipes(RecipeOutput out) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.TUFF_ALLOY.get())
                .requires(Items.TUFF).requires(Items.IRON_NUGGET).requires(Items.GOLD_NUGGET)
                .unlockedBy("has_tuff", has(Items.TUFF))
                .save(out);
        CreateDataGen.recipes(out);
        com.seba.reverie.compat.fd.FdDataGen.recipes(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.TRANSPORTATION, ModItems.PAPER_GLIDER.get())
                .pattern("PPP").pattern("P/P").pattern(" / ")
                .define('P', Items.PAPER).define('/', Items.STICK)
                .unlockedBy("has_paper", has(Items.PAPER)).save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.DREAM_CATCHER.get())
                .pattern("/S/").pattern("SLS").pattern("F F")
                .define('/', Items.STICK).define('S', Items.STRING).define('L', ModItems.LUCID_SHARD.get()).define('F', Items.FEATHER)
                .unlockedBy("has_lucid_shard", has(ModItems.LUCID_SHARD.get())).save(out);

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
        ShapedRecipeBuilder.shaped(RecipeCategory.FOOD, ModItems.MELATONIA_CAKE.get())
                .pattern("MMM").pattern("SPS").pattern("WEW")
                .define('M', Items.MILK_BUCKET).define('S', Items.SUGAR).define('P', ModItems.MELATONIA_PETALS.get())
                .define('W', Items.WHEAT).define('E', Items.EGG)
                .unlockedBy("has_petals", has(ModItems.MELATONIA_PETALS.get())).save(out);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, ModItems.MELATONIA_CAKE.get())
                .requires(ModItems.MELATONIA_CAKE_SLICE.get(), 4)
                .unlockedBy("has_slice", has(ModItems.MELATONIA_CAKE_SLICE.get()))
                .save(out, com.seba.reverie.Reverie.id("melatonia_cake_from_slices"));
        dreamland(out);
        net.minecraft.data.recipes.SpecialRecipeBuilder.special(com.seba.reverie.content.melatonia.LacingRecipe::new)
                .save(out, com.seba.reverie.Reverie.id("lacing").toString());
    }
}
