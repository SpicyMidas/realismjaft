package com.seba.reverie.client.dreamland;

import com.seba.reverie.ReverieConfig;
import com.seba.reverie.client.DreamClient;
import com.seba.reverie.client.DreamEffectsClient;
import com.seba.reverie.dreamland.DreamlandBiomeKeys;
import com.seba.reverie.shade.ShadeEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

/**
 * A lullaby that is never the same song: slow phrases on a pentatonic minor scale, played on note block chime,
 * bell and harp, detuned by a few cents, a small random walk that rests on the tonic at the end of each phrase.
 * Slower in Pillow Fields, music-box high in Upside-Down Town, sparse and ghostly with Shades about, silent during
 * The Hush and The Long Silence.
 */
final class LullabyPlayer {
    private static final int[] SCALE = {0, 3, 5, 7, 10};
    private static final int DEGREES = 10; // two octaves

    private static int wait = 120;
    private static int degree = 5;
    private static int phraseLeft;

    private LullabyPlayer() {
    }

    static void reset() {
        wait = 120;
        phraseLeft = 0;
    }

    static void tick(Minecraft mc) {
        if (mc.player == null || mc.isPaused() || !ReverieConfig.get(ReverieConfig.LULLABY) || !DreamClient.dreaming) return;
        if (DreamEffectsClient.silenced()) {
            wait = Math.max(wait, 60);
            return;
        }
        if (--wait > 0) return;
        RandomSource rng = mc.player.getRandom();
        var biome = mc.level.getBiome(mc.player.blockPosition());
        boolean musicBox = biome.is(DreamlandBiomeKeys.UPSIDE_DOWN_TOWN);
        float tempo = biome.is(DreamlandBiomeKeys.PILLOW_FIELDS) ? 1.4f : 1f;
        boolean ghostly = mc.level.getEntitiesOfClass(ShadeEntity.class, new AABB(mc.player.blockPosition()).inflate(30)).size() > 3;

        if (phraseLeft <= 0) {
            phraseLeft = 4 + rng.nextInt(5);
            wait = (int) ((40 + rng.nextInt(70)) * tempo * (ghostly ? 1.8f : 1f));
            return;
        }
        int[] steps = {-2, -1, -1, 0, 1, 1, 2};
        int step = steps[rng.nextInt(steps.length)];
        if (degree > 7) step = -Math.abs(step);
        if (degree < 2) step = Math.abs(step);
        degree = Mth.clamp(degree + step, 0, DEGREES - 1);
        if (--phraseLeft == 0) degree = degree >= 3 ? 5 : 0; // phrases come home to the tonic
        int semis = (degree / 5) * 12 + SCALE[degree % 5] + (musicBox ? 12 : 0);
        int note = Mth.clamp(6 + semis - (musicBox ? 6 : 0), 0, 24);
        double cents = rng.nextGaussian() * 4;
        float pitch = (float) (Math.pow(2, (note - 12) / 12.0) * Math.pow(2, cents / 1200));

        Holder<SoundEvent> instrument = musicBox ? SoundEvents.NOTE_BLOCK_BELL
                : ghostly ? SoundEvents.NOTE_BLOCK_CHIME
                : switch (rng.nextInt(5)) {
                    case 0 -> SoundEvents.NOTE_BLOCK_BELL;
                    case 1, 2 -> SoundEvents.NOTE_BLOCK_CHIME;
                    default -> SoundEvents.NOTE_BLOCK_HARP;
                };
        float volume = (float) (ReverieConfig.get(ReverieConfig.LULLABY_VOLUME) * (ghostly ? 0.55f : 0.85f) * (0.75f + rng.nextFloat() * 0.25f));
        mc.getSoundManager().play(new SimpleSoundInstance(instrument.value().getLocation(), SoundSource.MUSIC, volume, pitch, rng,
                false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
        int beat = (int) (16 * tempo);
        wait = rng.nextInt(4) == 0 ? beat * 2 : rng.nextInt(6) == 0 ? beat / 2 : beat;
        if (ghostly && rng.nextInt(3) == 0) wait += beat * 2;
    }
}
