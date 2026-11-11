package com.seba.reverie.network;

import com.seba.reverie.Reverie;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: the dreamer is under an upside-down island (centre and radius), or no longer (radius 0). */
public record UpsideDownPayload(int centerX, int centerZ, int radius) implements CustomPacketPayload {
    public static final Type<UpsideDownPayload> TYPE = new Type<>(Reverie.id("upside_down"));
    public static final StreamCodec<ByteBuf, UpsideDownPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UpsideDownPayload::centerX, ByteBufCodecs.VAR_INT, UpsideDownPayload::centerZ,
            ByteBufCodecs.VAR_INT, UpsideDownPayload::radius, UpsideDownPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
