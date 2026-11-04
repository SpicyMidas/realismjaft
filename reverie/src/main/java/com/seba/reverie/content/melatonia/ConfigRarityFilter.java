package com.seba.reverie.content.melatonia;

import com.mojang.serialization.MapCodec;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.registry.ModWorldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** Rarity filter whose "1 in N" comes from the config (wildMelatoniaFrequency; 0 disables). */
public class ConfigRarityFilter extends PlacementFilter {
    public static final ConfigRarityFilter INSTANCE = new ConfigRarityFilter();
    public static final MapCodec<ConfigRarityFilter> CODEC = MapCodec.unit(() -> INSTANCE);

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos) {
        int n = ReverieConfig.get(ReverieConfig.WILD_MELATONIA_FREQUENCY);
        return n > 0 && random.nextInt(n) == 0;
    }

    @Override
    public PlacementModifierType<?> type() {
        return ModWorldgen.CONFIG_RARITY.get();
    }
}
