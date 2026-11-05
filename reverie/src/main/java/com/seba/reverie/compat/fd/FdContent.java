package com.seba.reverie.compat.fd;

import java.util.List;
import java.util.function.Supplier;

import com.seba.reverie.Reverie;
import com.seba.reverie.registry.ModEffects;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import vectorwing.farmersdelight.common.block.PieBlock;
import vectorwing.farmersdelight.common.item.ConsumableItem;
import vectorwing.farmersdelight.common.item.DrinkableItem;
import vectorwing.farmersdelight.common.item.PlaceableItem;
import vectorwing.farmersdelight.common.registry.ModItems;

/** Farmer's Delight dishes. They do not exist at all without Farmer's Delight. */
public final class FdContent {
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Reverie.MOD_ID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Reverie.MOD_ID);

    private static Supplier<MobEffectInstance> dizzy(int ticks) {
        return () -> new MobEffectInstance(ModEffects.DIZZINESS, ticks);
    }

    static final FoodProperties LULLABY_STEW_FOOD = new FoodProperties.Builder().nutrition(12).saturationModifier(0.8f)
            .effect(() -> new MobEffectInstance(vectorwing.farmersdelight.common.registry.ModEffects.COMFORT, 6000), 1f)
            .effect(dizzy(1200), 1f).build();
    static final FoodProperties DOZING_DUMPLINGS_FOOD = new FoodProperties.Builder().nutrition(8).saturationModifier(0.9f)
            .effect(dizzy(800), 1f).build();
    static final FoodProperties DREAMY_PIE_SLICE_FOOD = new FoodProperties.Builder().nutrition(3).saturationModifier(0.3f).fast()
            .effect(dizzy(400), 1f).build();
    static final FoodProperties NIGHT_BLOOM_ROLL_FOOD = new FoodProperties.Builder().nutrition(7).saturationModifier(0.6f)
            .effect(dizzy(320), 0.6f).build();
    static final FoodProperties MELATONIA_SYRUP_FOOD = new FoodProperties.Builder().nutrition(2).saturationModifier(0.2f).alwaysEdible()
            .effect(dizzy(600), 1f).build();

    public static final DeferredItem<Item> DREAMY_PIE_SLICE = ITEMS.register("dreamy_pie_slice",
            () -> new ConsumableItem(ModItems.foodItem(DREAMY_PIE_SLICE_FOOD), true));
    public static final DeferredBlock<PieBlock> DREAMY_PIE = BLOCKS.register("dreamy_pie",
            () -> new PieBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CAKE), DREAMY_PIE_SLICE::get));
    public static final DeferredItem<Item> DREAMY_PIE_ITEM = ITEMS.register("dreamy_pie",
            () -> new PlaceableItem(DREAMY_PIE.get(), ModItems.basicItem()));
    public static final DeferredItem<Item> LULLABY_STEW = ITEMS.register("lullaby_stew",
            () -> new ConsumableItem(ModItems.bowlFoodItem(LULLABY_STEW_FOOD), true));
    public static final DeferredItem<Item> DOZING_DUMPLINGS = ITEMS.register("dozing_dumplings",
            () -> new ConsumableItem(ModItems.foodItem(DOZING_DUMPLINGS_FOOD), true));
    public static final DeferredItem<Item> NIGHT_BLOOM_ROLL = ITEMS.register("night_bloom_roll",
            () -> new ConsumableItem(ModItems.foodItem(NIGHT_BLOOM_ROLL_FOOD), true));
    /** A sweetener; also a lacing agent (reverie:lacing_agents) as strong as a normal Extract. */
    public static final DeferredItem<Item> MELATONIA_SYRUP = ITEMS.register("melatonia_syrup",
            () -> new DrinkableItem(ModItems.drinkItem().food(MELATONIA_SYRUP_FOOD), true));

    /** Creative tab order. */
    static final List<DeferredItem<Item>> TAB = List.of(LULLABY_STEW, DOZING_DUMPLINGS, DREAMY_PIE_ITEM, DREAMY_PIE_SLICE, NIGHT_BLOOM_ROLL,
            MELATONIA_SYRUP);

    private FdContent() {
    }
}
