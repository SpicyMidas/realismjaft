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
    }

    private static void models(ModelEvent.RegisterAdditional event) {
        event.register(DecorRenderer.CLOCK_HANDS);
        event.register(DecorRenderer.PUPIL);
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
    }
}
