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
import dev.agentbuddy.data.AgentStore;
import dev.agentbuddy.data.CreditStore;
import dev.agentbuddy.entity.AgentEntity;
import dev.agentbuddy.network.AgentScreenData;
import dev.agentbuddy.tool.DeathReturnTool;
import dev.agentbuddy.tool.FetchItemTool;
import dev.agentbuddy.tool.FindOreTool;
import dev.agentbuddy.tool.FindItemTool;
import dev.agentbuddy.tool.IgnoreTool;
import dev.agentbuddy.tool.ListContainerTool;
import dev.agentbuddy.tool.LookAtTool;
import dev.agentbuddy.tool.ModeTool;
import dev.agentbuddy.tool.SetNameTool;
import dev.agentbuddy.tool.SnapshotTool;
import dev.agentbuddy.tool.UnignoreTool;
import dev.agentbuddy.tool.WaystoneTools;
import java.time.Clock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
        // Registration order matters for routing (see brain Router); it follows ToolSpecs.ALL.
        List<Tool> tools = List.of(new LookAtTool(this), new FindOreTool(this), new IgnoreTool(this), new UnignoreTool(this),
                new ListContainerTool(this), new FindItemTool(this), new FetchItemTool(this), new SnapshotTool(this),
                new WaystoneTools.Set(this), new WaystoneTools.Teleport(this), new WaystoneTools.Remove(this),
                new DeathReturnTool(this), new SetNameTool(this), new ModeTool(this, true), new ModeTool(this, false));
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

    /** A player and the agent they currently control. */
    public record Controlled(ServerPlayer player, AgentEntity agent) {
    }

    /** Resolves a tool's player key to the online player and their agent; null if either is missing. */
    public Controlled controlled(String playerKey) {
        ServerPlayer p = player(playerKey);
        AgentEntity a = p == null ? null : agentFor(p);
        return a == null ? null : new Controlled(p, a);
    }

    /**
     * The agent record a player's virtual features (waystones) use: the loaded agent if there is one, else the
     * last selected agent, else any agent they own. Null if they have none.
     */
    public UUID agentIdFor(ServerPlayer player) {
        AgentEntity loaded = agentFor(player);
        if (loaded != null && loaded.agentId() != null) return loaded.agentId();
        UUID selected = selectedAgent.get(player.getUUID());
        AgentStore store = AgentStore.get(server);
        if (selected != null && store.get(selected) != null && store.get(selected).owner.equals(player.getUUID())) return selected;
        return store.anyOwnedBy(player.getUUID());
    }

    /**
     * Teleports the player; a following agent next to them comes along if the destination is in the same
     * dimension (one in another dimension stays where it is).
     */
    public void teleport(ServerPlayer player, ServerLevel dest, BlockPos feet, float yaw) {
        AgentEntity agent = agentFor(player);
        boolean bring = agent != null && agent.isFollowing() && agent.level() == dest && agent.distanceTo(player) < 24;
        player.teleportTo(dest, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, player.getXRot());
        if (bring) {
            BlockPos spot = dev.agentbuddy.world.SafeSpot.find(dest, feet, 3, 1.5);
            if (spot != null) {
                agent.getNavigation().stop();
                agent.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, agent.getYRot(), agent.getXRot());
            }
        }
    }

    public AgentStore.Record record(AgentEntity agent) {
        return agent.agentId() == null ? null : AgentStore.get(server).get(agent.agentId());
    }

    /** True for blocks this agent ignores. Ignored blocks do not exist for its perception (AGENT_SPEC.md section 4). */
    public Predicate<BlockPos> ignoredBy(AgentEntity agent, ServerLevel level) {
        AgentStore.Record r = record(agent);
        if (r == null) return pos -> false;
        return pos -> r.isIgnored(GlobalPos.of(level.dimension(), pos));
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
        AgentStore.Record r = record(agent);
        List<BlockPos> ignored = r == null ? List.of() : r.ignored().stream()
                .filter(g -> g.dimension().equals(player.level().dimension()))
                .map(GlobalPos::pos).limit(AgentScreenData.MAX_IGNORED).toList();
        List<AgentScreenData.WaystoneInfo> waystones = r == null ? List.of() : r.waystones().stream()
                .map(w -> new AgentScreenData.WaystoneInfo(w.name(), w.pos().dimension().equals(player.level().dimension())
                        ? (int) Math.round(player.position().distanceTo(w.pos().pos().getCenter())) : -1))
                .toList();
        boolean canReturn = player.getLastDeathLocation()
                .map(d -> !dev.agentbuddy.data.DeathReturnStore.get(server).usedFor(player.getUUID(), d)).orElse(false);
        return new AgentScreenData(agent.agentId(), name, agent.isFollowing(), credits(player), message,
                new AgentScreenData.Lists(ignored, waystones, canReturn));
    }
}
