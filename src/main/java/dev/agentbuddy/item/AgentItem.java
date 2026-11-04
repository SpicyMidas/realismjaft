package dev.agentbuddy.item;

import dev.agentbuddy.ModRegistries;
import dev.agentbuddy.data.AgentStore;
import dev.agentbuddy.entity.AgentEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The agent in item form. A fresh item (crafted, creative) creates a new agent owned by whoever places it; an item
 * picked up from an agent carries its id and restores that same agent.
 */
public class AgentItem extends Item {
    public AgentItem(Properties properties) {
        super(properties);
    }

    public static ItemStack stackFor(UUID agentId) {
        ItemStack stack = new ItemStack(ModRegistries.AGENT_ITEM.get());
        stack.set(ModRegistries.AGENT_ID.get(), agentId);
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        AgentStore store = AgentStore.get(level.getServer());

        UUID id = stack.get(ModRegistries.AGENT_ID.get());
        AgentStore.Record record;
        if (id == null) {
            id = UUID.randomUUID();
            record = store.create(id, player.getUUID());
        } else {
            record = store.get(id);
            // Item from another world/save: this server has no record, so it starts fresh with the same id.
            if (record == null) record = store.create(id, player.getUUID());
        }
        if (!record.owner.equals(player.getUUID())) {
            player.displayClientMessage(Component.literal("Not your agent."), true);
            return InteractionResult.FAIL;
        }
        if (record.deployed()) {
            player.displayClientMessage(Component.literal("Agent is already out."), true);
            return InteractionResult.FAIL;
        }

        BlockPos at = context.getClickedPos().relative(context.getClickedFace());
        AgentEntity agent = ModRegistries.AGENT.get().create(level, null, at, MobSpawnType.SPAWN_EGG, false, false);
        if (agent == null) return InteractionResult.FAIL;
        agent.moveTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
        agent.bind(id, record.owner, record.name());
        level.addFreshEntity(agent);
        store.setDeployed(id, true);
        stack.shrink(1); // also in creative: one item is one agent
        return InteractionResult.CONSUME;
    }
}
