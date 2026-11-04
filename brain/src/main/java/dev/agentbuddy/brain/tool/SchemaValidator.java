package dev.agentbuddy.brain.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates tool arguments against the small JSON-schema subset our tools use: an object with typed properties
 * (string / integer / number / boolean), {@code enum}, {@code minimum}/{@code maximum} and {@code required}.
 *
 * <p>Tolerant where it is safe: unknown properties and nulls are dropped, and numeric/boolean strings are coerced
 * ("5" to 5), because small models often emit them. Everything else is an error the model is told about once.
 */
public final class SchemaValidator {
    private SchemaValidator() {
    }

    public record Result(JsonObject args, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    public static Result validate(JsonObject schema, JsonObject input) {
        JsonObject props = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
        JsonObject out = new JsonObject();
        List<String> errors = new ArrayList<>();
        for (String key : input.keySet()) {
            JsonElement value = input.get(key);
            if (!props.has(key) || value == null || value.isJsonNull()) continue;
            JsonObject p = props.getAsJsonObject(key);
            JsonElement coerced = coerce(p, value);
            if (coerced == null) {
                errors.add(key + " must be " + describe(p));
                continue;
            }
            if (p.has("enum") && !contains(p.getAsJsonArray("enum"), coerced)) {
                errors.add(key + " must be one of: " + join(p.getAsJsonArray("enum")));
                continue;
            }
            if (coerced.isJsonPrimitive() && coerced.getAsJsonPrimitive().isNumber()) {
                double d = coerced.getAsDouble();
                if (p.has("minimum") && d < p.get("minimum").getAsDouble()) { errors.add(key + " must be >= " + p.get("minimum")); continue; }
                if (p.has("maximum") && d > p.get("maximum").getAsDouble()) { errors.add(key + " must be <= " + p.get("maximum")); continue; }
            }
            if (coerced.isJsonPrimitive() && coerced.getAsJsonPrimitive().isString() && coerced.getAsString().isBlank()) continue;
            out.add(key, coerced);
        }
        if (schema.has("required")) {
            for (JsonElement r : schema.getAsJsonArray("required")) {
                if (!out.has(r.getAsString()) && errors.stream().noneMatch(e -> e.startsWith(r.getAsString() + " "))) {
                    errors.add("missing " + r.getAsString());
                }
            }
        }
        return new Result(out, errors);
    }

    /** Returns the value converted to the property's type, or null if it cannot be. */
    private static JsonElement coerce(JsonObject p, JsonElement v) {
        String type = p.has("type") ? p.get("type").getAsString() : "string";
        if (!v.isJsonPrimitive()) return null;
        JsonPrimitive prim = v.getAsJsonPrimitive();
        switch (type) {
            case "string" -> {
                return new JsonPrimitive(prim.getAsString().trim());
            }
            case "integer" -> {
                try {
                    double d = prim.isNumber() ? prim.getAsDouble() : Double.parseDouble(prim.getAsString().trim());
                    if (d != Math.rint(d) || Double.isInfinite(d)) return null;
                    return new JsonPrimitive((long) d);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case "number" -> {
                try {
                    return new JsonPrimitive(prim.isNumber() ? prim.getAsDouble() : Double.parseDouble(prim.getAsString().trim()));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            case "boolean" -> {
                if (prim.isBoolean()) return prim;
                String s = prim.getAsString().trim().toLowerCase();
                return s.equals("true") ? new JsonPrimitive(true) : s.equals("false") ? new JsonPrimitive(false) : null;
            }
            default -> {
                return null;
            }
        }
    }

    private static boolean contains(JsonArray values, JsonElement v) {
        for (JsonElement e : values) if (e.equals(v)) return true;
        return false;
    }

    private static String describe(JsonObject p) {
        String type = p.has("type") ? p.get("type").getAsString() : "string";
        return "integer".equals(type) ? "an integer" : "a " + type;
    }

    private static String join(JsonArray a) {
        List<String> s = new ArrayList<>();
        for (JsonElement e : a) s.add(e.getAsString());
        return String.join(", ", s);
    }
}
