package com.seba.reverie.data;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.seba.reverie.content.melatonia.MelatoniaCropBlock;
import com.seba.reverie.registry.ModBlocks;
import com.seba.reverie.registry.ModItems;

import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;

final class LootGen {
    private LootGen() {
    }

    static LootTableProvider create(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        return new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(Blocks::new, LootContextParamSets.BLOCK)), lookup);
    }

    static final class Blocks extends BlockLootSubProvider {
        Blocks(HolderLookup.Provider registries) {
            super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
        }

        @Override
        protected void generate() {
            var fortune = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
            LootItemCondition.Builder mature = LootItemBlockStatePropertyCondition.hasBlockStateProperties(ModBlocks.MELATONIA.get())
                    .setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(MelatoniaCropBlock.AGE, MelatoniaCropBlock.MAX_AGE));
            add(ModBlocks.MELATONIA.get(), applyExplosionDecay(ModBlocks.MELATONIA.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).when(mature)
                            .add(LootItem.lootTableItem(ModItems.MELATONIA_PETALS.get())
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2)))
                                    .apply(ApplyBonusCount.addBonusBinomialDistributionCount(fortune, 0.5714286f, 0))))
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                            .add(LootItem.lootTableItem(ModItems.MELATONIA_SEEDS.get()).when(mature)
                                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2)))
                                    .apply(ApplyBonusCount.addBonusBinomialDistributionCount(fortune, 0.5714286f, 0))
                                    .otherwise(LootItem.lootTableItem(ModItems.MELATONIA_SEEDS.get()))))));
            add(ModBlocks.WILD_MELATONIA.get(), applyExplosionDecay(ModBlocks.WILD_MELATONIA.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(ModItems.MELATONIA_SEEDS.get())))
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(ModItems.MELATONIA_PETALS.get())
                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(0, 1)))))));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ModBlocks.BLOCKS.getEntries().stream().map(DeferredHolder::get).map(b -> (Block) b).toList();
        }
    }
}
