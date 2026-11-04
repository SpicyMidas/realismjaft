package dev.agentbuddy.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Which death position each player already used death_return for ("once per death"). */
public final class DeathReturnStore extends SavedData {
    private static final String FILE = "agentbuddy_death_return";
    private static final SavedData.Factory<DeathReturnStore> FACTORY = new SavedData.Factory<>(DeathReturnStore::new, DeathReturnStore::load);

    private final Map<UUID, GlobalPos> used = new HashMap<>();

    public static DeathReturnStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public boolean usedFor(UUID player, GlobalPos death) {
        return death.equals(used.get(player));
    }

    public void markUsed(UUID player, GlobalPos death) {
        used.put(player, death);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        used.forEach((player, pos) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Player", player);
            t.putString("Dim", pos.dimension().location().toString());
            t.putLong("Pos", pos.pos().asLong());
            list.add(t);
        });
        tag.put("Used", list);
        return tag;
    }

    public static DeathReturnStore load(CompoundTag tag, HolderLookup.Provider registries) {
        DeathReturnStore s = new DeathReturnStore();
        for (Tag t : tag.getList("Used", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            var dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(c.getString("Dim")));
            s.used.put(c.getUUID("Player"), GlobalPos.of(dim, BlockPos.of(c.getLong("Pos"))));
        }
        return s;
    }
}
