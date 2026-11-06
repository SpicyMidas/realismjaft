package com.seba.reverie.client.shader;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.seba.reverie.Reverie;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Full-screen post effects for Dizziness and Sleepyness (assets/reverie/shaders/post/). They run as the lowest GUI
 * layer, so the world and the hand are affected but the HUD stays readable. Each chain is built on first use,
 * resized with the window, rebuilt after a resource reload, and switched off for good (logged once) if it fails;
 * callers then draw their plain overlays instead. Never used while a shader pack is active.
 */
public final class ScreenEffects {
    /** Values for one frame, written by the effect code before the GUI renders. */
    public static final class Dizzy {
        public float strength;
        public float bend;
    }

    public static final class Sleepy {
        public float lid;
        public float dark;
    }

    public static final Dizzy DIZZY = new Dizzy();
    public static final Sleepy SLEEPY = new Sleepy();

    private static final Chain DIZZY_CHAIN = new Chain(Reverie.id("shaders/post/dizzy.json"));
    private static final Chain SLEEPY_CHAIN = new Chain(Reverie.id("shaders/post/sleepy.json"));

    private ScreenEffects() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerBelowAll(Reverie.id("screen_effects"), ScreenEffects::render));
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> {
                    DIZZY_CHAIN.close();
                    SLEEPY_CHAIN.close();
                }));
    }

    /** True if the shader version of the Dizziness effect will draw this frame (otherwise use the fallback). */
    public static boolean dizzyAvailable() {
        return DIZZY_CHAIN.usable();
    }

    public static boolean sleepyAvailable() {
        return SLEEPY_CHAIN.usable();
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float clock = (mc.player.tickCount + delta.getGameTimeDeltaPartialTick(false)) / 20f;
        g.flush();
        if (DIZZY.strength > 0.01f) {
            DIZZY_CHAIN.run(clock, chain -> {
                chain.setUniform("Strength", DIZZY.strength);
                chain.setUniform("Bend", DIZZY.bend);
            });
        }
        if (SLEEPY.lid > 0.005f || SLEEPY.dark > 0.01f) {
            SLEEPY_CHAIN.run(clock, chain -> {
                chain.setUniform("Lid", SLEEPY.lid);
                chain.setUniform("Dark", SLEEPY.dark);
            });
        }
    }

    private static final class Chain {
        private final ResourceLocation location;
        @Nullable
        private PostChain chain;
        private boolean failed;
        private int width = -1;
        private int height = -1;

        Chain(ResourceLocation location) {
            this.location = location;
        }

        boolean usable() {
            return !failed && !ReverieShaders.shaderPackActive();
        }

        void close() {
            if (chain != null) chain.close();
            chain = null;
            width = height = -1;
            failed = false;
        }

        void run(float clock, java.util.function.Consumer<PostChain> uniforms) {
            if (!usable()) return;
            Minecraft mc = Minecraft.getInstance();
            try {
                var target = mc.getMainRenderTarget();
                if (chain == null) {
                    chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), target, location);
                }
                if (target.width != width || target.height != height) {
                    width = target.width;
                    height = target.height;
                    chain.resize(width, height);
                }
                chain.setUniform("Clock", clock);
                uniforms.accept(chain);
                RenderSystem.disableBlend();
                RenderSystem.disableDepthTest();
                RenderSystem.resetTextureMatrix();
                chain.process(mc.getTimer().getGameTimeDeltaTicks());
                target.bindWrite(true);
                RenderSystem.enableDepthTest();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            } catch (Exception | LinkageError e) {
                failed = true;
                if (chain != null) chain.close();
                chain = null;
                Reverie.LOGGER.error("Reverie screen effect {} failed; using the plain overlay", location, e);
                mc.getMainRenderTarget().bindWrite(true);
            }
        }
    }
}
