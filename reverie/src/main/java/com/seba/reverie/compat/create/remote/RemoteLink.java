package com.seba.reverie.compat.create.remote;

import java.util.UUID;

import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** One ON remote transmitting strength 15 into Create's Redstone Link network. Holds no Level/Player refs. */
final class RemoteLink implements IRedstoneLinkable {
    final UUID remoteId;
    final UUID owner;
    final ResourceKey<Level> dimension;
    final Couple<Frequency> key;
    BlockPos pos;
    long lastSeenTick;
    boolean alive = true;

    RemoteLink(UUID remoteId, UUID owner, ResourceKey<Level> dimension, Couple<Frequency> key, BlockPos pos, long tick) {
        this.remoteId = remoteId;
        this.owner = owner;
        this.dimension = dimension;
        this.key = key;
        this.pos = pos;
        this.lastSeenTick = tick;
    }

    @Override
    public int getTransmittedStrength() {
        return alive ? 15 : 0;
    }

    @Override
    public void setReceivedStrength(int power) {
    }

    @Override
    public boolean isListening() {
        return false;
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return key;
    }

    @Override
    public BlockPos getLocation() {
        return pos;
    }
}
