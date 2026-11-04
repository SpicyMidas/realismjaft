package dev.agentbuddy;

import dev.agentbuddy.entity.AgentEntity;
import dev.agentbuddy.item.AgentItem;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistries {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, AgentBuddy.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AgentBuddy.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AgentBuddy.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<AgentEntity>> AGENT = ENTITY_TYPES.register("agent",
            () -> EntityType.Builder.of(AgentEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.0F)
                    .eyeHeight(0.75F)
                    .clientTrackingRange(10)
                    .build("agent"));

    /** The only thing the agent item stores: the id of the agent record in {@link dev.agentbuddy.data.AgentStore}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> AGENT_ID = COMPONENTS.registerComponentType("agent_id",
            b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    public static final DeferredItem<AgentItem> AGENT_ITEM = ITEMS.register("agent",
            () -> new AgentItem(new Item.Properties().stacksTo(1)));

    private ModRegistries() {
    }

    static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
    }
}
