package com.seba.reverie.content.melatonia;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import javax.annotation.Nullable;

import com.seba.reverie.registry.ModComponents;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;

/**
 * Laced items inside an ordinary stack ({@link Laced}). Layers are always the top of the stack and worst first, so
 * the laced ones are the first eaten, thrown or split off. Works on "concrete" layer lists (every count resolved
 * against the stack's size, nothing past it) and writes them back normalised (no empty layers, equal neighbours
 * joined, a layer covering the whole stack stored as {@link Laced#ALL}, no component at all once nothing is laced).
 */
public final class LacedStacks {
    private LacedStacks() {
    }

    /** The stack's layers with every count resolved, never past the stack's size. */
    public static List<Laced.Layer> concrete(ItemStack stack) {
        Laced laced = stack.get(ModComponents.LACED.get());
        return laced == null ? List.of() : concrete(laced.layers(), stack.getCount());
    }

    static List<Laced.Layer> concrete(List<Laced.Layer> layers, int size) {
        List<Laced.Layer> out = new ArrayList<>();
        int left = size;
        for (Laced.Layer layer : layers) {
            if (left <= 0) break;
            int n = layer.count() == Laced.ALL ? left : Math.min(left, Math.max(0, layer.count()));
            if (n > 0) out.add(layer.withCount(n));
            left -= n;
        }
        return out;
    }

    /** Writes {@code layers} (concrete) back onto {@code stack}, normalised. */
    public static void set(ItemStack stack, List<Laced.Layer> layers) {
        List<Laced.Layer> joined = new ArrayList<>();
        int size = stack.getCount();
        int used = 0;
        for (Laced.Layer layer : concrete(layers, size)) {
            used += layer.count();
            Laced.Layer last = joined.isEmpty() ? null : joined.get(joined.size() - 1);
            if (last != null && last.level() == layer.level() && last.knownBy().equals(layer.knownBy())) {
                joined.set(joined.size() - 1, last.withCount(last.count() + layer.count()));
            } else {
                joined.add(layer);
            }
        }
        if (joined.isEmpty() || size <= 0) {
            stack.remove(ModComponents.LACED.get());
            return;
        }
        if (joined.size() == 1 && used == size) joined.set(0, joined.get(0).withCount(Laced.ALL));
        stack.set(ModComponents.LACED.get(), new Laced(joined));
    }

    /** The first {@code n} items' layers. */
    public static List<Laced.Layer> take(List<Laced.Layer> layers, int n) {
        return concrete(layers, n);
    }

    /** The layers after the first {@code n} items. */
    public static List<Laced.Layer> drop(List<Laced.Layer> layers, int n) {
        List<Laced.Layer> out = new ArrayList<>();
        int skip = n;
        for (Laced.Layer layer : layers) {
            int c = layer.count();
            if (skip >= c) {
                skip -= c;
                continue;
            }
            out.add(layer.withCount(c - skip));
            skip = 0;
        }
        return out;
    }

    /** Two stacks' layers together, worst first (stable: same strength keeps its order). */
    public static List<Laced.Layer> merge(List<Laced.Layer> a, List<Laced.Layer> b) {
        List<Laced.Layer> all = new ArrayList<>(a);
        all.addAll(b);
        all.sort(Comparator.comparingInt(Laced.Layer::level).reversed());
        return all;
    }

    /** Laced level of the item at {@code index} from the top (0 = plain). */
    public static int levelAt(List<Laced.Layer> layers, int index) {
        int i = index;
        for (Laced.Layer layer : layers) {
            if (i < layer.count()) return layer.level();
            i -= layer.count();
        }
        return 0;
    }

    /** Every layer passed through {@code change}; returns true if anything changed. */
    public static boolean mapLayers(ItemStack stack, UnaryOperator<Laced.Layer> change) {
        List<Laced.Layer> before = concrete(stack);
        if (before.isEmpty()) return false;
        List<Laced.Layer> after = new ArrayList<>(before.size());
        for (Laced.Layer layer : before) after.add(change.apply(layer));
        if (after.equals(before)) return false;
        set(stack, after);
        return true;
    }

    /** {@code player} now knows every laced item in the stack. Returns true if they did not before. */
    public static boolean reveal(ItemStack stack, UUID player) {
        return mapLayers(stack, layer -> layer.withKnown(player));
    }

    /** True if some laced item in the stack is unknown to {@code player}. */
    public static boolean hasUnknown(ItemStack stack, UUID player) {
        for (Laced.Layer layer : concrete(stack)) if (!layer.knownTo(player)) return true;
        return false;
    }

    /** The crafter placeholder becomes {@code player} (the first one to hold a freshly laced item). */
    public static boolean claimCrafted(ItemStack stack, UUID player) {
        Laced laced = stack.get(ModComponents.LACED.get());
        if (laced == null || laced.layers().stream().noneMatch(l -> l.knownBy().contains(Laced.CRAFTER))) return false;
        return mapLayers(stack, layer -> layer.knownBy().contains(Laced.CRAFTER) ? layer.withKnown(player) : layer);
    }

    // ------------------------------------------------------------------ vanilla stack operations

    /** ItemStack.split (mixin): the part split off takes the top items' layers, the rest keeps the others. */
    public static void afterSplit(ItemStack rest, ItemStack taken) {
        if (taken.isEmpty() || !taken.has(ModComponents.LACED.get())) return;
        Laced laced = taken.get(ModComponents.LACED.get());
        List<Laced.Layer> all = concrete(laced.layers(), taken.getCount() + Math.max(0, rest.getCount()));
        set(taken, take(all, taken.getCount()));
        if (!rest.isEmpty()) set(rest, drop(all, taken.getCount()));
    }

    /** One item eaten from the top: {@code after} (what is left in hand) loses the top item's layer. */
    public static void afterEating(ItemStack before, ItemStack after) {
        if (!before.has(ModComponents.LACED.get()) || after.isEmpty() || !ItemStack.isSameItem(before, after)
                || after.getCount() != before.getCount() - 1) {
            return;
        }
        set(after, drop(concrete(before), 1));
    }

    /** Same item and same components apart from the lacing. */
    public static boolean sameExceptLacing(ItemStack a, ItemStack b) {
        if (!ItemStack.isSameItem(a, b)) return false;
        ItemStack ca = a.copyWithCount(1);
        ItemStack cb = b.copyWithCount(1);
        ca.remove(ModComponents.LACED.get());
        cb.remove(ModComponents.LACED.get());
        return ItemStack.isSameItemSameComponents(ca, cb);
    }

    /**
     * Clicking a stack onto the same food in a slot, when either is laced: they join (vanilla would swap them, the
     * components differing), worst first. Left click moves as many as fit, right click one. Runs on both sides.
     */
    public static void onStackedOn(ItemStackedOnOtherEvent event) {
        ItemStack carried = event.getCarriedItem();
        ItemStack target = event.getStackedOnItem();
        if (carried.isEmpty() || target.isEmpty()) return;
        if (!carried.has(ModComponents.LACED.get()) && !target.has(ModComponents.LACED.get())) return;
        if (!sameExceptLacing(carried, target)) return;
        Player player = event.getPlayer();
        if (!event.getSlot().mayPlace(carried) || !event.getSlot().allowModification(player)) return;
        int room = event.getSlot().getMaxStackSize(target) - target.getCount();
        int move = Math.min(room, event.getClickAction() == ClickAction.PRIMARY ? carried.getCount() : 1);
        if (move <= 0) return;
        joinInto(target, carried, move);
        event.getSlot().setChanged();
        event.setCanceled(true);
    }

    /** Moves the top {@code move} items of {@code from} onto {@code into} (same food), keeping worst first. */
    public static void joinInto(ItemStack into, ItemStack from, int move) {
        List<Laced.Layer> a = concrete(into);
        List<Laced.Layer> b = concrete(from);
        List<Laced.Layer> moved = take(b, move);
        into.grow(move);
        set(into, merge(a, moved));
        from.shrink(move);
        if (!from.isEmpty()) set(from, drop(b, move));
    }

    @Nullable
    public static Laced get(ItemStack stack) {
        return stack.get(ModComponents.LACED.get());
    }
}
