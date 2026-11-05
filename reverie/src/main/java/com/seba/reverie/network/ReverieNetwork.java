package com.seba.reverie.network;

import com.seba.reverie.Reverie;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Core payloads. Client handlers live in client code and are installed by the client entry point. */
public final class ReverieNetwork {
    /** Client-side handlers; no-ops on a dedicated server. */
    public interface ClientHandlers {
        default void transition(DreamTransitionPayload payload) {
        }

        default void state(DreamStatePayload payload) {
        }

        default void shadeEvent(ShadeEventPayload payload) {
        }
    }

    public static volatile ClientHandlers client = new ClientHandlers() {
    };

    private ReverieNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(Reverie.MOD_ID).versioned("1");
        r.playToClient(DreamTransitionPayload.TYPE, DreamTransitionPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> client.transition(p)));
        r.playToClient(DreamStatePayload.TYPE, DreamStatePayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> client.state(p)));
        r.playToClient(ShadeEventPayload.TYPE, ShadeEventPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> client.shadeEvent(p)));
    }
}
