package com.seba.reverie.dreamland;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.dream.DreamSession;
import com.seba.reverie.dream.HomeIsland;
import com.seba.reverie.network.Net;
import com.seba.reverie.network.ShadeEventPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;

/**
 * The endless fall. Falling never wakes you: a little before the bottom of the dream you come out above a nightmare
 * island (or, if you fell from the nightmares, above the spot you fell from), still falling, and drift down onto it. On the client the fall slows as you sink into a
 * pale mist that stays through the move and thins as you come down, so the jump is never seen.
 */
public final class FallLoop {
    /** Blocks above the bottom of the world where the loop catches you (the void fog is thickest there). */
    public static final int DEPTH = 40;
    /** ShadeEventPayload id: the client keeps the mist and lets you drift down for this many ticks. */
    public static final int EVENT = 31;
    private static final int DROP = 42;
    private static final Map<UUID, BlockPos> GROUND = new HashMap<>();

    private FallLoop() {
    }

    /** Remembers the last solid spot the dreamer stood on. */
    public static void track(ServerPlayer p) {
        if (p.tickCount % 5 != 0 || !p.onGround() || p.isSpectator()) return;
        BlockPos feet = p.blockPosition();
        if (p.level().getBlockState(feet.below()).isFaceSturdy(p.level(), feet.below(), Direction.UP)) GROUND.put(p.getUUID(), feet.immutable());
    }

    public static void loop(ServerPlayer p, DreamSession session) {
        ServerLevel level = p.serverLevel();
        BlockPos stand = target(level, p, session);
        // Come back from high above the spot, as high as the column above it is open.
        int top = Math.min(level.getMaxBuildHeight() - 4, stand.getY() + DROP);
        for (int y = stand.getY() + 2; y <= top; y++) {
            BlockPos at = new BlockPos(stand.getX(), y, stand.getZ());
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                top = y - 3;
                break;
            }
        }
        double y = Math.max(stand.getY() + 0.1, top);
        p.resetFallDistance();
        // Fully relative: the client keeps its (by now slow, see BiomeMechanicsClient) fall and its view, and its
        // interpolation is shifted along, so nothing jumps; the mist it is falling through hides the rest.
        p.teleportTo(level, stand.getX() + 0.5, y, stand.getZ() + 0.5, EnumSet.allOf(RelativeMovement.class), p.getYRot(), p.getXRot());
        p.resetFallDistance();
        Net.send(p, new ShadeEventPayload(EVENT, 90));
    }

    private static BlockPos target(ServerLevel level, ServerPlayer p, DreamSession session) {
        BlockPos ground = GROUND.get(p.getUUID());
        // The dream is layered: fall out of the dream or a lucid dream and you come down in the nightmares below.
        // Only a fall from the nightmares themselves brings you back where you were.
        if (ground == null || IslandField.nearestLayer(ground.getY()) > 0) {
            BlockPos below = IslandSearch.nightmareSpot(level, p.blockPosition(), false);
            if (below != null) return below;
        }
        if (ground != null) {
            if (HomeIsland.isSafeStand(level, ground) && level.canSeeSky(ground.above(2))) return ground;
            BlockPos near = HomeIsland.findSafe(level, ground, 5, ground.getY() - 12, ground.getY() + 12);
            if (near != null) return near;
        }
        return DreamManager.arrival(level, p.getUUID(), session.nightmare());
    }

    public static void forget(UUID id) {
        GROUND.remove(id);
    }
}
