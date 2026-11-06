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
    public static final DeferredHolder<EntityType<?>, EntityType<com.seba.reverie.fauna.SleepwalkingSheep>> SLEEPWALKING_SHEEP =
            ENTITIES.register("sleepwalking_sheep", () -> EntityType.Builder.<com.seba.reverie.fauna.SleepwalkingSheep>of(
                            com.seba.reverie.fauna.SleepwalkingSheep::new, MobCategory.CREATURE)
                    .sized(0.9f, 1.3f).eyeHeight(1.235f).clientTrackingRange(10).build("sleepwalking_sheep"));
    // Lucid Moths are called by Floating Lanterns and never saved.
    public static final DeferredHolder<EntityType<?>, EntityType<com.seba.reverie.fauna.LucidMoth>> LUCID_MOTH =
            ENTITIES.register("lucid_moth", () -> EntityType.Builder.<com.seba.reverie.fauna.LucidMoth>of(
                            com.seba.reverie.fauna.LucidMoth::new, MobCategory.AMBIENT)
                    .sized(0.35f, 0.35f).noSave().clientTrackingRange(6).build("lucid_moth"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.seba.reverie.content.melatonia.LacedSnowball>> LACED_SNOWBALL =
            ENTITIES.register("laced_snowball", () -> EntityType.Builder.<com.seba.reverie.content.melatonia.LacedSnowball>of(
                            com.seba.reverie.content.melatonia.LacedSnowball::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10).build("laced_snowball"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.seba.reverie.content.melatonia.LacedArrow>> LACED_ARROW =
            ENTITIES.register("laced_arrow", () -> EntityType.Builder.<com.seba.reverie.content.melatonia.LacedArrow>of(
                            com.seba.reverie.content.melatonia.LacedArrow::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).eyeHeight(0.13f).clientTrackingRange(4).updateInterval(20).build("laced_arrow"));

    /** Attributes and spawn rules (mod bus). */
    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(SLEEPWALKING_SHEEP.get(), com.seba.reverie.fauna.SleepwalkingSheep.createAttributes().build());
            e.put(LUCID_MOTH.get(), com.seba.reverie.fauna.LucidMoth.createAttributes().build());
        });
        modBus.addListener((net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent e) -> e.register(SLEEPWALKING_SHEEP.get(),
                net.minecraft.world.entity.SpawnPlacementTypes.ON_GROUND, net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> level.getBlockState(pos.below()).isSolid(),
                net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation.REPLACE));
    }

    private ModEntities() {
    }
}
