package com.seba.reverie.content.melatonia;

import com.seba.reverie.registry.ModRecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Any FOOD item + one Melatonia Extract (or Concentrated Extract) = the same food, laced. Returns the bottle. */
public class LacingRecipe extends CustomRecipe {
    public LacingRecipe(CraftingBookCategory category) {
        super(category);
    }

    private record Parts(ItemStack food, int agentSlot, int level) {
    }

    private static Parts parts(CraftingInput input) {
        ItemStack food = ItemStack.EMPTY;
        int agentSlot = -1;
        int level = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            int agent = Lacing.agentLevel(stack);
            if (agent > 0) {
                if (agentSlot >= 0) return null;
                agentSlot = i;
                level = agent;
            } else if (Lacing.isLaceableFood(stack)) {
                if (!food.isEmpty()) return null;
                food = stack;
            } else {
                return null;
            }
        }
        if (food.isEmpty() || agentSlot < 0) return null;
        return new Parts(food, agentSlot, level);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Parts p = parts(input);
        return p != null && !Lacing.lace(p.food(), p.level()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Parts p = parts(input);
        return p == null ? ItemStack.EMPTY : Lacing.lace(p.food(), p.level());
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(input);
        Parts p = parts(input);
        if (p != null) remaining.set(p.agentSlot(), new ItemStack(Items.GLASS_BOTTLE));
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.LACING.get();
    }
}
