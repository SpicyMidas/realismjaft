package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.world.Sight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** The block a container tool acts on: explicit x,y,z arguments, else the block under the crosshair. */
final class Targets {
    private Targets() {
    }

    static BlockPos target(ServerPlayer player, JsonObject args) {
        if (args.has("x") && args.has("y") && args.has("z")) {
            return new BlockPos(args.get("x").getAsInt(), args.get("y").getAsInt(), args.get("z").getAsInt());
        }
        return Sight.block(player);
    }

    static boolean inRange(ServerPlayer player, BlockPos pos, int range) {
        return range <= 0 || player.position().distanceTo(pos.getCenter()) <= range;
    }
}
