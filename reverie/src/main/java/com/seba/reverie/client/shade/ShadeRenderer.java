package com.seba.reverie.client.shade;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.shade.ShadeEntity;
import com.seba.reverie.shade.ShadeLook;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

/**
 * Flat black silhouettes: unlit, a soft translucent halo for the edges, and two pale pinprick eyes only while you
 * look straight at one (config shadeGlow).
 */
public class ShadeRenderer extends EntityRenderer<ShadeEntity> {
    private static final ResourceLocation TEXTURE = Reverie.id("textures/entity/shade.png");
    /** Halo alphas for the inner and outer inflated copies (both faces of each draw, so the rim is a gradient). */
    private static final float[] HALO_ALPHA = {0, 0.2f, 0.09f};

    private final ShadeModel[] models = new ShadeModel[ShadeModel.LAYERS.length];
    private final net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer> mimicWide;
    private final net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer> mimicSlim;

    public ShadeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        for (int i = 0; i < models.length; i++) {
            models[i] = new ShadeModel(ctx.bakeLayer(ShadeModel.humanoidLayer(i)), ctx.bakeLayer(ShadeModel.doorframeLayer(i)));
        }
        this.mimicWide = new net.minecraft.client.model.PlayerModel<>(ctx.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER), false);
        this.mimicSlim = new net.minecraft.client.model.PlayerModel<>(ctx.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM), true);
        this.shadowRadius = 0;
    }

    /** The Mimic: the dreamer's own shape (wide or slim arms), as a silhouette, walking. */
    private void renderMimic(ShadeEntity shade, float alpha, float yaw, float partial, PoseStack pose, MultiBufferSource buffers) {
        boolean slim = false;
        if (shade.level().getEntity(shade.ownerId()) instanceof net.minecraft.client.player.AbstractClientPlayer owner) {
            slim = owner.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        }
        var model = slim ? mimicSlim : mimicWide;
        float swing = shade.walkDist * 2.2f;
        float amount = shade.walkSpeed;
        model.head.resetPose();
        model.rightLeg.xRot = Mth.cos(swing) * 1.2f * amount;
        model.leftLeg.xRot = Mth.cos(swing + Mth.PI) * 1.2f * amount;
        model.rightArm.xRot = Mth.cos(swing + Mth.PI) * 0.9f * amount;
        model.leftArm.xRot = Mth.cos(swing) * 0.9f * amount;
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightPants.copyFrom(model.rightLeg);
        model.leftPants.copyFrom(model.leftLeg);
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501, 0);
        // Drawn with the near-black Shade texture (a skin-sized 64x64 layout): only the outline of the shape is left.
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(Mth.floor(alpha * 255), 255, 255, 255));
        pose.popPose();
    }

    @Override
    public void render(ShadeEntity shade, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float alpha = shade.alpha(partial);
        if (alpha <= 0.02f) return;
        ShadeEntity.Variant variant = shade.variant();
        float t = shade.tickCount + partial;
        boolean reduceMotion = ReverieConfig.get(ReverieConfig.REDUCE_MOTION);
        float yaw = Mth.rotLerp(partial, shade.yRotO, shade.getYRot());
        if (variant == ShadeEntity.Variant.MIMIC) {
            renderMimic(shade, alpha, yaw, partial, pose, buffers);
            return;
        }

        pose.pushPose();
        if (variant == ShadeEntity.Variant.HANGING) {
            float bob = reduceMotion ? 0 : Mth.sin(t * 0.03f + shade.getId()) * 0.12f;
            pose.translate(0, 2.0 + bob, 0);
        }
        pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        if (variant == ShadeEntity.Variant.HANGING) {
            float swing = reduceMotion ? 0 : Mth.sin(t * 0.017f + shade.getId() * 0.7f) * 4f;
            pose.mulPose(Axis.ZP.rotationDegrees(180f + swing));
        }
        if (variant == ShadeEntity.Variant.TALL_ONE) pose.scale(0.7f, 2.3f, 0.7f);
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501, 0);

        ShadeModel model = models[0];
        boolean frame = variant == ShadeEntity.Variant.DOORFRAME;
        if (!frame) model.pose(variant, t, shade.getId(), reduceMotion);
        model.frameEyes.visible = false;

        int body = FastColor.ARGB32.color(Mth.floor(alpha * 255), 255, 255, 255);
        (frame ? model.frame : model.root).render(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, body);

        // Halo: inflated copies, faint and without depth writes, so the edges are soft and the eyes still show.
        for (int i = 1; i < models.length; i++) {
            ShadeModel halo = models[i];
            halo.copyPose(model);
            int color = FastColor.ARGB32.color(Mth.floor(alpha * HALO_ALPHA[i] * 255), 255, 255, 255);
            (frame ? halo.frame : halo.root).render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(TEXTURE)),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        }

        // In a Fog Tide every shade shows its eyes: only silhouettes and dim lights.
        float tide = com.seba.reverie.client.dreamland.DreamAmbienceClient.fogTide();
        if (alpha > 0.6f && ReverieConfig.get(ReverieConfig.SHADE_GLOW) && (tide > 0.5f || ShadeLook.staredAt(shade))) {
            ModelPart owner = model.eyeOwner(variant);
            ModelPart eyes = model.eyesOf(variant);
            pose.pushPose();
            owner.translateAndRotate(pose);
            eyes.visible = true;
            int v = Mth.floor(alpha * 235);
            eyes.render(pose, buffers.getBuffer(RenderType.eyes(TEXTURE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.color(255, v, v, v));
            eyes.visible = false;
            pose.popPose();
        }
        pose.popPose();
    }

    @Override
    protected boolean shouldShowName(ShadeEntity entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(ShadeEntity entity) {
        return TEXTURE;
    }
}
