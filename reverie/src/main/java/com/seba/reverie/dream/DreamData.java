package com.seba.reverie.dream;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Player attachment reverie:dream_session. Copied on death so a dream can never be lost to a respawn. */
public final class DreamData {
    public static final Codec<DreamData> CODEC = RecordCodecBuilder.create(i -> i.group(
            DreamSession.CODEC.optionalFieldOf("session").forGetter(d -> Optional.ofNullable(d.session)),
            Codec.LONG.optionalFieldOf("cooldown_until", 0L).forGetter(d -> d.cooldownUntil),
            Codec.INT.optionalFieldOf("dreams", 0).forGetter(d -> d.dreamsCompleted),
            Codec.LONG.optionalFieldOf("last_backup", 0L).forGetter(d -> d.lastBackupTime),
            Codec.BOOL.optionalFieldOf("first_lantern", false).forGetter(d -> d.firstLantern)
    ).apply(i, (s, c, n, b, l) -> {
        DreamData d = new DreamData();
        d.session = s.orElse(null);
        d.cooldownUntil = c;
        d.dreamsCompleted = n;
        d.lastBackupTime = b;
        d.firstLantern = l;
        return d;
    }));

    DreamSession session;
    long cooldownUntil;
    int dreamsCompleted;
    long lastBackupTime;
    /** The first Sky Lantern has been put out for this dreamer on their Home island. */
    boolean firstLantern;

    public DreamSession session() {
        return session;
    }

    public boolean dreaming() {
        return session != null;
    }

    public long cooldownUntil() {
        return cooldownUntil;
    }

    public int dreamsCompleted() {
        return dreamsCompleted;
    }
}
