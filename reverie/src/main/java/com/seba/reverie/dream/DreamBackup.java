package com.seba.reverie.dream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Safety net for every failure mode, including the mod being removed mid-dream: before anything is taken from
 * the player, the whole session (original inventory first) is written to {@code <world>/reverie_backups/}.
 */
public final class DreamBackup {
    private DreamBackup() {
    }

    public static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("reverie_backups");
    }

    /** Returns the written file, or null if it could not be written (the dream must then not start). */
    public static Path write(ServerPlayer player, DreamSession session, long time) {
        try {
            Path dir = directory(player.server);
            Files.createDirectories(dir);
            var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            Tag encoded = DreamSession.CODEC.encodeStart(ops, session).getOrThrow();
            CompoundTag root = new CompoundTag();
            root.putString("player", player.getUUID().toString());
            root.putString("name", player.getGameProfile().getName());
            root.putLong("time", time);
            root.putString("note", "Original inventory of a Reverie dreamer. session.stash.items: slots 0-35 inventory, 36-39 armor, 40 offhand.");
            root.put("session", encoded);
            Path file = dir.resolve(player.getUUID() + "-" + time + ".nbt");
            Path tmp = dir.resolve(player.getUUID() + "-" + time + ".nbt.tmp");
            NbtIo.writeCompressed(root, tmp);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            prune(dir, player.getUUID());
            return file;
        } catch (Exception e) {
            Reverie.LOGGER.error("Could not write the dream backup for {}; the dream will not start", player.getName().getString(), e);
            return null;
        }
    }

    /** Reads a backup file back (used by tests and by /reverie debug). */
    public static DreamSession read(MinecraftServer server, Path file) throws IOException {
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return DreamSession.CODEC.parse(ops, root.get("session")).getOrThrow();
    }

    public static List<Path> list(MinecraftServer server, UUID player) {
        Path dir = directory(server);
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().startsWith(player + "-") && p.toString().endsWith(".nbt"))
                    .sorted(Comparator.comparingLong(DreamBackup::timeOf)).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static long timeOf(Path p) {
        String name = p.getFileName().toString();
        try {
            return Long.parseLong(name.substring(name.lastIndexOf('-') + 1, name.length() - 4));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static void prune(Path dir, UUID player) throws IOException {
        int keep = ReverieConfig.get(ReverieConfig.BACKUPS_KEPT_PER_PLAYER);
        List<Path> mine;
        try (Stream<Path> files = Files.list(dir)) {
            mine = files.filter(p -> p.getFileName().toString().startsWith(player + "-") && p.toString().endsWith(".nbt"))
                    .sorted(Comparator.comparingLong(DreamBackup::timeOf)).toList();
        }
        for (int i = 0; i < mine.size() - keep; i++) Files.deleteIfExists(mine.get(i));
    }
}
