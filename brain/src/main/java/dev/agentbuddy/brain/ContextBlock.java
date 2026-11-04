package dev.agentbuddy.brain;

/**
 * Facts sent with every request (AGENT_SPEC.md section 3). Built server-side from the world, never from client
 * claims. Ignored blocks must already be excluded by whoever builds this: the looking_at block is null if it is
 * ignored, and nearbyContainers does not count ignored containers.
 *
 * @param lookingAtBlock block id, or null for "nothing in reach"
 */
public record ContextBlock(String player, int hp, int maxHp, String heldItem,
                           String lookingAtBlock, Pos lookingAtPos,
                           String dimension, String biome, String timeOfDay,
                           String agentName, Pos agentPos, String agentState, int credits, int nearbyContainers) {

    public String render() {
        String looking = lookingAtBlock == null ? "nothing in reach" : shortId(lookingAtBlock) + " @ " + lookingAtPos;
        return "player: " + player + ", hp " + hp + "/" + maxHp + ", holding " + (heldItem == null ? "nothing" : shortId(heldItem)) + "\n"
                + "looking_at: " + looking + "\n"
                + "dimension: " + shortId(dimension) + ", biome: " + shortId(biome) + ", time: " + timeOfDay + "\n"
                + "agent: " + agentName + " @ " + agentPos + ", " + agentState + ", credits " + credits + "\n"
                + "nearby_containers: " + nearbyContainers;
    }

    private static String shortId(String id) {
        return id != null && id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
}
