package com.seba.reverie.compat.fd;

import java.util.function.Function;

import com.seba.reverie.Reverie;
import com.seba.reverie.compat.Compat;
import com.seba.reverie.data.JsonGen;
import com.seba.reverie.registry.ModItems;

import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import vectorwing.farmersdelight.common.block.PieBlock;
import vectorwing.farmersdelight.common.tag.CommonTags;
import vectorwing.farmersdelight.data.builder.CookingPotRecipeBuilder;
import vectorwing.farmersdelight.data.builder.CuttingBoardRecipeBuilder;
import vectorwing.farmersdelight.client.recipebook.CookingPotRecipeBookTab;

/** Data for the Farmer's Delight dishes; every file carries a farmersdelight mod_loaded condition. */
public final class FdDataGen {
    private static final String FD = "farmersdelight";

    private FdDataGen() {
    }

    private static ResourceLocation fd(String path) {
        return ResourceLocation.fromNamespaceAndPath(FD, path);
    }

    public static void recipes(RecipeOutput base) {
        RecipeOutput out = base.withConditions(new ModLoadedCondition(Compat.FARMERS_DELIGHT));
        var petals = ModItems.MELATONIA_PETALS.get();
        var hasPetals = InventoryChangeTrigger.TriggerInstance.hasItems(petals);
        Ingredient water = DataComponentIngredient.of(false, DataComponents.POTION_CONTENTS, new PotionContents(Potions.WATER), Items.POTION);

        CookingPotRecipeBuilder.cookingPotRecipe(ModItems.MELATONIA_EXTRACT.get(), 1, 200, 0.35f, Items.GLASS_BOTTLE)
                .addIngredient(petals, 3).addIngredient(water)
                .setRecipeBookTab(CookingPotRecipeBookTab.DRINKS).unlockedBy("has_petals", hasPetals).save(out);
        CookingPotRecipeBuilder.cookingPotRecipe(ModItems.CONCENTRATED_MELATONIA_EXTRACT.get(), 1, 300, 0.5f, Items.GLASS_BOTTLE)
                .addIngredient(ModItems.MELATONIA_EXTRACT.get(), 3).addIngredient(Tags.Items.DUSTS_GLOWSTONE)
                .setRecipeBookTab(CookingPotRecipeBookTab.DRINKS).unlockedBy("has_petals", hasPetals).save(out);
        CookingPotRecipeBuilder.cookingPotRecipe(FdContent.LULLABY_STEW.get(), 1, 200, 1.0f, Items.BOWL)
                .addIngredient(petals, 2).addIngredient(Tags.Items.FOODS_RAW_MEAT).addIngredient(Tags.Items.CROPS_POTATO)
                .addIngredient(CommonTags.Items.CROPS_ONION)
                .setRecipeBookTab(CookingPotRecipeBookTab.MEALS).unlockedBy("has_petals", hasPetals).save(out);
        CookingPotRecipeBuilder.cookingPotRecipe(FdContent.DOZING_DUMPLINGS.get(), 2, 200, 1.0f)
                .addIngredient(CommonTags.Items.FOODS_DOUGH).addIngredient(petals).addIngredient(CommonTags.Items.CROPS_CABBAGE)
                .addIngredient(CommonTags.Items.CROPS_ONION)
                .setRecipeBookTab(CookingPotRecipeBookTab.MEALS).unlockedBy("has_petals", hasPetals).save(out);
        CookingPotRecipeBuilder.cookingPotRecipe(FdContent.MELATONIA_SYRUP.get(), 1, 200, 0.35f, Items.GLASS_BOTTLE)
                .addIngredient(petals, 2).addIngredient(Items.HONEY_BOTTLE).addIngredient(Items.SUGAR)
                .setRecipeBookTab(CookingPotRecipeBookTab.DRINKS).unlockedBy("has_petals", hasPetals).save(out);

        Ingredient knife = Ingredient.of(CommonTags.Items.TOOLS_KNIFE);
        CuttingBoardRecipeBuilder.cuttingRecipe(Ingredient.of(ModItems.WILD_MELATONIA.get()), knife, petals, 2)
                .addResultWithChance(ModItems.MELATONIA_SEEDS.get(), 0.5f).save(out);
        CuttingBoardRecipeBuilder.cuttingRecipe(Ingredient.of(FdContent.DREAMY_PIE_ITEM.get()), knife, FdContent.DREAMY_PIE_SLICE.get(), 4)
                .save(out);

        ShapedRecipeBuilder.shaped(RecipeCategory.FOOD, FdContent.DREAMY_PIE_ITEM.get())
                .pattern("ppp").pattern("xex").pattern(" O ")
                .define('p', petals).define('x', Items.SUGAR).define('e', Tags.Items.EGGS)
                .define('O', vectorwing.farmersdelight.common.registry.ModItems.PIE_CRUST.get())
                .unlockedBy("has_petals", hasPetals).save(out);
        ShapedRecipeBuilder.shaped(RecipeCategory.FOOD, FdContent.DREAMY_PIE_ITEM.get())
                .pattern("##").pattern("##").define('#', FdContent.DREAMY_PIE_SLICE.get())
                .unlockedBy("has_slice", InventoryChangeTrigger.TriggerInstance.hasItems(FdContent.DREAMY_PIE_SLICE.get()))
                .save(out, Reverie.id("dreamy_pie_from_slices"));
        ShapelessRecipeBuilder.shapeless(RecipeCategory.FOOD, FdContent.NIGHT_BLOOM_ROLL.get(), 2)
                .requires(vectorwing.farmersdelight.common.registry.ModItems.COOKED_RICE.get()).requires(petals).requires(petals)
                .requires(Items.DRIED_KELP)
                .unlockedBy("has_petals", hasPetals).save(out);
    }

    public static void json(JsonGen gen) {
        // Like Farmer's Delight's own pies: the block drops nothing (it is eaten or cut).
        com.google.gson.JsonObject pie = new com.google.gson.JsonObject();
        pie.addProperty("type", "minecraft:block");
        pie.addProperty("random_sequence", "reverie:blocks/dreamy_pie");
        gen.put("data/reverie/loot_table/blocks/dreamy_pie.json", pie, Compat.FARMERS_DELIGHT);
    }

    public static void itemModels(ItemModelProvider p) {
        for (var item : FdContent.TAB) p.basicItem(item.get());
    }

    public static void blockStates(BlockStateProvider p) {
        ModelFile[] models = new ModelFile[4];
        for (int bites = 0; bites < 4; bites++) {
            String name = bites == 0 ? "dreamy_pie" : "dreamy_pie_slice" + bites;
            var builder = p.models().getBuilder("block/" + name)
                    .parent(new ModelFile.UncheckedModelFile(fd("block/template_pie" + (bites == 0 ? "" : "_slice" + bites))))
                    .texture("bottom", fd("block/pie_bottom")).texture("side", fd("block/pie_side"))
                    .texture("top", Reverie.id("block/dreamy_pie_top"));
            if (bites > 0) builder.texture("inner", Reverie.id("block/dreamy_pie_inner"));
            models[bites] = builder;
        }
        p.getVariantBuilder(FdContent.DREAMY_PIE.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(models[state.getValue(PieBlock.BITES)])
                .rotationY(((int) state.getValue(PieBlock.FACING).toYRot() + 180) % 360).build());
    }

    /** Optional tag entries, so the tags still load without Farmer's Delight. */
    public static void itemTags(Function<TagKey<Item>, TagBuilder> tags) {
        tags.apply(fdTag("meals")).addOptionalElement(FdContent.LULLABY_STEW.getId());
        tags.apply(fdTag("snacks")).addOptionalElement(FdContent.DOZING_DUMPLINGS.getId()).addOptionalElement(FdContent.NIGHT_BLOOM_ROLL.getId());
        tags.apply(fdTag("pies")).addOptionalElement(FdContent.DREAMY_PIE_ITEM.getId());
        tags.apply(fdTag("sweets")).addOptionalElement(FdContent.DREAMY_PIE_SLICE.getId());
        tags.apply(fdTag("drinks")).addOptionalElement(FdContent.MELATONIA_SYRUP.getId());
        tags.apply(Tags.Items.FOODS_SOUP).addOptionalElement(FdContent.LULLABY_STEW.getId());
        tags.apply(Tags.Items.FOODS_PIE).addOptionalElement(FdContent.DREAMY_PIE_SLICE.getId());
        tags.apply(Tags.Items.FOODS).addOptionalElement(FdContent.LULLABY_STEW.getId()).addOptionalElement(FdContent.DOZING_DUMPLINGS.getId())
                .addOptionalElement(FdContent.DREAMY_PIE_SLICE.getId()).addOptionalElement(FdContent.NIGHT_BLOOM_ROLL.getId())
                .addOptionalElement(FdContent.MELATONIA_SYRUP.getId());
    }

    private static TagKey<Item> fdTag(String path) {
        return TagKey.create(net.minecraft.core.registries.Registries.ITEM, fd(path));
    }
}
