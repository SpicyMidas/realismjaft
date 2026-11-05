package com.seba.reverie.client.dreamland;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Impossible Stairs: four flights of four steps, each climbing, around a square. Three are ordinary. The fourth
 * starts where it should and is re-solved every frame along the rays from the viewer's eye: its far end is placed on
 * the ray through the first step (a point on that ray covers the same pixel at any depth), at the depth that makes it
 * a climbing flight of stair-like slope. So from wherever you look, the climb never ends. When no climbing solution
 * exists (looking up at it from below), the fourth flight comes down to close the loop honestly.
 */
public class ImpossibleStairsRenderer implements BlockEntityRenderer<BlockEntity> {
    /** Size of one step in blocks, and where the loop sits relative to the anchor block. */
    static final float SCALE = 0.75f;
    static final Vec3 OFFSET = new Vec3(-1.5, 0, -1.5);

    private static final int[][] STEPS = {
            {0, 0, 0}, {1, 1, 0}, {2, 2, 0}, {3, 3, 0},       // east
            {4, 4, 0}, {4, 5, 1}, {4, 6, 2}, {4, 7, 3},       // south
            {4, 8, 4}, {3, 9, 4}, {2, 10, 4}, {1, 11, 4}};    // west
    private static final Direction[] FACING = {Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** Fourth flight (north): its start corner, and the corner it would continue into, sixteen steps lower. */
    private static final Vec3 START = new Vec3(0, 12, 5);
    private static final Vec3 TARGET = new Vec3(0, 0, 1);

    private final BlockRenderDispatcher blocks;

    public ImpossibleStairsRenderer(BlockEntityRendererProvider.Context ctx) {
        this.blocks = ctx.getBlockRenderDispatcher();
    }

    private static BlockState stair(Direction facing) {
        return Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    @Override
    public void render(BlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 origin = Vec3.atLowerCornerOf(be.getBlockPos()).add(OFFSET);
        Vec3 eye = cam.subtract(origin).scale(1 / SCALE); // the eye in step units
        int glow = LightTexture.FULL_BRIGHT;

        pose.pushPose();
        pose.translate(OFFSET.x, OFFSET.y, OFFSET.z);
        pose.scale(SCALE, SCALE, SCALE);
        for (int i = 0; i < STEPS.length; i++) {
            pose.pushPose();
            pose.translate(STEPS[i][0], STEPS[i][1], STEPS[i][2]);
            blocks.renderSingleBlock(stair(FACING[i / 4]), pose, buffers, glow, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        // The fourth flight: local step j is the unit block at (0, j, -j-1), climbing toward local -z.
        Matrix4f flight = fourthFlight(eye);
        for (int j = 0; j < 4; j++) {
            pose.pushPose();
            pose.last().pose().mul(flight);
            pose.last().normal().mul(new Matrix3f(flight).invert().transpose());
            pose.translate(0, j, -j - 1);
            blocks.renderSingleBlock(stair(Direction.NORTH), pose, buffers, glow, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
        pose.popPose();
    }

    /** Maps the fourth flight's local frame into step units, solved for this eye. */
    static Matrix4f fourthFlight(Vec3 eye) {
        Vec3 end = solveEnd(eye);
        Vec3 d = end.subtract(START);
        double run = Math.hypot(d.x, d.z) / 4;
        double rise = d.y / 4;
        Vec3 forward = run > 1e-4 ? new Vec3(d.x, 0, d.z).normalize() : new Vec3(0, 0, -1);
        Vec3 east = new Vec3(-forward.z, 0, forward.x);
        // columns: local x -> east * run, local y -> up * rise, local z -> -forward * run
        return new Matrix4f(
                (float) (east.x * run), 0, (float) (east.z * run), 0,
                0, (float) rise, 0, 0,
                (float) (-forward.x * run), 0, (float) (-forward.z * run), 0,
                (float) START.x, (float) START.y, (float) START.z, 1);
    }

    /**
     * The flight's far end: on the ray from the eye through TARGET, at the depth where the flight climbs at about
     * 45 degrees. Falls back to TARGET itself (the honest, descending close) when it cannot climb.
     */
    static Vec3 solveEnd(Vec3 eye) {
        Vec3 u = eye.subtract(START);
        Vec3 w = TARGET.subtract(eye);
        // (u + b w).y^2 = |(u + b w) horizontal|^2
        double a2 = w.y * w.y - (w.x * w.x + w.z * w.z);
        double b2 = 2 * (u.y * w.y - (u.x * w.x + u.z * w.z));
        double c2 = u.y * u.y - (u.x * u.x + u.z * u.z);
        double best = Double.NaN;
        if (Math.abs(a2) < 1e-9) {
            if (Math.abs(b2) > 1e-9) best = pick(best, -c2 / b2, u, w);
        } else {
            double disc = b2 * b2 - 4 * a2 * c2;
            if (disc >= 0) {
                double sq = Math.sqrt(disc);
                best = pick(best, (-b2 - sq) / (2 * a2), u, w);
                best = pick(best, (-b2 + sq) / (2 * a2), u, w);
            }
        }
        return Double.isNaN(best) ? TARGET : eye.add(w.scale(best));
    }

    /** Keeps the candidate depth if it lies in front of the eye and makes a real climb; prefers the nearer one. */
    private static double pick(double best, double b, Vec3 u, Vec3 w) {
        if (!(b > 0.02 && b < 1.6)) return best;
        Vec3 d = u.add(w.scale(b));
        if (d.y < 1.5) return best;
        return Double.isNaN(best) || b < best ? b : best;
    }

    @Override
    public boolean shouldRenderOffScreen(BlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }

    @Override
    public AABB getRenderBoundingBox(BlockEntity be) {
        return AABB.INFINITE; // the fourth flight can be anywhere between the stairs and the viewer
    }
}
