package com.seba.reverie.dream;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Hook for advancements/stats about how dreams end (filled in by the advancement triggers). */
public final class DreamStats {
    public interface Sink {
        void woke(ServerPlayer player, DreamManager.Reason reason, List<ItemStack> keepsakes);
    }

    public static volatile Sink sink = (p, r, k) -> { };

    private DreamStats() {
    }

    static void record(ServerPlayer player, DreamManager.Reason reason, List<ItemStack> keepsakes) {
        try {
            sink.woke(player, reason, keepsakes);
        } catch (RuntimeException ignored) {
            // stats are cosmetic
        }
    }
}
