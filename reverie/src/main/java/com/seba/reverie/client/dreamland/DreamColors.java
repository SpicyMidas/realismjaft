package com.seba.reverie.client.dreamland;

import net.minecraft.util.Mth;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;

/**
 * Pillow Fields signature: the grass colour drifts with position through slow pink -> lavender -> powder-blue
 * gradients. Other dream biomes shift the palette. Biomes are told apart by their (unique) fog colours.
 */
public final class DreamColors {
    private static final int[] PASTEL = {0xF2B3D6, 0xD3B6F2, 0xB4C8F5, 0xC7B2EC, 0xF5BFD0};
    private static final int[] MARSH = {0x8FD3C8, 0xA3C9E8, 0x9FE0C2};
    private static final int[] EYE = {0xC9A7E8, 0xE0A9D2, 0xBCE6D0, 0xD7B9F0};
    private static final int[] BLOSSOM = {0xFFB0D4, 0xF29BE0, 0xFFC7DA, 0xE7A8F5};
    private static final int[] DUSK = {0xB9A6D6, 0xA898C9, 0xC7B0DD};

    public static final ColorResolver GRASS = DreamColors::grass;
    /** Two-tint leaves: pink and orchid, drifting with position like the grass. */
    public static final ColorResolver LEAVES = (biome, x, z) -> {
        double t = (Math.sin(x * 0.05 + z * 0.021) + Math.cos(z * 0.043) + 2) / 4 * (BLOSSOM.length - 1);
        int i = Mth.clamp((int) Math.floor(t), 0, BLOSSOM.length - 2);
        return lerp(BLOSSOM[i], BLOSSOM[i + 1], (float) (t - i));
    };

    private DreamColors() {
    }

    static int grass(Biome biome, double x, double z) {
        int fog = biome.getFogColor();
        int[] palette = fog == 0x6FA4B8 ? MARSH : fog == 0xB58BC4 ? EYE : fog == 0x5A5878 || fog == 0x8C7FA8 ? DUSK : PASTEL;
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
