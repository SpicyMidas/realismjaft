package dev.agentbuddy.brain;

import dev.agentbuddy.brain.llm.ChatMessage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Last N turns per player (player text + final reply only; no tool traffic, to keep the prompt small). */
public final class History {
    private record Turn(String user, String reply) {
    }

    private final int maxTurns;
    private final Map<String, Deque<Turn>> turns = new HashMap<>();

    public History(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    public synchronized void add(String player, String user, String reply) {
        Deque<Turn> d = turns.computeIfAbsent(player, k -> new ArrayDeque<>());
        d.addLast(new Turn(user, reply));
        while (d.size() > maxTurns) d.removeFirst();
    }

    public synchronized List<ChatMessage> messages(String player) {
        List<ChatMessage> out = new ArrayList<>();
        for (Turn t : turns.getOrDefault(player, new ArrayDeque<>())) {
            out.add(ChatMessage.user("Player: " + t.user()));
            out.add(ChatMessage.assistant(t.reply()));
        }
        return out;
    }

    public synchronized void clear(String player) {
        turns.remove(player);
    }
}
