package dev.agentbuddy.server;

import com.google.gson.JsonObject;
import dev.agentbuddy.ModRegistries;
import dev.agentbuddy.brain.reply.Templates;
import dev.agentbuddy.brain.tool.Cooldowns;
import dev.agentbuddy.brain.tool.Credits;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolRegistry;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolRunner;
import dev.agentbuddy.data.CreditStore;
import dev.agentbuddy.entity.AgentEntity;
import dev.agentbuddy.network.AgentScreenData;
import dev.agentbuddy.tool.LookAtTool;
import dev.agentbuddy.tool.ModeTool;
import dev.agentbuddy.tool.SetNameTool;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side runtime: the tool runner (credits, cooldowns), which agent each player controls, and XP-to-credit
 * conversion. Everything here runs on the server thread. One instance per running server.
 */
public final class AgentService {
    public static final int XP_PER_CREDIT = 4;
    public static final int MAX_CREDITS = 999;
    /** An agent must be this close to its owner to take orders. */
    public static final double CONTROL_RANGE = 64.0;

    private static AgentService instance;

    private final MinecraftServer server;
    private final ToolRunner runner;
    private final Credits credits;
    private final Map<UUID, UUID> selectedAgent = new HashMap<>();
    private final Map<UUID, Integer> xpRemainder = new HashMap<>();

    private AgentService(MinecraftServer server) {
        this.server = server;
        this.credits = CreditStore.get(server).credits();
        List<Tool> tools = List.of(new LookAtTool(this), new SetNameTool(this), new ModeTool(this, true), new ModeTool(this, false));
        this.runner = new ToolRunner(new ToolRegistry(tools), credits, new Cooldowns(Clock.systemUTC()));
    }

    public static void start(MinecraftServer server) {
        instance = new AgentService(server);
    }

    public static void stop() {
        instance = null;
    }

    public static AgentService get() {
        if (instance == null) throw new IllegalStateException("AgentService used before server start");
        return instance;
    }

    public MinecraftServer server() {
        return server;
    }

    public ToolRunner runner() {
        return runner;
    }

    /** Tools receive the player as a UUID string (the brain's player key). */
    public ServerPlayer player(String playerKey) {
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(playerKey));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void select(ServerPlayer player, UUID agentId) {
        if (agentId != null) selectedAgent.put(player.getUUID(), agentId);
    }

    /** The player's selected agent if it is loaded and in range, else their nearest agent in range, else null. */
    public AgentEntity agentFor(ServerPlayer player) {
        List<AgentEntity> owned = player.serverLevel().getEntities(ModRegistries.AGENT.get(),
                player.getBoundingBox().inflate(CONTROL_RANGE), a -> a.isAlive() && player.getUUID().equals(a.owner()));
        UUID selected = selectedAgent.get(player.getUUID());
        return owned.stream().filter(a -> a.agentId() != null && a.agentId().equals(selected)).findFirst()
                .orElseGet(() -> owned.stream().min(Comparator.comparingDouble(a -> a.distanceToSqr(player))).orElse(null));
    }

    /** Validates and runs one tool for the player; returns the template reply. */
    public String runTool(ServerPlayer player, String tool, JsonObject args) {
        ToolRunner.Validation v = runner.validate(tool, args);
        if (!v.valid()) return Templates.DIDNT_UNDERSTAND;
        ToolResult r = runner.execute(player.getUUID().toString(), v.call());
        return Templates.render(tool, r);
    }

    public int credits(ServerPlayer player) {
        return credits.balance(player.getUUID().toString());
    }

    public void onXpPickup(ServerPlayer player, int xp) {
        int total = xpRemainder.getOrDefault(player.getUUID(), 0) + xp;
        xpRemainder.put(player.getUUID(), total % XP_PER_CREDIT);
        int earned = total / XP_PER_CREDIT;
        if (earned > 0) {
            String key = player.getUUID().toString();
            credits.set(key, Math.min(MAX_CREDITS, credits.balance(key) + earned));
        }
    }

    public AgentScreenData screenData(ServerPlayer player, AgentEntity agent, String message) {
        String name = agent.hasCustomName() ? agent.getCustomName().getString() : "Agent";
        return new AgentScreenData(agent.agentId(), name, agent.isFollowing(), credits(player), message);
    }
}
