package dev.agentbuddy.brain;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.agentbuddy.brain.llm.ChatResponse;
import dev.agentbuddy.brain.llm.ToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts tool calls from a response. Structured {@code tool_calls} come first; if there are none, the text is
 * searched for calls models sometimes emit as plain text: {@code <tool_call>{json}</tool_call>}, Qwen-style
 * {@code <function=name><parameter=k>v</parameter></function>}, bare {@code {"name":..,"arguments":..}} JSON, and
 * {@code name {json}} / {@code name(k="v")} for known tool names (the shape our few-shot examples use).
 */
public final class ToolCallParser {
    public enum Source { STRUCTURED, RECOVERED, NONE, MALFORMED }

    public record RawCall(String name, JsonObject args) {
    }

    /** {@code error} is set only for MALFORMED. */
    public record Parsed(List<RawCall> calls, Source source, String error) {
    }

    private static final Pattern TAGGED_JSON = Pattern.compile("(?s)<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>");
    private static final Pattern XML_FUNCTION = Pattern.compile("(?s)<function=([\\w.-]+)>(.*?)(?:</function>|$)");
    private static final Pattern XML_PARAM = Pattern.compile("(?s)<parameter=([\\w.-]+)>\\s*(.*?)\\s*(?:</parameter>|(?=<parameter=)|$)");
    private static final Pattern KWARG = Pattern.compile("(\\w+)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^,)\\s]+)");

    private ToolCallParser() {
    }

    public static Parsed parse(ChatResponse response, Set<String> knownTools) {
        if (!response.toolCalls().isEmpty()) {
            List<RawCall> calls = new ArrayList<>();
            for (ToolCall tc : response.toolCalls()) {
                JsonObject args = parseArgs(tc.argumentsJson());
                if (args == null) return new Parsed(List.of(), Source.MALFORMED, "arguments of " + tc.name() + " are not a JSON object");
                calls.add(new RawCall(tc.name(), args));
            }
            return new Parsed(calls, Source.STRUCTURED, null);
        }
        return parseText(response.content(), knownTools);
    }

    static Parsed parseText(String text, Set<String> knownTools) {
        if (text == null || text.isBlank()) return new Parsed(List.of(), Source.NONE, null);

        Matcher m = TAGGED_JSON.matcher(text);
        if (m.find()) {
            RawCall c = fromNameArgsJson(m.group(1));
            if (c != null) return recovered(c);
        }
        m = XML_FUNCTION.matcher(text);
        if (m.find()) {
            JsonObject args = new JsonObject();
            Matcher pm = XML_PARAM.matcher(m.group(2));
            while (pm.find()) args.add(pm.group(1), scalar(pm.group(2)));
            return recovered(new RawCall(m.group(1), args));
        }
        int brace = text.indexOf('{');
        if (brace >= 0 && text.contains("\"name\"")) {
            RawCall c = fromNameArgsJson(text.substring(brace, text.lastIndexOf('}') + 1));
            if (c != null) return recovered(c);
        }
        for (String tool : knownTools) {
            Matcher nm = Pattern.compile("\\b" + Pattern.quote(tool) + "\\s*(\\{[^}]*\\}|\\(([^)]*)\\))").matcher(text);
            if (!nm.find()) continue;
            if (nm.group(1).startsWith("{")) {
                JsonObject args = parseArgs(nm.group(1));
                if (args != null) return recovered(new RawCall(tool, args));
            } else {
                JsonObject args = new JsonObject();
                Matcher km = KWARG.matcher(nm.group(2));
                while (km.find()) args.add(km.group(1), scalar(unquote(km.group(2))));
                return recovered(new RawCall(tool, args));
            }
        }
        if (text.contains("<tool_call") || text.contains("<function=")) {
            return new Parsed(List.of(), Source.MALFORMED, "tool call markup could not be parsed");
        }
        return new Parsed(List.of(), Source.NONE, null);
    }

    private static Parsed recovered(RawCall c) {
        return new Parsed(List.of(c), Source.RECOVERED, null);
    }

    private static RawCall fromNameArgsJson(String json) {
        JsonObject o = parseArgs(json);
        if (o == null || !o.has("name") || !o.get("name").isJsonPrimitive()) return null;
        JsonElement a = o.has("arguments") ? o.get("arguments") : o.get("parameters");
        JsonObject args = a == null ? new JsonObject() : a.isJsonObject() ? a.getAsJsonObject()
                : a.isJsonPrimitive() ? parseArgs(a.getAsString()) : null;
        return args == null ? null : new RawCall(o.get("name").getAsString(), args);
    }

    /** Parses a JSON object, also unwrapping a JSON string that itself contains an object (double encoding). */
    static JsonObject parseArgs(String json) {
        if (json == null || json.isBlank()) return new JsonObject();
        try {
            JsonElement e = JsonParser.parseString(json.trim());
            if (e.isJsonObject()) return e.getAsJsonObject();
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) return parseArgs(e.getAsString());
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static JsonElement scalar(String raw) {
        String s = raw.trim();
        try {
            JsonElement e = JsonParser.parseString(s);
            if (e.isJsonPrimitive() && !e.getAsJsonPrimitive().isString()) return e;
        } catch (RuntimeException ignored) {
            // plain text value
        }
        return new JsonPrimitive(unquote(s));
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
