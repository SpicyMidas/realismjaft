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
                    .effect(() -> new MobEffectInstance(ModEffects.DIZZINESS, 800), 1f).build()), true));
    public static final DeferredItem<DreamFoodItem> CONCENTRATED_MELATONIA_EXTRACT = ITEMS.register("concentrated_melatonia_extract",
            () -> new DreamFoodItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(1).saturationModifier(0.1f).alwaysEdible().usingConvertsTo(Items.GLASS_BOTTLE)
                    .effect(() -> new MobEffectInstance(ModEffects.SLEEPYNESS, 4800), 1f)
                    .effect(() -> new MobEffectInstance(ModEffects.DIZZINESS, 400), 1f).build()), true));

    public static final DeferredItem<DreamFoodItem> MELATONIA_COOKIE = ITEMS.register("melatonia_cookie",
            () -> new DreamFoodItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(2).saturationModifier(0.1f)
                    .effect(() -> new MobEffectInstance(ModEffects.DIZZINESS, 320), 0.6f).build()), false));
    public static final DeferredItem<DreamFoodItem> DROWSY_TEA = ITEMS.register("drowsy_tea",
            () -> new DreamFoodItem(new Item.Properties().stacksTo(16).food(new FoodProperties.Builder()
                    .nutrition(3).saturationModifier(0.4f).alwaysEdible().usingConvertsTo(Items.GLASS_BOTTLE)
                    .effect(() -> new MobEffectInstance(ModEffects.DIZZINESS, 480), 1f).build()), true));
    public static final DeferredItem<DreamFoodItem> SLEEPY_STEW = ITEMS.register("sleepy_stew",
            () -> new DreamFoodItem(new Item.Properties().stacksTo(1).food(new FoodProperties.Builder()
                    .nutrition(7).saturationModifier(0.8f).usingConvertsTo(Items.BOWL)
                    .effect(() -> new MobEffectInstance(ModEffects.DIZZINESS, 600), 1f).build()), false));

    public static final DeferredItem<BlockItem> MELATONIA_CAKE = ITEMS.register("melatonia_cake",
            () -> new BlockItem(ModBlocks.MELATONIA_CAKE.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredItem<DreamFoodItem> MELATONIA_CAKE_SLICE = ITEMS.register("melatonia_cake_slice",
            () -> new DreamFoodItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(com.seba.reverie.content.melatonia.MelatoniaCakeBlock.NUTRITION)
                    .saturationModifier(com.seba.reverie.content.melatonia.MelatoniaCakeBlock.SATURATION).fast()
                    .effect(() -> new MobEffectInstance(ModEffects.DIZZINESS, com.seba.reverie.content.melatonia.MelatoniaCakeBlock.DIZZY_TICKS), 1f)
                    .build()), false));

    /** Eight snowballs or arrows around one Extract; the reverie:laced component carries the strength. */
    public static final DeferredItem<com.seba.reverie.content.melatonia.LacedSnowballItem> LACED_SNOWBALL = ITEMS.register("laced_snowball",
            () -> new com.seba.reverie.content.melatonia.LacedSnowballItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<com.seba.reverie.content.melatonia.LacedArrowItem> LACED_ARROW = ITEMS.register("laced_arrow",
            () -> new com.seba.reverie.content.melatonia.LacedArrowItem(new Item.Properties()));

    // --- Dreamland
    public static final DeferredItem<Item> LUCID_SHARD = ITEMS.registerSimpleItem("lucid_shard", new Item.Properties());
    public static final DeferredItem<Item> DREAM_DUST = ITEMS.registerSimpleItem("dream_dust", new Item.Properties());
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> SLEEPWALKING_SHEEP_SPAWN_EGG = ITEMS.register(
            "sleepwalking_sheep_spawn_egg", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.SLEEPWALKING_SHEEP,
                    0xE8E4F4, 0x9B8BD6, new Item.Properties()));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> LUCID_MOTH_SPAWN_EGG = ITEMS.register(
            "lucid_moth_spawn_egg", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.LUCID_MOTH,
                    0x2E2A55, 0xBFEFFF, new Item.Properties()));
    public static final DeferredItem<com.seba.reverie.content.PaperGliderItem> PAPER_GLIDER = ITEMS.register("paper_glider",
            () -> new com.seba.reverie.content.PaperGliderItem(new Item.Properties().durability(300)));
    public static final DeferredItem<com.seba.reverie.content.SkyLanternItem> SKY_LANTERN = ITEMS.register("sky_lantern",
            () -> new com.seba.reverie.content.SkyLanternItem(new Item.Properties().durability(240)));
    /** Uncraftable: 1% from spiders killed in Trial Chambers (data/reverie/loot_modifiers). */
    public static final DeferredItem<Item> GOLDEN_STRING = ITEMS.registerSimpleItem("golden_string",
            new Item.Properties().rarity(net.minecraft.world.item.Rarity.RARE));
    public static final DeferredItem<com.seba.reverie.content.DreamCatcherItem> DREAM_CATCHER = ITEMS.register("dream_catcher",
            () -> new com.seba.reverie.content.DreamCatcherItem(new Item.Properties().stacksTo(1)));
    public static final java.util.List<DeferredItem<? extends Item>> DREAM_BLOCK_ITEMS = new java.util.ArrayList<>();

    static {
        for (var block : new net.neoforged.neoforge.registries.DeferredBlock<?>[]{
                ModBlocks.DREAM_GRASS_BLOCK, ModBlocks.DREAM_SOIL, ModBlocks.LUCID_STONE, ModBlocks.COBBLED_LUCID_STONE,
                ModBlocks.LUCID_STONE_BRICKS, ModBlocks.DREAMWOOD_LOG, ModBlocks.DREAMWOOD_PLANKS, ModBlocks.DREAMWOOD_LEAVES,
                ModBlocks.LUCID_CRYSTAL_ORE, ModBlocks.LUCID_CRYSTAL_CLUSTER, ModBlocks.PILLOW_CLOUD, ModBlocks.MIRROR_WATER,
                ModBlocks.HOURGLASS_SAND, ModBlocks.FLOATING_LANTERN, ModBlocks.MELTED_CLOCK, ModBlocks.EYEBLOOM,
                ModBlocks.SLEEP_BELL, ModBlocks.SANDGLASS_CORE}) {
            if (block == ModBlocks.FLOATING_LANTERN) {
                DREAM_BLOCK_ITEMS.add(ITEMS.register("floating_lantern",
                        () -> new com.seba.reverie.content.FloatingLanternItem(ModBlocks.FLOATING_LANTERN.get(), new Item.Properties())));
                continue;
            }
            DREAM_BLOCK_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
        }
        for (var block : ModBlocks.biomeBlocks()) DREAM_BLOCK_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
        DREAM_BLOCK_ITEMS.add(ITEMS.registerSimpleBlockItem(ModBlocks.LANTERN_STAND));
    }

    public static final DeferredItem<net.minecraft.world.item.DoubleHighBlockItem> LONE_DOOR = ITEMS.register("lone_door",
            () -> new net.minecraft.world.item.DoubleHighBlockItem(ModBlocks.LONE_DOOR.get(), new Item.Properties()));

    private ModItems() {
    }
}
