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
                    ModItems.DROWSY_TEA.get(), ModItems.SLEEPY_STEW.get(), ModItems.MELATONIA_CAKE.get(), ModItems.MELATONIA_CAKE_SLICE.get()}) {
                basicItem(item);
            }
            withExistingParent("wild_melatonia", "item/generated").texture("layer0", modLoc("block/wild_melatonia"));
            basicItem(ModItems.LUCID_SHARD.get());
            basicItem(ModItems.DREAM_DUST.get());
            // Closed (something caught) shows the animated closed catcher.
            basicItem(ModItems.DREAM_CATCHER.get()).override().predicate(modLoc("closed"), 1f)
                    .model(getExistingFile(modLoc("item/dream_catcher_closed"))).end();
            // While gliding, the held glider draws nothing: GliderClient draws the 3D one overhead.
            basicItem(ModItems.PAPER_GLIDER.get()).override().predicate(modLoc("gliding"), 1f)
                    .model(getExistingFile(modLoc("item/paper_glider_gliding"))).end();
            // The lit 3D lantern in hand and in the inventory; nothing in hand while it lifts you (drawn overhead).
            getBuilder("sky_lantern").parent(getExistingFile(modLoc("item/sky_lantern_3d"))).override().predicate(modLoc("lifting"), 1f)
                    .model(getExistingFile(modLoc("item/sky_lantern_lifting"))).end();
            for (var egg : new String[]{"sleepwalking_sheep_spawn_egg", "lucid_moth_spawn_egg"}) {
                withExistingParent(egg, mcLoc("item/template_spawn_egg"));
            }
            for (String block : new String[]{"dream_grass_block", "dream_soil", "lucid_stone", "cobbled_lucid_stone", "lucid_stone_bricks",
                    "dreamwood_log", "dreamwood_planks", "dreamwood_leaves", "lucid_crystal_ore", "pillow_cloud", "mirror_water",
                    "hourglass_sand", "floating_lantern", "melted_clock", "sleep_bell", "sandglass_core"}) {
                // Parents generated in this same run are not "existing" yet; don't check them.
                getBuilder(block).parent(new net.neoforged.neoforge.client.model.generators.ModelFile.UncheckedModelFile(modLoc("block/" + block)));
            }
            withExistingParent("lucid_crystal_cluster", "item/generated").texture("layer0", modLoc("block/lucid_crystal_cluster"));
            // The new biomes: plants are flat items, everything else shows its block.
            for (var b : ModBlocks.biomeBlocks()) {
                String name = b.getId().getPath();
                if (BlockStates.PLANTS.contains(name)) {
                    withExistingParent(name, "item/generated").texture("layer0", modLoc("block/" + name));
                    continue;
                }
                String model = switch (name) {
                    case "watching_window" -> "watching_window_lit";
                    case "flicker_light" -> "flicker_light_on";
                    default -> name;
                };
                getBuilder(name).parent(new net.neoforged.neoforge.client.model.generators.ModelFile.UncheckedModelFile(modLoc("block/" + model)));
            }
            withExistingParent("eyebloom", "item/generated").texture("layer0", modLoc("block/eyebloom_eye"));
            com.seba.reverie.compat.create.CreateDataGen.itemModels(this);
            com.seba.reverie.compat.fd.FdDataGen.itemModels(this);
        }
    }

    static final class BlockStates extends BlockStateProvider {
        static final java.util.Set<String> PLANTS = java.util.Set.of("frostbloom", "ice_chime", "sky_coral", "sky_kelp", "withered_grass", "vein_vines");

        BlockStates(PackOutput output, ExistingFileHelper files) {
            super(output, Reverie.MOD_ID, files);
        }

        @Override
        protected void registerStatesAndModels() {
            // The Impossible Stairs anchor draws nothing itself; the model only names a particle texture.
            simpleBlock(ModBlocks.IMPOSSIBLE_STAIRS.get(), models().getBuilder("impossible_stairs").texture("particle", mcLoc("block/quartz_block_side")));
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
            biomeBlocks();
        }

        private void biomeBlocks() {
            for (var b : ModBlocks.biomeBlocks()) {
                String name = b.getId().getPath();
                if (PLANTS.contains(name)) {
                    simpleBlock(b.get(), models().cross(name, modLoc("block/" + name)).renderType("cutout"));
                    continue;
                }
                switch (name) {
                    case "starlight_ice" -> simpleBlock(b.get(), models().cubeAll(name, modLoc("block/" + name)).renderType("translucent"));
                    case "bubble_vent" -> simpleBlock(b.get(), models().cubeBottomTop(name, modLoc("block/bubble_vent_side"),
                            modLoc("block/reef_stone"), modLoc("block/bubble_vent_top")));
                    case "blackwood_log" -> logBlock(ModBlocks.BLACKWOOD_LOG.get());
                    case "resonant_crystal" -> {
                        var dim = models().cubeAll(name, modLoc("block/resonant_crystal"));
                        var lit = models().cubeAll("resonant_crystal_lit", modLoc("block/resonant_crystal_lit"));
                        getVariantBuilder(b.get()).forAllStates(st -> net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                                .modelFile(st.getValue(com.seba.reverie.dreamland.block.ResonantCrystalBlock.RESONANCE) >= 2 ? lit : dim).build());
                    }
                    case "watching_window", "flicker_light" -> {
                        boolean window = name.equals("watching_window");
                        var on = models().cubeAll(window ? "watching_window_lit" : "flicker_light_on", modLoc("block/" + (window ? "watching_window_lit" : "flicker_light_on")));
                        var off = models().cubeAll(window ? "watching_window" : "flicker_light_off", modLoc("block/" + (window ? "watching_window" : "flicker_light_off")));
                        getVariantBuilder(b.get()).forAllStates(st -> net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                                .modelFile(st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? on : off).build());
                    }
                    default -> simpleBlock(b.get());
                }
            }
        }
    }
}
