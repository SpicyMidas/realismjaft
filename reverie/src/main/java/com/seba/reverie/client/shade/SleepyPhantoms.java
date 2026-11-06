package com.seba.reverie.client.shade;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.seba.reverie.registry.ModEntities;
import com.seba.reverie.shade.ShadeEntity;
import com.seba.reverie.shade.ShadeLook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * What a sleepy player half-sees: Shades standing at the edge of sight. They are real Shade entities, but added only
 * to this client's world (negative ids, never sent anywhere), so nobody else sees them. They show up while the
 * eyes are shut in a heavy blink, or now and then at the edge of view, fade in, turn to face you, and fade away a
 * moment after you look straight at one, walk close, or after a few seconds.
 */
public final class SleepyPhantoms {
    private static final ShadeEntity.Variant[] SHAPES = {ShadeEntity.Variant.ONLOOKER, ShadeEntity.Variant.ONLOOKER,
            ShadeEntity.Variant.TALL_ONE, ShadeEntity.Variant.STOOPED_ONE, ShadeEntity.Variant.WIDE_ARMED, ShadeEntity.Variant.HEADLESS};
    private static final int MAX_ALIVE = 3;
    private static final Random RNG = new Random();

    private record Phantom(ShadeEntity shade, int lifetime) {
    }

    private static final List<Phantom> PHANTOMS = new ArrayList<>();
    private static final java.util.Map<Integer, Integer> SEEN_FOR = new java.util.HashMap<>();
    private static int nextId = -0x5EE9_0000;
    private static int nextIdle = 400;
    private static ClientLevel level;

    private SleepyPhantoms() {
    }

    /** The eyes have just shut in a heavy blink: something may be standing there when they open. */
    public static void eyesClosed(boolean microSleep) {
        if (RNG.nextFloat() > (microSleep ? 0.85f : 0.45f)) return;
        int n = microSleep ? 1 + RNG.nextInt(2) : 1;
        for (int i = 0; i < n; i++) spawn(RNG.nextFloat() < 0.5f ? 0f : 1f);
    }

    /** Called every client tick; {@code sleepy} is false once the effect is gone (everything then fades away). */
    public static void tick(boolean sleepy) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            PHANTOMS.clear();
            SEEN_FOR.clear();
            level = mc.level;
        }
        if (level == null || mc.player == null) return;
        if (sleepy && --nextIdle <= 0) {
            nextIdle = 300 + RNG.nextInt(500);
            spawn(1f);
        }
        LocalPlayer player = mc.player;
        for (var it = PHANTOMS.iterator(); it.hasNext(); ) {
            Phantom p = it.next();
            ShadeEntity s = p.shade();
            if (s.isRemoved()) {
                it.remove();
                continue;
            }
            if (s.fading()) {
                if (s.fadeProgress(0) >= 1f) {
                    s.discard();
                    SEEN_FOR.remove(s.getId());
                    it.remove();
                }
                continue;
            }
            int seen = ShadeLook.staredAt(s) ? SEEN_FOR.merge(s.getId(), 1, Integer::sum) : SEEN_FOR.getOrDefault(s.getId(), 0);
            boolean close = s.distanceToSqr(player) < 6 * 6;
            if (!sleepy || seen > 18 || close || s.tickCount > p.lifetime()) s.fadeOut();
        }
    }

    /** One Shade 9-18 blocks away. {@code side} 0 = anywhere ahead, 1 = toward the edge of view. */
    private static void spawn(float side) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (level == null || player == null || PHANTOMS.size() >= MAX_ALIVE) return;
        for (int attempt = 0; attempt < 8; attempt++) {
            float off = side > 0 ? (35f + RNG.nextFloat() * 40f) * (RNG.nextBoolean() ? 1 : -1) : (RNG.nextFloat() - 0.5f) * 50f;
            float yaw = player.getYRot() + off;
            double dist = 9 + RNG.nextDouble() * 9;
            double x = player.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * dist;
            double z = player.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * dist;
            BlockPos ground = ground(BlockPos.containing(x, player.getY(), z));
            if (ground == null) continue;
            ShadeEntity shade = ModEntities.SHADE.get().create(level);
            if (shade == null) return;
            shade.setId(nextId--);
            shade.setVariant(SHAPES[RNG.nextInt(SHAPES.length)]);
            shade.setOwner(player);
            shade.setMode(ShadeEntity.FACE);
            float facing = (float) (Mth.atan2(player.getZ() - z, player.getX() - x) * Mth.RAD_TO_DEG) - 90f;
            shade.moveTo(x, ground.getY(), z, facing, 0);
            level.addEntity(shade);
            PHANTOMS.add(new Phantom(shade, 70 + RNG.nextInt(90)));
            if (RNG.nextInt(3) == 0) {
                level.playLocalSound(x, ground.getY() + 1, z, SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT, 0.12f, 0.5f, false);
            }
            return;
        }
    }

    /** Floor near {@code around} with two blocks of air above, within 6 blocks up or down, or null. */
    private static BlockPos ground(BlockPos around) {
        for (int dy = 6; dy >= -6; dy--) {
            BlockPos feet = around.above(dy);
            BlockPos below = feet.below();
            if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                    && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return feet;
            }
        }
        return null;
    }
}
