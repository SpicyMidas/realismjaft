package com.seba.reverie.client.fauna;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seba.reverie.Reverie;
import com.seba.reverie.fauna.LucidMoth;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** A camera-facing moth sprite (two wing frames) inside a soft additive glow. */
public class LucidMothRenderer extends EntityRenderer<LucidMoth> {
    private static final ResourceLocation TEXTURE = Reverie.id("textures/entity/lucid_moth.png");

    public LucidMothRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public void render(LucidMoth moth, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, 0.15, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        float t = moth.tickCount + partial;
        float pulse = 0.75f + 0.25f * Mth.sin(t * 0.21f + moth.getId());
        // The texture is three 16x16 cells: wings up, wings down, glow.
        quad(pose, buffers.getBuffer(RenderType.eyes(TEXTURE)), 0.55f, 2, pulse);
        int frame = ((int) (t / 2)) % 2;
        quad(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)), 0.22f, frame, 1f);
        pose.popPose();
        super.render(moth, yaw, partial, pose, buffers, light);
    }

    private static void quad(PoseStack pose, VertexConsumer vc, float half, int cell, float brightness) {
        var p = pose.last();
        float u0 = cell / 3f;
        float u1 = (cell + 1) / 3f;
        int c = Mth.floor(brightness * 255);
        int light = LightTexture.FULL_BRIGHT;
        vc.addVertex(p, -half, -half, 0).setColor(c, c, c, 255).setUv(u0, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
        vc.addVertex(p, half, -half, 0).setColor(c, c, c, 255).setUv(u1, 1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
        vc.addVertex(p, half, half, 0).setColor(c, c, c, 255).setUv(u1, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
        vc.addVertex(p, -half, half, 0).setColor(c, c, c, 255).setUv(u0, 0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, 0, 1, 0);
    }

    @Override
    public ResourceLocation getTextureLocation(LucidMoth moth) {
        return TEXTURE;
    }
}
