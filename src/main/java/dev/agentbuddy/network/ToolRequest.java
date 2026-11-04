package dev.agentbuddy.network;

import dev.agentbuddy.AgentBuddy;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: run one tool (from a GUI button). The server validates everything; nothing is trusted. */
public record ToolRequest(UUID agentId, String tool, String argsJson) implements CustomPacketPayload {
    public static final int MAX_ARGS_LENGTH = 1024;
    public static final Type<ToolRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AgentBuddy.MODID, "tool_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ToolRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ToolRequest::agentId,
            ByteBufCodecs.stringUtf8(64), ToolRequest::tool,
            ByteBufCodecs.stringUtf8(MAX_ARGS_LENGTH), ToolRequest::argsJson,
            ToolRequest::new);

    @Override
    public Type<ToolRequest> type() {
        return TYPE;
    }
}
