package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import dev.agentbuddy.data.DeathReturnStore;
import dev.agentbuddy.server.AgentService;
import dev.agentbuddy.world.SafeSpot;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Teleports the player to a safe spot near their last death (vanilla's last death location, the one the recovery
 * compass uses), once per death, any dimension. No agent needs to be nearby: after a death it usually is not.
 */
public final class DeathReturnTool implements Tool {
    public static final int SEARCH_RADIUS = 8;
    private final AgentService service;

    public DeathReturnTool(AgentService service) {
        this.service = service;
    }

    @Override
    public ToolSpec spec() {
        return ToolSpecs.DEATH_RETURN;
    }

    @Override
    public ToolResult run(String playerKey, JsonObject args) {
        ServerPlayer p = service.player(playerKey);
        if (p == null) return ToolResult.fail(ToolResult.NOT_FOUND);
        Optional<GlobalPos> death = p.getLastDeathLocation();
        if (death.isEmpty()) return ToolResult.fail(ToolResult.NOT_FOUND);
        DeathReturnStore used = DeathReturnStore.get(service.server());
        if (used.usedFor(p.getUUID(), death.get())) return ToolResult.fail("used");
        ServerLevel level = service.server().getLevel(death.get().dimension());
        if (level == null) return ToolResult.fail(ToolResult.NOT_FOUND);
        BlockPos spot = SafeSpot.find(level, death.get().pos(), SEARCH_RADIUS);
        if (spot == null) return ToolResult.fail("no_safe_spot");
        service.teleport(p, level, spot, p.getYRot());
        used.markUsed(p.getUUID(), death.get());
        return ToolResult.ok(ToolResult.data("x", spot.getX(), "y", spot.getY(), "z", spot.getZ()));
    }
}
