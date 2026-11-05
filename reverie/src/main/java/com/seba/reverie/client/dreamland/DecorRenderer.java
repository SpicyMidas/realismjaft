package com.seba.reverie.client.dreamland;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.seba.reverie.Reverie;
import com.seba.reverie.dreamland.block.DecorBlockEntity;
import com.seba.reverie.dreamland.block.MeltedClockBlock;
import com.seba.reverie.registry.ModBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/** Floating lantern bobbing, melted clock hands turning (wrongly), eyebloom pupils following the nearest player. */
public class DecorRenderer implements BlockEntityRenderer<DecorBlockEntity> {
    public static final ModelResourceLocation CLOCK_HANDS = ModelResourceLocation.standalone(Reverie.id("block/melted_clock_hands"));
    public static final ModelResourceLocation PUPIL = ModelResourceLocation.standalone(Reverie.id("block/eyebloom_pupil"));
    private static boolean failed;

    public DecorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    @Override
    public void render(DecorBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffers, int light, int overlay) {
        if (failed || be.getLevel() == null) return;
        try {
            BlockState state = be.getBlockState();
            float time = be.getLevel().getGameTime() + pt;
            var models = Minecraft.getInstance().getModelManager();
            var blocks = Minecraft.getInstance().getBlockRenderer();
            if (state.is(ModBlocks.FLOATING_LANTERN.get())) {
                float phase = (be.getBlockPos().asLong() % 97) * 0.37f;
                ms.pushPose();
                ms.translate(0, Math.sin(time * 0.05 + phase) * 0.09, 0);
                ms.translate(0.5, 0, 0.5);
                ms.mulPose(Axis.YP.rotationDegrees((float) Math.sin(time * 0.02 + phase) * 8f));
                ms.translate(-0.5, 0, -0.5);
                render(ms, buffers, state, blocks.getBlockModel(state), light, overlay);
                ms.popPose();
            } else if (state.is(ModBlocks.MELTED_CLOCK.get())) {
                BakedModel hands = models.getModel(CLOCK_HANDS);
                ms.pushPose();
                ms.translate(0.5, 0, 0.5);
                ms.mulPose(Axis.YP.rotationDegrees(-state.getValue(MeltedClockBlock.FACING).toYRot() + 180));
                // Hands turn slowly and backwards.
                ms.mulPose(Axis.YP.rotationDegrees(time * 0.9f));
                ms.translate(-0.5, 0, -0.5);
                render(ms, buffers, state, hands, light, overlay);
                ms.popPose();
            } else if (state.is(ModBlocks.EYEBLOOM.get())) {
                Player p = be.getLevel().getNearestPlayer(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 0.6,
                        be.getBlockPos().getZ() + 0.5, 24, false);
                double ox = 0;
                double oz = 0;
                if (p != null) {
                    double dx = p.getX() - (be.getBlockPos().getX() + 0.5);
                    double dz = p.getZ() - (be.getBlockPos().getZ() + 0.5);
                    double len = Math.sqrt(dx * dx + dz * dz);
                    if (len > 0.01) {
                        ox = dx / len * 1.6 / 16;
                        oz = dz / len * 1.6 / 16;
                    }
                }
                ms.pushPose();
                ms.translate(ox, 0, oz);
                render(ms, buffers, state, models.getModel(PUPIL), light, overlay);
                ms.popPose();
            }
        } catch (RuntimeException e) {
            failed = true;
            Reverie.LOGGER.error("Dream decor rendering failed; disabling it", e);
        }
    }

    private static void render(PoseStack ms, MultiBufferSource buffers, BlockState state, BakedModel model, int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ms.last(), buffers.getBuffer(RenderType.cutout()),
                state, model, 1f, 1f, 1f, light, overlay, ModelData.EMPTY, RenderType.cutout());
    }
}
