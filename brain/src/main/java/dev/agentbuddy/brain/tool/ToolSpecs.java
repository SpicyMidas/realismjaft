package dev.agentbuddy.brain.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The tool set from AGENT_SPEC.md section 4: names, descriptions (what the model sees), schemas, and starting
 * range/credit/cooldown values. Implementations live elsewhere (fake world in tests, Minecraft in the mod).
 * Registry order matters: the router fills the tool list category by category in this order.
 */
public final class ToolSpecs {
    private ToolSpecs() {
    }

    public static final List<String> ORES = List.of("coal", "iron", "copper", "gold", "redstone", "lapis",
            "diamond", "emerald", "quartz", "ancient_debris");

    public static final ToolSpec LOOK_AT = spec("look_at",
            "Report the block the player is looking at. If the player says what it is, pass that as claim.",
            Category.WORLD, props("claim", str("What the player says the block is, copied from their words (e.g. \"wood\"). Omit if no claim.")),
            List.of(), 0, 0, 0);
    public static final ToolSpec FIND_ORE = spec("find_ore", "Find the nearest ore of a type in loaded chunks.",
            Category.WORLD, props("ore", enumOf(ORES)), List.of("ore"), 64, 1, 5);
    public static final ToolSpec IGNORE_BLOCK = spec("ignore_block",
            "Hide the block or container the player is looking at from the Agent.", Category.WORLD, props(), List.of(), 0, 0, 0);
    public static final ToolSpec UNIGNORE_BLOCK = spec("unignore_block",
            "Stop hiding the block the player is looking at.", Category.WORLD, props(), List.of(), 0, 0, 0);

    public static final ToolSpec LIST_CONTAINER = spec("list_container",
            "List the contents of the container the player is looking at, or the one at x,y,z.",
            Category.INVENTORY, coords(), List.of(), 32, 0, 0);
    public static final ToolSpec FIND_ITEM = spec("find_item", "Find which known containers hold an item.",
            Category.INVENTORY, props("item", str("Item name, e.g. diamond")), List.of("item"), 32, 1, 5);
    public static final ToolSpec FETCH_ITEM = spec("fetch_item",
            "Fly to a container holding the item, take it out and bring it to the player.",
            Category.INVENTORY, props("item", str("Item name, e.g. diamond"), "count", integer(1, 64)), List.of("item"), 32, 2, 10);
    public static final ToolSpec SNAPSHOT_CONTAINER = spec("snapshot_container",
            "Open a read-only picture of a container's contents (the one looked at, or at x,y,z).",
            Category.INVENTORY, coords(), List.of(), 32, 1, 5);
    public static final ToolSpec PICKUP_CONTAINER = spec("pickup_container",
            "Pick up the chest or barrel the player is looking at, contents included, and carry it.",
            Category.INVENTORY, props(), List.of(), 8, 5, 30);
    public static final ToolSpec PLACE_CONTAINER = spec("place_container",
            "Put the carried container down where the player is looking.", Category.INVENTORY, props(), List.of(), 16, 0, 0);

    public static final ToolSpec SET_WAYSTONE = spec("set_waystone", "Save the player's position as a named waystone.",
            Category.TRAVEL, props("name", str("Waystone name")), List.of("name"), 0, 0, 0);
    public static final ToolSpec TELEPORT_WAYSTONE = spec("teleport_waystone", "Teleport the player to a named waystone.",
            Category.TRAVEL, props("name", str("Waystone name")), List.of("name"), 0, 1, 60);
    public static final ToolSpec REMOVE_WAYSTONE = spec("remove_waystone", "Delete a named waystone.",
            Category.TRAVEL, props("name", str("Waystone name")), List.of("name"), 0, 0, 0);
    public static final ToolSpec DEATH_RETURN = spec("death_return",
            "Teleport the player to a safe spot near where they last died. Once per death.",
            Category.TRAVEL, props(), List.of(), 0, 10, 0);

    public static final ToolSpec SET_NAME = spec("set_name", "Rename the Agent.", Category.SETTINGS,
            props("name", str("New name")), List.of("name"), 0, 0, 0);
    public static final ToolSpec FOLLOW = spec("follow", "The Agent follows the player.", Category.SETTINGS, props(), List.of(), 0, 0, 0);
    public static final ToolSpec STAY = spec("stay", "The Agent stays where it is.", Category.SETTINGS, props(), List.of(), 0, 0, 0);

    public static final List<ToolSpec> ALL = List.of(LOOK_AT, FIND_ORE, IGNORE_BLOCK, UNIGNORE_BLOCK,
            LIST_CONTAINER, FIND_ITEM, FETCH_ITEM, SNAPSHOT_CONTAINER, PICKUP_CONTAINER, PLACE_CONTAINER,
            SET_WAYSTONE, TELEPORT_WAYSTONE, REMOVE_WAYSTONE, DEATH_RETURN, SET_NAME, FOLLOW, STAY);

    private static final Map<String, String> ORE_ALIASES = Map.of("lapis_lazuli", "lapis", "netherite", "ancient_debris",
            "debris", "ancient_debris", "nether_quartz", "quartz", "diamonds", "diamond", "emeralds", "emerald");

    /** "Iron Ore", "minecraft:deepslate_iron_ore", "diamonds" -> "iron", "iron", "diamond". */
    public static String normalizeOre(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).trim().replace("minecraft:", "").replace(' ', '_');
        s = s.replaceFirst("^deepslate_", "").replaceFirst("^nether_(?=gold)", "");
        s = s.replaceFirst("_?ores?$", "");
        s = ORE_ALIASES.getOrDefault(s, s);
        if (!ORES.contains(s) && s.endsWith("s") && ORES.contains(s.substring(0, s.length() - 1))) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** "Diamonds", "minecraft:iron_ingot", "iron ingots" -> "diamond", "iron_ingot", "iron_ingots" (world resolves plurals). */
    public static String normalizeItem(String raw) {
        return raw.toLowerCase(Locale.ROOT).trim().replace("minecraft:", "").replaceAll("\\s+", "_");
    }

    /** Applies {@link #normalizeOre} / {@link #normalizeItem} to an "ore" / "item" string argument if present. */
    public static JsonObject normalizeArgs(JsonObject args) {
        if (args.has("ore") && args.get("ore").isJsonPrimitive()) args.add("ore", new JsonPrimitive(normalizeOre(args.get("ore").getAsString())));
        if (args.has("item") && args.get("item").isJsonPrimitive()) args.add("item", new JsonPrimitive(normalizeItem(args.get("item").getAsString())));
        return args;
    }

    private static ToolSpec spec(String name, String description, Category c, JsonObject props, List<String> required,
                                 int range, int credits, int cooldownSeconds) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", props);
        JsonArray req = new JsonArray();
        required.forEach(req::add);
        schema.add("required", req);
        return new ToolSpec(name, description, c, schema, range, credits, Duration.ofSeconds(cooldownSeconds));
    }

    private static JsonObject props(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) o.add((String) kv[i], (JsonObject) kv[i + 1]);
        return o;
    }

    private static JsonObject coords() {
        return props("x", integer(null, null), "y", integer(null, null), "z", integer(null, null));
    }

    private static JsonObject str(String description) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "string");
        o.addProperty("description", description);
        return o;
    }

    private static JsonObject integer(Integer min, Integer max) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "integer");
        if (min != null) o.addProperty("minimum", min);
        if (max != null) o.addProperty("maximum", max);
        return o;
    }

    private static JsonObject enumOf(List<String> values) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "string");
        JsonArray a = new JsonArray();
        values.forEach(a::add);
        o.add("enum", a);
        return o;
    }
}
