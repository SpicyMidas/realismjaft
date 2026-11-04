package dev.agentbuddy.brain.tool;

import com.google.gson.JsonObject;
import java.time.Duration;

/**
 * Static description of a tool. Range, credits and cooldown are starting values from AGENT_SPEC.md section 4.
 *
 * @param parameters JSON schema (object) for the arguments
 * @param range      blocks; 0 = not range-limited. The tool enforces it, since only it knows the target.
 * @param credits    base cost; a tool may compute a different cost (e.g. by distance)
 */
public record ToolSpec(String name, String description, Category category, JsonObject parameters,
                       int range, int credits, Duration cooldown) {

    /** OpenAI "tools" array entry. */
    public JsonObject toOpenAi() {
        JsonObject fn = new JsonObject();
        fn.addProperty("name", name);
        fn.addProperty("description", description);
        fn.add("parameters", parameters.deepCopy());
        JsonObject o = new JsonObject();
        o.addProperty("type", "function");
        o.add("function", fn);
        return o;
    }
}
