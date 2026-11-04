package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import dev.agentbuddy.data.AgentStore;
import dev.agentbuddy.server.AgentService;
import dev.agentbuddy.world.SafeSpot;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** set_waystone, remove_waystone, teleport_waystone. Waystones are virtual and live in the agent's record. */
public final class WaystoneTools {
    private WaystoneTools() {
    }

    public static final class Set implements Tool {
        private final AgentService service;

        public Set(AgentService service) {
            this.service = service;
        }

        @Override
        public ToolSpec spec() {
            return ToolSpecs.SET_WAYSTONE;
        }

        @Override
        public ToolResult run(String playerKey, JsonObject args) {
            ServerPlayer p = service.player(playerKey);
            UUID agent = p == null ? null : service.agentIdFor(p);
            if (agent == null) return ToolResult.fail(ToolResult.NO_AGENT);
            String name = SetNameTool.sanitize(args.get("name").getAsString());
            if (name.isEmpty()) return ToolResult.fail("invalid_name");
            BlockPos at = p.blockPosition();
            var result = AgentStore.get(service.server()).addWaystone(agent,
                    new AgentStore.Waystone(name, GlobalPos.of(p.level().dimension(), at), p.getYRot()));
            return switch (result) {
                case OK -> ToolResult.ok(ToolResult.data("name", name, "x", at.getX(), "y", at.getY(), "z", at.getZ()));
                case EXISTS -> ToolResult.fail("exists", ToolResult.data("name", name));
                case LIMIT -> ToolResult.fail("limit", ToolResult.data("max", AgentStore.MAX_WAYSTONES));
            };
        }
    }

    public static final class Remove implements Tool {
        private final AgentService service;

        public Remove(AgentService service) {
            this.service = service;
        }

        @Override
        public ToolSpec spec() {
            return ToolSpecs.REMOVE_WAYSTONE;
        }

        @Override
        public ToolResult run(String playerKey, JsonObject args) {
            ServerPlayer p = service.player(playerKey);
            UUID agent = p == null ? null : service.agentIdFor(p);
            if (agent == null) return ToolResult.fail(ToolResult.NO_AGENT);
            String name = args.get("name").getAsString();
            return AgentStore.get(service.server()).removeWaystone(agent, name)
                    .map(w -> ToolResult.ok(ToolResult.data("name", w.name())))
                    .orElse(ToolResult.fail(ToolResult.NOT_FOUND, ToolResult.data("name", name)));
        }
    }

    public static final class Teleport implements Tool {
        private final AgentService service;

        public Teleport(AgentService service) {
            this.service = service;
        }

        @Override
        public ToolSpec spec() {
            return ToolSpecs.TELEPORT_WAYSTONE;
        }

        private Optional<AgentStore.Waystone> find(ServerPlayer p, String name) {
            UUID agent = service.agentIdFor(p);
            AgentStore.Record r = agent == null ? null : AgentStore.get(service.server()).get(agent);
            return r == null ? Optional.empty() : r.waystone(name);
        }

        /** 1 credit plus 1 per 100 blocks, so long trips cost more (AGENT_SPEC.md section 4). */
        @Override
        public int cost(String playerKey, JsonObject args) {
            ServerPlayer p = service.player(playerKey);
            if (p == null) return spec().credits();
            return find(p, args.get("name").getAsString())
                    .filter(w -> w.pos().dimension().equals(p.level().dimension()))
                    .map(w -> spec().credits() + (int) (p.position().distanceTo(w.pos().pos().getCenter()) / 100))
                    .orElse(spec().credits());
        }

        @Override
        public ToolResult run(String playerKey, JsonObject args) {
            ServerPlayer p = service.player(playerKey);
            if (p == null || service.agentIdFor(p) == null) return ToolResult.fail(ToolResult.NO_AGENT);
            String name = args.get("name").getAsString();
            Optional<AgentStore.Waystone> w = find(p, name);
            if (w.isEmpty()) return ToolResult.fail(ToolResult.NOT_FOUND, ToolResult.data("name", name));
            String shown = w.get().name();
            if (!w.get().pos().dimension().equals(p.level().dimension())) {
                return ToolResult.fail("other_dimension", ToolResult.data("name", shown));
            }
            ServerLevel level = p.serverLevel();
            BlockPos spot = SafeSpot.find(level, w.get().pos().pos(), 4);
            if (spot == null) return ToolResult.fail("no_safe_spot", ToolResult.data("name", shown));
            service.teleport(p, level, spot, w.get().yaw());
            return ToolResult.ok(ToolResult.data("name", shown, "x", spot.getX(), "y", spot.getY(), "z", spot.getZ()));
        }
    }
}
