package com.seba.reverie.registry;

import com.seba.reverie.Reverie;
import com.seba.reverie.content.melatonia.DreamFoodItem;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Reverie.MOD_ID);

    public static final DeferredItem<Item> TUFF_ALLOY = ITEMS.registerSimpleItem("tuff_alloy", new Item.Properties());

    // --- Melatonia
    public static final DeferredItem<ItemNameBlockItem> MELATONIA_SEEDS = ITEMS.register("melatonia_seeds",
            () -> new ItemNameBlockItem(ModBlocks.MELATONIA.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> WILD_MELATONIA = ITEMS.registerSimpleBlockItem(ModBlocks.WILD_MELATONIA);
    public static final DeferredItem<Item> MELATONIA_PETALS = ITEMS.registerSimpleItem("melatonia_petals", new Item.Properties());

    public static final DeferredItem<DreamFoodItem> MELATONIA_EXTRACT = ITEMS.register("melatonia_extract",
            () -> new DreamFoodItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(1).saturationModifier(0.1f).alwaysEdible().usingConvertsTo(Items.GLASS_BOTTLE)
                    .effect(() -> new MobEffectInstance(ModEffects.DIZZINESS, 400), 1f).build()), true));
    public static final DeferredItem<DreamFoodItem> CONCENTRATED_MELATONIA_EXTRACT = ITEMS.register("concentrated_melatonia_extract",
            () -> new DreamFoodItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(1).saturationModifier(0.1f).alwaysEdible().usingConvertsTo(Items.GLASS_BOTTLE)
                    .effect(() -> new MobEffectInstance(ModEffects.SLEEPYNESS, 4800), 1f)
                    .effect(() -> new MobEffectInstance(ModEffects.DIZZINESS, 200), 1f).build()), true));

    public static final DeferredItem<DreamFoodItem> MELATONIA_COOKIE = ITEMS.register("melatonia_cookie",
            () -> new DreamFoodItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(2).saturationModifier(0.1f)
                    .effect(() -> new MobEffectInstance(ModEffects.DIZZINESS, 160), 0.6f).build()), false));
    public static final DeferredItem<DreamFoodItem> DROWSY_TEA = ITEMS.register("drowsy_tea",
            () -> new DreamFoodItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(3).saturationModifier(0.4f).alwaysEdible().usingConvertsTo(Items.GLASS_BOTTLE)
                    .effect(() -> new MobEffectInstance(ModEffects.DIZZINESS, 240), 1f).build()), true));
    public static final DeferredItem<DreamFoodItem> SLEEPY_STEW = ITEMS.register("sleepy_stew",
            () -> new DreamFoodItem(new Item.Properties().stacksTo(1).food(new FoodProperties.Builder()
                    .nutrition(7).saturationModifier(0.8f).usingConvertsTo(Items.BOWL)
                    .effect(() -> new MobEffectInstance(ModEffects.DIZZINESS, 300), 1f).build()), false));

    // --- Dreamland
    public static final DeferredItem<Item> LUCID_SHARD = ITEMS.registerSimpleItem("lucid_shard", new Item.Properties());
    public static final DeferredItem<Item> DREAM_DUST = ITEMS.registerSimpleItem("dream_dust", new Item.Properties());
    public static final java.util.List<DeferredItem<? extends Item>> DREAM_BLOCK_ITEMS = new java.util.ArrayList<>();

    static {
        for (var block : new net.neoforged.neoforge.registries.DeferredBlock<?>[]{
                ModBlocks.DREAM_GRASS_BLOCK, ModBlocks.DREAM_SOIL, ModBlocks.LUCID_STONE, ModBlocks.COBBLED_LUCID_STONE,
                ModBlocks.LUCID_STONE_BRICKS, ModBlocks.DREAMWOOD_LOG, ModBlocks.DREAMWOOD_PLANKS, ModBlocks.DREAMWOOD_LEAVES,
                ModBlocks.LUCID_CRYSTAL_ORE, ModBlocks.LUCID_CRYSTAL_CLUSTER, ModBlocks.PILLOW_CLOUD, ModBlocks.MIRROR_WATER,
                ModBlocks.HOURGLASS_SAND, ModBlocks.FLOATING_LANTERN, ModBlocks.MELTED_CLOCK, ModBlocks.EYEBLOOM,
                ModBlocks.SLEEP_BELL, ModBlocks.SANDGLASS_CORE}) {
            DREAM_BLOCK_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
        }
    }

    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> LONE_DOOR = ITEMS.register("lone_door",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.LONE_DOOR.get(), new Item.Properties()));

    private ModItems() {
    }
}
