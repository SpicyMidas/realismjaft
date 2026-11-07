package com.seba.reverie.dream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.seba.reverie.ReverieConfig;
import com.seba.reverie.registry.ModComponents;
import com.seba.reverie.registry.ModTags;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Most of the time you bring nothing back, unless a Dream Catcher was waiting at home. */
public final class Keepsakes {
    /** Chance an open Dream Catcher catches something (if you had anything at all). */
    public static final double CATCH_CHANCE = 0.95;
    /** Chance the catch is what you held as you left, when you held something. */
    public static final double HELD_CATCH_CHANCE = 0.7;

    private Keepsakes() {
    }

    private static List<ItemStack> candidates(List<ItemStack> dreamItems) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : dreamItems) {
            // The item carried into the dream is gone either way.
            if (!stack.isEmpty() && !stack.has(ModComponents.DREAM_ORIGIN.get())) out.add(stack);
        }
        return out;
    }

    /**
     * The Dream Catcher's catch: almost surely one stack from the dream inventory, most likely {@code held} (what was
     * in hand on the way out). Returns the dream stack itself (not a copy) so it can be left out of {@link #roll}, or
     * empty.
     */
    public static ItemStack catchOne(List<ItemStack> dreamItems, ItemStack held, RandomSource random) {
        List<ItemStack> candidates = candidates(dreamItems);
        if (candidates.isEmpty() || random.nextDouble() >= CATCH_CHANCE) return ItemStack.EMPTY;
        if (candidates.contains(held) && random.nextDouble() < HELD_CATCH_CHANCE) return held;
        return candidates.get(random.nextInt(candidates.size()));
    }

    /** The ordinary keepsakes, never including {@code except} (already caught). */
    public static List<ItemStack> roll(List<ItemStack> dreamItems, RandomSource random, ItemStack except) {
        List<ItemStack> candidates = candidates(dreamItems);
        candidates.removeIf(s -> s == except);
        Collections.shuffle(candidates, new java.util.Random(random.nextLong()));
        int max = ReverieConfig.get(ReverieConfig.MAX_KEEPSAKES);
        double base = ReverieConfig.get(ReverieConfig.KEEPSAKE_CHANCE);
        double lucky = ReverieConfig.get(ReverieConfig.LUCKY_KEEPSAKE_CHANCE);
        List<ItemStack> kept = new ArrayList<>();
        for (ItemStack stack : candidates) {
            if (kept.size() >= max) break;
            double chance = stack.is(ModTags.Items.LUCID_KEEPSAKES) ? lucky : base;
            if (random.nextDouble() < chance) kept.add(clean(stack));
        }
        return kept;
    }

    public static ItemStack clean(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.remove(ModComponents.DREAM_ORIGIN.get());
        return copy;
    }
}
