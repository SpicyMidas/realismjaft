package dev.agentbuddy.world;

import dev.agentbuddy.brain.tool.ItemCount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Reading storage containers for the agent. Chests (incl. trapped), barrels and shulker boxes count; a double
 * chest is one container at its canonical (lower) position. Containers with an unopened loot table are reported
 * as unknown and never read, because reading them would generate their loot. Only loaded chunks are scanned.
 */
public final class Containers {
    private Containers() {
    }

    /** @param items sorted by count, largest first; empty when {@code lootUnknown} */
    public record Info(BlockPos pos, String typeId, List<ItemCount> items, boolean lootUnknown) {
        public int count(String itemId) {
            return items.stream().filter(i -> i.id().equals(itemId)).mapToInt(ItemCount::count).sum();
        }
    }

    public static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity;
    }

    /** The position a container is known by: for a double chest, the lower of its two halves. */
    public static BlockPos canonical(ServerLevel level, BlockPos pos) {
        BlockPos other = otherHalf(level, pos);
        return other != null && other.compareTo(pos) < 0 ? other : pos;
    }

    private static BlockPos otherHalf(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof ChestBlock) || s.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return null;
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(s));
        BlockState os = level.getBlockState(other);
        return os.is(s.getBlock()) && os.getValue(ChestBlock.TYPE) != ChestType.SINGLE ? other : null;
    }

    /** The block entities making up the container at pos (two for a double chest). Empty if not storage. */
    private static List<BlockEntity> parts(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!isStorage(be)) return List.of();
        BlockPos other = otherHalf(level, pos);
        BlockEntity obe = other == null ? null : level.getBlockEntity(other);
        return obe != null && isStorage(obe) ? List.of(be, obe) : List.of(be);
    }

    private static boolean lootUnknown(List<BlockEntity> parts) {
        return parts.stream().anyMatch(p -> p instanceof RandomizableContainerBlockEntity r && r.getLootTable() != null);
    }

    public static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** The container at pos (either half of a double chest), or null if there is no storage container there. */
    public static Info read(ServerLevel level, BlockPos pos) {
        BlockPos c = canonical(level, pos);
        List<BlockEntity> parts = parts(level, c);
        if (parts.isEmpty()) return null;
        String type = BuiltInRegistries.BLOCK.getKey(level.getBlockState(c).getBlock()).toString();
        if (lootUnknown(parts)) return new Info(c, type, List.of(), true);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BlockEntity p : parts) {
            Container ct = (Container) p;
            for (int i = 0; i < ct.getContainerSize(); i++) {
                ItemStack s = ct.getItem(i);
                if (!s.isEmpty()) counts.merge(itemId(s), s.getCount(), Integer::sum);
            }
        }
        List<ItemCount> items = new ArrayList<>();
        counts.forEach((id, n) -> items.add(new ItemCount(id, n)));
        items.sort(Comparator.comparingInt(ItemCount::count).reversed());
        return new Info(c, type, items, false);
    }

    /** Copies of every slot (empty slots included), for the snapshot screen. Empty for unknown loot. */
    public static List<ItemStack> stacks(ServerLevel level, BlockPos pos) {
        List<BlockEntity> parts = parts(level, canonical(level, pos));
        List<ItemStack> out = new ArrayList<>();
        if (lootUnknown(parts)) return out;
        for (BlockEntity p : parts) {
            Container ct = (Container) p;
            for (int i = 0; i < ct.getContainerSize(); i++) out.add(ct.getItem(i).copy());
        }
        return out;
    }

    /** Storage containers in loaded chunks within range of center, nearest first, skipping ignored ones. */
    public static List<Info> near(ServerLevel level, Vec3 center, double range, Predicate<BlockPos> ignored) {
        List<Info> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        int minCx = ((int) Math.floor(center.x - range)) >> 4, maxCx = ((int) Math.floor(center.x + range)) >> 4;
        int minCz = ((int) Math.floor(center.z - range)) >> 4, maxCz = ((int) Math.floor(center.z + range)) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!isStorage(be) || Vec3.atCenterOf(be.getBlockPos()).distanceTo(center) > range) continue;
                    BlockPos c = canonical(level, be.getBlockPos());
                    if (!seen.add(c) || ignored.test(c)) continue;
                    Info info = read(level, c);
                    if (info != null) out.add(info);
                }
            }
        }
        out.sort(Comparator.comparingDouble(i -> Vec3.atCenterOf(i.pos()).distanceTo(center)));
        return out;
    }

    /** Takes up to count of item out of the container at pos. The real stacks move, so names and enchantments are kept. */
    public static List<ItemStack> withdraw(ServerLevel level, BlockPos pos, Item item, int count) {
        List<BlockEntity> parts = parts(level, canonical(level, pos));
        List<ItemStack> taken = new ArrayList<>();
        if (lootUnknown(parts)) return taken;
        int left = count;
        for (BlockEntity p : parts) {
            Container ct = (Container) p;
            for (int i = 0; i < ct.getContainerSize() && left > 0; i++) {
                ItemStack s = ct.getItem(i);
                if (s.isEmpty() || !s.is(item)) continue;
                ItemStack removed = ct.removeItem(i, Math.min(left, s.getCount()));
                left -= removed.getCount();
                taken.add(removed);
            }
            ct.setChanged();
        }
        return taken;
    }
}
