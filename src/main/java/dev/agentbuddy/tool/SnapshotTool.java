package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import dev.agentbuddy.network.ContainerSnapshot;
import dev.agentbuddy.server.AgentService;
import dev.agentbuddy.world.Containers;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opens a read-only "photo" of a container on the player's screen. Nothing in the world changes. */
public final class SnapshotTool implements Tool {
    private final AgentService service;

    public SnapshotTool(AgentService service) {
        this.service = service;
    }

    @Override
    public ToolSpec spec() {
        return ToolSpecs.SNAPSHOT_CONTAINER;
    }

    @Override
    public ToolResult run(String playerKey, JsonObject args) {
        AgentService.Controlled c = service.controlled(playerKey);
        if (c == null) return ToolResult.fail(ToolResult.NO_AGENT);
        BlockPos target = Targets.target(c.player(), args);
        if (target == null) return ToolResult.fail(ToolResult.NOTHING_IN_SIGHT);
        ToolResult r = ListContainerTool.read(service, c, target, spec().range());
        if (!r.ok()) return r;
        BlockPos p = new BlockPos(r.integer("x"), r.integer("y"), r.integer("z"));
        ContainerSnapshot snap = new ContainerSnapshot(r.str("container"), p, Containers.stacks(c.player().serverLevel(), p));
        // Mock players in game tests have no modded channel; a real client always does.
        if (c.player().connection.hasChannel(ContainerSnapshot.TYPE)) PacketDistributor.sendToPlayer(c.player(), snap);
        return ToolResult.ok(ToolResult.data("container", r.str("container"), "x", p.getX(), "y", p.getY(), "z", p.getZ()));
    }
}
