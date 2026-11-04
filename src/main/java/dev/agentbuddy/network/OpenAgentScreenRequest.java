package dev.agentbuddy.network;

import dev.agentbuddy.AgentBuddy;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: the player pressed the Agent key. */
public record OpenAgentScreenRequest() implements CustomPacketPayload {
    public static final Type<OpenAgentScreenRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AgentBuddy.MODID, "open_agent_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAgentScreenRequest> STREAM_CODEC = StreamCodec.unit(new OpenAgentScreenRequest());

    @Override
    public Type<OpenAgentScreenRequest> type() {
        return TYPE;
    }
}
