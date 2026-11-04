package dev.agentbuddy.brain.fake;

import static dev.agentbuddy.brain.tool.ToolResult.NOTHING_IN_SIGHT;
import static dev.agentbuddy.brain.tool.ToolResult.NOT_FOUND;
import static dev.agentbuddy.brain.tool.ToolResult.OUT_OF_RANGE;
import static dev.agentbuddy.brain.tool.ToolResult.data;
import static dev.agentbuddy.brain.tool.ToolResult.fail;
import static dev.agentbuddy.brain.tool.ToolResult.ok;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.Pos;
import dev.agentbuddy.brain.tool.ClaimMatcher;
import dev.agentbuddy.brain.tool.ItemCount;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.ToIntBiFunction;

/** Implementations of every ToolSpecs tool against a {@link FakeWorld}. */
public final class FakeTools {
    private FakeTools() {
    }

    public static List<Tool> all(FakeWorld w) {
        List<Tool> out = new ArrayList<>();
        for (ToolSpec spec : ToolSpecs.ALL) out.add(create(spec, w));
        return out;
    }

    private static Tool create(ToolSpec spec, FakeWorld w) {
        return switch (spec.name()) {
            case "look_at" -> tool(spec, (p, a) -> lookAt(w, a));
            case "find_ore" -> tool(spec, (p, a) -> findOre(w, spec, a));
            case "ignore_block" -> tool(spec, (p, a) -> w.lookingAt().map(pos -> {
                w.ignored.add(pos);
                return ok(data("block", w.rawBlockAt(pos), "x", pos.x(), "y", pos.y(), "z", pos.z()));
            }).orElse(fail(NOTHING_IN_SIGHT)));
            case "unignore_block" -> tool(spec, (p, a) -> unignore(w, a));
            case "list_container" -> tool(spec, (p, a) -> listContainer(w, spec, a, false));
            case "snapshot_container" -> tool(spec, (p, a) -> listContainer(w, spec, a, true));
            case "find_item" -> tool(spec, (p, a) -> findItem(w, spec, a, false));
            case "fetch_item" -> tool(spec, (p, a) -> findItem(w, spec, a, true));
            case "pickup_container" -> tool(spec, (p, a) -> pickup(w, spec));
            case "place_container" -> tool(spec, (p, a) -> place(w, spec));
            case "set_waystone" -> tool(spec, (p, a) -> setWaystone(w, a));
            case "remove_waystone" -> tool(spec, (p, a) -> waystone(w, a).map(n -> {
                w.waystones.remove(n);
                return ok(data("name", n));
            }).orElse(fail(NOT_FOUND, data("name", a.get("name").getAsString()))));
            case "teleport_waystone" -> tool(spec, (p, a) -> waystone(w, a).map(n -> {
                Pos to = w.waystones.get(n);
                w.playerPos = to;
                return ok(data("name", n, "x", to.x(), "y", to.y(), "z", to.z()));
            }).orElse(fail(NOT_FOUND, data("name", a.get("name").getAsString()))), (p, a) -> waystone(w, a)
                    .map(n -> 1 + (int) (w.waystones.get(n).distance(w.playerPos) / 100)).orElse(0));
            case "death_return" -> tool(spec, (p, a) -> deathReturn(w));
            case "set_name" -> tool(spec, (p, a) -> {
                w.agentName = a.get("name").getAsString();
                return ok(data("name", w.agentName));
            });
            case "follow" -> tool(spec, (p, a) -> {
                w.agentState = "following";
                return ok(Map.of());
            });
            case "stay" -> tool(spec, (p, a) -> {
                w.agentState = "staying";
                return ok(Map.of());
            });
            default -> throw new IllegalArgumentException("no fake for " + spec.name());
        };
    }

    private static ToolResult lookAt(FakeWorld w, JsonObject a) {
        Optional<Pos> pos = w.lookingAt();
        if (pos.isEmpty()) return fail(NOTHING_IN_SIGHT);
        Pos p = pos.get();
        String id = w.rawBlockAt(p);
        Map<String, Object> d = data("block", id, "x", p.x(), "y", p.y(), "z", p.z());
        String claim = a.has("claim") ? a.get("claim").getAsString() : "";
        if (!ClaimMatcher.isEmpty(claim)) {
            d.put("claim", claim);
            d.put("claim_matches", ClaimMatcher.matches(claim, id, FakeWorld.tags(id)));
        }
        return ok(d);
    }

    private static ToolResult findOre(FakeWorld w, ToolSpec spec, JsonObject a) {
        String ore = a.get("ore").getAsString();
        return w.nearestOre(ore, spec.range()).map(p -> ok(data("ore", ore,
                        "distance", (int) Math.round(p.distance(w.playerPos)), "dir", w.playerPos.directionTo(p),
                        "x", p.x(), "y", p.y(), "z", p.z())))
                .orElse(fail(NOT_FOUND, data("ore", ore, "range", spec.range())));
    }

    private static ToolResult unignore(FakeWorld w, JsonObject a) {
        // The only tool that can see an ignored block: the one the player points at, or given coordinates.
        Pos p = a.has("x") && a.has("y") && a.has("z")
                ? new Pos(a.get("x").getAsInt(), a.get("y").getAsInt(), a.get("z").getAsInt()) : w.lookingAt;
        if (p == null || w.rawBlockAt(p) == null) return fail(NOTHING_IN_SIGHT);
        if (!w.ignored.remove(p)) return fail(NOT_FOUND);
        return ok(data("block", w.rawBlockAt(p), "x", p.x(), "y", p.y(), "z", p.z()));
    }

    private static Optional<Pos> target(FakeWorld w, JsonObject a) {
        if (a.has("x") && a.has("y") && a.has("z")) {
            return Optional.of(new Pos(a.get("x").getAsInt(), a.get("y").getAsInt(), a.get("z").getAsInt()));
        }
        return w.lookingAt();
    }

    private static ToolResult listContainer(FakeWorld w, ToolSpec spec, JsonObject a, boolean snapshot) {
        Optional<Pos> t = target(w, a);
        if (t.isEmpty()) return fail(NOTHING_IN_SIGHT);
        Pos p = t.get();
        FakeWorld.Container c = w.ignored.contains(p) ? null : w.containers.get(p);
        if (c == null) return fail(NOT_FOUND);
        if (p.distance(w.playerPos) > spec.range()) return fail(OUT_OF_RANGE);
        Map<String, Object> d = data("container", c.type, "x", p.x(), "y", p.y(), "z", p.z());
        if (!snapshot) {
            List<ItemCount> items = new ArrayList<>();
            c.items.forEach((id, n) -> items.add(new ItemCount(id, n)));
            items.sort(Comparator.comparingInt(ItemCount::count).reversed());
            d.put("items", items);
        }
        return ok(d);
    }

    private static ToolResult findItem(FakeWorld w, ToolSpec spec, JsonObject a, boolean fetch) {
        String name = a.get("item").getAsString();
        List<Map.Entry<Pos, FakeWorld.Container>> visible = w.visibleContainers(spec.range());
        if (visible.isEmpty()) return fail("no_containers", data("item", name));
        String id = w.resolveItem(name);
        List<Map.Entry<Pos, FakeWorld.Container>> holding = new ArrayList<>();
        for (var e : visible) if (e.getValue().items.getOrDefault(id, 0) > 0) holding.add(e);
        if (holding.isEmpty()) return fail(NOT_FOUND, data("item", id));
        holding.sort(Comparator.comparingInt((Map.Entry<Pos, FakeWorld.Container> e) -> -e.getValue().items.get(id)));
        Map.Entry<Pos, FakeWorld.Container> best = holding.get(0);
        Pos p = best.getKey();
        int have = best.getValue().items.get(id);
        if (!fetch) {
            return ok(data("item", id, "count", have, "container", best.getValue().type,
                    "x", p.x(), "y", p.y(), "z", p.z(), "other_containers", holding.size() - 1));
        }
        if (w.carrying != null) return fail("carrying");
        if (w.inventoryFreeSlots <= 0) return fail("inventory_full");
        int want = a.has("count") ? a.get("count").getAsInt() : Math.min(have, 64);
        int take = Math.min(want, have);
        best.getValue().items.merge(id, -take, Integer::sum);
        best.getValue().items.remove(id, 0);
        return ok(data("item", id, "count", take, "container", best.getValue().type, "x", p.x(), "y", p.y(), "z", p.z()));
    }

    private static ToolResult pickup(FakeWorld w, ToolSpec spec) {
        if (w.carrying != null) return fail("carrying");
        Optional<Pos> t = w.lookingAt();
        if (t.isEmpty()) return fail(NOTHING_IN_SIGHT);
        Pos p = t.get();
        FakeWorld.Container c = w.containers.get(p);
        if (c == null) return fail(NOT_FOUND);
        if (p.distance(w.playerPos) > spec.range()) return fail(OUT_OF_RANGE);
        w.containers.remove(p);
        w.carrying = c;
        w.lookingAt = null;
        return ok(data("container", c.type, "x", p.x(), "y", p.y(), "z", p.z()));
    }

    private static ToolResult place(FakeWorld w, ToolSpec spec) {
        if (w.carrying == null) return fail("not_carrying");
        if (w.lookingAt == null || w.rawBlockAt(w.lookingAt) == null) return fail(NOTHING_IN_SIGHT);
        Pos p = new Pos(w.lookingAt.x(), w.lookingAt.y() + 1, w.lookingAt.z());
        if (w.rawBlockAt(p) != null) return fail("blocked");
        if (p.distance(w.playerPos) > spec.range()) return fail(OUT_OF_RANGE);
        w.containers.put(p, w.carrying);
        String type = w.carrying.type;
        w.carrying = null;
        return ok(data("container", type, "x", p.x(), "y", p.y(), "z", p.z()));
    }

    private static ToolResult setWaystone(FakeWorld w, JsonObject a) {
        String name = a.get("name").getAsString();
        if (waystone(w, a).isPresent()) return fail("exists", data("name", name));
        if (w.waystones.size() >= w.maxWaystones) return fail("limit", data("max", w.maxWaystones));
        Pos p = w.playerPos;
        w.waystones.put(name, p);
        return ok(data("name", name, "x", p.x(), "y", p.y(), "z", p.z()));
    }

    private static Optional<String> waystone(FakeWorld w, JsonObject a) {
        String name = a.get("name").getAsString();
        return w.waystones.keySet().stream().filter(n -> n.equalsIgnoreCase(name)).findFirst();
    }

    private static ToolResult deathReturn(FakeWorld w) {
        if (w.lastDeath == null) return fail(NOT_FOUND);
        if (w.deathReturnUsed) return fail("used");
        w.playerPos = new Pos(w.lastDeath.x(), w.lastDeath.y() + 1, w.lastDeath.z());
        w.deathReturnUsed = true;
        return ok(data("x", w.playerPos.x(), "y", w.playerPos.y(), "z", w.playerPos.z()));
    }

    private static Tool tool(ToolSpec spec, BiFunction<String, JsonObject, ToolResult> run) {
        return tool(spec, run, null);
    }

    private static Tool tool(ToolSpec spec, BiFunction<String, JsonObject, ToolResult> run, ToIntBiFunction<String, JsonObject> cost) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return spec;
            }

            @Override
            public ToolResult run(String player, JsonObject args) {
                return run.apply(player, args);
            }

            @Override
            public JsonObject normalize(JsonObject args) {
                return ToolSpecs.normalizeArgs(args);
            }

            @Override
            public int cost(String player, JsonObject args) {
                return cost == null ? spec.credits() : cost.applyAsInt(player, args);
            }
        };
    }
}
