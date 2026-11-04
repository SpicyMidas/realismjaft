package dev.agentbuddy.entity;

import dev.agentbuddy.world.SafeSpot;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/**
 * Walks after the owner when farther than {@code start}. Beyond {@code teleport} blocks, or when no path can be
 * found for a while, it teleports to a safe spot next to the owner (like tamed animals do).
 */
final class FollowOwnerGoal extends Goal {
    private static final int STUCK_TICKS = 60;

    private final AgentEntity agent;
    private final double speed;
    private final float start;
    private final float stop;
    private final float teleport;
    private Player owner;
    private int recalc;
    private int stuck;

    FollowOwnerGoal(AgentEntity agent, double speed, float start, float stop, float teleport) {
        this.agent = agent;
        this.speed = speed;
        this.start = start;
        this.stop = stop;
        this.teleport = teleport;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        Player p = agent.ownerInLevel();
        if (p == null || p.isSpectator() || !agent.isFollowing()) return false;
        if (agent.distanceToSqr(p) < start * start) return false;
        owner = p;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null && owner.isAlive() && agent.isFollowing() && agent.distanceToSqr(owner) > stop * stop;
    }

    @Override
    public void start() {
        recalc = 0;
        stuck = 0;
    }

    @Override
    public void stop() {
        owner = null;
        agent.getNavigation().stop();
    }

    @Override
    public void tick() {
        agent.getLookControl().setLookAt(owner, 10.0F, agent.getMaxHeadXRot());
        if (agent.distanceToSqr(owner) > teleport * teleport || stuck > STUCK_TICKS) {
            teleportNearOwner();
            return;
        }
        if (--recalc > 0) return;
        recalc = adjustedTickDelay(10);
        boolean moving = agent.getNavigation().moveTo(owner, speed);
        stuck = moving ? 0 : stuck + 10;
    }

    private void teleportNearOwner() {
        stuck = 0;
        if (!(agent.level() instanceof ServerLevel level)) return;
        BlockPos spot = SafeSpot.find(level, owner.blockPosition(), 3, 1.5);
        if (spot == null) return;
        agent.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, agent.getYRot(), agent.getXRot());
        agent.getNavigation().stop();
    }
}
