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
 * reverie:laced on a food (or a laced projectile): which items of the stack are laced, from the top of the stack
 * (the next one eaten) down. Each {@link Layer} is {@code count} items laced at {@code level} (1 = Melatonia Extract,
 * 2 = Concentrated), worst first; any items past the layers are plain. A layer of {@link #ALL} covers the whole
 * stack, so uniform stacks still stack with each other the vanilla way. {@code knownBy}: who can see it is laced
 * (whoever laced it, and anyone who held it 30 seconds); {@link #CRAFTER} stands for "whoever takes it from the
 * crafting grid" until they do. LacedStacks does the arithmetic. Older saves (a bare level, or level + known_by)
 * still load.
 */
public record Laced(List<Layer> layers) {
    public static final int ALL = -1;
    /** Placeholder in {@code knownBy} for the player who will take the item out of the crafting result. */
    public static final UUID CRAFTER = new UUID(0L, 0L);
    private static final int MAX_KNOWN = 8;
    private static final int MAX_LAYERS = 16;

    public record Layer(int count, int level, List<UUID> knownBy) {
        static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("count", ALL).forGetter(Layer::count),
                Codec.intRange(1, 2).fieldOf("level").forGetter(Layer::level),
                UUIDUtil.CODEC.listOf().optionalFieldOf("known_by", List.of()).forGetter(Layer::knownBy)
        ).apply(i, Layer::new));
        static final StreamCodec<ByteBuf, Layer> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Layer::count,
                ByteBufCodecs.VAR_INT, Layer::level,
                UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_KNOWN)), Layer::knownBy,
                Layer::new);

        public Layer {
            knownBy = List.copyOf(knownBy);
        }

        /** Known to {@code player}: they laced it, they held it long enough, or it is still in the crafting result. */
        public boolean knownTo(UUID player) {
            return knownBy.contains(player) || knownBy.contains(CRAFTER);
        }

        public Layer withCount(int count) {
            return new Layer(count, level, knownBy);
        }

        /** Also known to {@code player} (the oldest name drops off past eight; the crafter placeholder is replaced). */
        public Layer withKnown(UUID player) {
            if (knownBy.contains(player) && !knownBy.contains(CRAFTER)) return this;
            List<UUID> list = new ArrayList<>(knownBy);
            list.remove(CRAFTER);
            if (!list.contains(player)) list.add(player);
            while (list.size() > MAX_KNOWN) list.remove(0);
            return new Layer(count, level, list);
        }
    }

    private static final Codec<Laced> FULL = RecordCodecBuilder.create(i -> i.group(
            Layer.CODEC.listOf().fieldOf("layers").forGetter(Laced::layers)
    ).apply(i, Laced::new));
    /** Older saves: {level, known_by} for the whole stack, or just the level. */
    private static final Codec<Laced> OLD_OBJECT = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 2).fieldOf("level").forGetter(Laced::topLevel),
            UUIDUtil.CODEC.listOf().optionalFieldOf("known_by", List.of()).forGetter(l -> List.of())
    ).apply(i, (level, known) -> uniform(level, known)));
    private static final Codec<Laced> OLD_LEVEL = Codec.intRange(1, 2).xmap(level -> uniform(level, List.of()), Laced::topLevel);
    public static final Codec<Laced> CODEC = Codec.withAlternative(FULL, Codec.withAlternative(OLD_OBJECT, OLD_LEVEL));
    public static final StreamCodec<ByteBuf, Laced> STREAM_CODEC = Layer.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LAYERS))
            .map(Laced::new, Laced::layers);

    public Laced {
        layers = List.copyOf(layers);
    }

    /** The whole stack laced at {@code level}. */
    public static Laced uniform(int level, List<UUID> knownBy) {
        return new Laced(List.of(new Layer(ALL, level, knownBy)));
    }

    public Laced(int level) {
        this(List.of(new Layer(ALL, level, List.of())));
    }

    /** Level of the top item (the next one eaten or thrown), 0 if it is plain. */
    public int topLevel() {
        return layers.isEmpty() || layers.get(0).count() == 0 ? 0 : layers.get(0).level();
    }
}
