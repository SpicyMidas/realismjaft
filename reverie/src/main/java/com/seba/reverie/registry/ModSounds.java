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

    // Dreamland ambience: pitched-down vanilla sounds (see sounds.json).
    public static final DeferredHolder<SoundEvent, SoundEvent> DREAM_HUM = ambient("dream_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPIRE_DRONE = ambient("spire_drone");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOID_WIND = ambient("void_wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> MARSH_HUM = ambient("marsh_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOWN_HUM = ambient("town_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> FAR_BELL = ambient("far_bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLASS_HUM = ambient("glass_hum");

    private static DeferredHolder<SoundEvent, SoundEvent> ambient(String name) {
        return SOUNDS.register("ambient." + name, () -> SoundEvent.createVariableRangeEvent(Reverie.id("ambient." + name)));
    }

    public static final List<DeferredHolder<SoundEvent, SoundEvent>> CAPTIONS = List.of(COUNTING, DOOR_FAR, BED_CREAK, FOOTSTEPS_STOP, FOLDING, CLOCK_BACKWARDS);

    private static DeferredHolder<SoundEvent, SoundEvent> caption(String name) {
        return SOUNDS.register("caption." + name, () -> SoundEvent.createVariableRangeEvent(Reverie.id("caption." + name)));
    }

    private ModSounds() {
    }
}
