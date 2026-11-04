package com.seba.reverie.compat.create;

import com.google.gson.JsonObject;
import com.seba.reverie.compat.Compat;
import com.seba.reverie.data.JsonGen;
import com.seba.reverie.registry.ModItems;
import com.seba.reverie.registry.ModTags;
import com.simibubi.create.AllBlocks;

import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

/** Datagen for Create-only content. Only called from datagen, which always runs with Create present. */
public final class CreateDataGen {
    private CreateDataGen() {
    }

    public static void recipes(RecipeOutput out) {
        RecipeOutput create = out.withConditions(new ModLoadedCondition(Compat.CREATE));
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, CreateContent.TUFF_GEARSHIFT_ITEM.get())
                .pattern(" R ").pattern("ACA").pattern(" G ")
                .define('R', Tags.Items.DUSTS_REDSTONE)
                .define('A', ModTags.Items.INGOTS_TUFF_ALLOY)
                .define('C', AllBlocks.ANDESITE_CASING.get())
                .define('G', AllBlocks.COGWHEEL.get())
                .unlockedBy("has_tuff_alloy", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.TUFF_ALLOY.get()))
                .save(create);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE, CreateContent.SPORT_TUFF_GEARSHIFT_ITEM.get())
                .requires(CreateContent.TUFF_GEARSHIFT_ITEM.get())
                .requires(ModTags.Items.INGOTS_BRASS).requires(ModTags.Items.INGOTS_BRASS)
                .requires(Tags.Items.DUSTS_REDSTONE)
                .unlockedBy("has_tuff_gearshift", InventoryChangeTrigger.TriggerInstance.hasItems(CreateContent.TUFF_GEARSHIFT_ITEM.get()))
                .save(create);
    }

    public static void json(JsonGen gen) {
        // Mixing (no heat) doubles the yield of Tuff Alloy, like Create's Andesite Alloy.
        JsonObject mixing = new JsonObject();
        mixing.addProperty("type", "create:mixing");
        mixing.add("ingredients", JsonGen.array(JsonGen.item("minecraft:tuff"), JsonGen.tag("c:nuggets/iron"), JsonGen.tag("c:nuggets/gold")));
        mixing.add("results", JsonGen.array(JsonGen.result("reverie:tuff_alloy", 2)));
        gen.put("data/reverie/recipe/mixing/tuff_alloy.json", mixing, Compat.CREATE);

        for (String block : new String[]{"tuff_gearshift", "sport_tuff_gearshift"}) {
            gen.put("data/reverie/loot_table/blocks/" + block + ".json", JsonGen.dropSelf("reverie:" + block), Compat.CREATE);
        }
    }

    /** Optional ("required": false) tag entries, so the tags still load without Create. */
    public static void blockTags(java.util.function.Function<TagKey<Block>, TagBuilder> tags) {
        for (var id : new net.minecraft.resources.ResourceLocation[]{CreateContent.TUFF_GEARSHIFT.getId(), CreateContent.SPORT_TUFF_GEARSHIFT.getId()}) {
            tags.apply(BlockTags.MINEABLE_WITH_PICKAXE).addOptionalElement(id);
            tags.apply(BlockTags.MINEABLE_WITH_AXE).addOptionalElement(id);
        }
    }
}
