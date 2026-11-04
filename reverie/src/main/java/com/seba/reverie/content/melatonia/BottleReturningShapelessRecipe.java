package com.seba.reverie.content.melatonia;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seba.reverie.registry.ModRecipes;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * A plain shapeless recipe that gives back {@code bottles} glass bottles from the bottle-shaped ingredients
 * (e.g. 3 Extract + Glowstone = 1 Concentrated Extract + 2 bottles). Still a ShapelessRecipe, so recipe viewers
 * show it normally.
 */
public class BottleReturningShapelessRecipe extends ShapelessRecipe {
    private final int bottles;

    public BottleReturningShapelessRecipe(String group, CraftingBookCategory category, ItemStack result,
                                          NonNullList<Ingredient> ingredients, int bottles) {
        super(group, category, result, ingredients);
        this.bottles = bottles;
    }

    public int bottles() {
        return bottles;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(input);
        int left = bottles;
        for (int i = 0; i < input.size() && left > 0; i++) {
            ItemStack stack = input.getItem(i);
            if (remaining.get(i).isEmpty() && stack.getItem() instanceof DreamFoodItem && stack.has(net.minecraft.core.component.DataComponents.FOOD)
                    && stack.get(net.minecraft.core.component.DataComponents.FOOD).usingConvertsTo().map(s -> s.is(Items.GLASS_BOTTLE)).orElse(false)) {
                remaining.set(i, new ItemStack(Items.GLASS_BOTTLE));
                left--;
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.BOTTLE_RETURNING_SHAPELESS.get();
    }

    public static final class Serializer implements RecipeSerializer<BottleReturningShapelessRecipe> {
        private static final MapCodec<BottleReturningShapelessRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(ShapelessRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(ShapelessRecipe::category),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.getResultItem(null)),
                Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").flatXmap(list -> {
                    if (list.isEmpty()) return DataResult.error(() -> "No ingredients for shapeless recipe");
                    if (list.size() > 9) return DataResult.error(() -> "Too many ingredients for shapeless recipe");
                    return DataResult.success(NonNullList.of(Ingredient.EMPTY, list.toArray(Ingredient[]::new)));
                }, DataResult::success).forGetter(ShapelessRecipe::getIngredients),
                Codec.intRange(0, 9).optionalFieldOf("returned_bottles", 0).forGetter(BottleReturningShapelessRecipe::bottles)
        ).apply(i, BottleReturningShapelessRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, BottleReturningShapelessRecipe> STREAM_CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeUtf(r.getGroup());
                    buf.writeEnum(r.category());
                    buf.writeVarInt(r.getIngredients().size());
                    for (Ingredient ing : r.getIngredients()) Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ing);
                    ItemStack.STREAM_CODEC.encode(buf, r.getResultItem(null));
                    buf.writeVarInt(r.bottles);
                },
                buf -> {
                    String group = buf.readUtf();
                    CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
                    int n = buf.readVarInt();
                    NonNullList<Ingredient> ings = NonNullList.withSize(n, Ingredient.EMPTY);
                    for (int k = 0; k < n; k++) ings.set(k, Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
                    ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
                    return new BottleReturningShapelessRecipe(group, category, result, ings, buf.readVarInt());
                });

        @Override
        public MapCodec<BottleReturningShapelessRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BottleReturningShapelessRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
