package com.seba.reverie.client.glider;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/** Holding the Sky Lantern up: one arm straight up, swaying a little. Added through META-INF/enumextensions.json. */
public final class LanternArmPose {
    public static final EnumProxy<HumanoidModel.ArmPose> PROXY = new EnumProxy<>(HumanoidModel.ArmPose.class, false,
            (IArmPoseTransformer) LanternArmPose::apply);

    private LanternArmPose() {
    }

    private static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        part.xRot = -2.95f + 0.05f * Mth.sin(entity.tickCount * 0.17f);
        part.yRot = 0;
        part.zRot = arm == HumanoidArm.RIGHT ? 0.1f : -0.1f;
    }
}
