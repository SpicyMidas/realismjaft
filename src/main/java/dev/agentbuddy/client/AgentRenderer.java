package dev.agentbuddy.client;

import dev.agentbuddy.AgentBuddy;
import dev.agentbuddy.entity.AgentEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class AgentRenderer extends MobRenderer<AgentEntity, AgentModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(AgentBuddy.MODID, "textures/entity/agent.png");

    public AgentRenderer(EntityRendererProvider.Context context) {
        super(context, new AgentModel(context.bakeLayer(AgentModel.LAYER)), 0.35F);
    }

    @Override
    public ResourceLocation getTextureLocation(AgentEntity entity) {
        return TEXTURE;
    }
}
