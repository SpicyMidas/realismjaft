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
 * Placeholder golem model defined in code: a boxy body, a big head whose face is a protruding camera lens, two
 * short arms. Texture layout matches {@code textures/entity/agent.png} (64x64). Replace with a Blockbench export.
 */
public class AgentModel extends HierarchicalModel<AgentEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(AgentBuddy.MODID, "agent"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;

    public AgentModel(ModelPart part) {
        this.root = part.getChild("root");
        this.head = root.getChild("head");
        this.rightArm = root.getChild("body").getChild("right_arm");
        this.leftArm = root.getChild("body").getChild("left_arm");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(0, 18).addBox(-4.0F, -11.0F, -2.5F, 8.0F, 9.0F, 5.0F),
                PartPose.ZERO);
        body.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(28, 18).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F),
                PartPose.offset(-4.0F, -10.5F, 0.0F));
        body.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(36, 18).addBox(0.0F, 0.0F, -1.0F, 2.0F, 7.0F, 2.0F),
                PartPose.offset(4.0F, -10.5F, 0.0F));
        PartDefinition head = root.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, -9.0F, -4.0F, 10.0F, 9.0F, 8.0F),
                PartPose.offset(0.0F, -11.0F, 0.0F));
        head.addOrReplaceChild("lens",
                CubeListBuilder.create().texOffs(36, 0).addBox(-3.0F, -7.5F, -6.0F, 6.0F, 6.0F, 2.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
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
        // Hover bob and a slow arm sway.
        root.y += Mth.sin(ageInTicks * 0.1F) * 1.0F;
        rightArm.xRot = Mth.cos(ageInTicks * 0.09F) * 0.15F;
        leftArm.xRot = -Mth.cos(ageInTicks * 0.09F) * 0.15F;
    }
}
