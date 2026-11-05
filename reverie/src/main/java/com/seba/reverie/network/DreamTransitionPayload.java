package com.seba.reverie.network;

import com.seba.reverie.Reverie;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: fade transition. 0 = falling asleep into a dream, 1 = waking up. */
public record DreamTransitionPayload(int kind) implements CustomPacketPayload {
    public static final int ENTER = 0;
    public static final int WAKE = 1;
    public static final int FLASH = 2;
    public static final Type<DreamTransitionPayload> TYPE = new Type<>(Reverie.id("dream_transition"));
    public static final StreamCodec<ByteBuf, DreamTransitionPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(DreamTransitionPayload::new, DreamTransitionPayload::kind);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
