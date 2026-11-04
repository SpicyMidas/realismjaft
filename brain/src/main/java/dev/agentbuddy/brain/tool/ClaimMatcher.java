package dev.agentbuddy.brain.tool;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Decides in Java whether the player's claim ("this is wood, right?") matches the real block, so agreement is never
 * the model's call. Word-based: every claim word must appear in the block id or one of its tags, after aliases.
 * "iron ore" matches deepslate_iron_ore; "stone" does not match redstone_ore or cobblestone.
 */
public final class ClaimMatcher {
    private static final Set<String> NOISE = Set.of("a", "an", "the", "this", "that", "it", "is", "block", "blocks",
            "some", "kind", "of", "type", "minecraft", "right", "here", "there");
    // A claim word on the left is satisfied by any of these block/tag words.
    private static final Map<String, List<String>> ALIASES = Map.of(
            "wood", List.of("wood", "log", "plank", "stem", "hyphae"),
            "wooden", List.of("wood", "log", "plank", "stem", "hyphae"),
            "dirt", List.of("dirt", "grass", "podzol", "mycelium"),
            "rock", List.of("stone", "deepslate", "andesite", "diorite", "granite", "tuff"),
            "lapis", List.of("lapis", "lazuli"));

    private ClaimMatcher() {
    }

    /** True if the claim has no meaningful words (treated as "no claim"). */
    public static boolean isEmpty(String claim) {
        return words(claim).isEmpty();
    }

    public static boolean matches(String claim, String blockId, Collection<String> tags) {
        Set<String> claimWords = words(claim);
        if (claimWords.isEmpty()) return true;
        Set<String> have = new HashSet<>(words(blockId));
        for (String t : tags) have.addAll(words(t));
        for (String w : claimWords) {
            List<String> accepted = ALIASES.getOrDefault(w, List.of(w));
            if (accepted.stream().noneMatch(have::contains)) return false;
        }
        return true;
    }

    private static Set<String> words(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        String cleaned = s.toLowerCase(Locale.ROOT).replaceAll("[a-z0-9_]+:", "").replaceAll("[^a-z0-9]+", " ");
        for (String w : cleaned.trim().split("\\s+")) {
            if (w.isEmpty() || NOISE.contains(w)) continue;
            out.add(singular(w));
        }
        return out;
    }

    private static String singular(String w) {
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }
}
