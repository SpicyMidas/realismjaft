package com.seba.reverie.client.shade;

import com.seba.reverie.Reverie;
import com.seba.reverie.shade.ShadeEntity;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Box silhouettes for the Shades: a thin humanoid with too-long arms (posed per variant) and an empty doorframe.
 * Model space as in vanilla: y down, face toward -z, feet at y=24.
 */
final class ShadeModel {
    /** Layers: the silhouette itself and two inflated copies that make the soft edge. */
    static final String[] LAYERS = {"main", "halo_inner", "halo_outer"};
    static final float[] GROW = {0, 0.5f, 1.0f};

    static ModelLayerLocation humanoidLayer(int i) {
        return new ModelLayerLocation(Reverie.id("shade"), LAYERS[i]);
    }

    static ModelLayerLocation doorframeLayer(int i) {
        return new ModelLayerLocation(Reverie.id("shade_doorframe"), LAYERS[i]);
    }

    final ModelPart root;
    final ModelPart head;
    final ModelPart eyes;
    final ModelPart body;
    final ModelPart chestEyes;
    final ModelPart rightArm;
    final ModelPart leftArm;
    final ModelPart rightLeg;
    final ModelPart leftLeg;

    final ModelPart frame;
    final ModelPart lintel;
    final ModelPart frameEyes;

    ShadeModel(ModelPart humanoid, ModelPart doorframe) {
        root = humanoid;
        head = humanoid.getChild("head");
        eyes = head.getChild("eyes");
        body = humanoid.getChild("body");
        chestEyes = body.getChild("chest_eyes");
        rightArm = humanoid.getChild("right_arm");
        leftArm = humanoid.getChild("left_arm");
        rightLeg = humanoid.getChild("right_leg");
        leftLeg = humanoid.getChild("left_leg");
        frame = doorframe;
        lintel = doorframe.getChild("lintel");
        frameEyes = lintel.getChild("eyes");
    }

    private static CubeListBuilder eyePair(float y, float z) {
        // Two 1x1 pale pixels, a hair in front of the face. The texture's top-right corner is pale.
        return CubeListBuilder.create().texOffs(56, 0).addBox(-2.5f, y, z, 1, 1, 0).texOffs(56, 0).addBox(1.5f, y, z, 1, 1, 0);
    }

    static LayerDefinition humanoid(float grow) {
        CubeDeformation g = new CubeDeformation(grow);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -7, -3.5f, 7, 7, 7, g), PartPose.ZERO);
        head.addOrReplaceChild("eyes", eyePair(-4.5f, -3.56f), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(28, 0).addBox(-3.5f, 0, -1.5f, 7, 12, 3, g), PartPose.ZERO);
        body.addOrReplaceChild("chest_eyes", eyePair(3.5f, -1.56f), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 16).addBox(-1.5f, -1.5f, -1.5f, 3, 14, 3, g), PartPose.offset(-5, 1.5f, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(12, 16).mirror().addBox(-1.5f, -1.5f, -1.5f, 3, 14, 3, g), PartPose.offset(5, 1.5f, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(24, 16).addBox(-1.5f, 0, -1.5f, 3, 12, 3, g), PartPose.offset(-1.8f, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(36, 16).mirror().addBox(-1.5f, 0, -1.5f, 3, 12, 3, g), PartPose.offset(1.8f, 12, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    static LayerDefinition doorframe(float grow) {
        CubeDeformation g = new CubeDeformation(grow);
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("right_jamb", CubeListBuilder.create().texOffs(48, 16).addBox(-8, -8, -1, 2, 32, 2, g), PartPose.ZERO);
        root.addOrReplaceChild("left_jamb", CubeListBuilder.create().texOffs(56, 16).addBox(6, -8, -1, 2, 32, 2, g), PartPose.ZERO);
        PartDefinition lintel = root.addOrReplaceChild("lintel", CubeListBuilder.create().texOffs(0, 50).addBox(-8, -10, -1, 16, 2, 2, g), PartPose.ZERO);
        lintel.addOrReplaceChild("eyes", eyePair(-9.5f, -1.06f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Gives an inflated copy (halo) the pose of the silhouette. */
    void copyPose(ShadeModel from) {
        ModelPart[] src = {from.head, from.body, from.rightArm, from.leftArm, from.rightLeg, from.leftLeg};
        ModelPart[] dst = {head, body, rightArm, leftArm, rightLeg, leftLeg};
        for (int i = 0; i < src.length; i++) {
            dst[i].copyFrom(src[i]);
            dst[i].visible = src[i].visible;
        }
        eyes.visible = false;
        chestEyes.visible = false;
        frameEyes.visible = false;
    }

    /** Poses the humanoid for a variant. {@code t} is in ticks, {@code seed} desynchronises the idle sway. */
    void pose(ShadeEntity.Variant variant, float t, int seed, boolean reduceMotion) {
        root.getAllParts().forEach(ModelPart::resetPose);
        head.visible = true;
        eyes.visible = false;
        chestEyes.visible = false;
        float sway = reduceMotion ? 0 : Mth.sin(t * 0.021f + seed) * 0.08f;
        head.zRot = 0.06f + sway;
        rightArm.zRot = 0.04f;
        leftArm.zRot = -0.04f;
        switch (variant) {
            case TALL_ONE -> head.xRot = 0.3f; // looking down at you
            case STOOPED_ONE -> {
                // Bent forward from the hips; the body pivots at its top, so move the pivot to keep the hips in place.
                float a = 0.62f;
                body.xRot = a;
                body.y = 12 - 12 * Mth.cos(a);
                body.z = -12 * Mth.sin(a);
                head.y = body.y + 0.6f;
                head.z = body.z - 0.9f;
                head.xRot = -0.45f;
                float shoulderY = body.y + 1.5f * Mth.cos(a);
                float shoulderZ = body.z + 1.5f * Mth.sin(a);
                rightArm.y = leftArm.y = shoulderY;
                rightArm.z = leftArm.z = shoulderZ;
                rightArm.xRot = leftArm.xRot = -0.12f;
                rightLeg.xRot = leftLeg.xRot = -0.08f;
            }
            case WIDE_ARMED -> {
                rightArm.zRot = 1.32f;
                leftArm.zRot = -1.32f;
                rightArm.yScale = leftArm.yScale = 1.45f;
                head.xRot = 0.12f;
            }
            case HEADLESS -> {
                head.visible = false;
                rightArm.zRot = 0.1f;
                leftArm.zRot = -0.1f;
            }
            case HANGING -> {
                // Rendered upside down: the arms dangle past the head, toward the ground.
                rightArm.xRot = Mth.PI - 0.05f + sway * 0.5f;
                leftArm.xRot = Mth.PI + 0.05f - sway * 0.5f;
                rightArm.zRot = -0.06f;
                leftArm.zRot = 0.06f;
            }
            default -> {
            }
        }
    }

    /** The part the pinprick eyes belong to for this variant, or null when it has none. */
    ModelPart eyeOwner(ShadeEntity.Variant variant) {
        return switch (variant) {
            case HEADLESS -> body;
            case DOORFRAME -> lintel;
            default -> head;
        };
    }

    ModelPart eyesOf(ShadeEntity.Variant variant) {
        return switch (variant) {
            case HEADLESS -> chestEyes;
            case DOORFRAME -> frameEyes;
            default -> eyes;
        };
    }
}
