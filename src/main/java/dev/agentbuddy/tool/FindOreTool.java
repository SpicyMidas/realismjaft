package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.Pos;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import dev.agentbuddy.server.AgentService;
import dev.agentbuddy.world.Ores;
import net.minecraft.core.BlockPos;

public final class FindOreTool implements Tool {
    private final AgentService service;

    public FindOreTool(AgentService service) {
        this.service = service;
    }

    @Override
    public ToolSpec spec() {
        return ToolSpecs.FIND_ORE;
    }

    @Override
    public JsonObject normalize(JsonObject args) {
        return ToolSpecs.normalizeArgs(args);
    }

    @Override
    public ToolResult run(String playerKey, JsonObject args) {
        AgentService.Controlled c = service.controlled(playerKey);
        if (c == null) return ToolResult.fail(ToolResult.NO_AGENT);
        String ore = args.get("ore").getAsString();
        var level = c.player().serverLevel();
        BlockPos found = Ores.nearest(level, c.player().position(), ore, spec().range(), service.ignoredBy(c.agent(), level));
        if (found == null) return ToolResult.fail(ToolResult.NOT_FOUND, ToolResult.data("ore", ore, "range", spec().range()));
        BlockPos from = c.player().blockPosition();
        String dir = new Pos(from.getX(), from.getY(), from.getZ()).directionTo(new Pos(found.getX(), found.getY(), found.getZ()));
        int distance = (int) Math.round(c.player().position().distanceTo(found.getCenter()));
        return ToolResult.ok(ToolResult.data("ore", ore, "distance", distance, "dir", dir,
                "x", found.getX(), "y", found.getY(), "z", found.getZ()));
    }
}
