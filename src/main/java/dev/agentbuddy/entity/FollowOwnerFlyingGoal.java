package dev.agentbuddy.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

/** Flies toward the owner when farther than {@code start}; teleports next to them beyond {@code teleport}. */
final class FollowOwnerFlyingGoal extends Goal {
    private final AgentEntity agent;
    private final double speed;
    private final float start;
    private final float stop;
    private final float teleport;
    private Player owner;
    private int recalc;

    FollowOwnerFlyingGoal(AgentEntity agent, double speed, float start, float stop, float teleport) {
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
    }

    @Override
    public void stop() {
        owner = null;
        agent.getNavigation().stop();
    }

    @Override
    public void tick() {
        agent.getLookControl().setLookAt(owner, 10.0F, agent.getMaxHeadXRot());
        if (--recalc > 0) return;
        recalc = adjustedTickDelay(10);
        if (agent.distanceToSqr(owner) > teleport * teleport) {
            teleportNearOwner();
        } else {
            // Aim a bit above the owner's head so it hovers instead of bumping into them.
            agent.getNavigation().moveTo(owner.getX(), owner.getY() + 2.0, owner.getZ(), speed);
        }
    }

    private void teleportNearOwner() {
        BlockPos base = owner.blockPosition();
        for (int i = 0; i < 10; i++) {
            BlockPos p = base.offset(agent.getRandom().nextInt(5) - 2, 1 + agent.getRandom().nextInt(2), agent.getRandom().nextInt(5) - 2);
            if (agent.level().noCollision(agent, agent.getType().getSpawnAABB(p.getX() + 0.5, p.getY(), p.getZ() + 0.5))) {
                agent.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, agent.getYRot(), agent.getXRot());
                agent.getNavigation().stop();
                return;
            }
        }
    }
}
