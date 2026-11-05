package com.seba.reverie.client.dreamland;

import com.seba.reverie.ReverieConfig;
import com.seba.reverie.client.DreamClient;
import com.seba.reverie.client.DreamEffectsClient;
import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.dreamland.DreamWeather;
import com.seba.reverie.dreamland.DreamlandBiomeKeys;
import com.seba.reverie.registry.ModParticles;
import com.seba.reverie.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

/**
 * Everything small in the dream air, client side: drifting petals and pages (falling up in some biomes: the
 * wrong rain), the weather visuals the server announces, and the rare dream captions from nowhere.
 */
public final class DreamAmbienceClient {
    private static float windX = 1;
    private static float windZ;
    private static int lastWeather;

    private DreamAmbienceClient() {
    }

    static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        RandomSource rng = mc.player.getRandom();
        Vec3 p = mc.player.position();
        Holder<Biome> biome = mc.level.getBiome(mc.player.blockPosition());
        boolean wrongRain = ReverieConfig.get(ReverieConfig.WRONG_RAIN);

        // Biome ambience: what drifts here, and which way.
        if (biome.is(DreamlandBiomeKeys.PILLOW_FIELDS)) emit(mc, rng, p, ModParticles.DREAM_PETAL.get(), 0.3f, -0.03f);
        else if (biome.is(DreamlandBiomeKeys.EYE_MEADOW)) emit(mc, rng, p, ModParticles.DREAM_PETAL.get(), 0.4f, wrongRain ? 0.035f : -0.03f);
        else if (biome.is(DreamlandBiomeKeys.UPSIDE_DOWN_TOWN)) emit(mc, rng, p, ModParticles.DREAM_PAGE.get(), 0.15f, wrongRain ? 0.03f : -0.025f);
        else if (biome.is(DreamlandBiomeKeys.MIRROR_MARSH)) emit(mc, rng, p, ModParticles.DREAM_RAINDROP.get(), wrongRain ? 1.2f : 0, 0.55f);
        else if (biome.is(DreamlandBiomeKeys.SANDGLASS_DUNES)) emit(mc, rng, p, ModParticles.DREAM_PAGE.get(), 0.08f, -0.025f);
        else if (biome.is(DreamlandBiomeKeys.HOLLOW_SPIRES)) emit(mc, rng, p, ModParticles.DREAM_PAGE.get(), 0.04f, -0.025f);
        else if (biome.is(DreamlandBiomeKeys.CLOUDREACH)) emit(mc, rng, p, ModParticles.DREAM_PETAL.get(), 0.15f, wrongRain ? 0.02f : -0.02f);
        else emit(mc, rng, p, ModParticles.DREAM_PAGE.get(), 0.03f, -0.02f);

        weather(mc, rng, p);
        captions(mc, rng, p);
    }

    /** {@code rate} particles per tick on average, in a box around the player, moving at {@code vy}. */
    private static void emit(Minecraft mc, RandomSource rng, Vec3 p, SimpleParticleType type, float rate, float vy) {
        int n = (int) rate + (rng.nextFloat() < rate - (int) rate ? 1 : 0);
        for (int i = 0; i < n; i++) {
            double x = p.x + (rng.nextDouble() - 0.5) * 24;
            double z = p.z + (rng.nextDouble() - 0.5) * 24;
            double y = p.y + (vy > 0 ? -6 + rng.nextDouble() * 6 : 2 + rng.nextDouble() * 9);
            mc.level.addParticle(type, x, y, z, (rng.nextDouble() - 0.5) * 0.02, vy * (0.7 + rng.nextDouble() * 0.6), (rng.nextDouble() - 0.5) * 0.02);
        }
    }

    private static void weather(Minecraft mc, RandomSource rng, Vec3 p) {
        int w = DreamEffectsClient.weatherTicks > 0 ? DreamEffectsClient.weather : 0;
        if (w != lastWeather && w == DreamWeather.Kind.PETAL_DRIFT.id) {
            float a = rng.nextFloat() * Mth.TWO_PI;
            windX = Mth.cos(a);
            windZ = Mth.sin(a);
        }
        lastWeather = w;
        if (w == 0) return;
        int elapsed = DreamEffectsClient.weatherTotal - DreamEffectsClient.weatherTicks;
        if (w == DreamWeather.Kind.STARFALL.id) {
            // Half of them where you are looking, so a Starfall is actually seen.
            if (rng.nextInt(7) == 0) DreamSky.shootingStar(rng, rng.nextBoolean() ? mc.player.getYRot() : Float.NaN);
        } else if (w == DreamWeather.Kind.PETAL_DRIFT.id) {
            // A long river of petals across the whole view, and a soft chime that rises at the start.
            float strength = DreamEffectsClient.weatherStrength(60);
            int n = (int) (9 * strength);
            for (int i = 0; i < n; i++) {
                double side = (rng.nextDouble() - 0.5) * 32;
                double x = p.x - windX * 14 - windZ * side;
                double z = p.z - windZ * 14 + windX * side;
                double y = p.y - 2 + rng.nextDouble() * 12;
                double speed = 0.25 + rng.nextDouble() * 0.15;
                mc.level.addParticle(ModParticles.DREAM_PETAL.get(), x, y, z, windX * speed, 0.01 * (rng.nextDouble() - 0.3), windZ * speed);
            }
            if (elapsed < 48 && elapsed % 12 == 0) {
                mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT,
                        0.6f, 0.7f + elapsed / 12 * 0.25f, rng, p.x, p.y + 2, p.z));
            }
        } else if (w == DreamWeather.Kind.UPWARD_RAIN.id) {
            int n = (int) (22 * DreamEffectsClient.weatherStrength(40));
            for (int i = 0; i < n; i++) {
                mc.level.addParticle(ModParticles.DREAM_RAINDROP.get(), p.x + (rng.nextDouble() - 0.5) * 24, p.y - 4 + rng.nextDouble() * 3,
                        p.z + (rng.nextDouble() - 0.5) * 24, 0, 0.6 + rng.nextDouble() * 0.3, 0);
            }
            if (elapsed == 0) {
                mc.getSoundManager().play(new SimpleSoundInstance(SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.4f, 0.6f, rng, p.x, p.y, p.z));
            }
        }
    }

    /** Fog Tide strength 0..1 (read by the fog handler and the Shade renderer). */
    public static float fogTide() {
        return DreamEffectsClient.weatherIs(DreamWeather.Kind.FOG_TIDE) ? DreamEffectsClient.weatherStrength(80) : 0;
    }

    private static void captions(Minecraft mc, RandomSource rng, Vec3 p) {
        if (!ReverieConfig.get(ReverieConfig.DREAM_CAPTIONS) || DreamEffectsClient.silenced() || !DreamClient.dreaming) return;
        if (rng.nextInt(900) != 0) return; // roughly every 45 s
        var event = ModSounds.CAPTIONS.get(rng.nextInt(ModSounds.CAPTIONS.size())).get();
        double a = rng.nextDouble() * Math.PI * 2;
        double r = 9 + rng.nextDouble() * 9;
        float pitch = event == ModSounds.CLOCK_BACKWARDS.get() ? 0.6f : event == ModSounds.BED_CREAK.get() ? 0.55f : 0.8f + rng.nextFloat() * 0.3f;
        mc.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.AMBIENT, 0.35f, pitch, rng,
                p.x + Math.cos(a) * r, p.y + rng.nextDouble() * 3, p.z + Math.sin(a) * r));
    }

    static boolean inDreamland(Minecraft mc) {
        return mc.level != null && mc.level.dimension().equals(DreamManager.DREAMLAND);
    }
}
