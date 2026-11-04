package com.seba.reverie.registry;

import com.seba.reverie.Reverie;
import com.seba.reverie.content.melatonia.MelatoniaCropBlock;
import com.seba.reverie.content.melatonia.WildMelatoniaBlock;

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

    private ModBlocks() {
    }
}
