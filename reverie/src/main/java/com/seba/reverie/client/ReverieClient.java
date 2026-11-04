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
        if (Compat.create()) CreateClientCompat.init(modBus);
    }
}
