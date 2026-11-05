package com.seba.reverie.client.dreamland;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

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
import net.minecraft.world.phys.AABB;

/**
 * A procedural lullaby, never the same song twice. Each dream picks a key; the harmony walks a minor progression
 * (i - VI - III - VII), one chord per bar: a soft bass on the downbeat, a harp arpeggio through the chord, and over
 * it a melody built from short motifs in an A A' B A shape, on bell or chime, each note followed by a quiet echo.
 * Between phrases it breathes for a bar. Slower in Pillow Fields, a music box in Upside-Down Town, sparse and ghostly
 * when Shades are near, silent during The Hush and The Long Silence.
 */
final class LullabyPlayer {
    /** Semitones of the natural minor scale; chords are built on degrees 0 (i), 5 (VI), 2 (III), 6 (VII). */
    private static final int[] MINOR = {0, 2, 3, 5, 7, 8, 10};
    private static final int[] PROGRESSION = {0, 5, 2, 6};

    private record Note(long at, Holder<SoundEvent> instrument, int semitone, float volume) implements Comparable<Note> {
        @Override
        public int compareTo(Note o) {
            return Long.compare(at, o.at);
        }
    }

    private static final PriorityQueue<Note> QUEUE = new PriorityQueue<>();
    private static final Random RNG = new Random();
    private static long clock;
    private static long nextBar = 40;
    private static int bar;
    private static int key = -1;      // tonic as a note-block note (0 = F#3)
    private static List<Integer> motifA;
    private static List<Integer> motifB;

    private LullabyPlayer() {
    }

    static void reset() {
        QUEUE.clear();
        clock = 0;
        nextBar = 40;
        bar = 0;
        key = -1;
        motifA = motifB = null;
    }

    static void tick(Minecraft mc) {
        if (mc.player == null || mc.isPaused()) return;
        if (!ReverieConfig.get(ReverieConfig.LULLABY) || !DreamClient.dreaming) {
            if (key >= 0) reset();
            return;
        }
        clock++;
        if (DreamEffectsClient.silenced()) {
            QUEUE.clear();
            nextBar = clock + 60;
            return;
        }
        if (key < 0) {
            key = RNG.nextInt(7);          // somewhere low in the note-block range, so melodies fit above
            motifA = motif();
            motifB = motif();
        }
        var biome = mc.level.getBiome(mc.player.blockPosition());
        boolean musicBox = biome.is(DreamlandBiomeKeys.UPSIDE_DOWN_TOWN);
        boolean slow = biome.is(DreamlandBiomeKeys.PILLOW_FIELDS) || biome.is(DreamlandBiomeKeys.CLOUDREACH);
        boolean ghostly = mc.level.getEntitiesOfClass(ShadeEntity.class, new AABB(mc.player.blockPosition()).inflate(30)).size() > 3;
        if (clock >= nextBar) scheduleBar(musicBox, slow, ghostly);
        while (!QUEUE.isEmpty() && QUEUE.peek().at <= clock) play(mc, QUEUE.poll());
    }

    /** A motif: eight steps over two bars, as scale-degree offsets (null = rest). */
    private static List<Integer> motif() {
        List<Integer> m = new ArrayList<>();
        int degree = 2 + RNG.nextInt(3);
        for (int i = 0; i < 8; i++) {
            if (i > 0 && RNG.nextInt(4) == 0) {
                m.add(null);
                continue;
            }
            int[] steps = {-2, -1, -1, 0, 1, 1, 2};
            degree = Math.max(0, Math.min(9, degree + steps[RNG.nextInt(steps.length)]));
            m.add(degree);
        }
        return m;
    }

    private static void scheduleBar(boolean musicBox, boolean slow, boolean ghostly) {
        int beat = slow ? 15 : 12;
        long t0 = clock;
        nextBar = t0 + beat * 4L;
        int phrase = bar % 9;            // bars 0-7 play the A A' B A form; bar 8 breathes
        int chordDegree = PROGRESSION[bar % 4];
        bar++;
        float base = (float) (double) ReverieConfig.get(ReverieConfig.LULLABY_VOLUME);
        int root = MINOR[chordDegree];
        int third = MINOR[(chordDegree + 2) % 7] + ((chordDegree + 2) >= 7 ? 12 : 0);
        int fifth = MINOR[(chordDegree + 4) % 7] + ((chordDegree + 4) >= 7 ? 12 : 0);

        // bass and pad
        if (!ghostly) add(t0, SoundEvents.NOTE_BLOCK_HARP, root - 12, base * 0.45f);
        int[] arp = {root, fifth, third + 12, fifth};
        for (int i = 0; i < 4; i++) {
            if (ghostly && i % 2 == 1) continue;
            add(t0 + (long) i * beat + (i == 0 ? 2 : 0), SoundEvents.NOTE_BLOCK_HARP, arp[i], base * (i == 0 ? 0.28f : 0.2f));
        }
        if (phrase == 8) {
            if (RNG.nextInt(3) == 0) { // a new song now and then
                motifA = motif();
                motifB = motif();
            }
            return;
        }
        // melody: which motif, and which half of it
        List<Integer> motif = (phrase / 2 == 2) ? motifB : motifA;
        boolean variant = phrase / 2 == 1;
        int half = phrase % 2;
        Holder<SoundEvent> lead = musicBox ? SoundEvents.NOTE_BLOCK_BELL : ghostly ? SoundEvents.NOTE_BLOCK_CHIME
                : (bar / 8) % 2 == 0 ? SoundEvents.NOTE_BLOCK_BELL : SoundEvents.NOTE_BLOCK_CHIME;
        for (int i = 0; i < 4; i++) {
            Integer degree = motif.get(half * 4 + i);
            if (degree == null || ghostly && RNG.nextBoolean()) continue;
            if (variant && half == 1 && i == 3) degree = Math.max(0, degree + (RNG.nextBoolean() ? 1 : -2));
            // lean on chord tones on the strong beats
            if (i % 2 == 0 && RNG.nextInt(3) != 0) degree = nearestChordTone(degree, chordDegree);
            int semi = degreeToSemitone(degree) + (musicBox ? 12 : 0);
            long at = t0 + (long) i * beat;
            float v = base * (i == 0 ? 0.62f : 0.5f) * (ghostly ? 0.7f : 1f);
            add(at, lead, semi, v);
            add(at + beat / 2, lead, semi, v * 0.28f);       // the echo
            if (RNG.nextInt(6) == 0) add(at + beat / 2 + 2, lead, semi + (RNG.nextBoolean() ? 2 : 3), v * 0.3f); // grace
        }
    }

    private static int degreeToSemitone(int degree) {
        return MINOR[degree % 7] + 12 * (degree / 7) + 7;   // melodies sit a fifth above the tonic
    }

    private static int nearestChordTone(int degree, int chordDegree) {
        int best = degree;
        int bestDist = 99;
        for (int d = Math.max(0, degree - 2); d <= degree + 2; d++) {
            int rel = Math.floorMod(d - chordDegree, 7);
            if ((rel == 0 || rel == 2 || rel == 4) && Math.abs(d - degree) < bestDist) {
                best = d;
                bestDist = Math.abs(d - degree);
            }
        }
        return best;
    }

    private static void add(long at, Holder<SoundEvent> instrument, int semitone, float volume) {
        QUEUE.add(new Note(at, instrument, semitone, volume));
    }

    private static void play(Minecraft mc, Note n) {
        int note = key + n.semitone();
        while (note < 0) note += 12;
        while (note > 24) note -= 12;
        double cents = RNG.nextGaussian() * 3;
        float pitch = (float) (Math.pow(2, (note - 12) / 12.0) * Math.pow(2, cents / 1200));
        mc.getSoundManager().play(new SimpleSoundInstance(n.instrument().value().getLocation(), SoundSource.MUSIC, n.volume(), pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }
}
