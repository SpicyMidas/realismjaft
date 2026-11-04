package com.seba.reverie.data;

import java.util.concurrent.CompletableFuture;

import com.seba.reverie.registry.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.DataMapProvider;
import net.neoforged.neoforge.registries.datamaps.builtin.Compostable;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

final class DataMapGen extends DataMapProvider {
    DataMapGen(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup);
    }

    @Override
    protected void gather() {
        builder(NeoForgeDataMaps.COMPOSTABLES)
                .add(ModItems.MELATONIA_PETALS, new Compostable(0.65f), false)
                .add(ModItems.MELATONIA_SEEDS, new Compostable(0.3f), false)
                .add(ModItems.WILD_MELATONIA, new Compostable(0.65f), false);
    }
}
