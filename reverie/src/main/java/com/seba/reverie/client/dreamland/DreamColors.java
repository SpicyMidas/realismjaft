package com.seba.reverie.client.dreamland;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;

/**
 * Grass and leaf tints, drifting with position. Muted and a little sickly rather than pastel: sage, moss, slate and
 * dun on the fields, bog greens in the marsh, bruised rose and olive in the Eye Meadow, ash in the dark biomes; the
 * leaves turn from dark sage through plum and slate to a dead ochre. Biomes are told apart by their fog colours
 * (DreamlandBiomeKeys.FOG_*).
 */
public final class DreamColors {
    private static final int[] FIELDS = {0x76907E, 0x667E84, 0x86866A, 0x6E748C, 0x7E8C70};
    private static final int[] MARSH = {0x4A7462, 0x587456, 0x466872};
    private static final int[] EYE = {0x84647A, 0x76765A, 0x667484, 0x7C5E6A};
    private static final int[] LEAVES_PALETTE = {0x56705F, 0x6E5468, 0x5E6C80, 0x84744E};
    private static final int[] DUSK = {0x5A6068, 0x666250, 0x545A68};

    public static final ColorResolver GRASS = DreamColors::grass;
    private static final int[] FROST_LEAVES = {0xB8CCD8, 0xC8D8E4, 0xA8BCCC, 0xD4E0EA};

    /** Leaves drift through their own palette with position; in Frostlight they are rimed with frost. */
    public static final ColorResolver LEAVES = (biome, x, z) -> {
        int[] palette = biome.getFogColor() == com.seba.reverie.dreamland.DreamlandBiomeKeys.FOG_FROST ? FROST_LEAVES : LEAVES_PALETTE;
        double t = (Math.sin(x * 0.05 + z * 0.021) + Math.cos(z * 0.043) + 2) / 4 * (palette.length - 1);
        int i = Mth.clamp((int) Math.floor(t), 0, palette.length - 2);
        return lerp(palette[i], palette[i + 1], (float) (t - i));
    };

    private DreamColors() {
    }

    static int grass(Biome biome, double x, double z) {
        int fog = biome.getFogColor();
        int[] palette = fog == com.seba.reverie.dreamland.DreamlandBiomeKeys.FOG_MARSH ? MARSH
                : fog == com.seba.reverie.dreamland.DreamlandBiomeKeys.FOG_EYE ? EYE
                : fog == com.seba.reverie.dreamland.DreamlandBiomeKeys.FOG_SPIRES || fog == com.seba.reverie.dreamland.DreamlandBiomeKeys.FOG_TOWN
                || fog == com.seba.reverie.dreamland.DreamlandBiomeKeys.FOG_VOID ? DUSK : FIELDS;
        double t = (Math.sin(x * 0.011 + Math.sin(z * 0.007) * 1.7) + Math.cos(z * 0.009 - x * 0.003) + 2) / 4 * (palette.length - 1);
        int i = Mth.clamp((int) Math.floor(t), 0, palette.length - 2);
        return lerp(palette[i], palette[i + 1], (float) (t - i));
    }

    static int lerp(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    static int shift(int c, float f) {
        return (int) ((c >> 16 & 255) * f) << 16 | (int) ((c >> 8 & 255) * f) << 8 | (int) ((c & 255) * f);
    }
}
