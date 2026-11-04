package dev.agentbuddy.entity;

import dev.agentbuddy.brain.reply.Names;
import dev.agentbuddy.data.AgentStore;
import dev.agentbuddy.item.AgentItem;
import dev.agentbuddy.network.ModNetwork;
import dev.agentbuddy.world.Containers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.Item;
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
    private FetchTask fetch;
    // Items taken from a container and not yet handed over; saved with the entity and dropped on death.
    private final List<ItemStack> carried = new ArrayList<>();

    /** An item run: go to {@code container}, take {@code count} of {@code item}, bring them back. */
    public record FetchTask(BlockPos container, Item item, int count, boolean returning) {
    }

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

    public boolean isFetching() {
        return fetch != null;
    }

    FetchTask fetchTask() {
        return fetch;
    }

    public void startFetch(BlockPos container, Item item, int count) {
        fetch = new FetchTask(container, item, count, false);
    }

    /** At the container: move the real stacks into the agent (components such as enchantments stay intact). */
    void takeFetchedItems() {
        if (!(level() instanceof ServerLevel server)) return;
        AgentStore.Record rec = agentId == null ? null : AgentStore.get(server.getServer()).get(agentId);
        boolean ignored = rec != null && rec.isIgnored(GlobalPos.of(server.dimension(), fetch.container()));
        List<ItemStack> taken = ignored ? List.of() : Containers.withdraw(server, fetch.container(), fetch.item(), fetch.count());
        carried.addAll(taken);
        fetch = new FetchTask(fetch.container(), fetch.item(), fetch.count(), true);
    }

    /** At the owner: hand everything over (drops at their feet if the inventory is full). */
    void deliverFetchedItems(Player to) {
        int total = carried.stream().mapToInt(ItemStack::getCount).sum();
        Item item = fetch.item();
        for (ItemStack s : carried) {
            if (!to.getInventory().add(s)) to.drop(s, false);
        }
        carried.clear();
        fetch = null;
        to.displayClientMessage(Component.literal(total == 0 ? "Nothing to bring."
                : "Here: " + Names.count(total, BuiltInRegistries.ITEM.getKey(item).toString()) + "."), true);
    }

    public List<ItemStack> carriedItems() {
        return List.copyOf(carried);
    }

    Player ownerInLevel() {
        return owner == null ? null : level().getPlayerByUUID(owner);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FetchGoal(this));
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
            carried.forEach(this::spawnAtLocation);
            carried.clear();
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
        if (!carried.isEmpty()) {
            ListTag items = new ListTag();
            for (ItemStack s : carried) items.add(s.save(registryAccess()));
            tag.put("Carried", items);
        }
        if (fetch != null) {
            CompoundTag f = new CompoundTag();
            f.putLong("Container", fetch.container().asLong());
            f.putString("Item", BuiltInRegistries.ITEM.getKey(fetch.item()).toString());
            f.putInt("Count", fetch.count());
            f.putBoolean("Returning", fetch.returning());
            tag.put("Fetch", f);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        agentId = tag.hasUUID("AgentId") ? tag.getUUID("AgentId") : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        following = !tag.contains("Following") || tag.getBoolean("Following");
        carried.clear();
        for (Tag t : tag.getList("Carried", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registryAccess(), t).ifPresent(carried::add);
        }
        fetch = null;
        if (tag.contains("Fetch")) {
            CompoundTag f = tag.getCompound("Fetch");
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(f.getString("Item")));
            fetch = new FetchTask(BlockPos.of(f.getLong("Container")), item, f.getInt("Count"), f.getBoolean("Returning"));
        }
    }
}
