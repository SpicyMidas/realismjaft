package com.seba.reverie.compat.create;

import com.seba.reverie.Reverie;
import com.seba.reverie.compat.create.gearshift.GateLayout;
import com.seba.reverie.compat.create.gearshift.TuffGearshiftBlock;
import com.seba.reverie.compat.create.gearshift.TuffGearshiftBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Create-only registry entries. They do not exist at all without Create. */
public final class CreateContent {
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Reverie.MOD_ID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Reverie.MOD_ID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Reverie.MOD_ID);

    public static final DeferredBlock<TuffGearshiftBlock> TUFF_GEARSHIFT = BLOCKS.register("tuff_gearshift",
            () -> new TuffGearshiftBlock(gearshiftProperties(), GateLayout.REGULAR));
    public static final DeferredBlock<TuffGearshiftBlock> SPORT_TUFF_GEARSHIFT = BLOCKS.register("sport_tuff_gearshift",
            () -> new TuffGearshiftBlock(gearshiftProperties(), GateLayout.SPORT));

    public static final DeferredItem<BlockItem> TUFF_GEARSHIFT_ITEM = ITEMS.registerSimpleBlockItem(TUFF_GEARSHIFT);
    public static final DeferredItem<BlockItem> SPORT_TUFF_GEARSHIFT_ITEM = ITEMS.registerSimpleBlockItem(SPORT_TUFF_GEARSHIFT,
            new Item.Properties());

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TuffGearshiftBlockEntity>> GEARSHIFT_BE =
            BLOCK_ENTITIES.register("tuff_gearshift", () -> BlockEntityType.Builder
                    .of((pos, state) -> new TuffGearshiftBlockEntity(CreateContent.GEARSHIFT_BE.get(), pos, state), TUFF_GEARSHIFT.get(), SPORT_TUFF_GEARSHIFT.get()).build(null));

    private CreateContent() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    private static BlockBehaviour.Properties gearshiftProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.TERRACOTTA_GREEN)
                .strength(1.5f, 6f)
                .sound(SoundType.POLISHED_TUFF)
                .noOcclusion();
    }
}
