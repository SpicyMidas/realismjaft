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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Rising with the Sky Lantern: the arm holding it goes straight up ({@link LanternArmPose}) and the lit 3D lantern
 * (models/item/sky_lantern_3d.json) is drawn over that hand, glowing; the held item itself draws nothing meanwhile
 * (item property reverie:lifting). In first person the lantern hangs at the top of the view, the hand above it.
 */
public final class SkyLanternClient {
    public static final ModelResourceLocation MODEL = ModelResourceLocation.standalone(Reverie.id("item/sky_lantern_3d"));
    private static final RandomSource RANDOM = RandomSource.create();

    private SkyLanternClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((ModelEvent.RegisterAdditional e) -> e.register(MODEL));
        modBus.addListener((RegisterClientExtensionsEvent e) -> e.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return lifting(entity, stack) ? LanternArmPose.PROXY.getValue() : null;
            }
        }, ModItems.SKY_LANTERN.get()));
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> ItemProperties.register(ModItems.SKY_LANTERN.get(),
                Reverie.id("lifting"), (stack, level, entity, seed) -> entity != null && lifting(entity, stack) ? 1f : 0f)));
        NeoForge.EVENT_BUS.addListener(SkyLanternClient::thirdPerson);
        NeoForge.EVENT_BUS.addListener(SkyLanternClient::firstPerson);
    }

    static boolean lifting(LivingEntity entity, ItemStack stack) {
        return entity.isUsingItem() && entity.getUseItem() == stack && stack.is(ModItems.SKY_LANTERN.get());
    }

    private static boolean lifting(LivingEntity entity) {
        return entity.isUsingItem() && entity.getUseItem().is(ModItems.SKY_LANTERN.get());
    }

    /** +1 when the lantern is in the right hand, -1 in the left. */
    private static int side(LivingEntity entity) {
        HumanoidArm arm = entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
        return arm == HumanoidArm.RIGHT ? 1 : -1;
    }

    private static void thirdPerson(RenderPlayerEvent.Post event) {
        var player = event.getEntity();
        if (!lifting(player)) return;
        float pt = event.getPartialTick();
        float t = player.tickCount + pt;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot)));
        // Over the raised fist (body space: +x is the player's right, -z is forward).
        ps.translate(side(player) * 0.37f, 2.45f, 0.02f);
        ps.mulPose(Axis.ZP.rotationDegrees(3f * Mth.sin(t * 0.17f)));
        ps.mulPose(Axis.XP.rotationDegrees(2.5f * Mth.cos(t * 0.13f)));
        draw(ps, event.getMultiBufferSource(), 1f);
        ps.popPose();
    }

    private static void firstPerson(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || !lifting(player) || event.getHand() != player.getUsedItemHand()) return;
        event.setCanceled(true);
        PoseStack ps = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int side = side(player);
        float t = player.tickCount + event.getPartialTick();
        // Held high overhead: your hand is above the view, the lantern hangs at the top of it, swaying.
        ps.pushPose();
        ps.translate(side * 0.3f, 0.2f + 0.012f * Mth.sin(t * 0.17f), -0.9f);
        ps.mulPose(Axis.ZP.rotationDegrees(side * -5f + 2f * Mth.sin(t * 0.13f)));
        draw(ps, buffers, 0.42f);
        ps.popPose();
    }

    /** The lantern, its bottom centre at the origin (the stick hangs below), lit from inside: always full bright. */
    private static void draw(PoseStack ps, MultiBufferSource buffers, float scale) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(MODEL);
        ps.pushPose();
        ps.scale(scale, scale, scale);
        ps.translate(-0.5f, 0, -0.5f);
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        PoseStack.Pose pose = ps.last();
        for (Direction dir : Direction.values()) {
            RANDOM.setSeed(42L);
            put(pose, vc, model.getQuads(null, dir, RANDOM, ModelData.EMPTY, null));
        }
        RANDOM.setSeed(42L);
        put(pose, vc, model.getQuads(null, null, RANDOM, ModelData.EMPTY, null));
        ps.popPose();
    }

    private static void put(PoseStack.Pose pose, VertexConsumer vc, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            float shade = quad.getDirection() == Direction.UP ? 1f : quad.getDirection() == Direction.DOWN ? 0.9f : 0.95f;
            vc.putBulkData(pose, quad, shade, shade, shade, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }
}
