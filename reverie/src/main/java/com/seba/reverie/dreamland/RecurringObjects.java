package com.seba.reverie.dreamland;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.dream.DreamManager;
import com.seba.reverie.dream.DreamSession;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The dream keeps returning to one thing: each dream picks an object (a dandelion, a brass key, a paper boat) and
 * leaves it lying in the open once in every biome the dreamer visits. Harmless; like anything picked up in a
 * dream it can, rarely, survive the waking.
 */
public final class RecurringObjects implements DreamManager.Listener {
    private record Thing(net.minecraft.world.item.Item item, String key) {
    }

    private static final List<Thing> THINGS = List.of(new Thing(Items.DANDELION, "dandelion"), new Thing(Items.TRIPWIRE_HOOK, "key"),
            new Thing(Items.PAPER, "boat"));

    private static final class State {
        final Thing thing;
        final Set<ResourceKey<Biome>> seen = new HashSet<>();

        State(Thing thing) {
            this.thing = thing;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static void init() {
        DreamManager.addListener(new RecurringObjects());
        NeoForge.EVENT_BUS.addListener(RecurringObjects::onTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> STATES.clear());
    }

    @Override
    public void ending(ServerPlayer player, DreamSession session, DreamManager.Reason reason) {
        STATES.remove(player.getUUID());
    }

    private static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 40 != 7 || !ReverieConfig.get(ReverieConfig.RECURRING_OBJECTS)) return;
        ServerLevel dream = event.getServer().getLevel(DreamManager.DREAMLAND);
        if (dream == null) return;
        try {
            for (ServerPlayer p : dream.players()) {
                DreamSession session = DreamManager.data(p).session();
                if (session == null) continue;
                State s = STATES.computeIfAbsent(p.getUUID(), k -> new State(THINGS.get((int) Math.floorMod(session.startTime() * 31 + k.hashCode(), THINGS.size()))));
                var biome = dream.getBiome(p.blockPosition()).unwrapKey().orElse(null);
                if (biome == null || biome.equals(DreamlandBiomeKeys.VOID_BETWEEN) || s.seen.contains(biome)) continue;
                if (place(dream, p, s.thing)) s.seen.add(biome);
            }
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Recurring object update failed", e);
        }
    }

    private static boolean place(ServerLevel level, ServerPlayer p, Thing thing) {
        var rng = p.getRandom();
        for (int attempt = 0; attempt < 10; attempt++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double r = 7 + rng.nextDouble() * 11;
            int x = (int) Math.floor(p.getX() + Math.cos(a) * r);
            int z = (int) Math.floor(p.getZ() + Math.sin(a) * r);
            if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (top <= level.getMinBuildHeight() + 4 || Math.abs(top - p.getY()) > 8) continue;
            ItemStack stack = new ItemStack(thing.item());
            stack.set(DataComponents.CUSTOM_NAME, Component.translatable("reverie.recurring." + thing.key()).withStyle(Style.EMPTY.withItalic(false)));
            stack.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("reverie.recurring.lore"))));
            ItemEntity entity = new ItemEntity(level, x + 0.5, top + 0.1, z + 0.5, stack, 0, 0, 0);
            entity.setDefaultPickUpDelay();
            return level.addFreshEntity(entity);
        }
        return false;
    }
}
