package com.seba.reverie.compat.create.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.seba.reverie.Reverie;
import com.seba.reverie.ReverieConfig;
import com.seba.reverie.compat.create.gearshift.GateLayout;
import com.seba.reverie.compat.create.gearshift.GearstickAnimator;
import com.seba.reverie.compat.create.gearshift.TuffGearshiftBlock;
import com.seba.reverie.compat.create.gearshift.TuffGearshiftBlockEntity;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Spinning shaft halves (same approach as Create's split shaft) plus the animated gearstick: a plain rod and knob,
 * one baked model posed from {@link GearstickAnimator} in the shared {@link GateLayout}.
 */
public class TuffGearshiftRenderer extends KineticBlockEntityRenderer<TuffGearshiftBlockEntity> {
    public static final ModelResourceLocation STICK_REGULAR = ModelResourceLocation.standalone(Reverie.id("block/tuff_gearshift/stick"));
    public static final ModelResourceLocation STICK_SPORT = ModelResourceLocation.standalone(Reverie.id("block/sport_tuff_gearshift/stick"));

    /** Set when the Flywheel visual for the shafts registered; otherwise this renderer draws the shafts itself. */
    static volatile boolean visualRegistered;
    private static boolean stickFailureLogged;

    /**
     * Pivot of the stick in canonical model space (pixels): deep inside the casing, so the rod passes the top face
     * exactly where tools/gen_textures.py cuts the gate slots (GATE there must match these numbers).
     */
    static final float PIVOT_X = 8f;
    static final float PIVOT_Y = 4f;
    static final float PIVOT_Z = 8f;
    /** Knob centre height above the pivot (knob at 21 px), and the rod's travel where it passes the top face. */
    static final float KNOB_HEIGHT = 17f;
    private static final float TOP_TO_KNOB = KNOB_HEIGHT / (16f - PIVOT_Y);
    /**
     * Knob travel (pixels) so the rod meets the plate slots: rows 4 px off the rail. The top row of the GUI (odd gears
     * and R) is toward +x, so that seen from the side the columns run 1/2 ... R left to right with odd gears away
     * from the viewer, exactly as on screen.
     */
    static final float ROW_OFFSET = 4f * TOP_TO_KNOB;
    /**
     * Where the rod crosses the top face for each column, in pixels from the middle (gate_plate in
     * tools/gen_textures.py, two-texel grooves): regular columns at z 4/8/12, sport at z 3/6/10/13.
     */
    private static final float REGULAR_SPACING = 4f;
    private static final float[] SPORT_COLUMNS = {-5f, -2f, 2f, 5f};

    private final RandomSource random = RandomSource.create();

    public TuffGearshiftRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    protected void renderSafe(TuffGearshiftBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (!visualRegistered || !VisualizationManager.supportsVisualization(be.getLevel())) renderShafts(be, ms, buffer, light);
        try {
            renderStick(be, partialTicks, ms, buffer, light, overlay);
        } catch (RuntimeException e) {
            if (!stickFailureLogged) {
                stickFailureLogged = true;
                Reverie.LOGGER.error("Gearstick rendering failed; the stick will be hidden", e);
            }
        }
    }

    private void renderShafts(TuffGearshiftBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        BlockState state = be.getBlockState();
        Direction.Axis boxAxis = state.getValue(BlockStateProperties.AXIS);
        BlockPos pos = be.getBlockPos();
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != boxAxis) continue;
            float offset = getRotationOffsetForPosition(be, pos, boxAxis);
            float angle = (time * be.getSpeed() * 3f / 10) % 360;
            angle *= be.getRotationSpeedModifier(direction);
            angle += offset;
            angle = angle / 180f * (float) Math.PI;
            SuperByteBuffer shaft = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, direction);
            kineticRotationTransform(shaft, be, boxAxis, angle, light);
            shaft.renderInto(ms, buffer.getBuffer(RenderType.solid()));
        }
    }

    /** Column position at the top face for a (possibly fractional, mid-shift) gate column. */
    static float columnAtTop(GateLayout layout, float gx) {
        if (layout != GateLayout.SPORT) return (gx - layout.restX) * REGULAR_SPACING;
        int i = Math.max(0, Math.min(SPORT_COLUMNS.length - 2, (int) Math.floor(gx)));
        return SPORT_COLUMNS[i] + (SPORT_COLUMNS[i + 1] - SPORT_COLUMNS[i]) * (gx - i);
    }

    private void renderStick(TuffGearshiftBlockEntity be, float pt, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof TuffGearshiftBlock block)) return;
        GateLayout layout = block.layout;
        GearstickAnimator anim = be.animator();
        boolean animated = ReverieConfig.get(ReverieConfig.ANIMATED_STICK);
        float gx = anim.x(pt);
        float gy = anim.y(pt);
        float time = AnimationTickHolder.getRenderTime(be.getLevel());

        boolean sport = layout == GateLayout.SPORT;
        float knobHeight = KNOB_HEIGHT;
        float dz = columnAtTop(layout, gx) * TOP_TO_KNOB;
        float dx = -gy * ROW_OFFSET;
        float tiltX = (float) Math.toDegrees(Math.atan2(dz, knobHeight));
        float tiltZ = (float) -Math.toDegrees(Math.atan2(dx, knobHeight));

        if (animated) {
            float in = Math.abs(be.getSpeed());
            float out = Math.abs(be.getSpeed() * be.appliedRatio());
            if (out > 0) {
                float amp = 0.6f * Math.min(1f, out / 128f);
                float f = 0.9f + Math.min(out, 256) / 40f;
                tiltX += amp * (float) Math.sin(time * f);
                tiltZ += amp * (float) Math.cos(time * f * 1.13f);
            } else if (be.effectiveGear() == GateLayout.N && in > 0) {
                // Idle rattle: the stick sways loosely in the rail while the input still turns.
                tiltX += 2f * (float) Math.sin(time * 0.31f) * (0.6f + 0.4f * (float) Math.sin(time * 0.071f));
                tiltZ += 0.8f * (float) Math.sin(time * 0.53f + 1.7f);
            }
            float shake = anim.shake(time);
            tiltX += shake;
            tiltZ += shake * 0.5f;
        }

        var models = Minecraft.getInstance().getModelManager();
        BakedModel stick = models.getModel(sport ? STICK_SPORT : STICK_REGULAR);

        ms.pushPose();
        orientToAxis(ms, state.getValue(BlockStateProperties.AXIS));
        ms.pushPose();
        pivot(ms, tiltX, tiltZ);
        renderBaked(ms, buffer, state, stick, light, overlay);
        ms.popPose();

        ms.popPose();
    }

    /** Canonical model space has the shaft along Z and the stick on top; match the blockstate rotations. */
    static void orientToAxis(PoseStack ms, Direction.Axis axis) {
        if (axis == Direction.Axis.Z) return;
        ms.translate(0.5, 0.5, 0.5);
        if (axis == Direction.Axis.X) ms.mulPose(Axis.YP.rotationDegrees(-90));
        else ms.mulPose(Axis.XP.rotationDegrees(-90));
        ms.translate(-0.5, -0.5, -0.5);
    }

    private static void pivot(PoseStack ms, float tiltX, float tiltZ) {
        ms.translate(PIVOT_X / 16f, PIVOT_Y / 16f, PIVOT_Z / 16f);
        ms.mulPose(Axis.XP.rotationDegrees(tiltX));
        ms.mulPose(Axis.ZP.rotationDegrees(tiltZ));
        ms.translate(-PIVOT_X / 16f, -PIVOT_Y / 16f, -PIVOT_Z / 16f);
    }

    private void renderBaked(PoseStack ms, MultiBufferSource buffer, BlockState state, BakedModel model, int light, int overlay) {
        VertexConsumer vc = buffer.getBuffer(RenderType.cutout());
        PoseStack.Pose pose = ms.last();
        for (Direction dir : Direction.values()) {
            random.setSeed(42L);
            quads(pose, vc, model.getQuads(state, dir, random, ModelData.EMPTY, RenderType.cutout()), light, overlay);
        }
        random.setSeed(42L);
        quads(pose, vc, model.getQuads(state, null, random, ModelData.EMPTY, RenderType.cutout()), light, overlay);
    }

    private static void quads(PoseStack.Pose pose, VertexConsumer vc, List<BakedQuad> quads, int light, int overlay) {
        for (BakedQuad quad : quads) {
            float shade = quad.isShade() ? faceShade(quad.getDirection()) : 1f;
            vc.putBulkData(pose, quad, shade, shade, shade, 1f, light, overlay);
        }
    }

    private static float faceShade(Direction d) {
        return switch (d) {
            case DOWN -> 0.5f;
            case UP -> 1f;
            case NORTH, SOUTH -> 0.8f;
            case WEST, EAST -> 0.6f;
        };
    }
}
