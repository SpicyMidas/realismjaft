package com.seba.reverie.data;

import com.seba.reverie.Reverie;
import com.seba.reverie.content.melatonia.MelatoniaCropBlock;
import com.seba.reverie.registry.ModBlocks;
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
            for (var item : new net.minecraft.world.item.Item[]{ModItems.MELATONIA_SEEDS.get(), ModItems.MELATONIA_PETALS.get(),
                    ModItems.MELATONIA_EXTRACT.get(), ModItems.CONCENTRATED_MELATONIA_EXTRACT.get(), ModItems.MELATONIA_COOKIE.get(),
                    ModItems.DROWSY_TEA.get(), ModItems.SLEEPY_STEW.get()}) {
                basicItem(item);
            }
            withExistingParent("wild_melatonia", "item/generated").texture("layer0", modLoc("block/wild_melatonia"));
            basicItem(ModItems.LUCID_SHARD.get());
            basicItem(ModItems.DREAM_DUST.get());
            basicItem(ModItems.DREAM_CATCHER.get());
            for (String block : new String[]{"dream_grass_block", "dream_soil", "lucid_stone", "cobbled_lucid_stone", "lucid_stone_bricks",
                    "dreamwood_log", "dreamwood_planks", "dreamwood_leaves", "lucid_crystal_ore", "pillow_cloud", "mirror_water",
                    "hourglass_sand", "floating_lantern", "melted_clock", "sleep_bell", "sandglass_core"}) {
                // Parents generated in this same run are not "existing" yet; don't check them.
                getBuilder(block).parent(new net.neoforged.neoforge.client.model.generators.ModelFile.UncheckedModelFile(modLoc("block/" + block)));
            }
            withExistingParent("lucid_crystal_cluster", "item/generated").texture("layer0", modLoc("block/lucid_crystal_cluster"));
            withExistingParent("eyebloom", "item/generated").texture("layer0", modLoc("block/eyebloom_eye"));
            com.seba.reverie.compat.create.CreateDataGen.itemModels(this);
            com.seba.reverie.compat.fd.FdDataGen.itemModels(this);
        }
    }

    static final class BlockStates extends BlockStateProvider {
        BlockStates(PackOutput output, ExistingFileHelper files) {
            super(output, Reverie.MOD_ID, files);
        }

        @Override
        protected void registerStatesAndModels() {
            com.seba.reverie.compat.fd.FdDataGen.blockStates(this);
            getVariantBuilder(ModBlocks.MELATONIA.get()).forAllStates(state -> {
                int age = state.getValue(MelatoniaCropBlock.AGE);
                return net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                        .modelFile(models().crop("melatonia_stage" + age, modLoc("block/melatonia_stage" + age)).renderType("cutout"))
                        .build();
            });
            simpleBlock(ModBlocks.WILD_MELATONIA.get(), models().cross("wild_melatonia", modLoc("block/wild_melatonia")).renderType("cutout"));

            // Dreamland. The grass bottom is grassy too, so upside-down islands look right from below.
            simpleBlock(ModBlocks.DREAM_GRASS_BLOCK.get(), models().withExistingParent("dream_grass_block", mcLoc("block/grass_block"))
                    .texture("particle", modLoc("block/dream_soil")).texture("bottom", modLoc("block/dream_grass_block_bottom"))
                    .texture("top", modLoc("block/dream_grass_block_top")).texture("side", modLoc("block/dream_grass_block_side"))
                    .texture("overlay", modLoc("block/dream_grass_block_side_overlay")).renderType("cutout_mipped"));
            for (var b : new net.neoforged.neoforge.registries.DeferredBlock<?>[]{ModBlocks.DREAM_SOIL, ModBlocks.LUCID_STONE,
                    ModBlocks.COBBLED_LUCID_STONE, ModBlocks.LUCID_STONE_BRICKS, ModBlocks.DREAMWOOD_PLANKS, ModBlocks.LUCID_CRYSTAL_ORE,
                    ModBlocks.HOURGLASS_SAND, ModBlocks.MIRROR_WATER}) {
                simpleBlock(b.get());
            }
            simpleBlock(ModBlocks.PILLOW_CLOUD.get(), models().cubeAll("pillow_cloud", modLoc("block/pillow_cloud")).renderType("translucent"));
            logBlock(ModBlocks.DREAMWOOD_LOG.get());
            simpleBlock(ModBlocks.DREAMWOOD_LEAVES.get(), models().withExistingParent("dreamwood_leaves", mcLoc("block/leaves"))
                    .texture("all", modLoc("block/dreamwood_leaves")).renderType("cutout_mipped"));
            var cluster = models().cross("lucid_crystal_cluster", modLoc("block/lucid_crystal_cluster")).renderType("cutout");
            getVariantBuilder(ModBlocks.LUCID_CRYSTAL_CLUSTER.get()).forAllStatesExcept(state -> {
                var f = state.getValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING);
                int x = f == net.minecraft.core.Direction.DOWN ? 180 : f.getAxis().isHorizontal() ? 90 : 0;
                int y = f.getAxis().isHorizontal() ? ((int) f.toYRot() + 180) % 360 : 0;
                return net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder().modelFile(cluster).rotationX(x).rotationY(y).build();
            }, net.minecraft.world.level.block.AmethystClusterBlock.WATERLOGGED);
        }
    }
}
