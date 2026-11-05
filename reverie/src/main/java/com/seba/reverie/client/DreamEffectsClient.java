package com.seba.reverie.client;

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
    }

    /** True while the world should be quiet (The Hush; later also The Long Silence). */
    public static boolean silenced() {
        return hushTicks > 0;
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
            case ShadeCrowdManager.FAREWELL -> {
                farewellTicks = payload.durationTicks();
                farewellTotal = Math.max(1, payload.durationTicks());
            }
            default -> {
            }
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        if (hushTicks > 0) hushTicks--;
        if (farewellTicks > 0) farewellTicks--;
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
        if (silenced()) event.setMusic(null);
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
