package dev.agentbuddy.tool;

import com.google.gson.JsonObject;
import dev.agentbuddy.brain.tool.ClaimMatcher;
import dev.agentbuddy.brain.tool.Tool;
import dev.agentbuddy.brain.tool.ToolResult;
import dev.agentbuddy.brain.tool.ToolSpec;
import dev.agentbuddy.brain.tool.ToolSpecs;
import dev.agentbuddy.entity.AgentEntity;
import dev.agentbuddy.server.AgentService;
import dev.agentbuddy.world.Containers;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Reports the block or entity under the player's crosshair, raycast on the server within the player's reach. If the
 * player made a claim, Java decides whether it matches (block id and block tags), never the model.
 */
public final class LookAtTool implements Tool {
    private final AgentService service;

    public LookAtTool(AgentService service) {
        this.service = service;
    }

    @Override
    public ToolSpec spec() {
        return ToolSpecs.LOOK_AT;
    }

    @Override
    public ToolResult run(String playerKey, JsonObject args) {
        ServerPlayer p = service.player(playerKey);
        if (p == null) return ToolResult.fail(ToolResult.NOTHING_IN_SIGHT);
        String claim = args.has("claim") ? args.get("claim").getAsString() : "";

        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        HitResult block = p.pick(p.blockInteractionRange(), 1.0F, false);
        double entityReach = p.entityInteractionRange();
        AABB sweep = p.getBoundingBox().expandTowards(look.scale(entityReach)).inflate(1.0);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(p, eye, eye.add(look.scale(entityReach)), sweep,
                e -> !e.isSpectator() && e.isPickable(), entityReach * entityReach);

        boolean entityCloser = entity != null && (block.getType() == HitResult.Type.MISS
                || eye.distanceToSqr(entity.getLocation()) < eye.distanceToSqr(block.getLocation()));
        if (entityCloser) {
            Entity e = entity.getEntity();
            return result(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(), List.of(), e.blockPosition(), claim);
        }
        if (block instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            AgentEntity agent = service.agentFor(p);
            BlockPos canonical = Containers.canonical(p.serverLevel(), bh.getBlockPos());
            if (agent != null && service.ignoredBy(agent, p.serverLevel()).test(canonical)) {
                return ToolResult.fail(ToolResult.NOTHING_IN_SIGHT); // ignored blocks do not exist for the agent
            }
            BlockState state = p.serverLevel().getBlockState(bh.getBlockPos());
            List<String> tags = state.getTags().map(t -> t.location().toString()).toList();
            return result(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), tags, bh.getBlockPos(), claim);
        }
        return ToolResult.fail(ToolResult.NOTHING_IN_SIGHT);
    }

    private static ToolResult result(String id, List<String> tags, BlockPos pos, String claim) {
        Map<String, Object> d = ToolResult.data("block", id, "x", pos.getX(), "y", pos.getY(), "z", pos.getZ());
        if (!ClaimMatcher.isEmpty(claim)) {
            d.put("claim", claim);
            d.put("claim_matches", ClaimMatcher.matches(claim, id, tags));
        }
        return ToolResult.ok(d);
    }
}
