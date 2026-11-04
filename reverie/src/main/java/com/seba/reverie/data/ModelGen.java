package com.seba.reverie.data;

import com.seba.reverie.Reverie;
import com.seba.reverie.registry.ModItems;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

final class ModelGen {
    private ModelGen() {
    }

    static final class Items extends ItemModelProvider {
        Items(PackOutput output, ExistingFileHelper files) {
            super(output, Reverie.MOD_ID, files);
        }

        @Override
        protected void registerModels() {
            basicItem(ModItems.TUFF_ALLOY.get());
        }
    }

    static final class BlockStates extends BlockStateProvider {
        BlockStates(PackOutput output, ExistingFileHelper files) {
            super(output, Reverie.MOD_ID, files);
        }

        @Override
        protected void registerStatesAndModels() {
        }
    }
}
