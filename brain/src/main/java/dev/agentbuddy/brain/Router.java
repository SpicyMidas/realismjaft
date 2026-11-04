package dev.agentbuddy.brain;

import dev.agentbuddy.brain.tool.Category;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic keyword routing so a small model sees at most {@code maxTools} tools (AGENT_SPEC.md section 2).
 * {@code look_at} is always offered because "this/that" questions can come with any wording.
 */
public final class Router {
    /**
     * @param categories   matched categories, best first; empty if nothing matched
     * @param toolExpected the message is about the game world, so a free-text answer is rejected once
     */
    public record Route(List<Category> categories, boolean toolExpected) {
    }

    private static final Map<Category, List<Pattern>> KEYWORDS = new EnumMap<>(Category.class);
    private static final Pattern SMALL_TALK = p("^(thanks|thank you|thx|ty|cheers|nice|cool|great|ok|okay|lol|good job|well done)\\b");

    static {
        KEYWORDS.put(Category.WORLD, List.of(
                p("\\b(ores?|iron|gold|copper|coal|diamonds?|emeralds?|redstone|lapis|quartz|debris|netherite|veins?)\\b"),
                p("\\b(this|that|here|over there)\\b"),
                p("\\b(blocks?|looking at|look at|(what is|what's|whats|is) (this|that)|(this|that) is|that's)\\b"),
                p("\\b(ignore|unignore|ignoring|hide|forget)\\b"),
                p("\\b(nearby|near me|around here|closest|nearest)\\b")));
        KEYWORDS.put(Category.INVENTORY, List.of(
                p("\\b(chests?|barrels?|containers?|shulkers?|boxes)\\b"),
                p("\\b(items?|stuff|loot|stored|storage|inventory|contents|inside|in it)\\b"),
                p("\\b(bring|fetch|grab|get me|give me|hand me)\\b"),
                p("\\b(pick (it |this |that )?up|pickup|carry|lift|place|put (it |this |that )?(down|here)|set (it )?down|drop it)\\b"),
                p("\\b(snapshot|photo|picture|show me|what's in|whats in|what is in)\\b"),
                p("\\bmy (diamonds?|iron|gold|items?|stuff)\\b")));
        KEYWORDS.put(Category.TRAVEL, List.of(
                p("\\b(waystones?|teleport|tp|warp)\\b"),
                p("\\b(take me|bring me back|send me|go (back|home|to))\\b"),
                p("\\b(home|base)\\b"),
                p("\\b(died|death|where i died|my grave)\\b")));
        KEYWORDS.put(Category.SETTINGS, List.of(
                p("\\b(call you|call yourself|rename|be called|name you|your name is|change your name)\\b"),
                p("\\b(follow|come with|come here|come back|stay|wait here|stop following|don't move|stay put)\\b")));
    }

    private final ToolRegistry registry;
    private final int maxTools;

    public Router(ToolRegistry registry, int maxTools) {
        this.registry = registry;
        this.maxTools = maxTools;
    }

    public Route route(String text) {
        String t = text.toLowerCase(Locale.ROOT).trim();
        Map<Category, Integer> scores = new EnumMap<>(Category.class);
        for (Map.Entry<Category, List<Pattern>> e : KEYWORDS.entrySet()) {
            int s = 0;
            for (Pattern pattern : e.getValue()) if (pattern.matcher(t).find()) s++;
            if (s > 0) scores.put(e.getKey(), s);
        }
        List<Category> cats = new ArrayList<>(scores.keySet());
        cats.sort(Comparator.comparing((Category c) -> -scores.get(c)).thenComparing(Enum::ordinal));
        boolean toolExpected = !cats.isEmpty() && !SMALL_TALK.matcher(t).find();
        return new Route(cats, toolExpected);
    }

    /** look_at, then the tools of each matched category in order, capped at maxTools. Default: WORLD + SETTINGS. */
    public List<Tool> select(Route route) {
        List<Category> cats = route.categories().isEmpty() ? List.of(Category.WORLD, Category.SETTINGS) : route.categories();
        Set<Tool> out = new LinkedHashSet<>();
        registry.get("look_at").ifPresent(out::add);
        for (Category c : cats) {
            for (Tool tool : registry.inCategory(c)) {
                if (out.size() >= maxTools) return List.copyOf(out);
                out.add(tool);
            }
        }
        return List.copyOf(out);
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex);
    }
}
