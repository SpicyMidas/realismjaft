package dev.agentbuddy.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.agentbuddy.AgentBuddy;
import dev.agentbuddy.ModRegistries;
import dev.agentbuddy.network.OpenAgentScreenRequest;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only entry point; never loaded on a dedicated server. */
@Mod(value = AgentBuddy.MODID, dist = Dist.CLIENT)
public class AgentBuddyClient {
    // C as in the spec. Vanilla also binds C (save hotbar, creative only), so Controls shows it red until rebound.
    static final KeyMapping OPEN_AGENT = new KeyMapping("key.agentbuddy.open", InputConstants.KEY_C, "key.categories.agentbuddy");

    public AgentBuddyClient(IEventBus modEventBus) {
        modEventBus.addListener(AgentBuddyClient::registerLayers);
        modEventBus.addListener(AgentBuddyClient::registerRenderers);
        modEventBus.addListener(AgentBuddyClient::registerKeys);
        NeoForge.EVENT_BUS.addListener(AgentBuddyClient::onClientTick);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AgentModel.LAYER, AgentModel::createBodyLayer);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistries.AGENT.get(), AgentRenderer::new);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_AGENT);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_AGENT.consumeClick()) {
            if (mc.player != null && mc.screen == null) PacketDistributor.sendToServer(new OpenAgentScreenRequest());
        }
    }
}
