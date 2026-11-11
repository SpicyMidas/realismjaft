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
            tag(ModTags.Blocks.WILD_MELATONIA_PLACEABLE).add(ModBlocks.DREAM_GRASS_BLOCK.get(), ModBlocks.DREAM_SOIL.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.LUCID_STONE.get(), ModBlocks.COBBLED_LUCID_STONE.get(),
                    ModBlocks.LUCID_STONE_BRICKS.get(), ModBlocks.LUCID_CRYSTAL_ORE.get(), ModBlocks.LUCID_CRYSTAL_CLUSTER.get(),
                    ModBlocks.MIRROR_WATER.get(), ModBlocks.SLEEP_BELL.get(), ModBlocks.MELTED_CLOCK.get());
            tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.DREAMWOOD_LOG.get(), ModBlocks.DREAMWOOD_PLANKS.get(), ModBlocks.LONE_DOOR.get(),
                    ModBlocks.SANDGLASS_CORE.get(), ModBlocks.FLOATING_LANTERN.get());
            tag(BlockTags.MINEABLE_WITH_SHOVEL).add(ModBlocks.DREAM_GRASS_BLOCK.get(), ModBlocks.DREAM_SOIL.get(), ModBlocks.HOURGLASS_SAND.get(),
                    ModBlocks.PILLOW_CLOUD.get());
            tag(BlockTags.MINEABLE_WITH_HOE).add(ModBlocks.DREAMWOOD_LEAVES.get());
            tag(BlockTags.LEAVES).add(ModBlocks.DREAMWOOD_LEAVES.get());
            tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.RIME_STONE.get(), ModBlocks.STARLIGHT_ICE.get(), ModBlocks.REEF_STONE.get(),
                    ModBlocks.TIDE_CORAL.get(), ModBlocks.DUSK_CORAL.get(), ModBlocks.BUBBLE_VENT.get(), ModBlocks.GEODE_SHELL.get(),
                    ModBlocks.LILAC_CALCITE.get(), ModBlocks.RESONANT_CRYSTAL.get(), ModBlocks.NIGHT_LIGHT.get(), ModBlocks.CEILING_TILE.get(),
                    ModBlocks.FLICKER_LIGHT.get(), ModBlocks.ENAMEL.get(), ModBlocks.STATIC_BLOCK.get(), ModBlocks.DEAD_PIXEL.get(), ModBlocks.TEST_CARD.get());
            tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.LANTERN_STAND.get(), ModBlocks.BLACKWOOD_LOG.get(), ModBlocks.TOY_BLOCK_RED.get(), ModBlocks.TOY_BLOCK_YELLOW.get(),
                    ModBlocks.TOY_BLOCK_BLUE.get(), ModBlocks.TOY_BLOCK_GREEN.get(), ModBlocks.WATCHING_WINDOW.get(), ModBlocks.YELLOWED_WALLPAPER.get());
            tag(BlockTags.MINEABLE_WITH_SHOVEL).add(ModBlocks.DREAMSNOW.get(), ModBlocks.VIOLET_MOSS.get(), ModBlocks.ASHEN_SOIL.get(),
                    ModBlocks.STUFFING.get(), ModBlocks.NURSERY_RUG.get(), ModBlocks.STALE_CARPET.get());
            tag(BlockTags.MINEABLE_WITH_HOE).add(ModBlocks.DREAM_FLESH.get(), ModBlocks.SINEW.get());
            tag(BlockTags.LOGS).add(ModBlocks.BLACKWOOD_LOG.get());
            tag(BlockTags.DIRT).add(ModBlocks.ASHEN_SOIL.get(), ModBlocks.VIOLET_MOSS.get());
            tag(BlockTags.ICE).add(ModBlocks.STARLIGHT_ICE.get());
            tag(BlockTags.LOGS).add(ModBlocks.DREAMWOOD_LOG.get());
            tag(BlockTags.PLANKS).add(ModBlocks.DREAMWOOD_PLANKS.get());
            tag(BlockTags.DIRT).add(ModBlocks.DREAM_SOIL.get(), ModBlocks.DREAM_GRASS_BLOCK.get());
            tag(BlockTags.SMALL_FLOWERS).add(ModBlocks.EYEBLOOM.get());
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
            tag(ModTags.Items.LUCID_KEEPSAKES).add(ModItems.LUCID_SHARD.get(), ModItems.DREAM_DUST.get());
            tag(ItemTags.LOGS).add(ModBlocks.DREAMWOOD_LOG.get().asItem(), ModBlocks.BLACKWOOD_LOG.get().asItem());
            tag(ItemTags.PLANKS).add(ModBlocks.DREAMWOOD_PLANKS.get().asItem());
            tag(ItemTags.LEAVES).add(ModBlocks.DREAMWOOD_LEAVES.get().asItem());
            tag(net.neoforged.neoforge.common.Tags.Items.FOODS).add(ModItems.MELATONIA_COOKIE.get(), ModItems.SLEEPY_STEW.get(), ModItems.DROWSY_TEA.get(),
                    ModItems.MELATONIA_CAKE_SLICE.get());
            tag(net.neoforged.neoforge.common.Tags.Items.FOODS_COOKIE).add(ModItems.MELATONIA_COOKIE.get());
            tag(net.neoforged.neoforge.common.Tags.Items.FOODS_SOUP).add(ModItems.SLEEPY_STEW.get());
            tag(net.neoforged.neoforge.common.Tags.Items.FOODS_EDIBLE_WHEN_PLACED).add(ModItems.MELATONIA_CAKE.get());
            tag(ItemTags.ARROWS).add(ModItems.LACED_ARROW.get());
            tag(ModTags.Items.LACING_BONUS_FOODS)
                    .addTag(net.neoforged.neoforge.common.Tags.Items.FOODS_SOUP).addTag(net.neoforged.neoforge.common.Tags.Items.FOODS_PIE)
                    .addTag(net.neoforged.neoforge.common.Tags.Items.FOODS_COOKIE)
                    .add(ModItems.MELATONIA_CAKE_SLICE.get(), ModItems.DROWSY_TEA.get(), net.minecraft.world.item.Items.PUMPKIN_PIE,
                            net.minecraft.world.item.Items.MUSHROOM_STEW, net.minecraft.world.item.Items.RABBIT_STEW,
                            net.minecraft.world.item.Items.BEETROOT_SOUP, net.minecraft.world.item.Items.SUSPICIOUS_STEW,
                            net.minecraft.world.item.Items.HONEY_BOTTLE)
                    .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("farmersdelight", "cake_slice"))
                    .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("farmersdelight", "apple_pie_slice"))
                    .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("farmersdelight", "sweet_berry_cheesecake_slice"))
                    .addOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("farmersdelight", "chocolate_pie_slice"))
                    .addOptional(Reverie.id("dreamy_pie_slice")).addOptional(Reverie.id("lullaby_stew"))
                    .addOptional(Reverie.id("dozing_dumplings")).addOptional(Reverie.id("melatonia_syrup"));
            com.seba.reverie.compat.fd.FdDataGen.itemTags(this::getOrCreateRawBuilder);
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
