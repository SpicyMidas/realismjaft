package com.seba.reverie.registry;

import com.seba.reverie.Reverie;
import com.seba.reverie.content.melatonia.MelatoniaCropBlock;
import com.seba.reverie.content.melatonia.WildMelatoniaBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Reverie.MOD_ID);

    public static final DeferredBlock<MelatoniaCropBlock> MELATONIA = BLOCKS.register("melatonia",
            () -> new MelatoniaCropBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BEETROOTS).mapColor(MapColor.COLOR_PURPLE)));
    public static final DeferredBlock<com.seba.reverie.content.melatonia.MelatoniaCakeBlock> MELATONIA_CAKE = BLOCKS.register("melatonia_cake",
            () -> new com.seba.reverie.content.melatonia.MelatoniaCakeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CAKE).mapColor(MapColor.COLOR_PURPLE),
                    () -> ModItems.MELATONIA_CAKE_SLICE.get()));
    public static final DeferredBlock<WildMelatoniaBlock> WILD_MELATONIA = BLOCKS.register("wild_melatonia",
            () -> new WildMelatoniaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollission()
                    .instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ)
                    .pushReaction(PushReaction.DESTROY).lightLevel(s -> 3)));

    // --- Dreamland
    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5f, 6f)
                .sound(SoundType.CALCITE).requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(2f, 3f).sound(SoundType.CHERRY_WOOD).ignitedByLava();
    }

    public static final DeferredBlock<Block> DREAM_GRASS_BLOCK = BLOCKS.register("dream_grass_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.6f).sound(SoundType.GRASS)));
    public static final DeferredBlock<Block> DREAM_SOIL = BLOCKS.register("dream_soil",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.5f).sound(SoundType.ROOTED_DIRT)));
    public static final DeferredBlock<Block> LUCID_STONE = BLOCKS.register("lucid_stone", () -> new Block(stone()));
    public static final DeferredBlock<Block> COBBLED_LUCID_STONE = BLOCKS.register("cobbled_lucid_stone", () -> new Block(stone().strength(2f, 6f)));
    public static final DeferredBlock<Block> LUCID_STONE_BRICKS = BLOCKS.register("lucid_stone_bricks", () -> new Block(stone()));
    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> DREAMWOOD_LOG = BLOCKS.register("dreamwood_log",
            () -> new net.minecraft.world.level.block.RotatedPillarBlock(wood()));
    public static final DeferredBlock<Block> DREAMWOOD_PLANKS = BLOCKS.register("dreamwood_planks", () -> new Block(wood()));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.DreamLeavesBlock> DREAMWOOD_LEAVES = BLOCKS.register("dreamwood_leaves",
            () -> new com.seba.reverie.dreamland.block.DreamLeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_LEAVES)
                    .mapColor(MapColor.COLOR_PINK)));
    public static final DeferredBlock<net.minecraft.world.level.block.DropExperienceBlock> LUCID_CRYSTAL_ORE = BLOCKS.register("lucid_crystal_ore",
            () -> new net.minecraft.world.level.block.DropExperienceBlock(net.minecraft.util.valueproviders.UniformInt.of(2, 5),
                    stone().lightLevel(s -> 4)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.CrystalClusterBlock> LUCID_CRYSTAL_CLUSTER = BLOCKS.register("lucid_crystal_cluster",
            () -> new com.seba.reverie.dreamland.block.CrystalClusterBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                    .mapColor(MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 6)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.PillowCloudBlock> PILLOW_CLOUD = BLOCKS.register("pillow_cloud",
            () -> new com.seba.reverie.dreamland.block.PillowCloudBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                    .strength(0.3f).sound(SoundType.WOOL).noOcclusion().isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.MirrorWaterBlock> MIRROR_WATER = BLOCKS.register("mirror_water",
            () -> new com.seba.reverie.dreamland.block.MirrorWaterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.2f).sound(SoundType.GLASS).lightLevel(s -> 3)));
    public static final DeferredBlock<Block> HOURGLASS_SAND = BLOCKS.register("hourglass_sand",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5f).sound(SoundType.SAND)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.FloatingLanternBlock> FLOATING_LANTERN = BLOCKS.register("floating_lantern",
            () -> new com.seba.reverie.dreamland.block.FloatingLanternBlock(BlockBehaviour.Properties.of().strength(0.6f)
                    .sound(SoundType.LANTERN).lightLevel(s -> 15).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.MeltedClockBlock> MELTED_CLOCK = BLOCKS.register("melted_clock",
            () -> new com.seba.reverie.dreamland.block.MeltedClockBlock(BlockBehaviour.Properties.of().strength(0.8f)
                    .sound(SoundType.METAL).noOcclusion().mapColor(MapColor.GOLD)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.EyebloomBlock> EYEBLOOM = BLOCKS.register("eyebloom",
            () -> new com.seba.reverie.dreamland.block.EyebloomBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK)
                    .noCollission().instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.ResonatorBlock> SLEEP_BELL = BLOCKS.register("sleep_bell",
            () -> new com.seba.reverie.dreamland.block.ResonatorBlock(BlockBehaviour.Properties.of().strength(1.5f)
                    .sound(SoundType.METAL).noOcclusion().mapColor(MapColor.GOLD)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.ResonatorBlock> SANDGLASS_CORE = BLOCKS.register("sandglass_core",
            () -> new com.seba.reverie.dreamland.block.ResonatorBlock(BlockBehaviour.Properties.of().strength(0.8f)
                    .sound(SoundType.GLASS).noOcclusion().lightLevel(s -> 6).mapColor(MapColor.SAND)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.LoneDoorBlock> LONE_DOOR = BLOCKS.register("lone_door",
            () -> new com.seba.reverie.dreamland.block.LoneDoorBlock(BlockBehaviour.Properties.of().strength(2f)
                    .sound(SoundType.CHERRY_WOOD).noOcclusion().lightLevel(s -> 7).mapColor(MapColor.COLOR_MAGENTA)));
    /** Anchor of the Impossible Stairs (invisible, no shape, no item; worldgen only). */
    public static final DeferredBlock<com.seba.reverie.dreamland.block.ImpossibleStairsBlock> IMPOSSIBLE_STAIRS = BLOCKS.register("impossible_stairs",
            () -> new com.seba.reverie.dreamland.block.ImpossibleStairsBlock(BlockBehaviour.Properties.of().strength(-1f, 3600000f)
                    .noCollission().noOcclusion().noLootTable()));

    // --- Dreamland: the lucid biomes
    public static final DeferredBlock<Block> DREAMSNOW = BLOCKS.register("dreamsnow",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.4f).sound(SoundType.SNOW)));
    public static final DeferredBlock<Block> RIME_STONE = BLOCKS.register("rime_stone", () -> new Block(stone().mapColor(MapColor.ICE)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.StarlightIceBlock> STARLIGHT_ICE = BLOCKS.register("starlight_ice",
            () -> new com.seba.reverie.dreamland.block.StarlightIceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.6f)
                    .friction(0.993f).sound(SoundType.GLASS).noOcclusion().lightLevel(s -> 5).isValidSpawn((s, l, p, e) -> false)
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.DreamPlantBlock> FROSTBLOOM = BLOCKS.register("frostbloom",
            () -> new com.seba.reverie.dreamland.block.DreamPlantBlock(plant(MapColor.ICE).lightLevel(s -> 9)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.HangingPlantBlock> ICE_CHIME = BLOCKS.register("ice_chime",
            () -> new com.seba.reverie.dreamland.block.HangingPlantBlock(plant(MapColor.ICE).sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 4),
                    net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 70));
    public static final DeferredBlock<Block> REEF_STONE = BLOCKS.register("reef_stone",
            () -> new Block(stone().mapColor(MapColor.SAND).sound(SoundType.CORAL_BLOCK)));
    public static final DeferredBlock<Block> TIDE_CORAL = BLOCKS.register("tide_coral",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1.2f).sound(SoundType.CORAL_BLOCK)));
    public static final DeferredBlock<Block> DUSK_CORAL = BLOCKS.register("dusk_coral",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(1.2f).sound(SoundType.CORAL_BLOCK)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.DreamPlantBlock> SKY_CORAL = BLOCKS.register("sky_coral",
            () -> new com.seba.reverie.dreamland.block.DreamPlantBlock(plant(MapColor.COLOR_PINK).sound(SoundType.WET_GRASS).lightLevel(s -> 2)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.HangingPlantBlock> SKY_KELP = BLOCKS.register("sky_kelp",
            () -> new com.seba.reverie.dreamland.block.HangingPlantBlock(plant(MapColor.COLOR_GREEN).sound(SoundType.WET_GRASS), null, 1));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.BubbleVentBlock> BUBBLE_VENT = BLOCKS.register("bubble_vent",
            () -> new com.seba.reverie.dreamland.block.BubbleVentBlock(stone().mapColor(MapColor.SAND).sound(SoundType.CORAL_BLOCK).lightLevel(s -> 3)));
    public static final DeferredBlock<Block> GEODE_SHELL = BLOCKS.register("geode_shell",
            () -> new Block(stone().mapColor(MapColor.COLOR_BLACK).strength(2f, 6f).sound(SoundType.DEEPSLATE)));
    public static final DeferredBlock<Block> LILAC_CALCITE = BLOCKS.register("lilac_calcite",
            () -> new Block(stone().mapColor(MapColor.COLOR_PINK).sound(SoundType.CALCITE)));
    public static final DeferredBlock<Block> VIOLET_MOSS = BLOCKS.register("violet_moss",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.4f).sound(SoundType.MOSS).lightLevel(s -> 2)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.ResonantCrystalBlock> RESONANT_CRYSTAL = BLOCKS.register("resonant_crystal",
            () -> new com.seba.reverie.dreamland.block.ResonantCrystalBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                    .strength(1.5f).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()
                    .lightLevel(com.seba.reverie.dreamland.block.ResonantCrystalBlock::light)));
    public static final DeferredBlock<Block> TOY_BLOCK_RED = toyBlock("toy_block_red", MapColor.COLOR_RED);
    public static final DeferredBlock<Block> TOY_BLOCK_YELLOW = toyBlock("toy_block_yellow", MapColor.COLOR_YELLOW);
    public static final DeferredBlock<Block> TOY_BLOCK_BLUE = toyBlock("toy_block_blue", MapColor.COLOR_BLUE);
    public static final DeferredBlock<Block> TOY_BLOCK_GREEN = toyBlock("toy_block_green", MapColor.COLOR_GREEN);
    public static final DeferredBlock<Block> NURSERY_RUG = BLOCKS.register("nursery_rug",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.6f).sound(SoundType.WOOL)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.StuffingBlock> STUFFING = BLOCKS.register("stuffing",
            () -> new com.seba.reverie.dreamland.block.StuffingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.4f).sound(SoundType.WOOL)));
    public static final DeferredBlock<Block> NIGHT_LIGHT = BLOCKS.register("night_light",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5f).sound(SoundType.GLASS).lightLevel(s -> 14)));

    // --- Dreamland: the nightmares
    public static final DeferredBlock<Block> ASHEN_SOIL = BLOCKS.register("ashen_soil",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5f).sound(SoundType.ROOTED_DIRT)));
    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> BLACKWOOD_LOG = BLOCKS.register("blackwood_log",
            () -> new net.minecraft.world.level.block.RotatedPillarBlock(wood().mapColor(MapColor.COLOR_BLACK).sound(SoundType.WOOD)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.DreamPlantBlock> WITHERED_GRASS = BLOCKS.register("withered_grass",
            () -> new com.seba.reverie.dreamland.block.DreamPlantBlock(plant(MapColor.COLOR_BROWN).replaceable()));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.WatchingWindowBlock> WATCHING_WINDOW = BLOCKS.register("watching_window",
            () -> new com.seba.reverie.dreamland.block.WatchingWindowBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1f)
                    .sound(SoundType.WOOD).lightLevel(s -> s.getValue(com.seba.reverie.dreamland.block.WatchingWindowBlock.LIT) ? 11 : 0)));
    public static final DeferredBlock<Block> STALE_CARPET = BLOCKS.register("stale_carpet",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).strength(0.6f).sound(SoundType.WOOL)));
    public static final DeferredBlock<Block> YELLOWED_WALLPAPER = BLOCKS.register("yellowed_wallpaper",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1f).sound(SoundType.WOOD)));
    public static final DeferredBlock<Block> CEILING_TILE = BLOCKS.register("ceiling_tile",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(0.6f).sound(SoundType.CALCITE)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.FlickerLightBlock> FLICKER_LIGHT = BLOCKS.register("flicker_light",
            () -> new com.seba.reverie.dreamland.block.FlickerLightBlock(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(0.5f)
                    .sound(SoundType.GLASS).randomTicks().lightLevel(s -> s.getValue(com.seba.reverie.dreamland.block.FlickerLightBlock.LIT) ? 15 : 0)));
    public static final DeferredBlock<Block> DREAM_FLESH = BLOCKS.register("dream_flesh",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.8f).sound(SoundType.MUD).speedFactor(0.78f)));
    public static final DeferredBlock<Block> SINEW = BLOCKS.register("sinew",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.CRIMSON_HYPHAE).strength(1f).sound(SoundType.WART_BLOCK)));
    public static final DeferredBlock<Block> ENAMEL = BLOCKS.register("enamel", () -> new Block(stone().mapColor(MapColor.QUARTZ).sound(SoundType.BONE_BLOCK)));
    public static final DeferredBlock<com.seba.reverie.dreamland.block.HangingPlantBlock> VEIN_VINES = BLOCKS.register("vein_vines",
            () -> new com.seba.reverie.dreamland.block.HangingPlantBlock(plant(MapColor.COLOR_RED).sound(SoundType.WEEPING_VINES), null, 1));
    public static final DeferredBlock<Block> STATIC_BLOCK = BLOCKS.register("static_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(1f).sound(SoundType.GLASS).lightLevel(s -> 6)));
    public static final DeferredBlock<Block> DEAD_PIXEL = BLOCKS.register("dead_pixel",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1f).sound(SoundType.GLASS)));
    public static final DeferredBlock<Block> TEST_CARD = BLOCKS.register("test_card",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1f).sound(SoundType.GLASS).lightLevel(s -> 4)));

    private static BlockBehaviour.Properties plant(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).noCollission().instabreak().sound(SoundType.GRASS)
                .pushReaction(PushReaction.DESTROY);
    }

    private static DeferredBlock<Block> toyBlock(String name, MapColor color) {
        return BLOCKS.register(name, () -> new Block(BlockBehaviour.Properties.of().mapColor(color).strength(1f).sound(SoundType.WOOD)));
    }

    /** Everything the new biomes added, in creative tab order. */
    public static java.util.List<DeferredBlock<?>> biomeBlocks() {
        return java.util.List.of(DREAMSNOW, RIME_STONE, STARLIGHT_ICE, FROSTBLOOM, ICE_CHIME, REEF_STONE, TIDE_CORAL, DUSK_CORAL, SKY_CORAL,
                SKY_KELP, BUBBLE_VENT, GEODE_SHELL, LILAC_CALCITE, VIOLET_MOSS, RESONANT_CRYSTAL, TOY_BLOCK_RED, TOY_BLOCK_YELLOW,
                TOY_BLOCK_BLUE, TOY_BLOCK_GREEN, NURSERY_RUG, STUFFING, NIGHT_LIGHT, ASHEN_SOIL, BLACKWOOD_LOG, WITHERED_GRASS,
                WATCHING_WINDOW, STALE_CARPET, YELLOWED_WALLPAPER, CEILING_TILE, FLICKER_LIGHT, DREAM_FLESH, SINEW, ENAMEL, VEIN_VINES,
                STATIC_BLOCK, DEAD_PIXEL, TEST_CARD);
    }

    private ModBlocks() {
    }
}
