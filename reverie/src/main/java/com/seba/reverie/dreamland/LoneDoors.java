package com.seba.reverie.dreamland;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.seba.reverie.ReverieConfig;
import com.seba.reverie.dream.HomeIsland;
import com.seba.reverie.network.DreamTransitionPayload;
import com.seba.reverie.network.Net;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Walking through a Lone Door: a white flash, then a safe spot on another island within ~300 blocks. */
public final class LoneDoors {
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private LoneDoors() {
    }

    public static void walkThrough(ServerPlayer player, BlockPos door) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Long until = COOLDOWN.get(player.getUUID());
        if (until != null && now < until) return;
        // Only when the body actually crosses the doorway, not when brushing the frame.
        double cx = door.getX() + 0.5;
        double cz = door.getZ() + 0.5;
        if (Math.abs(player.getX() - cx) > 0.45 || Math.abs(player.getZ() - cz) > 0.45) return;
        COOLDOWN.put(player.getUUID(), now + 60);
        RandomSource random = player.getRandom();
        BlockPos target = null;
        if (ReverieConfig.get(ReverieConfig.LYING_DOORS) && random.nextInt(6) == 0) {
            // Doors that lie: a place that looks exactly like the one you left. One block off.
            target = player.blockPosition().relative(net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(random));
            if (!HomeIsland.isSafeStand(level, target)) target = null;
        }
        if (target == null) target = IslandSearch.skyDoorReturn(level, door);
        if (target == null) target = IslandSearch.randomIslandSpot(level, door, 300, random);
        if (target == null) return;
        Net.send(player, new DreamTransitionPayload(DreamTransitionPayload.FLASH));
        level.playSound(null, door, SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 0.6f);
        player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        level.playSound(null, target, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, 0.5f, 0.7f);
    }

    public static void clear() {
        COOLDOWN.clear();
    }
}
