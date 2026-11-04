package com.seba.reverie.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.seba.reverie.Reverie;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Reverie.MOD_ID);

    /** Ordered tab contents; compat modules append to it only when their mod is loaded. */
    private static final List<Supplier<? extends ItemLike>> ENTRIES = new ArrayList<>();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.reverie"))
            .icon(() -> new ItemStack(ModItems.TUFF_ALLOY.get()))
            .displayItems((params, output) -> {
                for (Supplier<? extends ItemLike> entry : ENTRIES) {
                    output.accept(entry.get());
                }
            })
            .build());

    public static void add(Supplier<? extends ItemLike> item) {
        ENTRIES.add(item);
    }

    private ModCreativeTab() {
    }
}
