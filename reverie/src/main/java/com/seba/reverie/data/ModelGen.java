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
            com.seba.reverie.compat.create.CreateDataGen.itemModels(this);
        }
    }

    static final class BlockStates extends BlockStateProvider {
        BlockStates(PackOutput output, ExistingFileHelper files) {
            super(output, Reverie.MOD_ID, files);
        }

        @Override
        protected void registerStatesAndModels() {
            getVariantBuilder(ModBlocks.MELATONIA.get()).forAllStates(state -> {
                int age = state.getValue(MelatoniaCropBlock.AGE);
                return net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                        .modelFile(models().crop("melatonia_stage" + age, modLoc("block/melatonia_stage" + age)).renderType("cutout"))
                        .build();
            });
            simpleBlock(ModBlocks.WILD_MELATONIA.get(), models().cross("wild_melatonia", modLoc("block/wild_melatonia")).renderType("cutout"));
        }
    }
}
