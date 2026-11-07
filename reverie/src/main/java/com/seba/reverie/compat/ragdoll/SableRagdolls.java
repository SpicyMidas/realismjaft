package com.seba.reverie.compat.ragdoll;

import java.util.List;

import com.seba.reverie.Reverie;

import dev.leo.sableplayerragdoll.api.DespawnCondition;
import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.api.RagdollLaunchOptions;
import dev.leo.sableplayerragdoll.mob.api.MobRagdollLaunchOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Sable: Ragdolls (mod id sable_player_ragdoll) does the falling when it is installed: the hit player or mob becomes
 * a physics ragdoll for the knock-out time. Only loaded when that mod is present (Compat.sableRagdolls()); any
 * failure falls back to the plain Out Cold effect.
 */
public final class SableRagdolls {
    private SableRagdolls() {
    }

    /** True if a ragdoll was started. */
    public static boolean knockOut(ServerLevel level, LivingEntity target, Vec3 push, int ticks) {
        try {
            if (target instanceof ServerPlayer player) {
                if (RagdollAPI.isRagdolled(player)) return true;
                RagdollAPI.launch(player, push, RagdollLaunchOptions.builder()
                        .despawnConditions(List.of(DespawnCondition.afterTicks(ticks)))
                        .lockDismount(true)
                        .build());
                return true;   // the player's pose is captured asynchronously, so launch() itself may return null
            }
            if (RagdollAPI.isMobRagdolled(target)) return true;
            Vec3 spin = new Vec3(level.random.nextGaussian(), 0, level.random.nextGaussian()).scale(2.0);
            return RagdollAPI.launchMob(level, target, push, spin, MobRagdollLaunchOptions.builder().durationTicks(ticks).build()) != null;
        } catch (RuntimeException | LinkageError e) {
            Reverie.LOGGER.warn("Sable: Ragdolls knock-out failed; falling back to the plain one", e);
            return false;
        }
    }
}
