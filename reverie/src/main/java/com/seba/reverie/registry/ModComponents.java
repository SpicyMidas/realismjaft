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

    /** Marks the item a dreamer carried into the dream (it never comes back as a keepsake). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.util.Unit>> DREAM_ORIGIN =
            COMPONENTS.registerComponentType("dream_origin", b -> b.persistent(net.minecraft.util.Unit.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE)));

    /** What a Dream Catcher caught in the last dream (it comes back closed; using it opens it). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.world.item.component.ItemContainerContents>> CAUGHT =
            COMPONENTS.registerComponentType("caught", b -> b.persistent(net.minecraft.world.item.component.ItemContainerContents.CODEC)
                    .networkSynchronized(net.minecraft.world.item.component.ItemContainerContents.STREAM_CODEC));

    private ModComponents() {
    }
}
