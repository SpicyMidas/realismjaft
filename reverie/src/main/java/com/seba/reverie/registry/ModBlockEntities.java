package com.seba.reverie.registry;

import com.seba.reverie.Reverie;
import com.seba.reverie.dreamland.block.DecorBlockEntity;
import com.seba.reverie.dreamland.block.ResonatorBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Reverie.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DecorBlockEntity>> DECOR = BLOCK_ENTITIES.register("decor",
            () -> BlockEntityType.Builder.of(DecorBlockEntity::new, ModBlocks.FLOATING_LANTERN.get(), ModBlocks.MELTED_CLOCK.get(),
                    ModBlocks.EYEBLOOM.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResonatorBlockEntity>> RESONATOR = BLOCK_ENTITIES.register("resonator",
            () -> BlockEntityType.Builder.of(ResonatorBlockEntity::new, ModBlocks.SLEEP_BELL.get(), ModBlocks.SANDGLASS_CORE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<net.minecraft.world.level.block.entity.BlockEntity>> IMPOSSIBLE_STAIRS =
            BLOCK_ENTITIES.register("impossible_stairs", () -> BlockEntityType.Builder.<net.minecraft.world.level.block.entity.BlockEntity>of(
                    (pos, state) -> ModBlocks.IMPOSSIBLE_STAIRS.get().newBlockEntity(pos, state), ModBlocks.IMPOSSIBLE_STAIRS.get()).build(null));

    private ModBlockEntities() {
    }
}
