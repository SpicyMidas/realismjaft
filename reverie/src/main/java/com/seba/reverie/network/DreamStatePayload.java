package com.seba.reverie.network;

import com.seba.reverie.Reverie;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: whether the player is dreaming and how long is left (drives the end-of-dream vignette and sky Eye). */
public record DreamStatePayload(boolean active, int ticksLeft, int totalTicks) implements CustomPacketPayload {
    public static final Type<DreamStatePayload> TYPE = new Type<>(Reverie.id("dream_state"));
    public static final StreamCodec<ByteBuf, DreamStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DreamStatePayload::active,
            ByteBufCodecs.VAR_INT, DreamStatePayload::ticksLeft,
            ByteBufCodecs.VAR_INT, DreamStatePayload::totalTicks,
            DreamStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
