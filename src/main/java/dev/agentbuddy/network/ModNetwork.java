package dev.agentbuddy.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.agentbuddy.client.ClientPayloadHandler;
import dev.agentbuddy.entity.AgentEntity;
import dev.agentbuddy.server.AgentService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration and server-side handlers (handlers run on the main thread, NeoForge's default). */
public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(OpenAgentScreenRequest.TYPE, OpenAgentScreenRequest.STREAM_CODEC, ModNetwork::onOpenScreen);
        r.playToServer(ToolRequest.TYPE, ToolRequest.STREAM_CODEC, ModNetwork::onToolRequest);
        // The lambda body only links ClientPayloadHandler when a packet actually arrives, i.e. on a client.
        r.playToClient(AgentScreenData.TYPE, AgentScreenData.STREAM_CODEC, (data, ctx) -> ClientPayloadHandler.onScreenData(data));
        r.playToClient(ContainerSnapshot.TYPE, ContainerSnapshot.STREAM_CODEC, (snap, ctx) -> ClientPayloadHandler.onSnapshot(snap));
    }

    private static void onOpenScreen(OpenAgentScreenRequest req, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        AgentService service = AgentService.get();
        AgentEntity agent = service.agentFor(player);
        if (agent == null) {
            player.displayClientMessage(Component.literal("No agent nearby."), true);
            return;
        }
        openScreen(player, agent, "");
    }

    private static void onToolRequest(ToolRequest req, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        AgentService service = AgentService.get();
        service.select(player, req.agentId());
        String reply = service.runTool(player, req.tool(), parseArgs(req.argsJson()));
        AgentEntity agent = service.agentFor(player);
        if (agent != null) {
            PacketDistributor.sendToPlayer(player, service.screenData(player, agent, reply));
        } else {
            player.displayClientMessage(Component.literal(reply), true);
        }
    }

    /** Sends the screen for this agent and makes it the player's selected agent. */
    public static void openScreen(ServerPlayer player, AgentEntity agent, String message) {
        AgentService service = AgentService.get();
        service.select(player, agent.agentId());
        PacketDistributor.sendToPlayer(player, service.screenData(player, agent, message));
    }

    static JsonObject parseArgs(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }
}
