package dev.agentbuddy.client;

import dev.agentbuddy.AgentBuddy;
import dev.agentbuddy.entity.AgentEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Small walking golem, copper-golem-like proportions: a big head whose face is a camera lens, a small antenna,
 * a short body, arms that swing opposite to the legs. Texture layout matches {@code textures/entity/agent.png}
 * (64x32). Defined in code; replace with a Blockbench export.
 */
public class AgentModel extends HierarchicalModel<AgentEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(AgentBuddy.MODID, "agent"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart antenna;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public AgentModel(ModelPart part) {
        this.root = part.getChild("root");
        this.head = root.getChild("head");
        this.antenna = head.getChild("antenna");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 13).addBox(-3.0F, 0.0F, -2.0F, 6.0F, 5.0F, 4.0F),
                PartPose.offset(0.0F, 15.0F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -6.0F, -3.5F, 8.0F, 6.0F, 7.0F),
                PartPose.offset(0.0F, 15.0F, 0.0F));
        head.addOrReplaceChild("lens",
                CubeListBuilder.create().texOffs(30, 0).addBox(-2.0F, -5.0F, -5.5F, 4.0F, 4.0F, 2.0F),
                PartPose.ZERO);
        head.addOrReplaceChild("antenna",
                CubeListBuilder.create()
                        .texOffs(30, 6).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F)
                        .texOffs(34, 6).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 1.0F, 2.0F),
                PartPose.offset(0.0F, -6.0F, 0.0F));
        root.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(16, 22).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offset(-3.0F, 15.0F, 0.0F));
        root.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(24, 22).addBox(0.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offset(3.0F, 15.0F, 0.0F));
        root.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 22).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(-1.5F, 20.0F, 0.0F));
        root.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(8, 22).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offset(1.5F, 20.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(AgentEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        // Walk cycle: legs alternate, arms swing against the legs, the head waddles a little.
        float walk = limbSwing * 0.6662F;
        rightLeg.xRot = Mth.cos(walk) * 1.4F * limbSwingAmount;
        leftLeg.xRot = Mth.cos(walk + Mth.PI) * 1.4F * limbSwingAmount;
        rightArm.xRot = Mth.cos(walk + Mth.PI) * 1.0F * limbSwingAmount;
        leftArm.xRot = Mth.cos(walk) * 1.0F * limbSwingAmount;
        head.zRot = Mth.sin(walk) * 0.08F * limbSwingAmount;
        antenna.xRot = Mth.sin(ageInTicks * 0.15F) * 0.12F;
    }
}
