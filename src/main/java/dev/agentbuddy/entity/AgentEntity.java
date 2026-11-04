package dev.agentbuddy.entity;

import dev.agentbuddy.data.AgentStore;
import dev.agentbuddy.item.AgentItem;
import dev.agentbuddy.network.ModNetwork;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Agent: a flying camera-lens golem. Movement copies the Allay (flying move control and navigation, no ground
 * pathfinding, no fall damage). Its persistent data lives in {@link AgentStore} under {@link #agentId()}.
 */
public class AgentEntity extends PathfinderMob {
    private UUID agentId;
    private UUID owner;
    private boolean following = true;

    public AgentEntity(EntityType<? extends AgentEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.12F)
                .add(Attributes.MOVEMENT_SPEED, 0.12F)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    /** Called once when spawned from an item. */
    public void bind(UUID agentId, UUID owner, String name) {
        this.agentId = agentId;
        this.owner = owner;
        setCustomName(Component.literal(name));
        setPersistenceRequired();
    }

    public UUID agentId() {
        return agentId;
    }

    public UUID owner() {
        return owner;
    }

    public boolean isFollowing() {
        return following;
    }

    public void setFollowing(boolean following) {
        this.following = following;
    }

    Player ownerInLevel() {
        return owner == null ? null : level().getPlayerByUUID(owner);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FollowOwnerFlyingGoal(this, 1.6, 5.0F, 3.0F, 24.0F));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    // Same as Allay.travel: flying movement with drag, slower in liquids.
    @Override
    public void travel(Vec3 travelVector) {
        if (isControlledByLocalInstance()) {
            float drag = isInWater() ? 0.8F : isInLava() ? 0.5F : 0.91F;
            moveRelative(isInWater() || isInLava() ? 0.02F : getSpeed(), travelVector);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(drag));
        }
        calculateEntityAnimation(false);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    /** Right-click by the owner opens the Agent screen; shift + right-click turns it back into an item. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (agentId == null || !player.getUUID().equals(owner)) {
            player.displayClientMessage(Component.literal("Not your agent."), true);
            return InteractionResult.FAIL;
        }
        if (!player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer sp) ModNetwork.openScreen(sp, this, "");
            return InteractionResult.CONSUME;
        }
        ItemStack stack = AgentItem.stackFor(agentId);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        AgentStore.get(((ServerLevel) level()).getServer()).setDeployed(agentId, false);
        discard();
        return InteractionResult.CONSUME;
    }

    /** Always drops the agent item on death (independent of doMobLoot), so the agent is never lost. */
    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (agentId != null && level() instanceof ServerLevel server) {
            spawnAtLocation(AgentItem.stackFor(agentId));
            AgentStore.get(server.getServer()).setDeployed(agentId, false);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (agentId != null) tag.putUUID("AgentId", agentId);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putBoolean("Following", following);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        agentId = tag.hasUUID("AgentId") ? tag.getUUID("AgentId") : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        following = !tag.contains("Following") || tag.getBoolean("Following");
    }
}
