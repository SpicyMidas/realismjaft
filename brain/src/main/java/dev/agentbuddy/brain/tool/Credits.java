package dev.agentbuddy.brain.tool;

import java.util.HashMap;
import java.util.Map;

/** Per-player credit balances (earned from XP in game). The mod persists them via the change listener. */
public final class Credits {
    private final Map<String, Integer> balances = new HashMap<>();
    private final Runnable onChange;

    public Credits() {
        this(() -> { });
    }

    public Credits(Runnable onChange) {
        this.onChange = onChange;
    }

    public synchronized int balance(String player) {
        return balances.getOrDefault(player, 0);
    }

    public synchronized void set(String player, int amount) {
        balances.put(player, Math.max(0, amount));
        onChange.run();
    }

    public synchronized void add(String player, int amount) {
        set(player, balance(player) + amount);
    }

    /** Returns false (and changes nothing) if the player cannot afford it. */
    public synchronized boolean tryDebit(String player, int amount) {
        int have = balance(player);
        if (have < amount) return false;
        set(player, have - amount);
        return true;
    }

    public synchronized Map<String, Integer> snapshot() {
        return Map.copyOf(balances);
    }
}
