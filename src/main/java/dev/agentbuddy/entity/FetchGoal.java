package dev.agentbuddy.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Runs a fetch: fly to the container, take the items, fly back to the owner, hand them over. A leg that takes
 * longer than {@link #LEG_TIMEOUT} ticks ends with a teleport, so the agent never gets stuck on terrain.
 */
final class FetchGoal extends Goal {
    static final int LEG_TIMEOUT = 300;
    private static final double REACH_SQR = 2.5 * 2.5;

    private final AgentEntity agent;
    private int ticks;
    private int recalc;

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
            if (agent.distanceToSqr(c.getCenter()) <= REACH_SQR) {
                agent.takeFetchedItems();
                ticks = 0;
            } else if (ticks > LEG_TIMEOUT) {
                agent.moveTo(c.getX() + 0.5, c.getY() + 1.2, c.getZ() + 0.5);
            } else if (--recalc <= 0) {
                recalc = 10;
                agent.getNavigation().moveTo(c.getX() + 0.5, c.getY() + 1.0, c.getZ() + 0.5, 1.6);
            }
            return;
        }
        Player owner = agent.ownerInLevel();
        if (owner == null) return; // keep the items until the owner is back
        agent.getLookControl().setLookAt(owner, 10.0F, agent.getMaxHeadXRot());
        if (agent.distanceToSqr(owner) <= REACH_SQR) {
            agent.deliverFetchedItems(owner);
        } else if (ticks > LEG_TIMEOUT) {
            agent.moveTo(owner.getX(), owner.getY() + 1.0, owner.getZ());
        } else if (--recalc <= 0) {
            recalc = 10;
            agent.getNavigation().moveTo(owner.getX(), owner.getY() + 1.0, owner.getZ(), 1.6);
        }
    }
}
