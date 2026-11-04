package dev.agentbuddy.brain.fake;

import dev.agentbuddy.brain.ContextBlock;
import dev.agentbuddy.brain.Pos;
import dev.agentbuddy.brain.tool.Credits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In-memory stand-in for the Minecraft world. Perception methods ({@link #lookingAt}, {@link #visibleContainers},
 * {@link #nearestOre}, {@link #context}) filter ignored blocks, the same contract the real world adapter must keep.
 */
public final class FakeWorld {
    public static final class Container {
        public final String type;
        public final Map<String, Integer> items;

        public Container(String type, Map<String, Integer> items) {
            this.type = type;
            this.items = new LinkedHashMap<>(items);
        }
    }

    public String player = "Steve";
    public Pos playerPos = new Pos(0, 64, 0);
    public int hp = 20;
    public String heldItem = "minecraft:stone_pickaxe";
    public String dimension = "minecraft:overworld";
    public String biome = "minecraft:plains";
    public String timeOfDay = "day";
    public int reach = 5;
    public int inventoryFreeSlots = 30;

    /** Raw target of the player's crosshair (may be ignored; perception filters it). */
    public Pos lookingAt;

    public final Map<Pos, String> blocks = new HashMap<>();
    public final Map<Pos, Container> containers = new LinkedHashMap<>();
    public final Set<Pos> ignored = new HashSet<>();
    public final Map<String, Pos> waystones = new LinkedHashMap<>();
    public int maxWaystones = 5;
    public Pos lastDeath;
    public boolean deathReturnUsed;

    public Pos agentPos = new Pos(1, 65, 1);
    public String agentName = "Agent";
    public String agentState = "following";
    public Container carrying;

    public final Credits credits = new Credits();

    // Minimal tag data for claim matching.
    private static final Map<String, Set<String>> TAGS = Map.of(
            "minecraft:oak_log", Set.of("minecraft:logs"),
            "minecraft:oak_planks", Set.of("minecraft:planks"),
            "minecraft:spruce_log", Set.of("minecraft:logs"),
            "minecraft:iron_ore", Set.of("minecraft:iron_ores"),
            "minecraft:deepslate_iron_ore", Set.of("minecraft:iron_ores"),
            "minecraft:diamond_ore", Set.of("minecraft:diamond_ores"),
            "minecraft:redstone_ore", Set.of("minecraft:redstone_ores"));

    private static final Map<String, List<String>> ORE_BLOCKS = Map.of(
            "coal", List.of("minecraft:coal_ore", "minecraft:deepslate_coal_ore"),
            "iron", List.of("minecraft:iron_ore", "minecraft:deepslate_iron_ore"),
            "copper", List.of("minecraft:copper_ore", "minecraft:deepslate_copper_ore"),
            "gold", List.of("minecraft:gold_ore", "minecraft:deepslate_gold_ore", "minecraft:nether_gold_ore"),
            "redstone", List.of("minecraft:redstone_ore", "minecraft:deepslate_redstone_ore"),
            "lapis", List.of("minecraft:lapis_ore", "minecraft:deepslate_lapis_ore"),
            "diamond", List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore"),
            "emerald", List.of("minecraft:emerald_ore", "minecraft:deepslate_emerald_ore"),
            "quartz", List.of("minecraft:nether_quartz_ore"),
            "ancient_debris", List.of("minecraft:ancient_debris"));

    public static Set<String> tags(String blockId) {
        return TAGS.getOrDefault(blockId, Set.of());
    }

    public static boolean isContainerType(String blockId) {
        return blockId.endsWith("chest") || blockId.endsWith("barrel") || blockId.endsWith("shulker_box");
    }

    /** Block id at a position: a container, a placed block, or null for air. Ignores the ignore list. */
    public String rawBlockAt(Pos p) {
        Container c = containers.get(p);
        if (c != null) return c.type;
        return blocks.get(p);
    }

    /** The looked-at position if it exists, is in reach and is not ignored. */
    public Optional<Pos> lookingAt() {
        if (lookingAt == null || ignored.contains(lookingAt) || rawBlockAt(lookingAt) == null) return Optional.empty();
        if (lookingAt.distance(playerPos) > reach + 0.5) return Optional.empty();
        return Optional.of(lookingAt);
    }

    public List<Map.Entry<Pos, Container>> visibleContainers(int range) {
        List<Map.Entry<Pos, Container>> out = new ArrayList<>();
        for (Map.Entry<Pos, Container> e : containers.entrySet()) {
            if (ignored.contains(e.getKey())) continue;
            if (e.getKey().distance(playerPos) <= range) out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.getKey().distance(playerPos)));
        return out;
    }

    public Optional<Pos> nearestOre(String ore, int range) {
        List<String> ids = ORE_BLOCKS.getOrDefault(ore, List.of());
        return blocks.entrySet().stream()
                .filter(e -> ids.contains(e.getValue()) && !ignored.contains(e.getKey()))
                .map(Map.Entry::getKey)
                .filter(p -> p.distance(playerPos) <= range)
                .min(Comparator.comparingDouble(p -> p.distance(playerPos)));
    }

    /**
     * Resolves a model-provided item name ("diamonds") to an item id: one present in a visible container if possible,
     * else a singular guess. Never looks inside ignored containers.
     */
    public String resolveItem(String name) {
        Set<String> known = new HashSet<>();
        for (Map.Entry<Pos, Container> e : visibleContainers(Integer.MAX_VALUE)) known.addAll(e.getValue().items.keySet());
        for (String candidate : List.of(name, name.replaceFirst("s$", ""), name.replaceFirst("es$", ""))) {
            String id = "minecraft:" + candidate;
            if (known.contains(id)) return id;
        }
        return "minecraft:" + (name.endsWith("s") && !name.endsWith("ss") ? name.substring(0, name.length() - 1) : name);
    }

    public ContextBlock context() {
        Optional<Pos> look = lookingAt();
        return new ContextBlock(player, hp, 20, heldItem,
                look.map(this::rawBlockAt).orElse(null), look.orElse(null),
                dimension, biome, timeOfDay, agentName, agentPos, agentState, credits.balance(player),
                visibleContainers(32).size());
    }
}
