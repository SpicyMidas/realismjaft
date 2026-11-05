package com.seba.reverie;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.seba.reverie.compat.Compat;
import com.seba.reverie.compat.create.CreateCompat;
import com.seba.reverie.compat.fd.FdCompat;
import com.seba.reverie.data.ReverieDataGen;
import com.seba.reverie.content.melatonia.Lacing;
import com.seba.reverie.registry.ModBlocks;
import com.seba.reverie.registry.ModComponents;
import com.seba.reverie.registry.ModCreativeTab;
import com.seba.reverie.registry.ModEffects;
import com.seba.reverie.registry.ModItems;
import com.seba.reverie.registry.ModRecipes;
import com.seba.reverie.registry.ModWorldgen;
import net.neoforged.neoforge.common.NeoForge;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Reverie.MOD_ID)
public final class Reverie {
    public static final String MOD_ID = "reverie";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Reverie(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, ReverieConfig.COMMON_SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, ReverieConfig.CLIENT_SPEC);

        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        com.seba.reverie.registry.ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModComponents.COMPONENTS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        ModWorldgen.PLACEMENT_MODIFIERS.register(modBus);
        ModWorldgen.CHUNK_GENERATORS.register(modBus);
        ModWorldgen.BIOME_SOURCES.register(modBus);
        com.seba.reverie.registry.ModAttachments.ATTACHMENTS.register(modBus);
        com.seba.reverie.registry.ModEntities.ENTITIES.register(modBus);
        com.seba.reverie.registry.ModParticles.PARTICLES.register(modBus);
        com.seba.reverie.registry.ModSounds.SOUNDS.register(modBus);
        modBus.addListener(com.seba.reverie.network.ReverieNetwork::register);
        ModCreativeTab.TABS.register(modBus);
        ModCreativeTab.add(ModItems.TUFF_ALLOY);
        ModCreativeTab.add(ModItems.WILD_MELATONIA);
        ModCreativeTab.add(ModItems.MELATONIA_SEEDS);
        ModCreativeTab.add(ModItems.MELATONIA_PETALS);
        ModCreativeTab.add(ModItems.MELATONIA_EXTRACT);
        ModCreativeTab.add(ModItems.CONCENTRATED_MELATONIA_EXTRACT);
        ModCreativeTab.add(ModItems.MELATONIA_COOKIE);
        ModCreativeTab.add(ModItems.DROWSY_TEA);
        ModCreativeTab.add(ModItems.SLEEPY_STEW);
        for (var item : ModItems.DREAM_BLOCK_ITEMS) ModCreativeTab.add(item);
        ModCreativeTab.add(ModItems.LONE_DOOR);
        ModCreativeTab.add(ModItems.LUCID_SHARD);
        ModCreativeTab.add(ModItems.DREAM_DUST);
        ModCreativeTab.add(ModItems.DREAM_CATCHER);

        NeoForge.EVENT_BUS.addListener(Lacing::onFinishUsing);
        com.seba.reverie.dream.DreamEvents.init();
        com.seba.reverie.dream.DreamManager.addListener(new com.seba.reverie.dreamland.MemoryIsland());
        com.seba.reverie.shade.ShadeCrowdManager.init();
        com.seba.reverie.dreamland.DreamWeather.init();
        com.seba.reverie.dreamland.RecurringObjects.init();
        com.seba.reverie.dreamland.DreamFootsteps.init();
        com.seba.reverie.dream.DreamAdvancements.init();
        modBus.addListener(ReverieDataGen::gather);

        // Optional integrations: their classes are only touched behind these checks.
        if (Compat.create()) CreateCompat.init(modBus);
        if (Compat.farmersDelight()) FdCompat.init(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
