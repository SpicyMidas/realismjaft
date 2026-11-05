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

    private ModBlocks() {
    }
}
