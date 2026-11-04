package dev.agentbuddy.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Resolves what the model wrote ("diamonds", "iron ingot", "minecraft:oak_log") to a registered item. */
public final class ItemNames {
    private ItemNames() {
    }

    public static Optional<Item> resolve(String raw) {
        String n = raw.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", "_");
        for (String candidate : candidates(n)) {
            ResourceLocation rl = ResourceLocation.tryParse(candidate.contains(":") ? candidate : "minecraft:" + candidate);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                Item item = BuiltInRegistries.ITEM.get(rl);
                if (item != Items.AIR) return Optional.of(item);
            }
        }
        // Modded items: same path in any namespace.
        for (String candidate : candidates(n)) {
            if (candidate.contains(":")) continue;
            for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
                if (key.getPath().equals(candidate)) return Optional.of(BuiltInRegistries.ITEM.get(key));
            }
        }
        return Optional.empty();
    }

    private static List<String> candidates(String n) {
        List<String> c = new ArrayList<>(List.of(n));
        if (n.endsWith("ies")) c.add(n.substring(0, n.length() - 3) + "y");
        if (n.endsWith("es")) c.add(n.substring(0, n.length() - 2));
        if (n.endsWith("s") && !n.endsWith("ss")) c.add(n.substring(0, n.length() - 1));
        return c;
    }
}
