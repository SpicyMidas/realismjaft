package dev.agentbuddy.brain.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.agentbuddy.brain.Pos;
import dev.agentbuddy.brain.Reply;
import dev.agentbuddy.brain.fake.FakeWorld;
import dev.agentbuddy.brain.reply.ReplyFilter;
import dev.agentbuddy.brain.reply.Templates;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** One replay case from cases.json, plus world building and pass/fail checks. */
record ReplayCase(String id, String group, String say, JsonObject world, boolean toolSpecified, String expectTool,
                  JsonObject expectArgs, List<String> mustContain, List<String> mustContainAny,
                  List<String> mustNotContain, int maxWords) {

    static final List<String> GROUPS = List.of("tool_selection", "false_premise", "context_premise", "absence", "brevity");
    private static final Pattern EMOJI = Pattern.compile("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}]");

    record Suite(JsonObject baseWorld, List<ReplayCase> cases) {
    }

    static Suite load() throws IOException {
        try (InputStream in = ReplayCase.class.getResourceAsStream("/replay/cases.json")) {
            if (in == null) throw new IOException("replay/cases.json not on the test classpath");
            JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            List<ReplayCase> cases = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("cases")) {
                JsonObject c = e.getAsJsonObject();
                boolean specified = c.has("expect_tool");
                String tool = specified && !c.get("expect_tool").isJsonNull() ? c.get("expect_tool").getAsString() : null;
                cases.add(new ReplayCase(c.get("id").getAsString(), c.get("group").getAsString(), c.get("say").getAsString(),
                        c.has("world") ? c.getAsJsonObject("world") : new JsonObject(), specified, tool,
                        c.has("expect_args") ? c.getAsJsonObject("expect_args") : new JsonObject(),
                        strings(c, "must_contain"), strings(c, "must_contain_any"), strings(c, "must_not_contain"),
                        c.has("max_words") ? c.get("max_words").getAsInt() : ReplyFilter.MAX_WORDS));
            }
            return new Suite(root.getAsJsonObject("base_world"), cases);
        }
    }

    FakeWorld buildWorld(JsonObject base) {
        FakeWorld w = new FakeWorld();
        apply(w, base);
        apply(w, world);
        return w;
    }

    private static void apply(FakeWorld w, JsonObject s) {
        if (s.has("clear_blocks")) w.blocks.clear();
        if (s.has("clear_containers")) w.containers.clear();
        if (s.has("player_pos")) w.playerPos = pos(s.get("player_pos"));
        if (s.has("blocks")) for (JsonElement b : s.getAsJsonArray("blocks")) {
            w.blocks.put(pos(b.getAsJsonObject().get("pos")), b.getAsJsonObject().get("block").getAsString());
        }
        if (s.has("containers")) for (JsonElement ce : s.getAsJsonArray("containers")) {
            JsonObject c = ce.getAsJsonObject();
            FakeWorld.Container container = new FakeWorld.Container(c.get("type").getAsString(), Map.of());
            for (Map.Entry<String, JsonElement> it : c.getAsJsonObject("items").entrySet()) {
                container.items.put(it.getKey(), it.getValue().getAsInt());
            }
            w.containers.put(pos(c.get("pos")), container);
        }
        if (s.has("looking_at")) {
            JsonElement l = s.get("looking_at");
            if (l.isJsonNull()) {
                w.lookingAt = null;
            } else {
                Pos p = pos(l.getAsJsonObject().get("pos"));
                if (l.getAsJsonObject().has("block")) w.blocks.put(p, l.getAsJsonObject().get("block").getAsString());
                w.lookingAt = p;
            }
        }
        if (s.has("ignored")) for (JsonElement p : s.getAsJsonArray("ignored")) w.ignored.add(pos(p));
        if (s.has("waystones")) for (Map.Entry<String, JsonElement> e : s.getAsJsonObject("waystones").entrySet()) {
            w.waystones.put(e.getKey(), pos(e.getValue()));
        }
        if (s.has("death")) w.lastDeath = pos(s.get("death"));
        if (s.has("credits")) w.credits.set(w.player, s.get("credits").getAsInt());
        if (s.has("dimension")) w.dimension = s.get("dimension").getAsString();
        if (s.has("biome")) w.biome = s.get("biome").getAsString();
        if (s.has("time")) w.timeOfDay = s.get("time").getAsString();
        if (s.has("hp")) w.hp = s.get("hp").getAsInt();
        if (s.has("held")) w.heldItem = s.get("held").getAsString();
        if (s.has("agent_state")) w.agentState = s.get("agent_state").getAsString();
        if (s.has("carrying")) w.carrying = new FakeWorld.Container(s.get("carrying").getAsString(), Map.of());
    }

    /** Returns the list of failed checks; empty means pass. */
    List<String> check(Reply r) {
        List<String> f = new ArrayList<>();
        String text = r.text();
        if (text.startsWith("LLM ")) f.add("llm failure: " + r.failure());
        if (Templates.DIDNT_UNDERSTAND.equals(text)) f.add("fell back to \"Didn't understand.\"");
        if (toolSpecified) {
            if (expectTool == null && r.tool() != null) f.add("expected free text, got tool " + r.tool());
            if (expectTool != null && !expectTool.equals(r.tool())) f.add("expected tool " + expectTool + ", got " + r.tool());
        }
        if (expectTool != null && expectTool.equals(r.tool())) {
            for (String key : expectArgs.keySet()) {
                JsonElement want = expectArgs.get(key);
                JsonElement got = r.args() == null ? null : r.args().get(key);
                if (got == null || !sameValue(want, got)) f.add("arg " + key + ": expected " + want + ", got " + got);
            }
        }
        for (String s : mustContain) if (!containsCI(text, s)) f.add("missing \"" + s + "\"");
        if (!mustContainAny.isEmpty() && mustContainAny.stream().noneMatch(s -> containsCI(text, s))) {
            f.add("missing any of " + mustContainAny);
        }
        for (String s : mustNotContain) if (containsCI(text, s)) f.add("contains forbidden \"" + s + "\"");
        int words = ReplyFilter.wordCount(text);
        if (words > maxWords) f.add(words + " words > " + maxWords);
        if (text.contains("\n")) f.add("multi-line reply");
        if (EMOJI.matcher(text).find()) f.add("emoji");
        return f;
    }

    private static boolean sameValue(JsonElement want, JsonElement got) {
        if (want.isJsonPrimitive() && want.getAsJsonPrimitive().isNumber() && got.isJsonPrimitive()) {
            try {
                return want.getAsDouble() == got.getAsDouble();
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return want.getAsString().equalsIgnoreCase(got.getAsString());
    }

    /** Case-insensitive; a single word must match on word boundaries ("no" does not match "know"). */
    static boolean containsCI(String text, String needle) {
        String t = text.toLowerCase(Locale.ROOT);
        String n = needle.toLowerCase(Locale.ROOT);
        if (n.matches("[\\p{L}\\p{N}']+")) return Pattern.compile("(?<![\\p{L}\\p{N}'])" + Pattern.quote(n) + "(?![\\p{L}\\p{N}'])").matcher(t).find();
        return t.contains(n);
    }

    private static Pos pos(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new Pos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        if (o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) out.add(e.getAsString());
        return out;
    }
}
