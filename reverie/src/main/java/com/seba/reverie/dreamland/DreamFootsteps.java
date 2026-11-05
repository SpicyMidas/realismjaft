package com.seba.reverie.dreamland;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.seba.reverie.dream.DreamManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** The Sea of Doors creaks softly under your feet. */
public final class DreamFootsteps {
    private static final Map<UUID, Vec3> LAST = new HashMap<>();

    private DreamFootsteps() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(DreamFootsteps::onTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> LAST.remove(e.getEntity().getUUID()));
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 4 != 0) return;
        if (!p.level().dimension().equals(DreamManager.DREAMLAND) || !p.onGround()) return;
        if (!(p.level().getBlockState(p.blockPosition()).getBlock() instanceof TrapDoorBlock)) return;
        Vec3 last = LAST.get(p.getUUID());
        if (last != null && last.distanceToSqr(p.position()) < 1.6 * 1.6) return;
        LAST.put(p.getUUID(), p.position());
        p.level().playSound(null, p.blockPosition(), SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.25f, 0.5f + p.getRandom().nextFloat() * 0.2f);
    }
}
