package dev.agentbuddy.data;

import dev.agentbuddy.brain.tool.Credits;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-player credits (AGENT_SPEC.md section 6), persisted in {@code data/agentbuddy_credits.dat}. Keys are player UUID strings. */
public final class CreditStore extends SavedData {
    private static final String FILE = "agentbuddy_credits";
    private static final SavedData.Factory<CreditStore> FACTORY = new SavedData.Factory<>(CreditStore::new, CreditStore::load);

    private final Credits credits = new Credits(this::setDirty);

    public static CreditStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public Credits credits() {
        return credits;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Integer> e : credits.snapshot().entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putString("Player", e.getKey());
            t.putInt("Credits", e.getValue());
            list.add(t);
        }
        tag.put("Balances", list);
        return tag;
    }

    public static CreditStore load(CompoundTag tag, HolderLookup.Provider registries) {
        CreditStore s = new CreditStore();
        for (Tag t : tag.getList("Balances", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            s.credits.set(c.getString("Player"), c.getInt("Credits"));
        }
        s.setDirty(false);
        return s;
    }
}
