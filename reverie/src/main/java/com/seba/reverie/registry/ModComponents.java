package com.seba.reverie.registry;

import com.seba.reverie.Reverie;
import com.seba.reverie.content.melatonia.Laced;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Reverie.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Laced>> LACED =
            COMPONENTS.registerComponentType("laced", b -> b.persistent(Laced.CODEC).networkSynchronized(Laced.STREAM_CODEC));

    private ModComponents() {
    }
}
