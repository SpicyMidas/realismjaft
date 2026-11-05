package com.seba.reverie.client.dreamland;

import com.seba.reverie.Reverie;
import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.registry.ModBlockEntities;
import com.seba.reverie.registry.ModBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client registration for Dreamland: colours, renderers, dimension effects and fog. */
public final class DreamlandClient {
    private DreamlandClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(DreamlandClient::colorResolvers);
        modBus.addListener(DreamlandClient::blockColors);
        modBus.addListener(DreamlandClient::itemColors);
        modBus.addListener(DreamlandClient::renderers);
        modBus.addListener(DreamlandClient::models);
        modBus.addListener(DreamlandClient::effects);
        NeoForge.EVENT_BUS.addListener(DreamlandClient::fog);
        modBus.addListener(DreamlandClient::particles);
        NeoForge.EVENT_BUS.addListener(DreamlandClient::fogColor);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> {
            var mc = Minecraft.getInstance();
            if (mc.level == null || !mc.level.dimension().equals(DreamManager.DREAMLAND)) return;
            DreamSky.tick();
            DreamAmbienceClient.tick(mc);
            LullabyPlayer.tick(mc);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> {
            DreamSky.reset();
            LullabyPlayer.reset();
        });
    }

    private static void colorResolvers(RegisterColorHandlersEvent.ColorResolvers event) {
        event.register(DreamColors.GRASS);
        event.register(DreamColors.LEAVES);
    }

    private static void blockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> level == null || pos == null ? 0xD3B6F2 : level.getBlockTint(pos, DreamColors.GRASS),
                ModBlocks.DREAM_GRASS_BLOCK.get());
        event.register((state, level, pos, tint) -> level == null || pos == null ? 0xE6A6CF : level.getBlockTint(pos, DreamColors.LEAVES),
                ModBlocks.DREAMWOOD_LEAVES.get());
    }

    private static void itemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tint) -> tint == 0 ? 0xD3B6F2 : -1, ModBlocks.DREAM_GRASS_BLOCK.get());
        event.register((stack, tint) -> 0xE6A6CF, ModBlocks.DREAMWOOD_LEAVES.get());
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.DECOR.get(), DecorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.IMPOSSIBLE_STAIRS.get(), ImpossibleStairsRenderer::new);
        event.registerEntityRenderer(com.seba.reverie.registry.ModEntities.SLEEPWALKING_SHEEP.get(), net.minecraft.client.renderer.entity.SheepRenderer::new);
        event.registerEntityRenderer(com.seba.reverie.registry.ModEntities.LUCID_MOTH.get(), com.seba.reverie.client.fauna.LucidMothRenderer::new);
    }

    private static void models(ModelEvent.RegisterAdditional event) {
        event.register(DecorRenderer.CLOCK_HANDS);
        event.register(DecorRenderer.PUPIL);
    }

    private static void particles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(com.seba.reverie.registry.ModParticles.DREAM_PETAL.get(), s -> DriftParticle.provider(s, DriftParticle.Kind.PETAL));
        event.registerSpriteSet(com.seba.reverie.registry.ModParticles.DREAM_PAGE.get(), s -> DriftParticle.provider(s, DriftParticle.Kind.PAGE));
        event.registerSpriteSet(com.seba.reverie.registry.ModParticles.DREAM_RAINDROP.get(), s -> DriftParticle.provider(s, DriftParticle.Kind.RAINDROP));
        event.registerSpriteSet(com.seba.reverie.registry.ModParticles.DREAM_Z.get(), s -> DriftParticle.provider(s, DriftParticle.Kind.Z));
    }

    /** Fog Tide: the fog turns a pale glowing lavender. */
    private static void fogColor(ViewportEvent.ComputeFogColor event) {
        float tide = DreamAmbienceClient.fogTide();
        if (tide <= 0) return;
        event.setRed(net.minecraft.util.Mth.lerp(tide, event.getRed(), 0.72f));
        event.setGreen(net.minecraft.util.Mth.lerp(tide, event.getGreen(), 0.66f));
        event.setBlue(net.minecraft.util.Mth.lerp(tide, event.getBlue(), 0.92f));
    }

    private static void effects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Reverie.id("dreamland"), new DreamlandEffects());
    }

    /** Thick blue-violet fog in the Void Between, gentler over the islands. */
    private static void fog(ViewportEvent.RenderFog event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimension().equals(DreamManager.DREAMLAND)) return;
        if (event.getType() != net.minecraft.world.level.material.FogType.NONE) return;
        var biome = mc.level.getBiome(event.getCamera().getBlockPosition());
        boolean voidBetween = biome.is(com.seba.reverie.dreamland.DreamlandBiomeKeys.VOID_BETWEEN);
        float far = event.getFarPlaneDistance() * (voidBetween ? 0.8f : 1.0f);
        far = net.minecraft.util.Mth.lerp(DreamAmbienceClient.fogTide(), far, Math.min(far, 26f));
        event.setFarPlaneDistance(far);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.15f));
        event.setCanceled(true);
    }

    /** No clouds, a twilight fog tint. The sky objects are added on top by the sky renderer. */
    static final class DreamlandEffects extends DimensionSpecialEffects {
        DreamlandEffects() {
            super(Float.NaN, false, SkyType.NORMAL, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
            return fogColor.scale(0.55 + brightness * 0.45);
        }

        /** No sunset band: the twilight is the same in every direction. */
        @Override
        public float[] getSunriseColor(float timeOfDay, float partialTicks) {
            return null;
        }

        @Override
        public boolean isFoggyAt(int x, int y) {
            return false;
        }

        @Override
        public boolean renderSky(net.minecraft.client.multiplayer.ClientLevel level, int ticks, float partialTick, org.joml.Matrix4f modelViewMatrix,
                                 net.minecraft.client.Camera camera, org.joml.Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
            return DreamSky.render(level, partialTick, modelViewMatrix, camera, setupFog);
        }
    }
}
