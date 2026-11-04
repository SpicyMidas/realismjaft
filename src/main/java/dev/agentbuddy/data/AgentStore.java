package dev.agentbuddy.data;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-side agent records, keyed by agent id (AGENT_SPEC.md section 6). The agent item and the entity only carry
 * the id; everything else lives here, in the overworld's data storage ({@code data/agentbuddy_agents.dat}).
 */
public final class AgentStore extends SavedData {
    private static final String FILE = "agentbuddy_agents";
    private static final SavedData.Factory<AgentStore> FACTORY = new SavedData.Factory<>(AgentStore::new, AgentStore::load);

    public static final int MAX_WAYSTONES = 5;

    /** A saved teleport target: where the player stood, facing {@code yaw}. */
    public record Waystone(String name, GlobalPos pos, float yaw) {
    }

    public enum AddResult { OK, EXISTS, LIMIT }

    public static final class Record {
        public final UUID owner;
        private String name;
        // True while an entity for this record exists in the world; blocks deploying a duplicated item.
        private boolean deployed;
        // Blocks this agent must not perceive (canonical position; a double chest is stored once).
        private final Set<GlobalPos> ignored = new LinkedHashSet<>();
        // Keyed by lower-case name; the Waystone keeps the name as typed.
        private final Map<String, Waystone> waystones = new LinkedHashMap<>();

        Record(UUID owner, String name, boolean deployed) {
            this.owner = owner;
            this.name = name;
            this.deployed = deployed;
        }

        public String name() {
            return name;
        }

        public boolean deployed() {
            return deployed;
        }

        public Set<GlobalPos> ignored() {
            return Set.copyOf(ignored);
        }

        public boolean isIgnored(GlobalPos pos) {
            return ignored.contains(pos);
        }

        public Optional<Waystone> waystone(String name) {
            return Optional.ofNullable(waystones.get(key(name)));
        }

        public java.util.List<Waystone> waystones() {
            return java.util.List.copyOf(waystones.values());
        }
    }

    private static String key(String name) {
        return name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public AddResult addWaystone(UUID agentId, Waystone w) {
        Record r = records.get(agentId);
        if (r == null) return AddResult.LIMIT;
        if (r.waystones.containsKey(key(w.name()))) return AddResult.EXISTS;
        if (r.waystones.size() >= MAX_WAYSTONES) return AddResult.LIMIT;
        r.waystones.put(key(w.name()), w);
        setDirty();
        return AddResult.OK;
    }

    /** Returns the removed waystone, if there was one with that name. */
    public Optional<Waystone> removeWaystone(UUID agentId, String name) {
        Record r = records.get(agentId);
        Waystone w = r == null ? null : r.waystones.remove(key(name));
        if (w != null) setDirty();
        return Optional.ofNullable(w);
    }

    private final Map<UUID, Record> records = new HashMap<>();

    public static AgentStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Record get(UUID agentId) {
        return records.get(agentId);
    }

    /** Some agent id owned by this player, or null (used when no agent entity is loaded nearby). */
    public UUID anyOwnedBy(UUID owner) {
        return records.entrySet().stream().filter(e -> e.getValue().owner.equals(owner)).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    public Record create(UUID agentId, UUID owner) {
        Record r = new Record(owner, "Agent", false);
        records.put(agentId, r);
        setDirty();
        return r;
    }

    public void setDeployed(UUID agentId, boolean deployed) {
        Record r = records.get(agentId);
        if (r != null && r.deployed != deployed) {
            r.deployed = deployed;
            setDirty();
        }
    }

    /** Returns false if it was already ignored. */
    public boolean ignore(UUID agentId, GlobalPos pos) {
        Record r = records.get(agentId);
        if (r == null || !r.ignored.add(pos)) return false;
        setDirty();
        return true;
    }

    /** Returns false if it was not ignored. */
    public boolean unignore(UUID agentId, GlobalPos pos) {
        Record r = records.get(agentId);
        if (r == null || !r.ignored.remove(pos)) return false;
        setDirty();
        return true;
    }

    public void setName(UUID agentId, String name) {
        Record r = records.get(agentId);
        if (r != null) {
            r.name = name;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Record> e : records.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", e.getKey());
            t.putUUID("Owner", e.getValue().owner);
            t.putString("Name", e.getValue().name);
            t.putBoolean("Deployed", e.getValue().deployed);
            ListTag ignored = new ListTag();
            for (GlobalPos g : e.getValue().ignored) {
                CompoundTag it = new CompoundTag();
                it.putString("Dim", g.dimension().location().toString());
                it.putLong("Pos", g.pos().asLong());
                ignored.add(it);
            }
            t.put("Ignored", ignored);
            ListTag ways = new ListTag();
            for (Waystone w : e.getValue().waystones.values()) {
                CompoundTag wt = new CompoundTag();
                wt.putString("Name", w.name());
                wt.putString("Dim", w.pos().dimension().location().toString());
                wt.putLong("Pos", w.pos().pos().asLong());
                wt.putFloat("Yaw", w.yaw());
                ways.add(wt);
            }
            t.put("Waystones", ways);
            list.add(t);
        }
        tag.put("Agents", list);
        return tag;
    }

    public static AgentStore load(CompoundTag tag, HolderLookup.Provider registries) {
        AgentStore s = new AgentStore();
        for (Tag t : tag.getList("Agents", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Record r = new Record(c.getUUID("Owner"), c.getString("Name"), c.getBoolean("Deployed"));
            for (Tag it : c.getList("Ignored", Tag.TAG_COMPOUND)) {
                CompoundTag ic = (CompoundTag) it;
                ResourceKey<net.minecraft.world.level.Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(ic.getString("Dim")));
                r.ignored.add(GlobalPos.of(dim, BlockPos.of(ic.getLong("Pos"))));
            }
            for (Tag wt : c.getList("Waystones", Tag.TAG_COMPOUND)) {
                CompoundTag w = (CompoundTag) wt;
                ResourceKey<net.minecraft.world.level.Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(w.getString("Dim")));
                Waystone way = new Waystone(w.getString("Name"), GlobalPos.of(dim, BlockPos.of(w.getLong("Pos"))), w.getFloat("Yaw"));
                r.waystones.put(key(way.name()), way);
            }
            s.records.put(c.getUUID("Id"), r);
        }
        return s;
    }
}
