package dev.agentbuddy.client;

import dev.agentbuddy.network.AgentScreenData;
import net.minecraft.client.Minecraft;

/** Client-only payload handling. Only referenced from a lambda, so a server never loads it. */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {
    }

    public static void onScreenData(AgentScreenData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AgentScreen screen && screen.agentId().equals(data.agentId())) {
            screen.update(data);
        } else {
            mc.setScreen(new AgentScreen(data));
        }
    }
}
