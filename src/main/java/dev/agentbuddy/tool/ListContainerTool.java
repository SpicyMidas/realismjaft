package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import dev.agentbuddy.server.AgentService;
import dev.agentbuddy.world.Containers;
import net.minecraft.core.BlockPos;

public final class ListContainerTool implements Tool {
    private final AgentService service;

    public ListContainerTool(AgentService service) {
        this.service = service;
    }

    @Override
    public ToolSpec spec() {
        return ToolSpecs.LIST_CONTAINER;
    }

    @Override
    public ToolResult run(String playerKey, JsonObject args) {
        AgentService.Controlled c = service.controlled(playerKey);
        if (c == null) return ToolResult.fail(ToolResult.NO_AGENT);
        BlockPos target = Targets.target(c.player(), args);
        if (target == null) return ToolResult.fail(ToolResult.NOTHING_IN_SIGHT);
        return read(service, c, target, spec().range());
    }

    /** Shared with SnapshotTool: the container at target as this agent may see it. */
    static ToolResult read(AgentService service, AgentService.Controlled c, BlockPos target, int range) {
        var level = c.player().serverLevel();
        Containers.Info info = Containers.read(level, target);
        if (info == null || service.ignoredBy(c.agent(), level).test(info.pos())) return ToolResult.fail(ToolResult.NOT_FOUND);
        if (!Targets.inRange(c.player(), info.pos(), range)) return ToolResult.fail(ToolResult.OUT_OF_RANGE);
        if (info.lootUnknown()) return ToolResult.fail("loot_unknown");
        BlockPos p = info.pos();
        return ToolResult.ok(ToolResult.data("container", info.typeId(), "x", p.getX(), "y", p.getY(), "z", p.getZ(), "items", info.items()));
    }
}
