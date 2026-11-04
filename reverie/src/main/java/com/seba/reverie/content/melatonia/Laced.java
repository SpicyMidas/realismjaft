package com.seba.reverie.content.melatonia;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** reverie:laced on any food stack: 1 = laced with Melatonia, 2 = strongly laced. */
public record Laced(int level) {
    public static final Codec<Laced> CODEC = Codec.intRange(1, 2).xmap(Laced::new, Laced::level);
    public static final StreamCodec<ByteBuf, Laced> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(Laced::new, Laced::level);
}
