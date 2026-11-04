package com.seba.reverie.registry;

import com.seba.reverie.Reverie;
import com.seba.reverie.content.melatonia.ConfigRarityFilter;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModWorldgen {
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS = DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Reverie.MOD_ID);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ConfigRarityFilter>> CONFIG_RARITY =
            PLACEMENT_MODIFIERS.register("config_rarity", () -> () -> ConfigRarityFilter.CODEC);

    private ModWorldgen() {
    }
}
