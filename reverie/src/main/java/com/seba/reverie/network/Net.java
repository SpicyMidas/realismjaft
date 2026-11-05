package com.seba.reverie.network;

import com.seba.reverie.Reverie;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends a cosmetic S2C payload only if the client can receive it; a failed send never breaks game logic. */
public final class Net {
    private Net() {
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        try {
            if (player.connection != null && player.connection.hasChannel(payload)) PacketDistributor.sendToPlayer(player, payload);
        } catch (RuntimeException e) {
            Reverie.LOGGER.debug("Could not send {} to {}", payload.type().id(), player.getName().getString(), e);
        }
    }
}
