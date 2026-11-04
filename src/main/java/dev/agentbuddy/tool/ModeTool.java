package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import dev.agentbuddy.entity.AgentEntity;
import dev.agentbuddy.server.AgentService;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

/** {@code follow} and {@code stay}. */
public final class ModeTool implements Tool {
    private final AgentService service;
    private final boolean follow;

    public ModeTool(AgentService service, boolean follow) {
        this.service = service;
        this.follow = follow;
    }

    @Override
    public ToolSpec spec() {
        return follow ? ToolSpecs.FOLLOW : ToolSpecs.STAY;
    }

    @Override
    public ToolResult run(String playerKey, JsonObject args) {
        ServerPlayer p = service.player(playerKey);
        AgentEntity agent = p == null ? null : service.agentFor(p);
        if (agent == null) return ToolResult.fail(ToolResult.NO_AGENT);
        agent.setFollowing(follow);
        if (!follow) agent.getNavigation().stop();
        return ToolResult.ok(Map.of());
    }
}
