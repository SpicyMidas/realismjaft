package com.seba.reverie.client;

import com.seba.reverie.Reverie;
import com.seba.reverie.compat.Compat;
import com.seba.reverie.compat.create.client.CreateClientCompat;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/** Client-only entry point. Everything that touches Minecraft client classes is wired from here. */
@Mod(value = Reverie.MOD_ID, dist = Dist.CLIENT)
public final class ReverieClient {
    public ReverieClient(IEventBus modBus, ModContainer container) {
        DizzinessClient.init();
        DreamClient.init(modBus);
        LucidSightClient.init();
        LacedProjectilesClient.init(modBus);
        DreamEffectsClient.init();
        modBus.addListener(com.seba.reverie.client.shader.ReverieShaders::onRegisterShaders);
        com.seba.reverie.client.shader.ScreenEffects.init(modBus);
        com.seba.reverie.client.shade.ShadeClient.init(modBus);
        com.seba.reverie.client.dreamland.DreamlandClient.init(modBus);
        com.seba.reverie.client.dreamland.LucidConfusionClient.init(modBus);
        com.seba.reverie.client.dreamland.BiomeMechanicsClient.init(modBus);
        com.seba.reverie.client.glider.GliderClient.init(modBus);
        com.seba.reverie.client.glider.SkyLanternClient.init(modBus);
        modBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent e) -> e.enqueueWork(() ->
                net.minecraft.client.renderer.item.ItemProperties.register(com.seba.reverie.registry.ModItems.DREAM_CATCHER.get(),
                        Reverie.id("closed"), (stack, level, entity, seed) -> com.seba.reverie.content.DreamCatcherItem.isOpen(stack) ? 0f : 1f)));
        if (Compat.create()) CreateClientCompat.init(modBus);
    }
}
