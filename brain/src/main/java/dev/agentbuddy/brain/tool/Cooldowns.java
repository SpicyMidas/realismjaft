package dev.agentbuddy.brain.tool;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Per-player, per-tool cooldowns. */
public final class Cooldowns {
    private final Clock clock;
    private final Map<String, Instant> readyAt = new HashMap<>();

    public Cooldowns(Clock clock) {
        this.clock = clock;
    }

    public synchronized Duration remaining(String player, String tool) {
        Instant at = readyAt.get(player + "\u0000" + tool);
        if (at == null) return Duration.ZERO;
        Duration d = Duration.between(clock.instant(), at);
        return d.isNegative() ? Duration.ZERO : d;
    }

    public synchronized void start(String player, String tool, Duration cooldown) {
        if (cooldown.isZero() || cooldown.isNegative()) return;
        readyAt.put(player + "\u0000" + tool, clock.instant().plus(cooldown));
    }
}
