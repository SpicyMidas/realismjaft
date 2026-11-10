package com.seba.reverie.content.melatonia;

import javax.annotation.Nullable;

import com.seba.reverie.compat.Compat;
import com.seba.reverie.content.effect.OutColdEffect;
import com.seba.reverie.registry.ModEffects;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * A laced arrow or snowball knocks what it hits out cold: a mob falls down for about 30 seconds; a player only if
 * they are already Sleepy, and only sometimes, for a couple of seconds. With Sable: Ragdolls installed the fall is a
 * physics ragdoll (compat/ragdoll), otherwise the Out Cold effect lays them flat. Bosses shrug it off.
 */
public final class Collapse {
    public static final int MOB_TICKS = 600;
    public static final int PLAYER_TICKS = 50;
    /** Chance a Sleepy player goes down when hit. */
    public static final float PLAYER_CHANCE = 0.35f;

    private Collapse() {
    }

    public static void fromLacedHit(LivingEntity target, @Nullable Entity projectile) {
        if (!(target.level() instanceof ServerLevel level) || target.getType().is(Tags.EntityTypes.BOSSES) || !target.isAlive()) return;
        int ticks;
        if (target instanceof Player player) {
            if (!ModEffects.sleepy(player) || player.isSpectator() || player.getAbilities().invulnerable
                    || target.getRandom().nextFloat() >= PLAYER_CHANCE) {
                return;
            }
            ticks = PLAYER_TICKS;
        } else {
            ticks = MOB_TICKS;
        }
        Vec3 push = projectile == null ? Vec3.ZERO : projectile.getDeltaMovement().normalize().scale(1.5).add(0, 0.6, 0);
        boolean ragdolled = Compat.sableRagdolls() && com.seba.reverie.compat.ragdoll.SableRagdolls.knockOut(level, target, push, ticks);
        if (!ragdolled) target.addEffect(new MobEffectInstance(ModEffects.OUT_COLD, ticks, 0, false, true, true));
        level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 0.6f, 0.6f);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.8, target.getZ(), 6, 0.3, 0.2, 0.3, 0.05);
    }

    // The pose has to go back when the effect ends early (milk) or runs out.
    public static void onRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect().is(ModEffects.OUT_COLD)) OutColdEffect.stand(event.getEntity());
    }

    public static void onExpired(MobEffectEvent.Expired event) {
        if (event.getEffectInstance() != null && event.getEffectInstance().getEffect().is(ModEffects.OUT_COLD)) {
            OutColdEffect.stand(event.getEntity());
        }
    }

    /** A player who logs in still lying down (the effect ended while they were away) gets up. */
    public static void onLogin(ServerPlayer player) {
        if (!player.hasEffect(ModEffects.OUT_COLD)) OutColdEffect.stand(player);
    }
}
