package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import dev.agentbuddy.server.AgentService;
import net.minecraft.core.BlockPos;

/**
 * Sends the agent to the container holding the most of an item; it flies there, takes the items and brings them
 * back (see AgentEntity fetch task). The reply is given now; the items arrive later.
 */
public final class FetchItemTool implements Tool {
    private final AgentService service;

    public FetchItemTool(AgentService service) {
        this.service = service;
    }

    @Override
    public ToolSpec spec() {
        return ToolSpecs.FETCH_ITEM;
    }

    @Override
    public JsonObject normalize(JsonObject args) {
        return ToolSpecs.normalizeArgs(args);
    }

    @Override
    public ToolResult run(String playerKey, JsonObject args) {
        AgentService.Controlled c = service.controlled(playerKey);
        if (c == null) return ToolResult.fail(ToolResult.NO_AGENT);
        if (c.agent().isFetching()) return ToolResult.fail("busy");
        FindItemTool.Lookup l = FindItemTool.lookup(service, c, args.get("item").getAsString(), spec().range());
        if (l.failure() != null) return l.failure();
        if (c.player().getInventory().getFreeSlot() < 0) return ToolResult.fail("inventory_full");
        String id = FindItemTool.id(l.item());
        int available = l.best().count(id);
        int want = args.has("count") ? args.get("count").getAsInt() : Math.min(available, l.item().getDefaultMaxStackSize());
        int count = Math.min(want, available);
        BlockPos p = l.best().pos();
        c.agent().startFetch(p, l.item(), count);
        return ToolResult.ok(ToolResult.data("item", id, "count", count, "container", l.best().typeId(),
                "x", p.getX(), "y", p.getY(), "z", p.getZ()));
    }
}
