package dev.agentbuddy.client;

import dev.agentbuddy.AgentBuddy;
import dev.agentbuddy.ModRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only entry point; never loaded on a dedicated server. */
@Mod(value = AgentBuddy.MODID, dist = Dist.CLIENT)
public class AgentBuddyClient {
    public AgentBuddyClient(IEventBus modEventBus) {
        modEventBus.addListener(AgentBuddyClient::registerLayers);
        modEventBus.addListener(AgentBuddyClient::registerRenderers);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AgentModel.LAYER, AgentModel::createBodyLayer);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistries.AGENT.get(), AgentRenderer::new);
    }
}
