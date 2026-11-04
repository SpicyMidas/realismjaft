package dev.agentbuddy.brain.reply;

import static dev.agentbuddy.brain.tool.ToolResult.COOLDOWN;
import static dev.agentbuddy.brain.tool.ToolResult.NOTHING_IN_SIGHT;
import static dev.agentbuddy.brain.tool.ToolResult.NO_AGENT;
import static dev.agentbuddy.brain.tool.ToolResult.NOT_FOUND;
import static dev.agentbuddy.brain.tool.ToolResult.NO_CREDITS;
import static dev.agentbuddy.brain.tool.ToolResult.OUT_OF_RANGE;

import dev.agentbuddy.brain.tool.ItemCount;
import dev.agentbuddy.brain.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Template-first replies (AGENT_SPEC.md 5.1): after a tool runs, the reply is built here from the result. No second
 * LLM call, so the reply can only contain what the tool returned.
 */
public final class Templates {
    public static final String DIDNT_UNDERSTAND = "Didn't understand.";

    private Templates() {
    }

    public static String render(String tool, ToolResult r) {
        if (!r.ok()) {
            switch (r.reason()) {
                case COOLDOWN -> { return "Wait " + r.integer("seconds") + " s."; }
                case NO_CREDITS -> { return "Need " + r.integer("need") + " credits, have " + r.integer("have") + "."; }
                case OUT_OF_RANGE -> { return "Too far away."; }
                case NO_AGENT -> { return "No agent nearby."; }
                default -> { }
            }
        }
        return switch (tool) {
            case "look_at" -> lookAt(r);
            case "find_ore" -> r.ok()
                    ? Names.capitalized(r.str("ore")) + ": " + r.integer("distance") + " blocks " + r.str("dir") + ", y=" + r.integer("y") + "."
                    : "No " + Names.display(r.str("ore")) + " within " + r.integer("range") + ".";
            case "list_container" -> r.ok() ? listContainer(r) : containerFailure(r);
            case "snapshot_container" -> r.ok() ? "Snapshot of " + Names.display(r.str("container")) + " at " + pos(r) + "." : containerFailure(r);
            case "find_item" -> findItem(r);
            case "fetch_item" -> fetchItem(r);
            case "set_waystone" -> r.ok() ? "Waystone " + r.str("name") + " set."
                    : "exists".equals(r.reason()) ? "Waystone " + r.str("name") + " already exists."
                    : "limit".equals(r.reason()) ? "Max " + r.integer("max") + " waystones."
                    : fallback(r);
            case "remove_waystone" -> r.ok() ? "Waystone " + r.str("name") + " removed." : noWaystone(r);
            case "teleport_waystone" -> r.ok() ? "Teleporting to " + r.str("name") + "." : noWaystone(r);
            case "death_return" -> r.ok() ? "Teleporting near your death spot."
                    : NOT_FOUND.equals(r.reason()) ? "No death position recorded."
                    : "used".equals(r.reason()) ? "Already used for this death."
                    : "no_safe_spot".equals(r.reason()) ? "No safe spot near your death position."
                    : fallback(r);
            case "pickup_container" -> r.ok() ? "Carrying " + Names.display(r.str("container")) + "."
                    : "carrying".equals(r.reason()) ? "Already carrying a container."
                    : "unsupported".equals(r.reason()) ? "Can't carry that."
                    : containerFailure(r);
            case "place_container" -> r.ok() ? "Placed " + Names.display(r.str("container")) + " at " + pos(r) + "."
                    : "not_carrying".equals(r.reason()) ? "Not carrying anything."
                    : "blocked".equals(r.reason()) ? "Can't place there."
                    : NOTHING_IN_SIGHT.equals(r.reason()) ? "Can't see where to put it."
                    : fallback(r);
            case "ignore_block" -> r.ok() ? "Ignoring " + Names.display(r.str("block")) + " at " + pos(r) + "."
                    : NOTHING_IN_SIGHT.equals(r.reason()) ? "Can't see anything there."
                    : "already".equals(r.reason()) ? "Already ignored."
                    : fallback(r);
            case "unignore_block" -> r.ok() ? "No longer ignoring " + Names.display(r.str("block")) + " at " + pos(r) + "."
                    : NOT_FOUND.equals(r.reason()) ? "That block isn't ignored."
                    : NOTHING_IN_SIGHT.equals(r.reason()) ? "Can't see anything there."
                    : fallback(r);
            case "set_name" -> r.ok() ? "Name set to " + r.str("name") + "." : fallback(r);
            case "follow" -> r.ok() ? "Following." : fallback(r);
            case "stay" -> r.ok() ? "Staying here." : fallback(r);
            default -> r.ok() ? "Done." : fallback(r);
        };
    }

    private static String lookAt(ToolResult r) {
        if (!r.ok()) return "Can't see anything there.";
        String name = Names.display(r.str("block"));
        // Never "Yes": if the model copied the real block into the claim, agreeing would echo its mistake.
        if (Boolean.FALSE.equals(r.data().get("claim_matches"))) return "No. That's " + name + ".";
        return "That's " + name + ".";
    }

    private static String listContainer(ToolResult r) {
        String container = Names.capitalized(r.str("container"));
        @SuppressWarnings("unchecked")
        List<ItemCount> items = (List<ItemCount>) r.data().getOrDefault("items", List.of());
        if (items.isEmpty()) return container + " at " + pos(r) + " is empty.";
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < Math.min(3, items.size()); i++) parts.add(Names.count(items.get(i).count(), items.get(i).id()));
        String more = items.size() > 3 ? ", +" + (items.size() - 3) + " more" : "";
        return container + ": " + String.join(", ", parts) + more + ".";
    }

    private static String findItem(ToolResult r) {
        if (r.ok()) {
            int others = r.integer("other_containers");
            String tail = others > 0 ? " (+" + others + " more)" : "";
            return Names.count(r.integer("count"), r.str("item")) + " in " + Names.display(r.str("container")) + " at " + pos(r) + tail + ".";
        }
        if ("no_containers".equals(r.reason())) return "I don't see any containers.";
        if (NOT_FOUND.equals(r.reason())) return "No " + Names.display(r.str("item")) + " in known containers.";
        return fallback(r);
    }

    private static String fetchItem(ToolResult r) {
        if (r.ok()) return "Bringing " + Names.count(r.integer("count"), r.str("item")) + " from " + Names.display(r.str("container")) + " at " + pos(r) + ".";
        return switch (r.reason()) {
            case "no_containers" -> "I don't see that chest.";
            case "inventory_full" -> "Your inventory is full.";
            case "carrying" -> "Busy carrying a container.";
            case "busy" -> "Still fetching.";
            case NOT_FOUND -> "No " + Names.display(r.str("item")) + " in known containers.";
            default -> fallback(r);
        };
    }

    private static String containerFailure(ToolResult r) {
        return switch (r.reason()) {
            case NOTHING_IN_SIGHT -> "Not looking at a container.";
            case NOT_FOUND -> "No container there.";
            case "loot_unknown" -> "Contents unknown until opened.";
            default -> fallback(r);
        };
    }

    private static String noWaystone(ToolResult r) {
        return NOT_FOUND.equals(r.reason()) ? "No waystone named " + r.str("name") + "." : fallback(r);
    }

    private static String fallback(ToolResult r) {
        return "Can't do that.";
    }

    private static String pos(ToolResult r) {
        return r.integer("x") + ", " + r.integer("y") + ", " + r.integer("z");
    }
}
