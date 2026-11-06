package com.seba.reverie.client;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.seba.reverie.registry.ModBlocks;
import com.seba.reverie.registry.ModEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;

/**
 * Lucid Sight (from a laced dish): for a few seconds the stone goes thin and the ores within 16 blocks show through
 * as glowing outlines in their own colours. Fades in over a second and out over the last two.
 */
public final class LucidSightClient {
    private static final int RADIUS = 16;
    private static final int MAX_SHOWN = 700;
    /** Outlines drawn through everything. */
    private static final RenderType THROUGH = RenderType.create("reverie_lucid_sight", DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES, 1536, false, false, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.0)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private record Ore(BlockPos pos, int color) {
    }

    private static final List<Ore> ORES = new ArrayList<>();
    private static int rescan;
    private static int activeTicks;

    private LucidSightClient() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(LucidSightClient::tick);
        NeoForge.EVENT_BUS.addListener(LucidSightClient::render);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.hasEffect(ModEffects.LUCID_SIGHT)) {
            ORES.clear();
            activeTicks = 0;
            return;
        }
        activeTicks++;
        if (--rescan > 0) return;
        rescan = 10;
        ORES.clear();
        BlockPos center = mc.player.blockPosition();
        List<Ore> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-RADIUS, -RADIUS, -RADIUS), center.offset(RADIUS, RADIUS, RADIUS))) {
            BlockState state = mc.level.getBlockState(p);
            if (state.isAir()) continue;
            int color = color(state);
            if (color != 0) found.add(new Ore(p.immutable(), color));
        }
        found.sort((a, b) -> Double.compare(a.pos.distSqr(center), b.pos.distSqr(center)));
        ORES.addAll(found.subList(0, Math.min(MAX_SHOWN, found.size())));
    }

    /** Outline colour for an ore, 0 for anything else. */
    private static int color(BlockState state) {
        if (state.is(ModBlocks.LUCID_CRYSTAL_ORE.get())) return 0xC79BFF;
        if (!state.is(Tags.Blocks.ORES)) return 0;
        if (is(state, Tags.Blocks.ORES_DIAMOND)) return 0x5CF0FF;
        if (is(state, Tags.Blocks.ORES_EMERALD)) return 0x3CFF6E;
        if (is(state, Tags.Blocks.ORES_GOLD)) return 0xFFD84A;
        if (is(state, Tags.Blocks.ORES_REDSTONE)) return 0xFF3A2E;
        if (is(state, Tags.Blocks.ORES_LAPIS)) return 0x3A62FF;
        if (is(state, Tags.Blocks.ORES_IRON)) return 0xE8B894;
        if (is(state, Tags.Blocks.ORES_COPPER)) return 0xF08A4B;
        if (is(state, Tags.Blocks.ORES_COAL)) return 0x8A8A8A;
        if (is(state, Tags.Blocks.ORES_QUARTZ)) return 0xF4EFE6;
        if (is(state, Tags.Blocks.ORES_NETHERITE_SCRAP) || state.is(Blocks.ANCIENT_DEBRIS)) return 0xB06A4A;
        int map = state.getMapColor(null, BlockPos.ZERO).col;
        return map == 0 ? 0xFFFFFF : map;
    }

    private static boolean is(BlockState state, TagKey<Block> tag) {
        return state.is(tag);
    }

    private static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ORES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        var effect = mc.player == null ? null : mc.player.getEffect(ModEffects.LUCID_SIGHT);
        if (effect == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float fade = Math.min(Mth.clamp((activeTicks + pt) / 20f, 0f, 1f), effect.isInfiniteDuration() ? 1f : Mth.clamp(effect.getDuration() / 40f, 0f, 1f));
        float pulse = 0.85f + 0.15f * Mth.sin((mc.player.tickCount + pt) * 0.25f);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(THROUGH);
        for (Ore ore : ORES) {
            double d = Math.sqrt(ore.pos.distToCenterSqr(cam));
            float a = fade * pulse * (1f - 0.6f * (float) Math.min(1, d / (RADIUS + 2)));
            if (a <= 0.02f) continue;
            float r = ((ore.color >> 16) & 255) / 255f;
            float g = ((ore.color >> 8) & 255) / 255f;
            float b = (ore.color & 255) / 255f;
            BlockPos p = ore.pos;
            LevelRenderer.renderLineBox(ps, vc, p.getX() + 0.02, p.getY() + 0.02, p.getZ() + 0.02,
                    p.getX() + 0.98, p.getY() + 0.98, p.getZ() + 0.98, r, g, b, a);
        }
        buffers.endBatch(THROUGH);
        ps.popPose();
    }
}
