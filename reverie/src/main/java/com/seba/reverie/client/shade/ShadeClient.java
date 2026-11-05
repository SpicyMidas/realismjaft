package com.seba.reverie.client.shade;

import com.seba.reverie.registry.ModEntities;
import com.seba.reverie.shade.ShadeLook;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Registers the Shade renderer and gives the common code a view of the camera. */
public final class ShadeClient {
    private ShadeClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e) -> {
            for (int i = 0; i < ShadeModel.LAYERS.length; i++) {
                float grow = ShadeModel.GROW[i];
                e.registerLayerDefinition(ShadeModel.humanoidLayer(i), () -> ShadeModel.humanoid(grow));
                e.registerLayerDefinition(ShadeModel.doorframeLayer(i), () -> ShadeModel.doorframe(grow));
            }
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> e.registerEntityRenderer(ModEntities.SHADE.get(), ShadeRenderer::new));
        ShadeLook.viewer = new ShadeLook.Viewer() {
            @Override
            public Vec3 eye() {
                Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
                return cam.isInitialized() ? cam.getPosition() : null;
            }

            @Override
            public Vec3 look() {
                return new Vec3(Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector());
            }
        };
    }
}
