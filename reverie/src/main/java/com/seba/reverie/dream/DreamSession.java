package com.seba.reverie.dream;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Everything needed to give a dreamer their life back. Every field has a default so older saves (or a partly
 * written session) still decode.
 *
 * @param endTime overworld game time at which the dream ends on its own
 */
public record DreamSession(Stash stash, Vitals vitals, ReturnPoint ret, long startTime, long endTime,
                           List<SnapshotBlock> snapshot, boolean dreamCatcher) {

    public static final int SLOTS = 41;

    /**
     * @param items    41 slots: inventory 0-35, armor 36-39, offhand 40 (exactly as {@code Inventory#getItem})
     * @param heldSlot hotbar slot of the item taken into the dream (-1 if none)
     */
    public record Stash(List<ItemStack> items, int selectedSlot, int heldSlot, ItemStack heldItem) {
        public static final Codec<Stash> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Stash::items),
                Codec.INT.optionalFieldOf("selected", 0).forGetter(Stash::selectedSlot),
                Codec.INT.optionalFieldOf("held_slot", -1).forGetter(Stash::heldSlot),
                ItemStack.OPTIONAL_CODEC.optionalFieldOf("held", ItemStack.EMPTY).forGetter(Stash::heldItem)
        ).apply(i, Stash::new));
    }

    public record Vitals(float health, int food, float saturation, int xpLevel, float xpProgress, int xpTotal,
                         List<MobEffectInstance> effects) {
        public static final Codec<Vitals> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("health", 20f).forGetter(Vitals::health),
                Codec.INT.optionalFieldOf("food", 20).forGetter(Vitals::food),
                Codec.FLOAT.optionalFieldOf("saturation", 5f).forGetter(Vitals::saturation),
                Codec.INT.optionalFieldOf("xp_level", 0).forGetter(Vitals::xpLevel),
                Codec.FLOAT.optionalFieldOf("xp_progress", 0f).forGetter(Vitals::xpProgress),
                Codec.INT.optionalFieldOf("xp_total", 0).forGetter(Vitals::xpTotal),
                MobEffectInstance.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(Vitals::effects)
        ).apply(i, Vitals::new));
    }

    /** Where to wake: the bed if it still stands, else the exact spot the dream began (then respawn/spawn). */
    public record ReturnPoint(Optional<BlockPos> bed, ResourceKey<Level> dimension, Vec3 pos, float yRot) {
        public static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.optionalFieldOf("bed").forGetter(ReturnPoint::bed),
                ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension", Level.OVERWORLD).forGetter(ReturnPoint::dimension),
                Vec3.CODEC.optionalFieldOf("pos", Vec3.ZERO).forGetter(ReturnPoint::pos),
                Codec.FLOAT.optionalFieldOf("y_rot", 0f).forGetter(ReturnPoint::yRot)
        ).apply(i, ReturnPoint::new));
    }

    public record SnapshotBlock(BlockPos offset, BlockState state) {
        public static final Codec<SnapshotBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("o").forGetter(SnapshotBlock::offset),
                BlockState.CODEC.fieldOf("s").forGetter(SnapshotBlock::state)
        ).apply(i, SnapshotBlock::new));
    }

    private static final Stash EMPTY_STASH = new Stash(List.of(), 0, -1, ItemStack.EMPTY);
    private static final Vitals DEFAULT_VITALS = new Vitals(20f, 20, 5f, 0, 0f, 0, List.of());
    private static final ReturnPoint DEFAULT_RETURN = new ReturnPoint(Optional.empty(), Level.OVERWORLD, Vec3.ZERO, 0f);

    public static final Codec<DreamSession> CODEC = RecordCodecBuilder.create(i -> i.group(
            Stash.CODEC.optionalFieldOf("stash", EMPTY_STASH).forGetter(DreamSession::stash),
            Vitals.CODEC.optionalFieldOf("vitals", DEFAULT_VITALS).forGetter(DreamSession::vitals),
            ReturnPoint.CODEC.optionalFieldOf("return", DEFAULT_RETURN).forGetter(DreamSession::ret),
            Codec.LONG.optionalFieldOf("start", 0L).forGetter(DreamSession::startTime),
            Codec.LONG.optionalFieldOf("end", 0L).forGetter(DreamSession::endTime),
            SnapshotBlock.CODEC.listOf().optionalFieldOf("snapshot", List.of()).forGetter(DreamSession::snapshot),
            Codec.BOOL.optionalFieldOf("dream_catcher", false).forGetter(DreamSession::dreamCatcher)
    ).apply(i, DreamSession::new));

    public DreamSession withEnd(long end) {
        return new DreamSession(stash, vitals, ret, startTime, end, snapshot, dreamCatcher);
    }
}
