package dev.agentbuddy.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
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
            list.add(t);
        }
        tag.put("Agents", list);
        return tag;
    }

    public static AgentStore load(CompoundTag tag, HolderLookup.Provider registries) {
        AgentStore s = new AgentStore();
        for (Tag t : tag.getList("Agents", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            s.records.put(c.getUUID("Id"), new Record(c.getUUID("Owner"), c.getString("Name"), c.getBoolean("Deployed")));
        }
        return s;
    }
}
