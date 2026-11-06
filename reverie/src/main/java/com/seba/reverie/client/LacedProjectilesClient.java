package com.seba.reverie.client;

import com.seba.reverie.content.melatonia.LacedArrow;
import com.seba.reverie.content.melatonia.Lacing;
import com.seba.reverie.registry.ModEntities;
import com.seba.reverie.registry.ModItems;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Laced snowballs and arrows: vanilla's own snowball and tipped-arrow textures, tinted Melatonia (lilac for Extract,
 * deep purple for Concentrated) through item colours, so the icons are vanilla's with a different palette.
 */
public final class LacedProjectilesClient {
    private static final ResourceLocation TIPPED = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/tipped_arrow.png");

    private LacedProjectilesClient() {
    }

    static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerEntityRenderer(ModEntities.LACED_SNOWBALL.get(), ThrownItemRenderer::new);
            e.registerEntityRenderer(ModEntities.LACED_ARROW.get(), Renderer::new);
        });
        modBus.addListener((RegisterColorHandlersEvent.Item e) -> {
            e.register((stack, layer) -> layer == 0 ? snowTint(stack) : -1, ModItems.LACED_SNOWBALL.get());
            e.register((stack, layer) -> layer == 0 ? headTint(stack) : -1, ModItems.LACED_ARROW.get());
        });
    }

    private static int snowTint(ItemStack stack) {
        return Lacing.level(stack) >= 2 ? FastColor.ARGB32.color(255, 168, 120, 226) : FastColor.ARGB32.color(255, 214, 190, 248);
    }

    private static int headTint(ItemStack stack) {
        return Lacing.level(stack) >= 2 ? FastColor.ARGB32.color(255, 112, 52, 176) : FastColor.ARGB32.color(255, 186, 140, 236);
    }

    static final class Renderer extends ArrowRenderer<LacedArrow> {
        Renderer(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public ResourceLocation getTextureLocation(LacedArrow arrow) {
            return TIPPED;
        }
    }
}
