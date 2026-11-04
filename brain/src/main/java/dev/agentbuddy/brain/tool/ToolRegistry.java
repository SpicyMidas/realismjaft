package dev.agentbuddy.brain.tool;

import com.google.gson.JsonArray;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry(Collection<? extends Tool> all) {
        for (Tool t : all) {
            if (tools.put(t.spec().name(), t) != null) throw new IllegalArgumentException("duplicate tool " + t.spec().name());
        }
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<Tool> all() {
        return List.copyOf(tools.values());
    }

    /** Tools of the given categories, in registration order. */
    public List<Tool> inCategory(Category c) {
        List<Tool> out = new ArrayList<>();
        for (Tool t : tools.values()) if (t.spec().category() == c) out.add(t);
        return out;
    }

    public static JsonArray toOpenAi(List<Tool> selection) {
        JsonArray a = new JsonArray();
        for (Tool t : selection) a.add(t.spec().toOpenAi());
        return a;
    }
}
