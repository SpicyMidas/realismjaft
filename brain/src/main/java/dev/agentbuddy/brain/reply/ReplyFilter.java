package dev.agentbuddy.brain.reply;

import java.util.regex.Pattern;

/**
 * Post-filter for the LLM's free text (used only when no tool applies). Keeps one sentence of at most
 * {@link #MAX_WORDS} words, removes filler openers, praise, apologies, emoji and markup.
 */
public final class ReplyFilter {
    public static final int MAX_WORDS = 15;

    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>|<think>.*");
    private static final Pattern MARKUP = Pattern.compile("(?s)<tool_call>.*|<function=.*|```.*?```|[*_`#>]");
    private static final Pattern OPENER = Pattern.compile(
            "^(?i)(sure( thing)?|absolutely|of course|certainly|definitely|great question|good question|"
                    + "excellent question|you're right|you are right|you're correct|i'm sorry|i am sorry|sorry|"
                    + "i apologi[sz]e|my apologies|apologies|okay|ok|alright|all right|great|awesome|"
                    + "perfect|happy to help|no problem|glad you asked)\\b[\\s,!.:;-]*");
    private static final Pattern APOLOGY = Pattern.compile("(?i)[,;]?\\s*\\b(sorry|my apologies|i apologi[sz]e)\\b[,.!]?");
    // Emoji and pictographs, variation selectors, zero-width joiners.
    private static final Pattern EMOJI = Pattern.compile("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE00}-\\x{FE0F}\\x{200D}\\x{2B00}-\\x{2BFF}]");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+");

    private ReplyFilter() {
    }

    public static String clean(String text) {
        if (text == null) return Templates.DIDNT_UNDERSTAND;
        String t = THINK.matcher(text).replaceAll("");
        t = MARKUP.matcher(t).replaceAll("");
        t = EMOJI.matcher(t).replaceAll("");
        t = firstNonBlankLine(t);
        String before;
        do {
            before = t;
            t = OPENER.matcher(t.trim()).replaceFirst("");
        } while (!t.equals(before) && !t.isBlank());
        t = APOLOGY.matcher(t).replaceAll("").trim();
        t = SENTENCE_END.split(t, 2)[0].trim();
        t = capWords(t);
        if (t.isBlank() || !t.matches(".*[\\p{L}\\p{N}].*")) return Templates.DIDNT_UNDERSTAND;
        t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        if (!t.matches(".*[.!?]$")) t = t + ".";
        return t;
    }

    private static String firstNonBlankLine(String t) {
        for (String line : t.split("\\R")) if (!line.isBlank()) return line.trim();
        return "";
    }

    private static String capWords(String t) {
        String[] words = t.split("\\s+");
        if (words.length <= MAX_WORDS) return t;
        String cut = String.join(" ", java.util.Arrays.copyOf(words, MAX_WORDS));
        return cut.replaceAll("[,;:\\-]+$", "") + ".";
    }

    public static int wordCount(String t) {
        String s = t == null ? "" : t.trim();
        return s.isEmpty() ? 0 : s.split("\\s+").length;
    }
}
