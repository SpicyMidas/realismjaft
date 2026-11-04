package com.seba.reverie.data;

import java.util.concurrent.CompletableFuture;

import com.seba.reverie.Reverie;
import com.seba.reverie.compat.create.CreateDataGen;
import com.seba.reverie.registry.ModItems;
import com.seba.reverie.registry.ModTags;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

final class TagGen {
    private TagGen() {
    }

    static final class Blocks extends BlockTagsProvider {
        Blocks(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
            super(output, lookup, Reverie.MOD_ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            CreateDataGen.blockTags(this::getOrCreateRawBuilder);
        }
    }

    static final class Items extends ItemTagsProvider {
        Items(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
              CompletableFuture<TagLookup<Block>> blockTags, ExistingFileHelper files) {
            super(output, lookup, blockTags, Reverie.MOD_ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            tag(ModTags.Items.INGOTS_TUFF_ALLOY).add(ModItems.TUFF_ALLOY.get());
        }
    }
}
