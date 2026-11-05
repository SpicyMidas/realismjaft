package com.seba.reverie.fauna;

import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Floating Lanterns in Dreamland keep two or three Lucid Moths while someone is near. */
public final class LanternMoths {
    private LanternMoths() {
    }

    public static void tick(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server) || !level.dimension().equals(DreamManager.DREAMLAND)) return;
        if ((level.getGameTime() + pos.hashCode()) % 200 != 0) return;
        if (server.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 28, false) == null) return;
        int have = server.getEntitiesOfClass(LucidMoth.class, new AABB(pos).inflate(5)).size();
        int want = 2 + Math.floorMod(pos.hashCode(), 2);
        for (int i = have; i < want; i++) {
            LucidMoth moth = ModEntities.LUCID_MOTH.get().create(server);
            if (moth == null) return;
            float a = server.random.nextFloat() * (float) Math.PI * 2;
            moth.moveTo(pos.getX() + 0.5 + Math.cos(a) * 1.5, pos.getY() + 0.5, pos.getZ() + 0.5 + Math.sin(a) * 1.5, 0, 0);
            moth.circle(pos, a);
            server.addFreshEntity(moth);
        }
    }
}
