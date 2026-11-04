package dev.agentbuddy.entity;

import dev.agentbuddy.world.SafeSpot;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Runs a fetch: walk to the container, take the items, walk back to the owner, hand them over. A leg that takes
 * longer than {@link #LEG_TIMEOUT} ticks ends with a teleport to a safe spot, so the agent never gets stuck.
 */
final class FetchGoal extends Goal {
    static final int LEG_TIMEOUT = 300;
    private static final double REACH = 2.5;
    // After a timeout teleport the agent is as close as a safe spot allows; accept that instead of looping.
    private static final double REACH_AFTER_TELEPORT = 4.0;

    private final AgentEntity agent;
    private int ticks;
    private int recalc;
    private boolean teleported;

    FetchGoal(AgentEntity agent) {
        this.agent = agent;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return agent.fetchTask() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return agent.fetchTask() != null;
    }

    @Override
    public void start() {
        ticks = 0;
        recalc = 0;
        teleported = false;
    }

    /** Measured from the agent's eyes, so a chest on a shelf is reachable from the floor. */
    private boolean inReach(net.minecraft.world.phys.Vec3 target) {
        double reach = teleported ? REACH_AFTER_TELEPORT : REACH;
        return agent.getEyePosition().distanceToSqr(target) <= reach * reach;
    }

    @Override
    public void stop() {
        agent.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        AgentEntity.FetchTask task = agent.fetchTask();
        if (task == null) return;
        ticks++;
        if (!task.returning()) {
            BlockPos c = task.container();
            agent.getLookControl().setLookAt(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5);
            if (inReach(c.getCenter())) {
                agent.takeFetchedItems();
                ticks = 0;
                teleported = false;
            } else if (ticks > LEG_TIMEOUT) {
                teleportNear(c);
            } else if (--recalc <= 0) {
                recalc = 10;
                agent.getNavigation().moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 1.2);
            }
            return;
        }
        Player owner = agent.ownerInLevel();
        if (owner == null) return; // keep the items until the owner is back
        agent.getLookControl().setLookAt(owner, 10.0F, agent.getMaxHeadXRot());
        if (inReach(owner.position().add(0, 1.0, 0))) {
            agent.deliverFetchedItems(owner);
        } else if (ticks > LEG_TIMEOUT) {
            teleportNear(owner.blockPosition());
        } else if (--recalc <= 0) {
            recalc = 10;
            agent.getNavigation().moveTo(owner, 1.2);
        }
    }

    private void teleportNear(BlockPos target) {
        ticks = 0;
        teleported = true;
        if (!(agent.level() instanceof ServerLevel level)) return;
        BlockPos spot = SafeSpot.find(level, target, 3, 1.0);
        if (spot != null) {
            agent.getNavigation().stop();
            agent.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, agent.getYRot(), agent.getXRot());
        }
    }
}
