package com.seba.reverie.client;

import com.seba.reverie.ReverieConfig;
import com.seba.reverie.dreamland.DreamWeather;
import com.seba.reverie.network.ShadeEventPayload;
import com.seba.reverie.shade.ShadeCrowdManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client side of dream phenomena: The Hush cuts ambience and music, the Farewell timer (read by the sky). */
public final class DreamEffectsClient {
    private static final float HUSH_VOLUME = 0.08f;

    public static int hushTicks;
    public static int farewellTicks;
    public static int farewellTotal = 1;
    /** Current dream weather (DreamWeather.Kind id, 0 = none) and its timer. */
    public static int weather;
    public static int weatherTicks;
    public static int weatherTotal = 1;

    private DreamEffectsClient() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(DreamEffectsClient::tick);
        NeoForge.EVENT_BUS.addListener(DreamEffectsClient::onSound);
        NeoForge.EVENT_BUS.addListener(DreamEffectsClient::onMusic);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> reset());
    }

    static void reset() {
        hushTicks = 0;
        farewellTicks = 0;
        weather = 0;
        weatherTicks = 0;
    }

    /** True while the world should be quiet (The Hush, The Long Silence). */
    public static boolean silenced() {
        return hushTicks > 0 || weatherIs(DreamWeather.Kind.LONG_SILENCE);
    }

    public static boolean weatherIs(DreamWeather.Kind kind) {
        return weatherTicks > 0 && weather == kind.id;
    }

    /** 0..1..0 envelope of the current weather: fades in and out over {@code ramp} ticks. */
    public static float weatherStrength(int ramp) {
        if (weatherTicks <= 0) return 0;
        int elapsed = weatherTotal - weatherTicks;
        return Math.min(1f, Math.min(elapsed, weatherTicks) / (float) ramp);
    }

    static void onEvent(ShadeEventPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        switch (payload.event()) {
            case ShadeCrowdManager.HUSH -> {
                hushTicks = payload.durationTicks();
                mc.getMusicManager().stopPlaying();
                mc.getSoundManager().stop(null, SoundSource.AMBIENT);
                mc.getSoundManager().stop(null, SoundSource.WEATHER);
                mc.getSoundManager().stop(null, SoundSource.RECORDS);
            }
            case ShadeCrowdManager.RUSH_HIT -> com.seba.reverie.client.dreamland.LucidConfusionClient.jolt();
            case ShadeCrowdManager.FAREWELL -> {
                farewellTicks = payload.durationTicks();
                farewellTotal = Math.max(1, payload.durationTicks());
            }
            default -> {
                DreamWeather.Kind kind = DreamWeather.Kind.byId(payload.event());
                if (kind != null) startWeather(mc, kind, payload.durationTicks());
            }
        }
    }

    private static void startWeather(Minecraft mc, DreamWeather.Kind kind, int duration) {
        weather = kind.id;
        weatherTicks = duration;
        weatherTotal = Math.max(1, duration);
        if (mc.player == null) return;
        var rng = mc.player.getRandom();
        switch (kind) {
            case LONG_SILENCE -> {
                mc.getMusicManager().stopPlaying();
                mc.getSoundManager().stop(null, SoundSource.AMBIENT);
                // A single far-off sound; MASTER so the ducking leaves it alone.
                double a = rng.nextDouble() * Math.PI * 2;
                var p = mc.player.position();
                mc.getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                        net.minecraft.sounds.SoundEvents.BELL_BLOCK, SoundSource.MASTER,
                        0.5f * (float) (double) ReverieConfig.get(ReverieConfig.AMBIENT_VOLUME), 0.5f, rng,
                        p.x + Math.cos(a) * 40, p.y + 10, p.z + Math.sin(a) * 40));
            }
            default -> {
            }
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        if (hushTicks > 0) hushTicks--;
        if (farewellTicks > 0) farewellTicks--;
        if (weatherTicks > 0 && --weatherTicks == 0) weather = 0;
        if (!DreamClient.dreaming) reset();
    }

    private static void onSound(PlaySoundEvent event) {
        if (!silenced()) return;
        SoundInstance sound = event.getSound();
        if (sound == null || sound.getSource() == SoundSource.MASTER) return;
        if (sound.getSource() == SoundSource.MUSIC || sound.getSource() == SoundSource.AMBIENT || sound.getSource() == SoundSource.WEATHER) {
            event.setSound(null);
        } else if (!(sound instanceof TickableSoundInstance)) {
            event.setSound(new Ducked(sound, HUSH_VOLUME));
        }
    }

    private static void onMusic(SelectMusicEvent event) {
        // No music in Dreamland: only its own sounds.
        var level = Minecraft.getInstance().level;
        boolean dreamland = level != null && level.dimension().equals(com.seba.reverie.dream.DreamManager.DREAMLAND);
        if (silenced() || dreamland) event.setMusic(null);
    }

    /** A sound played at a fraction of its volume. Tickable sounds are left alone (the engine ticks them by type). */
    private record Ducked(SoundInstance inner, float factor) implements SoundInstance {
        @Override
        public ResourceLocation getLocation() {
            return inner.getLocation();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return inner.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return inner.getSound();
        }

        @Override
        public SoundSource getSource() {
            return inner.getSource();
        }

        @Override
        public boolean isLooping() {
            return inner.isLooping();
        }

        @Override
        public boolean isRelative() {
            return inner.isRelative();
        }

        @Override
        public int getDelay() {
            return inner.getDelay();
        }

        @Override
        public float getVolume() {
            return inner.getVolume() * factor;
        }

        @Override
        public float getPitch() {
            return inner.getPitch();
        }

        @Override
        public double getX() {
            return inner.getX();
        }

        @Override
        public double getY() {
            return inner.getY();
        }

        @Override
        public double getZ() {
            return inner.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return inner.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return inner.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return inner.canPlaySound();
        }

        @Override
        public java.util.concurrent.CompletableFuture<net.minecraft.client.sounds.AudioStream> getStream(
                net.minecraft.client.sounds.SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return inner.getStream(buffers, sound, looping);
        }
    }
}
