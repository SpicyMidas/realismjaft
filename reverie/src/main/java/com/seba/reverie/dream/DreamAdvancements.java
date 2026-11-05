package com.seba.reverie.dream;

import com.seba.reverie.Reverie;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Awards the dream advancements (their criteria are "impossible" in data, granted from here). */
public final class DreamAdvancements implements DreamManager.Listener {
    public static final ResourceLocation COUNTING_SHEEP = Reverie.id("counting_sheep");
    public static final ResourceLocation WIDE_AWAKE = Reverie.id("wide_awake");
    public static final ResourceLocation SOUVENIR = Reverie.id("souvenir");

    public static void init() {
        DreamManager.addListener(new DreamAdvancements());
        DreamStats.sink = (player, reason, keepsakes) -> {
            if (reason == DreamManager.Reason.BED) award(player, WIDE_AWAKE, "woke_in_a_dream_bed");
            if (!keepsakes.isEmpty()) award(player, SOUVENIR, "kept_something");
        };
    }

    @Override
    public void started(ServerPlayer player, DreamSession session) {
        award(player, COUNTING_SHEEP, "dreamed");
    }

    static void award(ServerPlayer player, ResourceLocation id, String criterion) {
        AdvancementHolder holder = player.server.getAdvancements().get(id);
        if (holder != null) player.getAdvancements().award(holder, criterion);
    }
}
