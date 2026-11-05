package com.seba.reverie.registry;

import java.util.List;

import com.seba.reverie.Reverie;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Dream captions: vanilla sounds (see sounds.json) under new names, for their odd subtitles. */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Reverie.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> COUNTING = caption("counting");
    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_FAR = caption("door_far");
    public static final DeferredHolder<SoundEvent, SoundEvent> BED_CREAK = caption("bed_creak");
    public static final DeferredHolder<SoundEvent, SoundEvent> FOOTSTEPS_STOP = caption("footsteps_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> FOLDING = caption("folding");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOCK_BACKWARDS = caption("clock_backwards");

    public static final List<DeferredHolder<SoundEvent, SoundEvent>> CAPTIONS = List.of(COUNTING, DOOR_FAR, BED_CREAK, FOOTSTEPS_STOP, FOLDING, CLOCK_BACKWARDS);

    private static DeferredHolder<SoundEvent, SoundEvent> caption(String name) {
        return SOUNDS.register("caption." + name, () -> SoundEvent.createVariableRangeEvent(Reverie.id("caption." + name)));
    }

    private ModSounds() {
    }
}
