package com.seba.reverie.client.glider;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/**
 * The gliding pose: both arms straight up, a little apart, holding the glider overhead. Added to
 * {@link HumanoidModel.ArmPose} through META-INF/enumextensions.json (client only; the enum never loads on a server).
 */
public final class GliderArmPose {
    public static final EnumProxy<HumanoidModel.ArmPose> PROXY = new EnumProxy<>(HumanoidModel.ArmPose.class, true,
            (IArmPoseTransformer) GliderArmPose::apply);

    private GliderArmPose() {
    }

    /** Two-handed: the other arm gets EMPTY (which only resets its yRot), so this sets both arms. */
    private static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        float sway = 0.04f * Mth.sin(entity.tickCount * 0.21f);
        model.rightArm.xRot = -2.85f + sway;
        model.leftArm.xRot = -2.85f - sway;
        model.rightArm.yRot = 0;
        model.leftArm.yRot = 0;
        model.rightArm.zRot = 0.3f;
        model.leftArm.zRot = -0.3f;
    }
}
