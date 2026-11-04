package com.seba.reverie.compat.create.client;

import com.seba.reverie.Reverie;
import com.seba.reverie.compat.create.CreateContent;
import com.seba.reverie.compat.create.gearshift.TuffGearshiftBlock;
import com.simibubi.create.content.kinetics.transmission.SplitShaftVisual;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Client half of the Create integration; only reached from the client entry point when Create is loaded. */
public final class CreateClientCompat {
    private CreateClientCompat() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(CreateClientCompat::renderers);
        modBus.addListener(CreateClientCompat::models);
        modBus.addListener(CreateClientCompat::setup);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CreateContent.GEARSHIFT_BE.get(), TuffGearshiftRenderer::new);
    }

    private static void models(ModelEvent.RegisterAdditional event) {
        event.register(TuffGearshiftRenderer.STICK_REGULAR);
        event.register(TuffGearshiftRenderer.STICK_SPORT);
        event.register(TuffGearshiftRenderer.BOOT_UPPER);
    }

    private static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            TuffGearshiftBlock.screenOpener = pos -> Minecraft.getInstance().setScreen(new TuffGearshiftScreen(pos));
            try {
                // Same visual Create uses for its Gearshift; the renderer still runs for the stick.
                SimpleBlockEntityVisualizer.builder(CreateContent.GEARSHIFT_BE.get())
                        .factory(SplitShaftVisual::new)
                        .skipVanillaRender(be -> false)
                        .apply();
                TuffGearshiftRenderer.visualRegistered = true;
            } catch (Throwable t) {
                Reverie.LOGGER.warn("Could not register the Flywheel visual for the Tuff Gearshift; using the plain renderer", t);
            }
        });
    }
}
