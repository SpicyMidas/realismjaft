package com.seba.reverie.registry;

import com.seba.reverie.Reverie;
import com.seba.reverie.content.melatonia.BottleReturningShapelessRecipe;
import com.seba.reverie.content.melatonia.LacingRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Reverie.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<LacingRecipe>> LACING =
            SERIALIZERS.register("lacing", () -> new SimpleCraftingRecipeSerializer<>(LacingRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>, BottleReturningShapelessRecipe.Serializer> BOTTLE_RETURNING_SHAPELESS =
            SERIALIZERS.register("bottle_returning_shapeless", BottleReturningShapelessRecipe.Serializer::new);

    private ModRecipes() {
    }
}
