package com.seba.reverie;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * All tunables (spec section 10). Read through {@link #get} so a value read before the file is loaded
 * (datagen, early gametests) falls back to the default instead of throwing.
 */
public final class ReverieConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;

    // --- dream session
    public static final ModConfigSpec.IntValue DREAM_LENGTH_TICKS;
    public static final ModConfigSpec.IntValue DREAM_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue KEEPSAKE_CHANCE;
    public static final ModConfigSpec.DoubleValue LUCKY_KEEPSAKE_CHANCE;
    public static final ModConfigSpec.IntValue MAX_KEEPSAKES;
    public static final ModConfigSpec.IntValue BACKUPS_KEPT_PER_PLAYER;
    public static final ModConfigSpec.BooleanValue MEMORY_ISLAND;

    // --- gearshift
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> GEAR_RATIOS;
    public static final ModConfigSpec.DoubleValue REVERSE_RATIO;

    // --- melatonia
    public static final ModConfigSpec.BooleanValue NIGHT_BLOOMER_GROWTH_BOOST;
    public static final ModConfigSpec.IntValue WILD_MELATONIA_FREQUENCY;

    // --- remote
    public static final ModConfigSpec.BooleanValue TRANSMIT_WHILE_IN_CONTAINERS;

    // --- shades & phenomena
    public static final ModConfigSpec.IntValue SHADE_COUNT;
    public static final ModConfigSpec.IntValue SHADE_FLEE_RADIUS;
    public static final ModConfigSpec.IntValue MAX_SHADES_PER_DREAMER;
    public static final ModConfigSpec.IntValue MAX_SHADES_PER_SERVER;
    public static final ModConfigSpec.BooleanValue DREAM_CAPTIONS;
    public static final ModConfigSpec.BooleanValue TIME_BUBBLES;
    public static final ModConfigSpec.BooleanValue WEATHER_EVENTS;
    public static final ModConfigSpec.BooleanValue WRONG_RAIN;
    public static final ModConfigSpec.BooleanValue RECURRING_OBJECTS;
    public static final ModConfigSpec.BooleanValue LYING_DOORS;
    public static final ModConfigSpec.BooleanValue ENABLE_RARE_EVENTS;
    public static final ModConfigSpec.BooleanValue EVENT_HUSH;
    public static final ModConfigSpec.BooleanValue EVENT_EVERYONE_TURNS;
    public static final ModConfigSpec.BooleanValue EVENT_BEHIND_YOU;
    public static final ModConfigSpec.BooleanValue EVENT_MIMIC;
    public static final ModConfigSpec.BooleanValue EVENT_COUNTING;
    public static final ModConfigSpec.BooleanValue EVENT_GRAVITY_HICCUP;
    public static final ModConfigSpec.BooleanValue EVENT_FAREWELL;

    // --- client
    public static final ModConfigSpec.BooleanValue ANIMATED_STICK;
    public static final ModConfigSpec.BooleanValue LULLABY;
    public static final ModConfigSpec.DoubleValue LULLABY_VOLUME;
    public static final ModConfigSpec.DoubleValue AMBIENT_VOLUME;
    public static final ModConfigSpec.DoubleValue DIZZINESS_INTENSITY;
    public static final ModConfigSpec.BooleanValue CUSTOM_SKY;
    public static final ModConfigSpec.BooleanValue EYELID_OVERLAY;
    public static final ModConfigSpec.BooleanValue SHADE_GLOW;
    public static final ModConfigSpec.BooleanValue REDUCE_MOTION;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("dream");
        DREAM_LENGTH_TICKS = b.comment("Length of a dream in ticks (6000 = 5 minutes).")
                .defineInRange("dreamLengthTicks", 6000, 600, 72000);
        DREAM_COOLDOWN_TICKS = b.comment("After waking, Sleepyness does not let you dream again for this many ticks.")
                .defineInRange("dreamCooldownTicks", 3000, 0, 72000);
        KEEPSAKE_CHANCE = b.comment("Chance for each dream-inventory stack to survive waking up.")
                .defineInRange("keepsakeChance", 0.05, 0.0, 1.0);
        LUCKY_KEEPSAKE_CHANCE = b.comment("Survival chance for stacks tagged #reverie:lucid_keepsakes.")
                .defineInRange("luckyKeepsakeChance", 0.35, 0.0, 1.0);
        MAX_KEEPSAKES = b.comment("Maximum number of stacks brought back from one dream.")
                .defineInRange("maxKeepsakes", 1, 0, 36);
        BACKUPS_KEPT_PER_PLAYER = b.comment("How many inventory backup files (reverie_backups/) to keep per player.")
                .defineInRange("backupsKeptPerPlayer", 20, 1, 1000);
        MEMORY_ISLAND = b.comment("Build a warped echo of the room you fell asleep in next to your Home Island.")
                .define("memoryIsland", true);
        b.pop();

        b.push("gearshift");
        GEAR_RATIOS = b.comment("Output/input ratio for forward gears 1..6 (x1, x2, x4, x6, x8, x10; the regular gearshift uses the first four).",
                        "A gear that would spin faster than Create's maxRotationSpeed is refused.")
                .defineList("gearRatios", List.of(1.0, 2.0, 4.0, 6.0, 8.0, 10.0), () -> 1.0,
                        o -> o instanceof Number n && Math.abs(n.doubleValue()) <= 16);
        REVERSE_RATIO = b.comment("Ratio of gear R (negative = reversed).")
                .defineInRange("reverseRatio", -1.0, -16.0, 16.0);
        b.pop();

        b.push("melatonia");
        NIGHT_BLOOMER_GROWTH_BOOST = b.comment("Melatonia grows faster at night / in the dark and slower in full daylight.")
                .define("nightBloomerGrowthBoost", true);
        WILD_MELATONIA_FREQUENCY = b.comment("Rarity of Wild Melatonia patches in the Overworld (1 in N chunks, 0 disables). Needs a world restart.")
                .worldRestart()
                .defineInRange("wildMelatoniaFrequency", 24, 0, 1024);
        b.pop();

        b.push("latch_remote");
        TRANSMIT_WHILE_IN_CONTAINERS = b.comment("Keep transmitting while an ON remote sits in a container (shulker box, backpack) inside the player's inventory.")
                .define("transmitWhileInContainers", false);
        b.pop();

        b.push("dreamland");
        SHADE_COUNT = b.comment("Shades kept around each dreamer (more in Hollow Spires).")
                .defineInRange("shadeCount", 28, 0, 96);
        SHADE_FLEE_RADIUS = b.comment("A shade vanishes when the dreamer comes this close.")
                .defineInRange("shadeFleeRadius", 20, 4, 64);
        MAX_SHADES_PER_DREAMER = b.defineInRange("maxShadesPerDreamer", 48, 0, 128);
        MAX_SHADES_PER_SERVER = b.defineInRange("maxShadesPerServer", 400, 0, 4000);
        DREAM_CAPTIONS = b.comment("Rare odd sounds with odd subtitles.").define("dreamCaptions", true);
        TIME_BUBBLES = b.define("timeBubbles", true);
        WEATHER_EVENTS = b.comment("Starfall, Petal Drift, Fog Tide, Upward Rain, The Long Silence.").define("weatherEvents", true);
        WRONG_RAIN = b.define("wrongRain", true);
        RECURRING_OBJECTS = b.define("recurringObjects", true);
        LYING_DOORS = b.comment("Lone Doors sometimes lead to a copy of the spot you left, one block off.").define("lyingDoors", true);
        ENABLE_RARE_EVENTS = b.define("enableRareEvents", true);
        b.push("events");
        EVENT_HUSH = b.define("hush", true);
        EVENT_EVERYONE_TURNS = b.define("everyoneTurns", true);
        EVENT_BEHIND_YOU = b.define("behindYou", true);
        EVENT_MIMIC = b.define("mimic", true);
        EVENT_COUNTING = b.define("counting", true);
        EVENT_GRAVITY_HICCUP = b.define("gravityHiccup", true);
        EVENT_FAREWELL = b.define("farewell", true);
        b.pop();
        b.pop();

        COMMON_SPEC = b.build();

        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        ANIMATED_STICK = c.comment("Animate the Tuff Gearshift stick (off = static stick at the committed gear).")
                .define("animatedStick", true);
        LULLABY = c.define("lullaby", true);
        LULLABY_VOLUME = c.defineInRange("lullabyVolume", 0.6, 0.0, 1.0);
        AMBIENT_VOLUME = c.defineInRange("ambientVolume", 0.8, 0.0, 1.0);
        DIZZINESS_INTENSITY = c.defineInRange("dizzinessIntensity", 1.0, 0.0, 2.0);
        CUSTOM_SKY = c.define("customSky", true);
        EYELID_OVERLAY = c.define("eyelidOverlay", true);
        SHADE_GLOW = c.comment("Pale pinprick eyes on Shades you look at.").define("shadeGlow", true);
        REDUCE_MOTION = c.comment("Halves camera roll / FOV wobble.").define("reduceMotion", false);
        CLIENT_SPEC = c.build();
    }

    private ReverieConfig() {
    }

    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (RuntimeException notLoaded) {
            return value.getDefault();
        }
    }

    public static double gearRatio(int forwardGear) {
        List<? extends Double> ratios = get(GEAR_RATIOS);
        int i = forwardGear - 1;
        if (i < 0) return 0;
        if (i < ratios.size()) {
            Object o = ratios.get(i);
            if (o instanceof Number n) return n.doubleValue();
        }
        double[] defaults = {1.0, 2.0, 4.0, 6.0, 8.0, 10.0};
        return i < defaults.length ? defaults[i] : 1.0;
    }
}
