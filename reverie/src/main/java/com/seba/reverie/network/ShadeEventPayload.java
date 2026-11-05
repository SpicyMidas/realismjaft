package com.seba.reverie.network;

import com.seba.reverie.Reverie;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: a dream phenomenon starts on this client (The Hush, weather, captions...). */
public record ShadeEventPayload(int event, int durationTicks) implements CustomPacketPayload {
    public static final Type<ShadeEventPayload> TYPE = new Type<>(Reverie.id("shade_event"));
    public static final StreamCodec<ByteBuf, ShadeEventPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShadeEventPayload::event, ByteBufCodecs.VAR_INT, ShadeEventPayload::durationTicks,
            ShadeEventPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
