package com.seba.reverie.data;

import java.util.concurrent.CompletableFuture;

import com.seba.reverie.Reverie;
import com.seba.reverie.compat.create.CreateDataGen;
import com.seba.reverie.registry.ModBlocks;
import com.seba.reverie.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
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
            tag(ModTags.Blocks.MELATONIA_PLANTABLE).add(net.minecraft.world.level.block.Blocks.FARMLAND)
                    .addOptional(ResourceLocation.fromNamespaceAndPath("farmersdelight", "rich_soil_farmland"));
            tag(ModTags.Blocks.WILD_MELATONIA_PLACEABLE).addTag(BlockTags.DIRT);
            tag(BlockTags.CROPS).add(ModBlocks.MELATONIA.get());
            tag(BlockTags.MAINTAINS_FARMLAND).add(ModBlocks.MELATONIA.get());
            tag(BlockTags.SMALL_FLOWERS).add(ModBlocks.WILD_MELATONIA.get());
            tag(BlockTags.SWORD_EFFICIENT).add(ModBlocks.WILD_MELATONIA.get());
            tag(BlockTags.ENDERMAN_HOLDABLE).add(ModBlocks.WILD_MELATONIA.get());
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
            tag(ModTags.Items.LACING_AGENTS).add(ModItems.MELATONIA_EXTRACT.get(), ModItems.CONCENTRATED_MELATONIA_EXTRACT.get())
                    .addOptional(Reverie.id("melatonia_syrup"));
            tag(net.neoforged.neoforge.common.Tags.Items.SEEDS).add(ModItems.MELATONIA_SEEDS.get());
            tag(ItemTags.VILLAGER_PLANTABLE_SEEDS).add(ModItems.MELATONIA_SEEDS.get());
            tag(ItemTags.SMALL_FLOWERS).add(ModItems.WILD_MELATONIA.get());
            tag(net.neoforged.neoforge.common.Tags.Items.FOODS).add(ModItems.MELATONIA_COOKIE.get(), ModItems.SLEEPY_STEW.get(), ModItems.DROWSY_TEA.get());
        }
    }

    static final class Biomes extends net.minecraft.data.tags.TagsProvider<net.minecraft.world.level.biome.Biome> {
        Biomes(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup, ExistingFileHelper files) {
            super(output, net.minecraft.core.registries.Registries.BIOME, lookup, Reverie.MOD_ID, files);
        }

        @Override
        protected void addTags(HolderLookup.Provider provider) {
            tag(ModTags.Biomes.HAS_WILD_MELATONIA).addTag(net.minecraft.tags.BiomeTags.IS_FOREST)
                    .add(net.minecraft.world.level.biome.Biomes.PLAINS, net.minecraft.world.level.biome.Biomes.SUNFLOWER_PLAINS,
                            net.minecraft.world.level.biome.Biomes.MEADOW, net.minecraft.world.level.biome.Biomes.DARK_FOREST);
        }
    }
}
