package com.seba.reverie.dream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.seba.reverie.ReverieConfig;
import com.seba.reverie.registry.ModComponents;
import com.seba.reverie.registry.ModTags;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Most of the time you bring nothing back. */
public final class Keepsakes {
    private Keepsakes() {
    }

    public static List<ItemStack> roll(List<ItemStack> dreamItems, RandomSource random, boolean dreamCatcher) {
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack stack : dreamItems) {
            // The item carried into the dream is gone either way.
            if (!stack.isEmpty() && !stack.has(ModComponents.DREAM_ORIGIN.get())) candidates.add(stack);
        }
        Collections.shuffle(candidates, new java.util.Random(random.nextLong()));
        int max = ReverieConfig.get(ReverieConfig.MAX_KEEPSAKES);
        double base = ReverieConfig.get(ReverieConfig.KEEPSAKE_CHANCE);
        double lucky = ReverieConfig.get(ReverieConfig.LUCKY_KEEPSAKE_CHANCE);
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack stack : candidates) {
            if (kept.size() >= max) break;
            double chance = stack.is(ModTags.Items.LUCID_KEEPSAKES) ? lucky : base;
            if (dreamCatcher && kept.isEmpty()) chance = Math.max(chance, 0.5);
            if (random.nextDouble() < chance) {
                ItemStack copy = stack.copy();
                copy.remove(ModComponents.DREAM_ORIGIN.get());
                kept.add(copy);
            }
        }
        return kept;
    }
}
