package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import dev.agentbuddy.data.AgentStore;
import dev.agentbuddy.server.AgentService;
import dev.agentbuddy.world.Containers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;

/** Removes a block from the ignore list: the one at x,y,z (GUI list) or under the crosshair. */
public final class UnignoreTool implements Tool {
    private final AgentService service;

    public UnignoreTool(AgentService service) {
        this.service = service;
    }

    @Override
    public ToolSpec spec() {
        return ToolSpecs.UNIGNORE_BLOCK;
    }

    @Override
    public ToolResult run(String playerKey, JsonObject args) {
        AgentService.Controlled c = service.controlled(playerKey);
        if (c == null) return ToolResult.fail(ToolResult.NO_AGENT);
        BlockPos target = Targets.target(c.player(), args);
        if (target == null) return ToolResult.fail(ToolResult.NOTHING_IN_SIGHT);
        var level = c.player().serverLevel();
        BlockPos pos = Containers.canonical(level, target);
        if (!AgentStore.get(service.server()).unignore(c.agent().agentId(), GlobalPos.of(level.dimension(), pos))) {
            return ToolResult.fail(ToolResult.NOT_FOUND);
        }
        String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        return ToolResult.ok(ToolResult.data("block", block, "x", pos.getX(), "y", pos.getY(), "z", pos.getZ()));
    }
}
