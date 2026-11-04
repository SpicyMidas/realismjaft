package dev.agentbuddy.network;

import dev.agentbuddy.AgentBuddy;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: open (or refresh) the Agent screen. {@code message} is the last tool reply, or empty. */
public record AgentScreenData(UUID agentId, String name, boolean following, int credits, String message) implements CustomPacketPayload {
    public static final Type<AgentScreenData> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AgentBuddy.MODID, "agent_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AgentScreenData> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, AgentScreenData::agentId,
            ByteBufCodecs.stringUtf8(64), AgentScreenData::name,
            ByteBufCodecs.BOOL, AgentScreenData::following,
            ByteBufCodecs.VAR_INT, AgentScreenData::credits,
            ByteBufCodecs.stringUtf8(256), AgentScreenData::message,
            AgentScreenData::new);

    @Override
    public Type<AgentScreenData> type() {
        return TYPE;
    }
}
