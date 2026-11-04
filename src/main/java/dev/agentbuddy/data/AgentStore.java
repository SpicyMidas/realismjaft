package dev.agentbuddy.data;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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

    public static final class Record {
        public final UUID owner;
        private String name;
        // True while an entity for this record exists in the world; blocks deploying a duplicated item.
        private boolean deployed;
        // Blocks this agent must not perceive (canonical position; a double chest is stored once).
        private final Set<GlobalPos> ignored = new LinkedHashSet<>();

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
    }

    private final Map<UUID, Record> records = new HashMap<>();

    public static AgentStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Record get(UUID agentId) {
        return records.get(agentId);
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
            s.records.put(c.getUUID("Id"), r);
        }
        return s;
    }
}
