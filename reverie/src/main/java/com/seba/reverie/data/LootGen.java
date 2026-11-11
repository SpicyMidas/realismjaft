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
                    // Mostly seeds: one or two, and only now and then a petal.
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(ModItems.MELATONIA_SEEDS.get())
                            .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2)))))
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(ModItems.MELATONIA_PETALS.get()))
                            .when(net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition.randomChance(0.15f)))));
            add(ModBlocks.MELATONIA_CAKE.get(), noDrop());   // like a cake: eaten, not mined
            dreamland();
        }

        private void dreamland() {
            var fortune = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE);
            for (var b : new net.neoforged.neoforge.registries.DeferredBlock<?>[]{ModBlocks.DREAM_SOIL, ModBlocks.COBBLED_LUCID_STONE,
                    ModBlocks.LUCID_STONE_BRICKS, ModBlocks.DREAMWOOD_LOG, ModBlocks.DREAMWOOD_PLANKS, ModBlocks.HOURGLASS_SAND,
                    ModBlocks.FLOATING_LANTERN, ModBlocks.MELTED_CLOCK, ModBlocks.EYEBLOOM, ModBlocks.SLEEP_BELL, ModBlocks.SANDGLASS_CORE}) {
                dropSelf(b.get());
            }
            add(ModBlocks.LONE_DOOR.get(), b -> createDoorTable(b));
            add(ModBlocks.LUCID_STONE.get(), b -> createSingleItemTableWithSilkTouch(b, ModBlocks.COBBLED_LUCID_STONE.get()));
            add(ModBlocks.DREAM_GRASS_BLOCK.get(), b -> createSilkTouchDispatchTable(b, applyExplosionDecay(b,
                    LootItem.lootTableItem(ModBlocks.DREAM_SOIL.get()))).withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                    .when(doesNotHaveSilkTouch()).when(net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition.randomChance(0.2f))
                    .add(LootItem.lootTableItem(ModItems.DREAM_DUST.get()))));
            add(ModBlocks.PILLOW_CLOUD.get(), b -> createSilkTouchDispatchTable(b, applyExplosionDecay(b, LootItem.lootTableItem(ModItems.DREAM_DUST.get())
                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(2, 4))))));
            add(ModBlocks.LUCID_CRYSTAL_ORE.get(), b -> createSilkTouchDispatchTable(b, applyExplosionDecay(b, LootItem.lootTableItem(ModItems.LUCID_SHARD.get())
                    .apply(SetItemCountFunction.setCount(UniformGenerator.between(1, 2))).apply(ApplyBonusCount.addOreBonusCount(fortune)))));
            add(ModBlocks.LUCID_CRYSTAL_CLUSTER.get(), b -> createSilkTouchDispatchTable(b, LootItem.lootTableItem(ModItems.LUCID_SHARD.get())
                    .apply(SetItemCountFunction.setCount(ConstantValue.exactly(2)))));
            add(ModBlocks.DREAMWOOD_LEAVES.get(), b -> createSilkTouchOrShearsDispatchTable(b, applyExplosionCondition(b,
                    LootItem.lootTableItem(ModItems.DREAM_DUST.get())).when(net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition.randomChance(0.05f))));
            add(ModBlocks.MIRROR_WATER.get(), b -> createSilkTouchOnlyTable(b));
            for (var b : ModBlocks.biomeBlocks()) dropSelf(b.get());
            add(ModBlocks.LANTERN_STAND.get(), LootTable.lootTable()
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(ModBlocks.LANTERN_STAND.get())))
                    .withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(ModItems.SKY_LANTERN.get()))
                            .when(LootItemBlockStatePropertyCondition.hasBlockStateProperties(ModBlocks.LANTERN_STAND.get())
                                    .setProperties(net.minecraft.advancements.critereon.StatePropertiesPredicate.Builder.properties()
                                            .hasProperty(com.seba.reverie.dreamland.block.LanternStandBlock.LANTERN, true)))));
        }

        @Override
        protected Iterable<Block> getKnownBlocks() {
            return ModBlocks.BLOCKS.getEntries().stream().map(DeferredHolder::get).map(b -> (Block) b).toList();
        }
    }
}
