package com.seba.reverie.compat.create;

import com.seba.reverie.Reverie;
import com.seba.reverie.compat.create.gearshift.TuffGearshiftBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class CreateNetwork {
    private static final double MAX_DISTANCE_SQR = 8.0 * 8.0;

    private CreateNetwork() {
    }

    public record SelectGearPayload(BlockPos pos, int gear) implements CustomPacketPayload {
        public static final Type<SelectGearPayload> TYPE = new Type<>(Reverie.id("select_gear"));
        public static final StreamCodec<ByteBuf, SelectGearPayload> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SelectGearPayload::pos, ByteBufCodecs.VAR_INT, SelectGearPayload::gear, SelectGearPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Reverie.MOD_ID + "_create").versioned("1");
        registrar.playToServer(SelectGearPayload.TYPE, SelectGearPayload.CODEC, CreateNetwork::handleSelectGear);
    }

    public enum Selection { APPLIED, UNCHANGED, NOT_LOADED, TOO_FAR, NO_GEARSHIFT, LOCKED, INVALID_GEAR }

    private static void handleSelectGear(SelectGearPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            try {
                if (ctx.player() instanceof ServerPlayer player) applySelection(player, msg.pos(), msg.gear());
            } catch (RuntimeException e) {
                Reverie.LOGGER.warn("Rejected malformed gear selection from {}", ctx.player().getName().getString(), e);
            }
        });
    }

    /** Server-side validation of a gear change request; never trusts the client. */
    public static Selection applySelection(ServerPlayer player, BlockPos pos, int gear) {
        var level = player.serverLevel();
        if (!level.isLoaded(pos)) return Selection.NOT_LOADED;
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) return Selection.TOO_FAR;
        if (!(level.getBlockEntity(pos) instanceof TuffGearshiftBlockEntity be)) return Selection.NO_GEARSHIFT;
        if (be.isLocked()) return Selection.LOCKED; // redstone holds it in R
        if (!be.layout().isValid(gear)) return Selection.INVALID_GEAR;
        return be.select(gear) ? Selection.APPLIED : Selection.UNCHANGED;
    }
}
