package dev.agentbuddy.brain.tool;

import java.util.HashMap;
import java.util.Map;

/** Per-player credit balances (earned from XP in game; persistence is the mod's job). */
public final class Credits {
    private final Map<String, Integer> balances = new HashMap<>();

    public synchronized int balance(String player) {
        return balances.getOrDefault(player, 0);
    }

    public synchronized void set(String player, int amount) {
        balances.put(player, Math.max(0, amount));
    }

    public synchronized void add(String player, int amount) {
        set(player, balance(player) + amount);
    }

    /** Returns false (and changes nothing) if the player cannot afford it. */
    public synchronized boolean tryDebit(String player, int amount) {
        int have = balance(player);
        if (have < amount) return false;
        balances.put(player, have - amount);
        return true;
    }
}
