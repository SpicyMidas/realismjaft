package dev.agentbuddy.network;

import dev.agentbuddy.AgentBuddy;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: open (or refresh) the Agent screens. {@code message} is the last tool reply, or empty. */
public record AgentScreenData(UUID agentId, String name, boolean following, int credits, String message, Lists lists)
        implements CustomPacketPayload {
    public static final int MAX_IGNORED = 16;

    /** @param distance blocks from the player, or -1 when the waystone is in another dimension */
    public record WaystoneInfo(String name, int distance) {
        static final StreamCodec<RegistryFriendlyByteBuf, WaystoneInfo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), WaystoneInfo::name,
                ByteBufCodecs.VAR_INT, WaystoneInfo::distance,
                WaystoneInfo::new);
    }

    /** @param ignored ignored blocks in the player's current dimension, at most {@link #MAX_IGNORED} */
    public record Lists(List<BlockPos> ignored, List<WaystoneInfo> waystones, boolean canReturnToDeath) {
        static final StreamCodec<RegistryFriendlyByteBuf, Lists> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_IGNORED)), Lists::ignored,
                WaystoneInfo.STREAM_CODEC.apply(ByteBufCodecs.list(16)), Lists::waystones,
                ByteBufCodecs.BOOL, Lists::canReturnToDeath,
                Lists::new);
    }

    public static final Type<AgentScreenData> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AgentBuddy.MODID, "agent_screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AgentScreenData> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, AgentScreenData::agentId,
            ByteBufCodecs.stringUtf8(64), AgentScreenData::name,
            ByteBufCodecs.BOOL, AgentScreenData::following,
            ByteBufCodecs.VAR_INT, AgentScreenData::credits,
            ByteBufCodecs.stringUtf8(256), AgentScreenData::message,
            Lists.STREAM_CODEC, AgentScreenData::lists,
            AgentScreenData::new);

    @Override
    public Type<AgentScreenData> type() {
        return TYPE;
    }
}
