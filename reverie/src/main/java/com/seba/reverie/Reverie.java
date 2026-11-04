package com.seba.reverie;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.seba.reverie.compat.Compat;
import com.seba.reverie.compat.create.CreateCompat;
import com.seba.reverie.compat.fd.FdCompat;
import com.seba.reverie.data.ReverieDataGen;
import com.seba.reverie.registry.ModCreativeTab;
import com.seba.reverie.registry.ModItems;

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

        ModItems.ITEMS.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        ModCreativeTab.add(ModItems.TUFF_ALLOY);
        modBus.addListener(ReverieDataGen::gather);

        // Optional integrations: their classes are only touched behind these checks.
        if (Compat.create()) CreateCompat.init(modBus);
        if (Compat.farmersDelight()) FdCompat.init(modBus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
