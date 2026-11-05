package com.seba.reverie.client.dreamland;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.client.DreamEffectsClient;
import com.seba.reverie.shade.ShadeEntity;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Dreamland sky: a twilight gradient, stars that drift into constellations that are never the same twice, three
 * moons of the wrong sizes (the big one trails you as you walk), a slow ringed planet, the giant Eye on the
 * horizon (opens during the Farewell, stays shut while Shades are close) and Starfall streaks. Everything is a few
 * hundred quads. Any failure disables it for the session and the vanilla sky takes over.
 */
public final class DreamSky {
    private static final ResourceLocation MOON = Reverie.id("textures/environment/dream_moon.png");
    private static final ResourceLocation PLANET = Reverie.id("textures/environment/dream_planet.png");
    private static final ResourceLocation EYE = Reverie.id("textures/environment/dream_eye.png");

    private static final int STARS = 640;
    private static final int CONSTELLATIONS = 7;
    private static final int CYCLE = 1800;
    private static final float R = 100f;

    private static final float[][] BASE = new float[STARS][2];  // azimuth, elevation (radians)
    private static final float[][] TINT = new float[STARS][4];  // r, g, b, twinkle phase
    private static long cachedCycle = Long.MIN_VALUE;
    private static float[][] targetsPrev;
    private static float[][] targetsNow;
    private static int[][] linesNow;

    private static boolean failed;
    /** Size of the big sky objects; grows in Cloudreach, where the sky is closest. */
    static float closeness = 1f;

    // Moon Follows You: the big moon's azimuth is dragged toward behind your direction of travel.
    private static double moonAz = Math.toRadians(200);
    private static double moonVel;
    private static Vec3 lastPos;

    /** Shooting stars: start az/el, end az/el, age, life. */
    private static final List<float[]> STREAKS = new ArrayList<>();

    static {
        RandomSource rng = RandomSource.create(0x5EE5L);
        for (int i = 0; i < STARS; i++) {
            BASE[i][0] = rng.nextFloat() * Mth.TWO_PI;
            BASE[i][1] = (float) Math.asin(0.05 + rng.nextFloat() * 0.95);
            float warm = rng.nextFloat();
            TINT[i][0] = 0.85f + warm * 0.15f;
            TINT[i][1] = 0.85f + rng.nextFloat() * 0.1f;
            TINT[i][2] = 1.0f - warm * 0.12f;
            TINT[i][3] = rng.nextFloat() * Mth.TWO_PI;
        }
    }

    private DreamSky() {
    }

    /** Called by the dimension effects. Returns false to let vanilla draw the sky instead. */
    static boolean render(ClientLevel level, float partial, Matrix4f view, Camera camera, Runnable setupFog) {
        if (failed || !ReverieConfig.get(ReverieConfig.CUSTOM_SKY)) return false;
        try {
            draw(level, partial, view, camera);
            return true;
        } catch (Throwable t) {
            failed = true;
            Reverie.LOGGER.error("Dreamland sky renderer failed; using the plain sky for this session", t);
            return false;
        } finally {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            setupFog.run();
        }
    }

    /** Client tick: moon drag and streak ageing. */
    static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.isPaused()) return;
        Vec3 pos = mc.player.position();
        if (lastPos != null && pos.distanceToSqr(lastPos) < 64) {
            Vec3 move = pos.subtract(lastPos);
            double speed = Math.hypot(move.x, move.z);
            if (speed > 0.01) {
                // Behind you, in the direction you came from.
                double behind = Math.atan2(move.x, -move.z) + Math.PI; // sky azimuth: 0 = north, clockwise
                double diff = Mth.wrapDegrees(Math.toDegrees(behind - moonAz));
                moonVel += Math.toRadians(diff) * speed * 0.002;
            }
        }
        lastPos = pos;
        moonVel *= 0.95; // settles slowly when you stop
        moonAz += moonVel;
        for (int i = STREAKS.size() - 1; i >= 0; i--) {
            float[] s = STREAKS.get(i);
            if (++s[4] > s[5]) STREAKS.remove(i);
        }
    }

    static void reset() {
        STREAKS.clear();
        lastPos = null;
    }

    /** A shooting star across a random part of the sky. */
    /** A shooting star; {@code yaw} (Minecraft degrees, or NaN for anywhere) aims it into the view. */
    public static void shootingStar(RandomSource rng, float yaw) {
        float az = Float.isNaN(yaw) ? rng.nextFloat() * Mth.TWO_PI
                : (yaw + 180f + (rng.nextFloat() - 0.5f) * 70f) * Mth.DEG_TO_RAD; // sky azimuth = yaw + 180
        float el = 0.35f + rng.nextFloat() * 0.6f;
        float len = 0.25f + rng.nextFloat() * 0.35f;
        float dir = rng.nextFloat() * Mth.TWO_PI;
        STREAKS.add(new float[]{az, el, az + Mth.cos(dir) * len, Math.max(0.05f, el - Math.abs(Mth.sin(dir)) * len), 0, 14 + rng.nextInt(10)});
    }

    // ------------------------------------------------------------------ drawing

    private static void draw(ClientLevel level, float partial, Matrix4f view, Camera camera) {
        PoseStack ps = new PoseStack();
        ps.mulPose(view);
        Matrix4f m = ps.last().pose();
        Vec3 sky = level.getSkyColor(camera.getPosition(), partial);
        float[] fog = RenderSystem.getShaderFogColor();
        float time = level.getGameTime() + partial;

        FogRenderer.setupNoFog();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        gradient(m, sky, fog);

        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        stars(m, level.getGameTime(), time);
        streaks(m, partial);

        RenderSystem.defaultBlendFunc();
        planet(m, time);
        moons(m, time, partial);
        eye(m, level, camera);
    }

    private static Vec3 dir(double az, double el) {
        double c = Math.cos(el);
        return new Vec3(Math.sin(az) * c, Math.sin(el), -Math.cos(az) * c);
    }

    private static void gradient(Matrix4f m, Vec3 sky, float[] fog) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int lon = 24;
        float[] els = {-90, -20, -4, 2, 10, 24, 45, 70, 90};
        for (int j = 0; j < els.length - 1; j++) {
            float e0 = (float) Math.toRadians(els[j]);
            float e1 = (float) Math.toRadians(els[j + 1]);
            float[] c0 = skyAt(els[j], sky, fog);
            float[] c1 = skyAt(els[j + 1], sky, fog);
            for (int i = 0; i < lon; i++) {
                double a0 = i * Mth.TWO_PI / lon;
                double a1 = (i + 1) * Mth.TWO_PI / lon;
                vertex(b, m, dir(a0, e0), c0);
                vertex(b, m, dir(a1, e0), c0);
                vertex(b, m, dir(a1, e1), c1);
                vertex(b, m, dir(a0, e1), c1);
            }
        }
        draw(b);
    }

    private static float[] skyAt(float elevationDeg, Vec3 sky, float[] fog) {
        float t = Mth.clamp((elevationDeg + 4) / 50f, 0, 1);
        t = t * t * (3 - 2 * t);
        float below = Mth.clamp(-elevationDeg / 40f, 0, 1) * 0.35f;
        float r = Mth.lerp(t, fog[0], (float) sky.x) * (1 - below);
        float g = Mth.lerp(t, fog[1], (float) sky.y) * (1 - below);
        float bl = Mth.lerp(t, fog[2], (float) sky.z) * (1 - below * 0.6f);
        return new float[]{r, g, bl, 1};
    }

    private static void vertex(BufferBuilder b, Matrix4f m, Vec3 d, float[] c) {
        b.addVertex(m, (float) d.x * R, (float) d.y * R, (float) d.z * R).setColor(c[0], c[1], c[2], c[3]);
    }

    private static void draw(BufferBuilder b) {
        MeshData mesh = b.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    // --- stars and constellations

    private static void ensureCycle(long cycle) {
        if (cycle == cachedCycle) return;
        targetsPrev = cycle - 1 == cachedCycle ? targetsNow : targets(cycle - 1, null);
        linesNow = new int[CONSTELLATIONS * 8][];
        targetsNow = targets(cycle, linesNow);
        cachedCycle = cycle;
    }

    /** Per-star target (az, el) for one cycle, or null when the star stays at its own place. */
    private static float[][] targets(long cycle, int[][] lines) {
        float[][] t = new float[STARS][];
        RandomSource rng = RandomSource.create(cycle * 0x9E3779B97F4A7C15L + 77);
        int line = 0;
        for (int c = 0; c < CONSTELLATIONS; c++) {
            float az = rng.nextFloat() * Mth.TWO_PI;
            float el = 0.3f + rng.nextFloat() * 0.9f;
            int n = 5 + rng.nextInt(4);
            int prev = -1;
            float heading = rng.nextFloat() * Mth.TWO_PI;
            for (int k = 0; k < n; k++) {
                int star = rng.nextInt(STARS);
                if (t[star] != null) continue;
                t[star] = new float[]{az, Mth.clamp(el, 0.12f, 1.45f)};
                if (prev >= 0 && lines != null && line < lines.length) lines[line++] = new int[]{prev, star};
                prev = star;
                heading += (rng.nextFloat() - 0.5f) * 2.2f;
                float step = 0.06f + rng.nextFloat() * 0.06f;
                az += Mth.cos(heading) * step / Math.max(0.3f, Mth.cos(el));
                el += Mth.sin(heading) * step;
            }
        }
        return t;
    }

    private static float[] starPos(int i, float[][] targets) {
        return targets != null && targets[i] != null ? targets[i] : BASE[i];
    }

    private static void stars(Matrix4f m, long gameTime, float time) {
        long cycle = Math.floorDiv(gameTime, CYCLE);
        ensureCycle(cycle);
        float phase = (float) (Math.floorMod(gameTime, CYCLE) + (time - gameTime)) / CYCLE;
        float move = Mth.clamp(phase / 0.35f, 0, 1);
        move = move * move * (3 - 2 * move);
        float[][] current = new float[STARS][];
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < STARS; i++) {
            float[] a = starPos(i, targetsPrev);
            float[] c = starPos(i, targetsNow);
            float az = a[0] + Mth.wrapDegrees((float) Math.toDegrees(c[0] - a[0])) * Mth.DEG_TO_RAD * move;
            float el = Mth.lerp(move, a[1], c[1]);
            current[i] = new float[]{az, el};
            boolean lit = targetsNow[i] != null;
            float twinkle = 0.65f + 0.35f * Mth.sin(time * 0.05f + TINT[i][3]);
            float size = (lit ? 0.42f : 0.22f + (i % 5) * 0.03f);
            float a1 = (lit ? 1f : 0.75f) * twinkle;
            billboard(b, m, dir(az, el), size, 0, new float[]{TINT[i][0], TINT[i][1], TINT[i][2], a1});
        }
        // Faint lines once a constellation has formed; they fade before the next rearrangement.
        float lineAlpha = Mth.clamp((phase - 0.35f) / 0.1f, 0, 1) * Mth.clamp((0.9f - phase) / 0.1f, 0, 1) * 0.28f;
        if (lineAlpha > 0.01f) {
            for (int[] l : linesNow) {
                if (l == null) continue;
                segment(b, m, dir(current[l[0]][0], current[l[0]][1]), dir(current[l[1]][0], current[l[1]][1]), 0.05f,
                        new float[]{0.8f, 0.82f, 1f, lineAlpha});
            }
        }
        draw(b);
    }

    private static void streaks(Matrix4f m, float partial) {
        if (STREAKS.isEmpty()) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] s : STREAKS) {
            float t = (s[4] + partial) / s[5];
            float head = Mth.clamp(t * 1.3f, 0, 1);
            float tail = Mth.clamp(t * 1.3f - 0.35f, 0, 1);
            Vec3 h = dir(Mth.lerp(head, s[0], s[2]), Mth.lerp(head, s[1], s[3]));
            Vec3 tl = dir(Mth.lerp(tail, s[0], s[2]), Mth.lerp(tail, s[1], s[3]));
            float fade = 1 - Math.max(0, t - 0.7f) / 0.3f;
            segment(b, m, tl, h, 0.22f, new float[]{1f, 0.97f, 0.88f, 0.9f * fade});
        }
        draw(b);
    }

    // --- planet, moons, eye

    private static void planet(Matrix4f m, float time) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, PLANET);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        texturedQuad(b, m, dir(Math.toRadians(128), Math.toRadians(34)), 34 * closeness, 17 * closeness, time * 0.00035f + 0.35f, 0, 0, 1, 1, 0.92f);
        draw(b);
    }

    private static void moons(Matrix4f m, float time, float partial) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, MOON);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // The big one (full, follows you), a small crescent and a half moon that is lit from the wrong side.
        texturedQuad(b, m, dir(moonAz + moonVel * partial, Math.toRadians(27)), 15 * closeness, 15 * closeness, 0, 0, 0, 0.25f, 1, 1f);
        texturedQuad(b, m, dir(Math.toRadians(300), Math.toRadians(52)), 3.2f, 3.2f, 0.4f, 0.75f, 0, 1f, 1, 0.95f);
        texturedQuad(b, m, dir(Math.toRadians(40), Math.toRadians(16)), 6, 6, (float) Math.PI, 0.5f, 0, 0.75f, 1, 0.9f);
        draw(b);
    }

    private static void eye(Matrix4f m, ClientLevel level, Camera camera) {
        // 0 closed .. 3 open. Opens over the Farewell; stays shut while any Shade is close.
        int frame = 0;
        if (DreamEffectsClient.farewellTicks > 0) {
            float p = 1f - DreamEffectsClient.farewellTicks / (float) DreamEffectsClient.farewellTotal;
            frame = p < 0.2f ? 0 : p < 0.45f ? 1 : p < 0.7f ? 2 : 3;
        }
        if (frame > 0 && !level.getEntitiesOfClass(ShadeEntity.class, new AABB(camera.getBlockPosition()).inflate(18)).isEmpty()) frame = 0;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, EYE);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        texturedQuad(b, m, dir(0, Math.toRadians(5.5)), 30, 15, 0, 0, frame * 0.25f, 1, (frame + 1) * 0.25f, 0.8f);
        draw(b);
    }

    // --- geometry helpers

    /** Right and up vectors of a quad facing the viewer at sky direction d, rolled by {@code roll}. */
    private static Vec3[] basis(Vec3 d, float roll) {
        Vec3 ref = Math.abs(d.y) > 0.98 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = d.cross(ref).normalize();
        Vec3 up = right.cross(d).normalize();
        float c = Mth.cos(roll);
        float s = Mth.sin(roll);
        return new Vec3[]{right.scale(c).add(up.scale(s)), up.scale(c).subtract(right.scale(s))};
    }

    private static void billboard(BufferBuilder b, Matrix4f m, Vec3 d, float size, float roll, float[] c) {
        Vec3[] ru = basis(d, roll);
        Vec3 center = d.scale(R);
        Vec3 r = ru[0].scale(size);
        Vec3 u = ru[1].scale(size);
        put(b, m, center.subtract(r).subtract(u), c);
        put(b, m, center.add(r).subtract(u), c);
        put(b, m, center.add(r).add(u), c);
        put(b, m, center.subtract(r).add(u), c);
    }

    private static void segment(BufferBuilder b, Matrix4f m, Vec3 from, Vec3 to, float width, float[] c) {
        Vec3 a = from.scale(R);
        Vec3 z = to.scale(R);
        Vec3 side = z.subtract(a).cross(a).normalize().scale(width);
        put(b, m, a.subtract(side), c);
        put(b, m, a.add(side), c);
        put(b, m, z.add(side), c);
        put(b, m, z.subtract(side), c);
    }

    private static void put(BufferBuilder b, Matrix4f m, Vec3 p, float[] c) {
        b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(c[0], c[1], c[2], c[3]);
    }

    private static void texturedQuad(BufferBuilder b, Matrix4f m, Vec3 d, float w, float h, float roll,
                                     float u0, float v0, float u1, float v1, float alpha) {
        Vec3[] ru = basis(d, roll);
        Vec3 center = d.scale(R);
        Vec3 r = ru[0].scale(w);
        Vec3 u = ru[1].scale(h);
        uv(b, m, center.subtract(r).add(u), u0, v0, alpha);
        uv(b, m, center.subtract(r).subtract(u), u0, v1, alpha);
        uv(b, m, center.add(r).subtract(u), u1, v1, alpha);
        uv(b, m, center.add(r).add(u), u1, v0, alpha);
    }

    private static void uv(BufferBuilder b, Matrix4f m, Vec3 p, float u, float v, float alpha) {
        b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setUv(u, v).setColor(1f, 1f, 1f, alpha);
    }
}
