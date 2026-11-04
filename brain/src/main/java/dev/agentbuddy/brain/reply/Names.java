package dev.agentbuddy.brain.reply;

/** Turns registry ids into short display text: "minecraft:iron_ingot" -> "iron ingot". */
public final class Names {
    private Names() {
    }

    public static String display(String id) {
        if (id == null) return "";
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return path.replace('_', ' ').trim();
    }

    public static String capitalized(String id) {
        String d = display(id);
        return d.isEmpty() ? d : Character.toUpperCase(d.charAt(0)) + d.substring(1);
    }

    /** "12 diamonds", "1 iron ingot". */
    public static String count(int n, String id) {
        String d = display(id);
        if (n == 1 || d.endsWith("s")) return n + " " + d;
        return n + " " + d + "s";
    }
}
