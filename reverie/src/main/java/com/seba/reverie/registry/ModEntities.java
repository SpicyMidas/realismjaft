package com.seba.reverie.registry;

import com.seba.reverie.Reverie;
import com.seba.reverie.shade.ShadeEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Reverie.MOD_ID);

    // noSave: never written to disk; noSummon: only the crowd manager makes them.
    public static final DeferredHolder<EntityType<?>, EntityType<ShadeEntity>> SHADE = ENTITIES.register("shade",
            () -> EntityType.Builder.<ShadeEntity>of(ShadeEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build("shade"));

    private ModEntities() {
    }
}
