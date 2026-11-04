package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import dev.agentbuddy.server.AgentService;
import dev.agentbuddy.world.Containers;
import dev.agentbuddy.world.ItemNames;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/** Which known containers (loaded, within range, not ignored, contents known) hold an item. */
public final class FindItemTool implements Tool {
    private final AgentService service;

    public FindItemTool(AgentService service) {
        this.service = service;
    }

    @Override
    public ToolSpec spec() {
        return ToolSpecs.FIND_ITEM;
    }

    @Override
    public JsonObject normalize(JsonObject args) {
        return ToolSpecs.normalizeArgs(args);
    }

    /** Result of a lookup shared with FetchItemTool. {@code best} is null when nothing holds the item. */
    record Lookup(ToolResult failure, Item item, Containers.Info best, int holders) {
    }

    static Lookup lookup(AgentService service, AgentService.Controlled c, String name, int range) {
        var level = c.player().serverLevel();
        List<Containers.Info> visible = Containers.near(level, c.player().position(), range, service.ignoredBy(c.agent(), level));
        Optional<Item> item = ItemNames.resolve(name);
        if (visible.isEmpty()) return new Lookup(ToolResult.fail("no_containers", ToolResult.data("item", item.map(FindItemTool::id).orElse(name))), null, null, 0);
        if (item.isEmpty()) return new Lookup(ToolResult.fail(ToolResult.NOT_FOUND, ToolResult.data("item", name)), null, null, 0);
        String id = id(item.get());
        List<Containers.Info> holding = visible.stream().filter(i -> i.count(id) > 0).toList();
        if (holding.isEmpty()) return new Lookup(ToolResult.fail(ToolResult.NOT_FOUND, ToolResult.data("item", id)), item.get(), null, 0);
        // Most of the item first; ties go to the nearer container (list is nearest-first and the sort is stable).
        Containers.Info best = holding.stream().max(Comparator.comparingInt(i -> i.count(id))).orElseThrow();
        return new Lookup(null, item.get(), best, holding.size());
    }

    static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    @Override
    public ToolResult run(String playerKey, JsonObject args) {
        AgentService.Controlled c = service.controlled(playerKey);
        if (c == null) return ToolResult.fail(ToolResult.NO_AGENT);
        Lookup l = lookup(service, c, args.get("item").getAsString(), spec().range());
        if (l.failure() != null) return l.failure();
        BlockPos p = l.best().pos();
        String id = id(l.item());
        return ToolResult.ok(ToolResult.data("item", id, "count", l.best().count(id), "container", l.best().typeId(),
                "x", p.getX(), "y", p.getY(), "z", p.getZ(), "other_containers", l.holders() - 1));
    }
}
