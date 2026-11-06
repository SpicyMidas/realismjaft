package com.seba.reverie.content.melatonia;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * reverie:laced on a food or a projectile. {@code level} 1 = laced with Melatonia Extract, 2 = with Concentrated
 * Extract. {@code knownBy}: the players who know (whoever laced it, and anyone who held it in hand for 30 seconds);
 * only they see the "(Laced)" tag. Old saves stored just the level.
 */
public record Laced(int level, List<UUID> knownBy) {
    private static final int MAX_KNOWN = 8;

    private static final Codec<Laced> FULL = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 2).fieldOf("level").forGetter(Laced::level),
            UUIDUtil.CODEC.listOf().optionalFieldOf("known_by", List.of()).forGetter(Laced::knownBy)
    ).apply(i, Laced::new));
    public static final Codec<Laced> CODEC = Codec.withAlternative(FULL, Codec.intRange(1, 2).xmap(l -> new Laced(l, List.of()), Laced::level));
    public static final StreamCodec<ByteBuf, Laced> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Laced::level,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_KNOWN)), Laced::knownBy,
            Laced::new);

    public Laced {
        knownBy = List.copyOf(knownBy);
    }

    public Laced(int level) {
        this(level, List.of());
    }

    public boolean knownTo(UUID player) {
        return knownBy.contains(player);
    }

    /** The same lacing, now also known to {@code player} (the oldest name drops off past eight). */
    public Laced withKnown(UUID player) {
        if (knownTo(player)) return this;
        List<UUID> list = new ArrayList<>(knownBy);
        list.add(player);
        while (list.size() > MAX_KNOWN) list.remove(0);
        return new Laced(level, list);
    }
}
