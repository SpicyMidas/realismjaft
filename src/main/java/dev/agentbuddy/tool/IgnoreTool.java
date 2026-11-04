package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import dev.agentbuddy.data.AgentStore;
import dev.agentbuddy.server.AgentService;
import dev.agentbuddy.world.Containers;
import dev.agentbuddy.world.Sight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;

/** Adds the block under the crosshair to the agent's ignore list (a double chest is stored once). */
public final class IgnoreTool implements Tool {
    private final AgentService service;

    public IgnoreTool(AgentService service) {
        this.service = service;
    }

    @Override
    public ToolSpec spec() {
        return ToolSpecs.IGNORE_BLOCK;
    }

    @Override
    public ToolResult run(String playerKey, JsonObject args) {
        AgentService.Controlled c = service.controlled(playerKey);
        if (c == null) return ToolResult.fail(ToolResult.NO_AGENT);
        BlockPos hit = Sight.block(c.player());
        if (hit == null) return ToolResult.fail(ToolResult.NOTHING_IN_SIGHT);
        var level = c.player().serverLevel();
        BlockPos pos = Containers.canonical(level, hit);
        String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
        if (!AgentStore.get(service.server()).ignore(c.agent().agentId(), GlobalPos.of(level.dimension(), pos))) {
            return ToolResult.fail("already");
        }
        return ToolResult.ok(ToolResult.data("block", block, "x", pos.getX(), "y", pos.getY(), "z", pos.getZ()));
    }
}
