package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import dev.agentbuddy.data.AgentStore;
import dev.agentbuddy.entity.AgentEntity;
import dev.agentbuddy.server.AgentService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SetNameTool implements Tool {
    public static final int MAX_LENGTH = 24;
    private final AgentService service;

    public SetNameTool(AgentService service) {
        this.service = service;
    }

    @Override
    public ToolSpec spec() {
        return ToolSpecs.SET_NAME;
    }

    @Override
    public ToolResult run(String playerKey, JsonObject args) {
        ServerPlayer p = service.player(playerKey);
        AgentEntity agent = p == null ? null : service.agentFor(p);
        if (agent == null) return ToolResult.fail(ToolResult.NO_AGENT);
        String name = sanitize(args.get("name").getAsString());
        if (name.isEmpty()) return ToolResult.fail("invalid_name");
        agent.setCustomName(Component.literal(name));
        AgentStore.get(service.server()).setName(agent.agentId(), name);
        return ToolResult.ok(ToolResult.data("name", name));
    }

    /** Drops formatting codes and control characters, collapses spaces, caps the length. */
    static String sanitize(String raw) {
        String s = raw.replaceAll("§.", "").replaceAll("\\p{Cntrl}", "").replaceAll("\\s+", " ").trim();
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH).trim() : s;
    }
}
