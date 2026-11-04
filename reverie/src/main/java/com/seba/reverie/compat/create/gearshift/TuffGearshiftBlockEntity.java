package com.seba.reverie.compat.create.gearshift;

import java.util.List;

import com.seba.reverie.ReverieConfig;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class TuffGearshiftBlockEntity extends SplitShaftBlockEntity {
    private int selectedGear = GateLayout.N;
    /** Ratio of the effective gear, decided on the server and synced so client visuals match exactly. */
    private float appliedRatio = 0f;
    private boolean ratioChecked;

    // Client-only cosmetic state.
    private GearstickAnimator animator;
    private boolean lastPowered;
    public boolean muted;

    public TuffGearshiftBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        appliedRatio = ratioFor(state.getValue(TuffGearshiftBlock.GEAR));
    }

    public GateLayout layout() {
        return getBlockState().getBlock() instanceof TuffGearshiftBlock b ? b.layout : GateLayout.REGULAR;
    }

    public int selectedGear() {
        return selectedGear;
    }

    public int effectiveGear() {
        BlockState state = getBlockState();
        return state.hasProperty(TuffGearshiftBlock.GEAR) ? state.getValue(TuffGearshiftBlock.GEAR) : GateLayout.N;
    }

    public boolean isLocked() {
        BlockState state = getBlockState();
        return state.hasProperty(TuffGearshiftBlock.POWERED) && state.getValue(TuffGearshiftBlock.POWERED);
    }

    public float appliedRatio() {
        return appliedRatio;
    }

    public static float ratioFor(int gear) {
        if (gear == GateLayout.N) return 0f;
        if (gear == GateLayout.R) return (float) (double) ReverieConfig.get(ReverieConfig.REVERSE_RATIO);
        float r = (float) ReverieConfig.gearRatio(gear);
        return Float.isFinite(r) ? r : 1f;
    }

    @Override
    public float getRotationSpeedModifier(Direction face) {
        if (hasSource() && face != getSourceFacing()) return appliedRatio;
        return 1;
    }

    /** Server: the player picked a gear in the GUI (already validated). Returns false if nothing changed. */
    public boolean select(int gear) {
        if (level == null || level.isClientSide || !layout().isValid(gear)) return false;
        boolean changed = selectedGear != gear;
        selectedGear = gear;
        BlockState state = getBlockState();
        if (!isLocked() && state.getValue(TuffGearshiftBlock.GEAR) != gear && state.getBlock() instanceof TuffGearshiftBlock block) {
            block.applyState(level, worldPosition, state.setValue(TuffGearshiftBlock.GEAR, gear));
            changed = true;
        }
        if (changed) {
            setChanged();
            sendData();
        }
        return changed;
    }

    void onEffectiveGearChanged(int gear) {
        appliedRatio = ratioFor(gear);
        setChanged();
        sendData();
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide && !ratioChecked) {
            ratioChecked = true;
            float expected = ratioFor(effectiveGear());
            if (Float.compare(expected, appliedRatio) != 0 && getBlockState().getBlock() instanceof TuffGearshiftBlock block) {
                // The config changed while unloaded: re-propagate with the new ratio.
                block.detachKinetics(level, worldPosition, true);
                appliedRatio = expected;
                sendData();
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide) clientTick();
    }

    private void clientTick() {
        GearstickAnimator anim = animator();
        int gear = effectiveGear();
        boolean powered = isLocked();
        if (anim.targetGear() != gear) {
            boolean forced = powered && !lastPowered && gear == GateLayout.R;
            if (ReverieConfig.get(ReverieConfig.ANIMATED_STICK)) anim.moveTo(gear, forced);
            else anim.snapTo(gear);
        }
        lastPowered = powered;
        anim.tick();
    }

    public GearstickAnimator animator() {
        if (animator == null) {
            animator = new GearstickAnimator(layout(), effectiveGear());
            lastPowered = isLocked();
            animator.setListener(this::playCue);
        }
        return animator;
    }

    private void playCue(GearstickAnimator.Cue cue, int gear) {
        if (level == null || muted) return;
        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 1.0;
        double z = worldPosition.getZ() + 0.5;
        float jitter = level.random.nextFloat() * 0.08f;
        switch (cue) {
            case RAIL_TICK -> level.playLocalSound(x, y, z, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.08f, 1.8f + jitter, false);
            case COMMIT -> level.playLocalSound(x, y, z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.35f,
                    0.9f + 0.06f * Math.min(gear, 6) + jitter, false);
            case REVERSE_SNAP -> level.playLocalSound(x, y, z, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.4f, 0.7f + jitter, false);
            case NEUTRAL_CLICK -> level.playLocalSound(x, y, z, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.3f, 1.4f + jitter, false);
            case REFUSE -> level.playLocalSound(x, y, z, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.4f, 0.6f + jitter, false);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt("SelectedGear", selectedGear);
        tag.putFloat("AppliedRatio", appliedRatio);
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        int sel = tag.contains("SelectedGear") ? tag.getInt("SelectedGear") : GateLayout.N;
        selectedGear = sel >= 0 && sel <= GateLayout.R ? sel : GateLayout.N;
        if (tag.contains("AppliedRatio")) {
            float r = tag.getFloat("AppliedRatio");
            appliedRatio = Float.isFinite(r) ? r : 0f;
        }
        super.read(tag, registries, clientPacket);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        int gear = effectiveGear();
        float in = getSpeed();
        float out = hasSource() ? in * appliedRatio : in;
        tooltip.add(Component.literal("    ").append(getBlockState().getBlock().getName()));
        tooltip.add(Component.literal("     ").append(Component.translatable("reverie.gearshift.goggles.gear",
                Component.literal(GateLayout.label(gear)).withStyle(gearColor(gear)), formatRatio(appliedRatio)).withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.literal("     ").append(Component.translatable("reverie.gearshift.goggles.speed",
                formatRpm(Math.abs(in)), formatRpm(Math.abs(out))).withStyle(ChatFormatting.GRAY)));
        if (isLocked()) {
            tooltip.add(Component.literal("     ").append(Component.translatable("reverie.gearshift.goggles.locked").withStyle(ChatFormatting.RED)));
        } else if (selectedGear != gear) {
            tooltip.add(Component.literal("     ").append(Component.translatable("reverie.gearshift.goggles.selected", GateLayout.label(selectedGear)).withStyle(ChatFormatting.DARK_GRAY)));
        }
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return true;
    }

    public static ChatFormatting gearColor(int gear) {
        return gear == GateLayout.R ? ChatFormatting.RED : gear == GateLayout.N ? ChatFormatting.GOLD : ChatFormatting.GREEN;
    }

    public static String formatRatio(float ratio) {
        if (ratio == 0) return "×0";
        String s = (ratio == Math.rint(ratio)) ? Integer.toString((int) ratio) : String.format(java.util.Locale.ROOT, "%.2f", ratio).replaceAll("0+$", "");
        return "×" + s;
    }

    public static String formatRpm(float rpm) {
        return rpm == Math.rint(rpm) ? Integer.toString((int) rpm) : String.format(java.util.Locale.ROOT, "%.1f", rpm);
    }
}
