package com.seba.reverie.client;

import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.network.DreamStatePayload;
import com.seba.reverie.network.DreamTransitionPayload;
import com.seba.reverie.network.ReverieNetwork;
import com.seba.reverie.network.ShadeEventPayload;
import com.seba.reverie.registry.ModEffects;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client state of the dream (from the S2C payloads) and the screen overlays: the fade when falling asleep or
 * waking, the slow desaturating vignette of the last 15 seconds, and the heavy-eyelids overlay of Sleepyness.
 */
public final class DreamClient {
    private static final ResourceLocation VIGNETTE = Reverie.id("textures/misc/dream_vignette.png");

    public static boolean dreaming;
    public static int ticksLeft;
    public static int totalTicks = 1;

    private static int fadeHold;
    private static int fadeTicks;
    private static int fadeLength = 1;
    private static int eyelidTimer;
    // Heavy blinks: -1 = eyes open; otherwise ticks into the current blink.
    private static int blinkTick = -1;
    private static int blinkLength = 70;
    private static float blinkDepth = 1f;
    private static int nextBlink = 120;
    private static float lid;
    private static float lidPrev;
    private static float dark;
    private static final java.util.Random RNG = new java.util.Random();
    private static int flashTicks;
    private static int awakeTicks;

    private DreamClient() {
    }

    static void init(IEventBus modBus) {
        ReverieNetwork.client = new ReverieNetwork.ClientHandlers() {
            @Override
            public void transition(DreamTransitionPayload payload) {
                onTransition(payload);
            }

            @Override
            public void state(DreamStatePayload payload) {
                dreaming = payload.active();
                ticksLeft = payload.ticksLeft();
                totalTicks = Math.max(1, payload.totalTicks());
            }

            @Override
            public void shadeEvent(ShadeEventPayload payload) {
                DreamEffectsClient.onEvent(payload);
            }
        };
        modBus.addListener(DreamClient::layers);
        NeoForge.EVENT_BUS.addListener(DreamClient::tick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> reset());
    }

    private static void reset() {
        dreaming = false;
        ticksLeft = 0;
        fadeTicks = 0;
        fadeHold = 0;
    }

    private static void onTransition(DreamTransitionPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.kind() == DreamTransitionPayload.FLASH) {
            flashTicks = 10;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6f, 0.4f));
            return;
        }
        if (payload.kind() == DreamTransitionPayload.ENTER) {
            fadeHold = 20;
            fadeLength = fadeTicks = 50;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PORTAL_TRAVEL, 0.45f, 0.12f));
        } else {
            fadeHold = 12;
            fadeLength = fadeTicks = 36;
            dreaming = false;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 0.5f));
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        if (flashTicks > 0) flashTicks--;
        if (fadeHold > 0) fadeHold--;
        else if (fadeTicks > 0) fadeTicks--;
        if (dreaming && ticksLeft > 0) ticksLeft--;
        lidPrev = lid;
        if (mc.player != null && !dreaming && ModEffects.sleepy(mc.player) && !mc.player.isSleeping()) {
            awakeTicks++;
            sleepyTick();
            if (awakeTicks % 600 == 300 && mc.level != null) {
                // An occasional yawn.
                mc.level.playLocalSound(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), SoundEvents.FOX_SLEEP,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.15f, 1.35f, false);
            }
        } else {
            awakeTicks = 0;
            eyelidTimer = 0;
            blinkTick = -1;
            lid = Math.max(0, lid - 0.1f);
            dark = Math.max(0, dark - 0.05f);
        }
        var fx = com.seba.reverie.client.shader.ScreenEffects.SLEEPY;
        boolean on = ReverieConfig.get(ReverieConfig.EYELID_OVERLAY);
        fx.dark = on ? dark : 0;
        com.seba.reverie.client.shade.SleepyPhantoms.tick(on && mc.player != null && !dreaming && ModEffects.sleepy(mc.player));
    }

    /** Heavy, irregular blinks; sometimes the eyes stay shut a moment (and then the Shades may be there, see SleepyPhantoms). */
    private static void sleepyTick() {
        dark += (0.42f - dark) * 0.03f;
        if (blinkTick < 0) {
            if (--nextBlink <= 0) {
                blinkTick = 0;
                boolean microSleep = RNG.nextInt(4) == 0;
                blinkLength = microSleep ? 110 : 55 + RNG.nextInt(30);
                blinkDepth = microSleep ? 1f : 0.65f + RNG.nextFloat() * 0.3f;
                nextBlink = 120 + RNG.nextInt(220);
            }
            lid = Math.max(0, lid - 0.05f);
            return;
        }
        float t = ++blinkTick / (float) blinkLength;
        // close slowly (ease in), hold, open a little faster
        float shape = t < 0.45f ? smooth(t / 0.45f) : t < 0.65f ? 1f : 1f - smooth((t - 0.65f) / 0.35f);
        float before = lid;
        lid = shape * blinkDepth;
        // Eyes just shut in a heavy blink: when they open, something may be standing there.
        if (before < 0.8f && lid >= 0.8f) com.seba.reverie.client.shade.SleepyPhantoms.eyesClosed(blinkLength > 100);
        dark = Math.max(dark, 0.42f + 0.3f * lid);
        if (blinkTick >= blinkLength) blinkTick = -1;
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Eyelid position 0..1 for this frame (for the shader and the fallback). */
    static float lid(float partial) {
        return Mth.lerp(partial, lidPrev, lid);
    }

    private static void layers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Reverie.id("dream_overlay"), DreamClient::render);
    }

    /** 0..1: how close the dream is to its end (the last 15 seconds). */
    public static float endStrength(float partial) {
        if (!dreaming) return 0;
        float left = ticksLeft - partial;
        return Mth.clamp(1f - left / 300f, 0f, 1f);
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth();
        int h = g.guiHeight();

        float end = endStrength(pt);
        if (end > 0) {
            // Desaturate (grey wash) and close in (vignette).
            int grey = (int) (end * 0.45f * 255) << 24 | 0x8A8A96;
            g.fill(0, 0, w, h, grey);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, Math.min(1f, end * 1.1f));
            g.blit(VIGNETTE, 0, 0, 0, 0, w, h, w, h);
            g.setColor(1f, 1f, 1f, 1f);
        }

        float lidNow = ReverieConfig.get(ReverieConfig.EYELID_OVERLAY) ? lid(pt) : 0f;
        com.seba.reverie.client.shader.ScreenEffects.SLEEPY.lid = lidNow;
        if (lidNow > 0.005f && !com.seba.reverie.client.shader.ScreenEffects.sleepyAvailable()) {
            // Plain fallback: two gradient lids.
            int lidPx = (int) (h * lidNow * 0.5f);
            g.fillGradient(0, 0, w, lidPx, 0xF0100A14, 0xC0100A14);
            g.fillGradient(0, h - lidPx, w, h, 0xC0100A14, 0xF0100A14);
        }

        if (flashTicks > 0) {
            float a = Mth.clamp((flashTicks - pt) / 10f, 0f, 1f);
            g.fill(0, 0, w, h, ((int) (a * 255) << 24) | 0xFFFFFF);
        }
        if (fadeHold > 0 || fadeTicks > 0) {
            float a = fadeHold > 0 ? 1f : Mth.clamp((fadeTicks - pt) / fadeLength, 0f, 1f);
            g.fill(0, 0, w, h, ((int) (a * 255) << 24) | 0x05030A);
        }
    }
}
