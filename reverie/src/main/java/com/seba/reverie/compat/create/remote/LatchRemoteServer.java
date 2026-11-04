package com.seba.reverie.compat.create.remote;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps one {@link RemoteLink} registered for every ON Latch Remote carried by an online player. Remotes are
 * keyed by the UUID in their item data, so moving or copying a stack never registers twice; a remote that is
 * no longer carried (dropped, stored, player dead, logged out, dreaming) is unregistered within ~20 ticks.
 */
public final class LatchRemoteServer {
    private static final int SCAN_INTERVAL = 20;
    /** Links not seen by any scan for this long are dropped (covers players that vanished without an event). */
    private static final int GRACE_TICKS = 40;

    private static final Map<UUID, RemoteLink> LINKS = new HashMap<>();
    private static long tick;

    private LatchRemoteServer() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(LatchRemoteServer::onTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) dropOwner(p.server, p.getUUID());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) refresh(p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) refresh(p);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> clear(e.getServer()));
    }

    public static int activeLinks() {
        return LINKS.size();
    }

    public static boolean isTransmitting(UUID remoteId) {
        RemoteLink link = LINKS.get(remoteId);
        return link != null && link.alive;
    }

    private static void onTick(ServerTickEvent.Post event) {
        tick++;
        if (tick % SCAN_INTERVAL != 0) return;
        MinecraftServer server = event.getServer();
        try {
            Set<UUID> claimed = new HashSet<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) scan(player, claimed);
            List<RemoteLink> stale = new ArrayList<>();
            for (RemoteLink link : LINKS.values()) if (tick - link.lastSeenTick > GRACE_TICKS) stale.add(link);
            for (RemoteLink link : stale) unregister(server, link);
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Latch Remote scan failed", e);
        }
    }

    /** Immediately re-scan one player (after a toggle, a dimension change or a respawn). */
    public static void refresh(ServerPlayer player) {
        try {
            scan(player, new HashSet<>());
        } catch (RuntimeException e) {
            Reverie.LOGGER.error("Latch Remote refresh failed for {}", player.getName().getString(), e);
        }
    }

    private static void scan(ServerPlayer player, Set<UUID> claimed) {
        MinecraftServer server = player.server;
        UUID owner = player.getUUID();
        Set<UUID> found = new HashSet<>();
        if (player.isAlive() && !player.isSpectator()) {
            Inventory inv = player.getInventory();
            boolean deep = ReverieConfig.get(ReverieConfig.TRANSMIT_WHILE_IN_CONTAINERS);
            for (int i = 0; i < inv.getContainerSize(); i++) visit(player, inv.getItem(i), deep, found, claimed);
            visit(player, player.containerMenu.getCarried(), deep, found, claimed);
        }
        // Unregister this player's links that are no longer carried.
        List<RemoteLink> gone = new ArrayList<>();
        for (RemoteLink link : LINKS.values()) if (link.owner.equals(owner) && !found.contains(link.remoteId)) gone.add(link);
        for (RemoteLink link : gone) unregister(server, link);
    }

    private static void visit(ServerPlayer player, ItemStack stack, boolean deep, Set<UUID> found, Set<UUID> claimed) {
        if (stack.isEmpty()) return;
        if (stack.getItem() instanceof LatchRemoteItem) {
            if (LatchRemoteItem.isActive(stack)) carry(player, stack, found, claimed);
            return;
        }
        if (!deep) return;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            for (ItemStack inner : contents.nonEmptyItems()) {
                if (inner.getItem() instanceof LatchRemoteItem && LatchRemoteItem.isActive(inner)) {
                    UUID id = LatchRemoteItem.remoteId(inner);
                    if (id != null && claimed.add(id)) carryId(player, id, inner, found);
                }
            }
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack inner : bundle.items()) {
                if (inner.getItem() instanceof LatchRemoteItem && LatchRemoteItem.isActive(inner)) {
                    UUID id = LatchRemoteItem.remoteId(inner);
                    if (id != null && claimed.add(id)) carryId(player, id, inner, found);
                }
            }
        }
    }

    private static void carry(ServerPlayer player, ItemStack stack, Set<UUID> found, Set<UUID> claimed) {
        UUID id = LatchRemoteItem.remoteId(stack);
        if (id == null || !claimed.add(id) && !found.contains(id)) {
            // Missing id, or a duplicate (e.g. creative copy) already carried by someone: give it its own identity.
            id = LatchRemoteItem.assignNewId(stack);
            claimed.add(id);
        }
        carryId(player, id, stack, found);
    }

    private static void carryId(ServerPlayer player, UUID id, ItemStack stack, Set<UUID> found) {
        if (!found.add(id)) return;
        Couple<Frequency> key = LatchRemoteItem.networkKey(stack);
        ServerLevel level = player.serverLevel();
        RemoteLink link = LINKS.get(id);
        if (link != null && (!link.owner.equals(player.getUUID()) || link.dimension != level.dimension() || !link.key.equals(key))) {
            unregister(player.server, link);
            link = null;
        }
        if (link == null) {
            link = new RemoteLink(id, player.getUUID(), level.dimension(), key, player.blockPosition(), tick);
            LINKS.put(id, link);
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, link);
            return;
        }
        link.lastSeenTick = tick;
        if (!link.pos.equals(player.blockPosition())) {
            // Create only recomputes receivers in range of the actor: leave at the old spot (so receivers there
            // drop us), then rejoin at the new one.
            Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, link);
            link.pos = player.blockPosition();
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, link);
        }
    }

    private static void unregister(MinecraftServer server, RemoteLink link) {
        LINKS.remove(link.remoteId);
        link.alive = false;
        ServerLevel level = server.getLevel(link.dimension);
        if (level != null) Create.REDSTONE_LINK_NETWORK_HANDLER.removeFromNetwork(level, link);
    }

    private static void dropOwner(MinecraftServer server, UUID owner) {
        List<RemoteLink> gone = new ArrayList<>();
        for (RemoteLink link : LINKS.values()) if (link.owner.equals(owner)) gone.add(link);
        for (RemoteLink link : gone) unregister(server, link);
    }

    private static void clear(MinecraftServer server) {
        for (RemoteLink link : new ArrayList<>(LINKS.values())) {
            try {
                unregister(server, link);
            } catch (RuntimeException ignored) {
                // Worlds may already be unloading; Create drops its network maps with them.
            }
        }
        LINKS.clear();
        tick = 0;
    }
}
