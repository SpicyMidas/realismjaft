package com.seba.reverie.client.glider;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.seba.reverie.Reverie;
import com.seba.reverie.registry.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Gliding with the Paper Glider: the player holds it high with both arms (third person: {@link GliderArmPose}; first
 * person: both arms drawn reaching up into view), and the 3D glider (models/item/paper_glider_3d.json) is drawn
 * overhead. The held item itself switches to an empty model while gliding (item property reverie:gliding).
 */
public final class GliderClient {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(Reverie.id("item/paper_glider_3d"));
    /** The model is 16 px wide in its file; drawn this much bigger. */
    private static final float SCALE = 1.6f;
    /** Height of the glider's wings above the feet (blocks), just over the raised hands. */
    private static final float WING_HEIGHT = 2.12f;

    private static final RandomSource RANDOM = RandomSource.create();

    private GliderClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((ModelEvent.RegisterAdditional e) -> e.register(MODEL));
        modBus.addListener((RegisterClientExtensionsEvent e) -> e.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return gliding(entity, stack) ? GliderArmPose.PROXY.getValue() : null;
            }
        }, ModItems.PAPER_GLIDER.get()));
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> ItemProperties.register(ModItems.PAPER_GLIDER.get(),
                Reverie.id("gliding"), (stack, level, entity, seed) -> entity != null && gliding(entity, stack) ? 1f : 0f)));
        NeoForge.EVENT_BUS.addListener(GliderClient::thirdPerson);
        NeoForge.EVENT_BUS.addListener(GliderClient::firstPerson);
    }

    static boolean gliding(LivingEntity entity, ItemStack stack) {
        return entity.isUsingItem() && entity.getUseItem() == stack && stack.is(ModItems.PAPER_GLIDER.get());
    }

    private static boolean gliding(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItem().is(ModItems.PAPER_GLIDER.get());
    }

    private static void thirdPerson(RenderPlayerEvent.Post event) {
        var player = event.getEntity();
        if (!gliding(player)) return;
        float pt = event.getPartialTick();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(0, WING_HEIGHT, 0);
        ps.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot)));
        ps.mulPose(Axis.XP.rotationDegrees(-5f + 2f * Mth.sin((player.tickCount + pt) * 0.21f)));   // nose a touch down, bobbing
        drawGlider(ps, event.getMultiBufferSource(), event.getPackedLight(), SCALE);
        ps.popPose();
    }

    private static void firstPerson(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || !gliding(player)) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        PoseStack ps = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float t = player.tickCount + event.getPartialTick();
        if (mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer) {
            for (int side = -1; side <= 1; side += 2) {
                ps.pushPose();
                // From below the view edge, reaching up and a little forward to the glider.
                // From below the bottom corners straight up, hands just under the glider.
                ps.translate(side * 0.5f, -0.3f + 0.01f * Mth.sin(t * 0.21f + side), -0.72f);
                ps.mulPose(Axis.ZP.rotationDegrees(side * -10f));
                ps.mulPose(Axis.XP.rotationDegrees(-8f));
                ps.translate(side * 5f / 16f, -2f / 16f, 0);    // undo the model's shoulder pivot
                if (side > 0) renderer.renderRightHand(ps, buffers, light, player);
                else renderer.renderLeftHand(ps, buffers, light, player);
                ps.popPose();
            }
        }
        ps.pushPose();
        // Overhead, smaller than in third person: its underside spans the top of the view.
        ps.translate(0, 0.33f + 0.01f * Mth.sin(t * 0.21f), -0.5f);
        ps.mulPose(Axis.XP.rotationDegrees(-10f));
        drawGlider(ps, buffers, light, 0.9f);
        ps.popPose();
    }

    /** The glider model, centred on the pose origin with its wings at the origin's height, nose toward -z. */
    private static void drawGlider(PoseStack ps, MultiBufferSource buffers, int light, float scale) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);
        ps.pushPose();
        ps.scale(scale, scale, scale);
        ps.translate(-0.5f, -1f / 16f, -0.5f);
        // Block-style shading (no entity light direction), so the underside you look at stays paper-white.
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        PoseStack.Pose pose = ps.last();
        for (Direction dir : Direction.values()) {
            RANDOM.setSeed(42L);
            put(pose, vc, model.getQuads(null, dir, RANDOM, ModelData.EMPTY, null), light);
        }
        RANDOM.setSeed(42L);
        put(pose, vc, model.getQuads(null, null, RANDOM, ModelData.EMPTY, null), light);
        ps.popPose();
    }

    private static void put(PoseStack.Pose pose, VertexConsumer vc, List<BakedQuad> quads, int light) {
        for (BakedQuad quad : quads) {
            float shade = switch (quad.getDirection()) {
                case UP -> 1f;
                case DOWN -> 0.82f;
                case NORTH, SOUTH -> 0.85f;
                default -> 0.7f;
            };
            vc.putBulkData(pose, quad, shade, shade, shade, 1f, light, OverlayTexture.NO_OVERLAY);
        }
    }
}
