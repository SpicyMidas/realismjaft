package com.seba.reverie.client.dreamland;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.seba.reverie.Reverie;
import com.seba.reverie.dreamland.IslandField.Kind;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The skies of the new biomes, drawn over the twilight gradient and faded in and out by the region weights
 * (BiomeMechanicsClient), so walking from one biome into the next changes the sky slowly, as the Nether's
 * fog does. Everything is camera-relative: ceilings and surfaces hang at a fixed height above your head. The
 * Waiting Rooms have no sky at all, only the same stale haze in every direction.
 */
final class BiomeSkies {
    private static final ResourceLocation MOON = Reverie.id("textures/environment/dream_moon.png");
    private static final ResourceLocation GRIN = Reverie.id("textures/environment/grin_moon.png");
    private static final ResourceLocation BLACK_SUN = Reverie.id("textures/environment/black_sun.png");
    private static final ResourceLocation CORONA = Reverie.id("textures/environment/corona.png");
    private static final ResourceLocation STATIC = Reverie.id("textures/environment/sky_static.png");
    private static final ResourceLocation TEST_CARD = Reverie.id("textures/block/test_card.png");
    private static final ResourceLocation CEILING = Reverie.id("textures/environment/nursery_ceiling.png");
    private static final ResourceLocation STICKER = Reverie.id("textures/environment/glow_star.png");
    private static final ResourceLocation MOBILE = Reverie.id("textures/environment/mobile.png");
    private static final float R = 100f;

    private static final float[][] SHARDS = new float[40][6];   // az, el, size, roll, hue, phase
    private static final float[][] STICKERS = new float[90][4]; // x, z, size, phase
    private static final float[][] RAYS = new float[12][4];     // az, dist, width, phase
    private static double grinAz = Math.PI;
    private static int glimpse;
    private static int nextGlimpse = 500;

    static {
        RandomSource rng = RandomSource.create(0xB10E5L);
        for (float[] s : SHARDS) {
            s[0] = rng.nextFloat() * Mth.TWO_PI;
            s[1] = 0.12f + rng.nextFloat() * 0.95f;
            s[2] = 1.2f + rng.nextFloat() * 3.2f;
            s[3] = rng.nextFloat() * Mth.TWO_PI;
            s[4] = rng.nextFloat();
            s[5] = rng.nextFloat() * Mth.TWO_PI;
        }
        for (float[] s : STICKERS) {
            s[0] = (rng.nextFloat() - 0.5f) * 300;
            s[1] = (rng.nextFloat() - 0.5f) * 300;
            s[2] = 1.2f + rng.nextFloat() * 1.6f;
            s[3] = rng.nextFloat() * Mth.TWO_PI;
        }
        for (float[] s : RAYS) {
            s[0] = rng.nextFloat() * Mth.TWO_PI;
            s[1] = 25 + rng.nextFloat() * 70;
            s[2] = 3 + rng.nextFloat() * 6;
            s[3] = rng.nextFloat() * Mth.TWO_PI;
        }
    }

    private BiomeSkies() {
    }

    /** Client tick: the grinning moon creeps around behind you; Signal Lost now and then tunes in. */
    static void tick(Camera camera) {
        double behind = Math.toRadians(camera.getYRot()); // you look at sky azimuth yaw + 180, so yaw is behind you
        double diff = Mth.wrapDegrees(Math.toDegrees(behind - grinAz));
        grinAz += Math.toRadians(diff) * 0.035;
        if (glimpse > 0) glimpse--;
        else if (BiomeMechanicsClient.weight(Kind.SIGNAL_LOST) > 0.5f && --nextGlimpse <= 0) {
            glimpse = 26;
            nextGlimpse = 500 + RandomSource.create().nextInt(700);
        }
    }

    /** How much of the old sky (planet, moons, whale, eye) is left. */
    static float classic() {
        float k = 0;
        for (Kind kind : Kind.values()) k += BiomeMechanicsClient.weight(kind) * switch (kind) {
            case FROSTLIGHT_TUNDRA -> 0.55f;
            case SKYREEF -> 0.1f;
            case LUCID_GEODES -> 0.35f;
            case THE_NURSERY, BLACKWOOD, WAITING_ROOMS, THE_MAW, SIGNAL_LOST -> 0f;
            default -> 1f;
        };
        return isUnset() ? 1 : Mth.clamp(k, 0, 1);
    }

    static float stars() {
        float k = 0;
        for (Kind kind : Kind.values()) k += BiomeMechanicsClient.weight(kind) * switch (kind) {
            case FROSTLIGHT_TUNDRA -> 1.6f;
            case LUCID_GEODES -> 1.2f;
            case SKYREEF -> 0.15f;
            case BLACKWOOD -> 0.25f;
            case THE_MAW -> 0.1f;
            case THE_NURSERY, WAITING_ROOMS, SIGNAL_LOST -> 0f;
            default -> 1f;
        };
        return isUnset() ? 1 : k;
    }

    /** Aurora strength: in Frostlight the whole sky is aurora. */
    static float aurora() {
        float k = 0;
        for (Kind kind : Kind.values()) k += BiomeMechanicsClient.weight(kind) * switch (kind) {
            case FROSTLIGHT_TUNDRA -> 3.2f;
            case LUCID_GEODES -> 0.6f;
            case SKYREEF -> 0.2f;
            case THE_NURSERY, BLACKWOOD, WAITING_ROOMS, THE_MAW, SIGNAL_LOST -> 0f;
            default -> 1f;
        };
        return isUnset() ? 1 : k;
    }

    private static boolean isUnset() {
        float sum = 0;
        for (Kind kind : Kind.values()) sum += BiomeMechanicsClient.weight(kind);
        return sum < 0.01f;
    }

    // ------------------------------------------------------------------ drawing

    /** Additive layers (blend ONE/ONE-ish set by the caller): light that only ever brightens the sky. */
    static void additive(Matrix4f m, float time, float partial) {
        float reef = BiomeMechanicsClient.weight(Kind.SKYREEF);
        if (reef > 0.01f) reef(m, time, reef);
        float geode = BiomeMechanicsClient.weight(Kind.LUCID_GEODES);
        if (geode > 0.01f) prisms(m, time, geode);
        float nursery = BiomeMechanicsClient.weight(Kind.THE_NURSERY);
        if (nursery > 0.01f) stickers(m, time, nursery);
        float maw = BiomeMechanicsClient.weight(Kind.THE_MAW);
        if (maw > 0.01f) corona(m, maw);
    }

    /** Normally blended layers, drawn after the additive ones. */
    static void normal(Matrix4f m, float time, float partial, Camera camera) {
        float nursery = BiomeMechanicsClient.weight(Kind.THE_NURSERY);
        if (nursery > 0.01f) {
            ceiling(m, time, nursery);
            stickersOnTop(m, time, nursery);
            mobile(m, time, nursery, camera);
        }
        float geode = BiomeMechanicsClient.weight(Kind.LUCID_GEODES);
        if (geode > 0.01f) splitMoon(m, time, geode);
        float wood = BiomeMechanicsClient.weight(Kind.BLACKWOOD);
        if (wood > 0.01f) grin(m, time, wood);
        float maw = BiomeMechanicsClient.weight(Kind.THE_MAW);
        if (maw > 0.01f) blackSun(m, time, maw);
        float signal = BiomeMechanicsClient.weight(Kind.SIGNAL_LOST);
        if (signal > 0.01f) staticSky(m, time, signal);
    }

    // --- Skyreef: the underside of a sea, seen from below, and the light coming down through it

    private static void reef(Matrix4f m, float time, float w) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = begin(DefaultVertexFormat.POSITION_COLOR);
        int n = 22;
        float span = 340;
        float y = 52;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                float x0 = -span + i * 2 * span / n;
                float x1 = x0 + 2 * span / n;
                float z0 = -span + j * 2 * span / n;
                float z1 = z0 + 2 * span / n;
                surfaceVertex(b, m, x0, y, z0, time, w);
                surfaceVertex(b, m, x0, y, z1, time, w);
                surfaceVertex(b, m, x1, y, z1, time, w);
                surfaceVertex(b, m, x1, y, z0, time, w);
            }
        // God rays: slanted shafts from the surface down into the haze.
        for (float[] r : RAYS) {
            double a = r[0] + Math.sin(time * 0.002 + r[3]) * 0.05;
            float cx = (float) (Math.sin(a) * r[1]);
            float cz = (float) (-Math.cos(a) * r[1]);
            float sway = Mth.sin(time * 0.01f + r[3]) * 6;
            float alpha = 0.1f * w * (0.6f + 0.4f * Mth.sin(time * 0.017f + r[3] * 3));
            float px = (float) Math.cos(a) * r[2];
            float pz = (float) Math.sin(a) * r[2];
            b.addVertex(m, cx - px, y, cz - pz).setColor(0.7f, 0.95f, 1f, alpha);
            b.addVertex(m, cx + px, y, cz + pz).setColor(0.7f, 0.95f, 1f, alpha);
            b.addVertex(m, cx + px * 1.8f + sway, -30, cz + pz * 1.8f).setColor(0.5f, 0.85f, 1f, 0);
            b.addVertex(m, cx - px * 1.8f + sway, -30, cz - pz * 1.8f).setColor(0.5f, 0.85f, 1f, 0);
        }
        draw(b);
        // The sun, wobbling through the water.
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CORONA);
        BufferBuilder s = begin(DefaultVertexFormat.POSITION_TEX_COLOR);
        float wob = Mth.sin(time * 0.03f) * 0.02f;
        texQuad(s, m, dir(0.3 + wob, Math.toRadians(80)), 26, 26, time * 0.001f, 0.8f, 1f, 1f, 0.5f * w);
        draw(s);
    }

    private static void surfaceVertex(BufferBuilder b, Matrix4f m, float x, float y, float z, float time, float w) {
        double dist = Math.sqrt(x * x + z * z);
        float fade = (float) Mth.clamp(1 - (dist - 80) / 260, 0, 1);
        float caustic = Math.abs(Mth.sin(x * 0.045f + time * 0.03f + Mth.sin(z * 0.03f) * 2) * Mth.sin(z * 0.05f - time * 0.025f));
        float a = (0.06f + 0.22f * caustic) * fade * w;
        b.addVertex(m, x, y + Mth.sin(x * 0.03f + time * 0.02f) * 2.5f, z).setColor(0.55f, 0.92f, 1f, a);
    }

    // --- Lucid Geodes: drifting crystal shards, refracted light, a moon broken in two

    private static void prisms(Matrix4f m, float time, float w) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = begin(DefaultVertexFormat.POSITION_COLOR);
        for (float[] s : SHARDS) {
            float tw = 0.55f + 0.45f * Mth.sin(time * 0.04f + s[5]);
            float[] c = hue(s[4]);
            Vec3 d = dir(s[0] + time * 0.00008f, s[1]);
            Vec3[] ru = basis(d, s[3] + time * 0.002f);
            Vec3 center = d.scale(R);
            Vec3 up = ru[1].scale(s[2] * 1.8);
            Vec3 side = ru[0].scale(s[2] * 0.45);
            float a = 0.55f * tw * w;
            put(b, m, center.add(up), c, a);
            put(b, m, center.add(side), c, a * 0.6f);
            put(b, m, center.subtract(up), c, a);
            put(b, m, center.subtract(side), c, a * 0.6f);
        }
        // Two arcs of split light across the sky.
        for (int arc = 0; arc < 2; arc++) {
            double az = arc == 0 ? 0.8 : 3.9;
            for (int i = 0; i < 40; i++) {
                double e0 = i / 40.0 * Math.PI;
                double e1 = (i + 1) / 40.0 * Math.PI;
                for (int band = 0; band < 5; band++) {
                    float[] c = hue(band / 5f);
                    double off = (band - 2) * 0.012;
                    float a = 0.10f * w * Mth.sin((float) e0);
                    put(b, m, arcPoint(az + off, e0), c, a);
                    put(b, m, arcPoint(az + off + 0.012, e0), c, a);
                    put(b, m, arcPoint(az + off + 0.012, e1), c, a);
                    put(b, m, arcPoint(az + off, e1), c, a);
                }
            }
        }
        draw(b);
    }

    private static Vec3 arcPoint(double az, double t) {
        // A great circle tilted 60 degrees, from horizon to horizon.
        double x = Math.cos(t);
        double y = Math.sin(t) * 0.86;
        double z = Math.sin(t) * 0.5;
        return new Vec3(x * Math.cos(az) - z * Math.sin(az), y, x * Math.sin(az) + z * Math.cos(az)).scale(R);
    }

    private static void splitMoon(Matrix4f m, float time, float w) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, MOON);
        BufferBuilder b = begin(DefaultVertexFormat.POSITION_TEX_COLOR);
        float gap = 0.035f + 0.025f * Mth.sin(time * 0.002f);
        Vec3 d = dir(Math.toRadians(160), Math.toRadians(30));
        Vec3[] ru = basis(d, 0.25f);
        Vec3 c = d.scale(R);
        float half = 7;
        for (int side = -1; side <= 1; side += 2) {
            Vec3 center = c.add(ru[0].scale(side * (half + gap * R)));
            float u0 = side < 0 ? 0 : 0.125f;
            float u1 = side < 0 ? 0.125f : 0.25f;
            Vec3 r = ru[0].scale(half);
            Vec3 u = ru[1].scale(half * 2);
            uv(b, m, center.subtract(r).add(u), u0, 0, 0.85f * w);
            uv(b, m, center.subtract(r).subtract(u), u0, 1, 0.85f * w);
            uv(b, m, center.add(r).subtract(u), u1, 1, 0.85f * w);
            uv(b, m, center.add(r).add(u), u1, 0, 0.85f * w);
        }
        draw(b);
    }

    // --- The Nursery: the ceiling of a child's room, glow-in-the-dark stars, a mobile turning above you

    private static void ceiling(Matrix4f m, float time, float w) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CEILING);
        BufferBuilder b = begin(DefaultVertexFormat.POSITION_TEX_COLOR);
        int n = 16;
        float span = 420;
        float y = 34;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                float x0 = -span + i * 2 * span / n;
                float x1 = x0 + 2 * span / n;
                float z0 = -span + j * 2 * span / n;
                float z1 = z0 + 2 * span / n;
                ceilingVertex(b, m, x0, y, z0, w);
                ceilingVertex(b, m, x0, y, z1, w);
                ceilingVertex(b, m, x1, y, z1, w);
                ceilingVertex(b, m, x1, y, z0, w);
            }
        draw(b);
    }

    private static void ceilingVertex(BufferBuilder b, Matrix4f m, float x, float y, float z, float w) {
        double dist = Math.sqrt(x * x + z * z);
        float a = (float) Mth.clamp(1 - (dist - 110) / 260, 0, 1) * w;
        b.addVertex(m, x, y, z).setUv(x / 10f, z / 10f).setColor(1f, 1f, 1f, a);
    }

    private static void stickers(Matrix4f m, float time, float w) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, STICKER);
        BufferBuilder b = begin(DefaultVertexFormat.POSITION_TEX_COLOR);
        for (float[] s : STICKERS) stickerQuad(b, m, s, time, w * (0.5f + 0.25f * Mth.sin(time * 0.03f + s[3])), 33.6f, 1.6f);
        draw(b);
    }

    /** The sticker shapes themselves, drawn once more normally so they read as paper stars, not just glow. */
    private static void stickersOnTop(Matrix4f m, float time, float w) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, STICKER);
        BufferBuilder b = begin(DefaultVertexFormat.POSITION_TEX_COLOR);
        for (float[] s : STICKERS) stickerQuad(b, m, s, time, w * 0.8f, 33.5f, 1f);
        draw(b);
    }

    private static void stickerQuad(BufferBuilder b, Matrix4f m, float[] s, float time, float alpha, float y, float grow) {
        float size = s[2] * grow;
        double dist = Math.sqrt(s[0] * s[0] + s[1] * s[1]);
        float a = alpha * (float) Mth.clamp(1 - (dist - 90) / 80, 0, 1);
        float c = Mth.cos(s[3]);
        float sn = Mth.sin(s[3]);
        float[][] corners = {{-1, -1, 0, 0}, {-1, 1, 0, 1}, {1, 1, 1, 1}, {1, -1, 1, 0}};
        for (float[] k : corners) {
            float lx = (k[0] * c - k[1] * sn) * size;
            float lz = (k[0] * sn + k[1] * c) * size;
            b.addVertex(m, s[0] + lx, y, s[1] + lz).setUv(k[2], k[3]).setColor(1f, 1f, 1f, a);
        }
    }

    private static void mobile(Matrix4f m, float time, float w, Camera camera) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder lines = begin(DefaultVertexFormat.POSITION_COLOR);
        float hub = 30;
        float arm = 11;
        float turn = time * 0.004f;
        Vec3[] ends = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            float a = turn + i * Mth.HALF_PI;
            float sway = Mth.sin(time * 0.02f + i) * 0.4f;
            Vec3 tip = new Vec3(Mth.cos(a) * arm, hub - 1.5, Mth.sin(a) * arm);
            ends[i] = new Vec3(tip.x, hub - 9 - (i % 2) * 3 + sway, tip.z);
            thinLine(lines, m, new Vec3(0, hub, 0), tip, 0.1f, 0.8f * w);
            thinLine(lines, m, tip, ends[i].add(0, SPRITE, 0), 0.06f, 0.65f * w);
        }
        draw(lines);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, MOBILE);
        BufferBuilder b = begin(DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int i = 0; i < 4; i++) {
            Vec3 d = ends[i];
            // Billboard toward the camera (at the origin of sky space).
            Vec3 to = d.normalize();
            Vec3 right = to.cross(new Vec3(0, 1, 0)).normalize().scale(SPRITE);
            Vec3 up = new Vec3(0, SPRITE, 0);
            float u0 = i * 0.25f;
            uv(b, m, d.subtract(right).add(up), u0, 0, w);
            uv(b, m, d.subtract(right).subtract(up), u0, 1, w);
            uv(b, m, d.add(right).subtract(up), u0 + 0.25f, 1, w);
            uv(b, m, d.add(right).add(up), u0 + 0.25f, 0, w);
        }
        draw(b);
    }

    private static final float SPRITE = 3f;

    private static void thinLine(BufferBuilder b, Matrix4f m, Vec3 a, Vec3 z, float width, float alpha) {
        Vec3 side = z.subtract(a).cross(new Vec3(0, 0, 1)).normalize().scale(width);
        if (side.lengthSqr() < 1e-6) side = new Vec3(width, 0, 0);
        put(b, m, a.subtract(side), new float[]{0.85f, 0.82f, 0.7f}, alpha);
        put(b, m, a.add(side), new float[]{0.85f, 0.82f, 0.7f}, alpha);
        put(b, m, z.add(side), new float[]{0.85f, 0.82f, 0.7f}, alpha);
        put(b, m, z.subtract(side), new float[]{0.85f, 0.82f, 0.7f}, alpha);
    }

    // --- Blackwood: a thin smiling moon that keeps behind you

    private static void grin(Matrix4f m, float time, float w) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, GRIN);
        BufferBuilder b = begin(DefaultVertexFormat.POSITION_TEX_COLOR);
        texQuad(b, m, dir(grinAz, Math.toRadians(24)), 11, 11, Mth.sin(time * 0.004f) * 0.06f, 1f, 1f, 1f, 0.9f * w);
        draw(b);
    }

    // --- The Maw: a black sun with a corona that beats like a heart

    private static void corona(Matrix4f m, float w) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CORONA);
        BufferBuilder b = begin(DefaultVertexFormat.POSITION_TEX_COLOR);
        float beat = BiomeMechanicsClient.heartbeat;
        float size = 26 + beat * 7;
        texQuad(b, m, sunDir(), size, size, 0, 1f, 0.25f, 0.18f, (0.55f + 0.45f * beat) * w);
        draw(b);
    }

    private static Vec3 sunDir() {
        return dir(Math.toRadians(20), Math.toRadians(30));
    }

    private static void blackSun(Matrix4f m, float time, float w) {
        // Veins first: dark red lines spreading from the sun across the sky.
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder v = begin(DefaultVertexFormat.POSITION_COLOR);
        RandomSource rng = RandomSource.create(0xBEA7L);
        for (int k = 0; k < 9; k++) {
            double az = Math.toRadians(20);
            double el = Math.toRadians(30);
            double heading = rng.nextDouble() * Math.PI * 2;
            Vec3 prev = dir(az, el);
            for (int s = 0; s < 14; s++) {
                heading += (rng.nextDouble() - 0.5) * 0.9;
                az += Math.cos(heading) * 0.06;
                el = Mth.clamp(el + Math.sin(heading) * 0.05, 0.02, 1.4);
                Vec3 next = dir(az, el);
                float a = 0.5f * w * (1 - s / 14f);
                segment(v, m, prev, next, 0.16f * (1 - s / 16f), new float[]{0.32f, 0.02f, 0.04f}, a);
                prev = next;
            }
        }
        draw(v);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, BLACK_SUN);
        BufferBuilder b = begin(DefaultVertexFormat.POSITION_TEX_COLOR);
        texQuad(b, m, sunDir(), 12, 12, 0, 1f, 1f, 1f, w);
        draw(b);
    }

    // --- Signal Lost: the sky is static; now and then it tunes in to a daytime sky for a moment

    private static void staticSky(Matrix4f m, float time, float w) {
        RandomSource rng = RandomSource.create((long) (time * 1.7));
        float tuned = glimpse > 0 ? Mth.sin(glimpse / 26f * Mth.PI) : 0;
        if (tuned > 0.01f) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder day = begin(DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i < 24; i++) {
                double a0 = i * Mth.TWO_PI / 24;
                double a1 = (i + 1) * Mth.TWO_PI / 24;
                float a = tuned * w;
                put(day, m, dir(a0, -0.1).scale(R), new float[]{0.75f, 0.85f, 1f}, a);
                put(day, m, dir(a1, -0.1).scale(R), new float[]{0.75f, 0.85f, 1f}, a);
                put(day, m, dir(a1, 1.5).scale(R), new float[]{0.35f, 0.58f, 0.98f}, a);
                put(day, m, dir(a0, 1.5).scale(R), new float[]{0.35f, 0.58f, 0.98f}, a);
            }
            draw(day);
        }
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, STATIC);
        BufferBuilder b = begin(DefaultVertexFormat.POSITION_TEX_COLOR);
        float ou = rng.nextFloat();
        float ov = rng.nextFloat();
        float roll = (time * 0.004f) % 1f;
        float noiseAlpha = 0.5f * w * (1 - tuned);
        if (noiseAlpha > 0.01f) {
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < 6; j++) {
                    double a0 = i * Mth.TWO_PI / 16;
                    double a1 = (i + 1) * Mth.TWO_PI / 16;
                    double e0 = -0.2 + j * 0.29;
                    double e1 = e0 + 0.29;
                    float band = 1 - 0.6f * (float) Math.exp(-Math.pow(((e0 + 0.2) / 1.74 - roll) * 6, 2));
                    float a = noiseAlpha * band;
                    uv(b, m, dir(a0, e0).scale(R), ou + i * 0.5f, ov + j * 0.5f, a);
                    uv(b, m, dir(a1, e0).scale(R), ou + i * 0.5f + 0.5f, ov + j * 0.5f, a);
                    uv(b, m, dir(a1, e1).scale(R), ou + i * 0.5f + 0.5f, ov + j * 0.5f + 0.5f, a);
                    uv(b, m, dir(a0, e1).scale(R), ou + i * 0.5f, ov + j * 0.5f + 0.5f, a);
                }
            }
        }
        draw(b);
        RenderSystem.setShaderTexture(0, TEST_CARD);
        BufferBuilder card = begin(DefaultVertexFormat.POSITION_TEX_COLOR);
        texQuad(card, m, dir(Math.toRadians(200), Math.toRadians(28)), 11, 8.5f, 0, 1f, 1f, 1f, 0.9f * w * (1 - tuned));
        draw(card);
    }

    // ------------------------------------------------------------------ helpers

    private static BufferBuilder begin(VertexFormat format) {
        return Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, format);
    }

    private static void draw(BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    static Vec3 dir(double az, double el) {
        double c = Math.cos(el);
        return new Vec3(Math.sin(az) * c, Math.sin(el), -Math.cos(az) * c);
    }

    private static Vec3[] basis(Vec3 d, float roll) {
        Vec3 ref = Math.abs(d.y) > 0.98 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = d.cross(ref).normalize();
        Vec3 up = right.cross(d).normalize();
        float c = Mth.cos(roll);
        float s = Mth.sin(roll);
        return new Vec3[]{right.scale(c).add(up.scale(s)), up.scale(c).subtract(right.scale(s))};
    }

    private static void put(BufferBuilder b, Matrix4f m, Vec3 p, float[] c, float a) {
        b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(c[0], c[1], c[2], a);
    }

    private static void uv(BufferBuilder b, Matrix4f m, Vec3 p, float u, float v, float a) {
        b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(1f, 1f, 1f, a);
    }

    private static void segment(BufferBuilder b, Matrix4f m, Vec3 from, Vec3 to, float width, float[] c, float a) {
        Vec3 p = from.scale(R);
        Vec3 q = to.scale(R);
        Vec3 side = q.subtract(p).cross(p).normalize().scale(width * 4);
        put(b, m, p.subtract(side), c, a);
        put(b, m, p.add(side), c, a);
        put(b, m, q.add(side), c, a);
        put(b, m, q.subtract(side), c, a);
    }

    private static void texQuad(BufferBuilder b, Matrix4f m, Vec3 d, float w, float h, float roll, float r, float g, float bl, float a) {
        Vec3[] ru = basis(d, roll);
        Vec3 c = d.scale(R);
        Vec3 right = ru[0].scale(w);
        Vec3 up = ru[1].scale(h);
        vtx(b, m, c.subtract(right).add(up), 0, 0, r, g, bl, a);
        vtx(b, m, c.subtract(right).subtract(up), 0, 1, r, g, bl, a);
        vtx(b, m, c.add(right).subtract(up), 1, 1, r, g, bl, a);
        vtx(b, m, c.add(right).add(up), 1, 0, r, g, bl, a);
    }

    private static void vtx(BufferBuilder b, Matrix4f m, Vec3 p, float u, float v, float r, float g, float bl, float a) {
        b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(r, g, bl, a);
    }

    private static float[] hue(float h) {
        float r = Mth.clamp(Math.abs(h * 6 - 3) - 1, 0, 1);
        float g = Mth.clamp(2 - Math.abs(h * 6 - 2), 0, 1);
        float b = Mth.clamp(2 - Math.abs(h * 6 - 4), 0, 1);
        // Pull toward violet so the "rainbow" stays in the geode palette.
        return new float[]{Mth.lerp(0.45f, r, 0.75f), Mth.lerp(0.45f, g, 0.5f), Mth.lerp(0.45f, b, 1f)};
    }

    static void blendAdditive() {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
    }
}
