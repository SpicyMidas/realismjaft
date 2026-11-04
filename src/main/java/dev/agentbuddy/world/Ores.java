package dev.agentbuddy.world;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Nearest ore of a type within a radius, in loaded chunks only (no x-ray beyond what the server has loaded).
 * Matches vanilla ore tags and the common {@code c:ores/*} tags, so modded ores count too. Sections whose palette
 * cannot contain the ore are skipped, which keeps a 64-block scan cheap.
 */
public final class Ores {
    private static final Map<String, List<TagKey<Block>>> TAGS = Map.of(
            "coal", List.of(BlockTags.COAL_ORES, Tags.Blocks.ORES_COAL),
            "iron", List.of(BlockTags.IRON_ORES, Tags.Blocks.ORES_IRON),
            "copper", List.of(BlockTags.COPPER_ORES, Tags.Blocks.ORES_COPPER),
            "gold", List.of(BlockTags.GOLD_ORES, Tags.Blocks.ORES_GOLD),
            "redstone", List.of(BlockTags.REDSTONE_ORES, Tags.Blocks.ORES_REDSTONE),
            "lapis", List.of(BlockTags.LAPIS_ORES, Tags.Blocks.ORES_LAPIS),
            "diamond", List.of(BlockTags.DIAMOND_ORES, Tags.Blocks.ORES_DIAMOND),
            "emerald", List.of(BlockTags.EMERALD_ORES, Tags.Blocks.ORES_EMERALD),
            "quartz", List.of(Tags.Blocks.ORES_QUARTZ),
            "ancient_debris", List.of(Tags.Blocks.ORES_NETHERITE_SCRAP));

    private Ores() {
    }

    public static BlockPos nearest(ServerLevel level, Vec3 center, String ore, double range, Predicate<BlockPos> ignored) {
        List<TagKey<Block>> tags = TAGS.get(ore);
        if (tags == null) return null;
        Predicate<BlockState> isOre = s -> tags.stream().anyMatch(s::is);
        double rangeSqr = range * range;
        BlockPos best = null;
        double bestSqr = Double.MAX_VALUE;
        int minCx = ((int) Math.floor(center.x - range)) >> 4, maxCx = ((int) Math.floor(center.x + range)) >> 4;
        int minCz = ((int) Math.floor(center.z - range)) >> 4, maxCz = ((int) Math.floor(center.z + range)) >> 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(isOre)) continue;
                    int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
                    if (Math.abs(baseY + 8 - center.y) > range + 14) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (!isOre.test(section.getBlockState(x, y, z))) continue;
                                pos.set((cx << 4) + x, baseY + y, (cz << 4) + z);
                                double d = Vec3.atCenterOf(pos).distanceToSqr(center);
                                if (d < bestSqr && d <= rangeSqr && !ignored.test(pos)) {
                                    bestSqr = d;
                                    best = pos.immutable();
                                }
                            }
                        }
                    }
                }
            }
        }
        return best;
    }
}
