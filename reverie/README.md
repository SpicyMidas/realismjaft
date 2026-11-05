# Reverie

A Minecraft mod for **1.21.1** on **NeoForge 21.1.x** (built and tested against 21.1.265, Java 21).

Grow **Melatonia**, a flower that only blooms at night. Cook its petals into food that makes you dizzy, or boil
them down into an extract that makes you **Sleepy**. With Sleepyness you can lie down in a bed at any time of day,
and instead of skipping the night you **dream**: you wake up in **Dreamland**, a dimension of floating islands
under a sky with too many moons. You own nothing there but the item in your hand. Silhouettes called **Shades**
watch you from just too far away, and almost nothing you find survives waking up.

Two optional mods add more:

| Installed | Adds |
|---|---|
| [Create](https://github.com/Creators-of-Create/Create) 6.0.x (tested 6.0.10) | **Tuff Gearshift** and **Sport Tuff Gearshift** (multi-ratio gearboxes with an interactive gear-stick GUI), **Latch Remote** |
| [Farmer's Delight](https://github.com/vectorwing/FarmersDelight) 1.3.x (tested 1.3.4) | Cooking Pot and Cutting Board recipes, Lullaby Stew, Dozing Dumplings, Dreamy Pie, Night-Bloom Roll, Melatonia Syrup, and the *Sweet Dreams, Chef* advancement (eat every dish) |

Without them Reverie works on its own. Their content simply does not exist, and nothing breaks.

## Playing

1. **Find Wild Melatonia** in forests, plains and meadows (pale violet flowers). Break it for seeds (and sometimes a
   petal), then plant the seeds on farmland. It grows faster at night and in the dark, and slower in full sun.
2. **Melatonia Extract**: 3 Petals + a Glass Bottle. Drinking it makes you Dizzy for 20 s.
3. **Concentrated Melatonia Extract**: 3 Extracts + Glowstone Dust (returns 2 bottles). Drinking it gives
   **Sleepyness** for 4 minutes.
4. While Sleepy, **sleep in any bed, at any time**. Whatever you hold goes with you into the dream. Everything else
   stays behind, safe (see *Your items are safe* below).
5. A dream lasts 5 minutes. It ends when the time runs out, when you fall into the void, when you die, or when you
   lie down in a bed inside the dream (the Great Bed islands are the kind way out). You wake in your bed with your
   own inventory back. Each stack you picked up in the dream has a small chance to come back with you.

Food: any food can be **laced** with an Extract in the crafting grid (strongly with a Concentrated one). A laced
food also makes you Dizzy, and a strongly laced one also makes you Sleepy. Melatonia Cookie, Drowsy Tea and
Sleepy Stew are the plain recipes.

### Dreamland

- Eight biomes: Pillow Fields, Hollow Spires, Mirror Marsh, Sandglass Dunes, Eye Meadow, Upside-Down Town,
  Cloudreach and the Void Between.
- Islands in nine shapes: plateaus, spires, rings, inverted islands, a stairway into the sky, archipelagos, the
  Great Bed, the Sea of Doors and clouds.
- Landmarks: Lone Doors (walk through and you arrive somewhere else, and sometimes they lie), the Staircase to
  Nowhere, hanging houses, the Sleep Bell Grove, hourglasses with time bubbles, a giant clock, the Impossible
  Stairs, and a **Memory Island**: a scrambled echo of the room you fell asleep in.
- The sky: stars that slowly gather into constellations that are never the same twice, three moons of the wrong
  sizes (the big one trails behind you as you walk), a ringed planet, aurora curtains, a paper whale swimming
  slowly past, and a closed Eye on the horizon that opens as the dream ends.
- Dream weather: Starfall (sometimes a star lands and leaves a Lucid Shard), Petal Drift, Fog Tide, Upward Rain,
  and The Long Silence.
- **Shades**: a crowd of harmless silhouettes always 28 to 52 blocks away, watching. Walk toward one and it is gone.
  Now and then the whole crowd does something stranger, and once in a while one of them is shaped like you.
- Odd subtitles from nowhere (turn subtitles on), a procedural lullaby, and one recurring object that the dream
  keeps returning to.

### Create: Tuff Gearshifts and the Latch Remote

- **Tuff Alloy**: Tuff + Iron Nugget + Gold Nugget, or 2 per Create Mixing.
- **Tuff Gearshift**: R, N and gears 1 to 4. The **Sport** version has 6 gears. Right-click it to move the stick in
  the gate. Redstone power locks it into R, and the selected gear comes back when the power goes off. Ratios are in
  the config.
- **Latch Remote**: a Linked Controller that latches. One click turns the signal on, the next turns it off.
  Sneak-use it in the air to set its frequency pair, or sneak-click a Redstone Link to copy that link's frequencies.

**Dream Catcher** (sticks, string, feathers and a Lucid Shard): hold it as you fall asleep and one thing you find in
that dream has at least an even chance to come back with you. The catcher itself stays in the dream.

## Your items are safe

When you fall asleep, Reverie saves your whole inventory, health, hunger, XP and effects:

- in your player data (a data attachment), and
- in a backup file: `<world>/reverie_backups/<player-uuid>-<time>.nbt` (compressed NBT, newest 20 kept per player;
  see `backupsKeptPerPlayer`).

If the game or the server crashes mid-dream, or you log out, you are woken up properly the next time you join (or
the dream resumes, if time is left).

Admins can list and restore backups:

```
/reverie backup list <player>
/reverie backup restore <player> <n>
```

`<n>` is 1 for the newest. Restoring **gives back** the stacks from that backup (inventory first, the rest dropped
at the player's feet). It never deletes or overwrites anything, so check the player's inventory first.

**If you remove Reverie from a world while someone is dreaming,** their real inventory is still in
`reverie_backups/`. Each file holds the session under `session`, with the original inventory in
`session.stash.items` (slots 0 to 35 inventory, 36 to 39 armour, 40 offhand) and the item they carried into the
dream in `session.stash.held` (its slot in `session.stash.held_slot`). Any NBT editor can read it.

## Commands

All `/reverie` commands need permission level 2, except `/reverie wake` on yourself.

| Command | |
|---|---|
| `/reverie wake [player]` | End a dream now |
| `/reverie dream <player>` | Start a dream without a bed (not from inside Dreamland) |
| `/reverie home <player>` | Show and teleport to a player's dream home |
| `/reverie shades clear` / `count` | Remove all Shades / count them |
| `/reverie backup list <player>` / `restore <player> <n>` | See *Your items are safe* |
| `/reverie debug dreamstate <player>` | Dream state, cooldown, backups |
| `/reverie debug genstats` | Dreamland generation timings |
| `/reverie debug shadeevent <player> <event>` | hush, everyone_turns, behind_you, gravity_hiccup, counting, mimic |
| `/reverie debug weather <player> <kind>` | starfall, petal_drift, fog_tide, upward_rain, long_silence |

## Configuration

`config/reverie-common.toml` (both sides; on a server, the server's copy decides):

| Option | Default | |
|---|---|---|
| `dream.dreamLengthTicks` | 6000 | Length of a dream (6000 = 5 min) |
| `dream.dreamCooldownTicks` | 3000 | After waking, no new dream for this long |
| `dream.keepsakeChance` | 0.05 | Chance for each dream stack to survive waking |
| `dream.luckyKeepsakeChance` | 0.35 | The same, for `#reverie:lucid_keepsakes` |
| `dream.maxKeepsakes` | 1 | Most stacks brought back from one dream |
| `dream.backupsKeptPerPlayer` | 20 | Backup files kept per player |
| `dream.memoryIsland` | true | Build the Memory Island |
| `gearshift.gearRatios` | [1, 1.5, 2, 2.5, 3, 4] | Forward ratios (the regular gearshift uses the first four) |
| `gearshift.reverseRatio` | -1.0 | Ratio of R |
| `melatonia.nightBloomerGrowthBoost` | true | Faster growth at night and in the dark, slower in full sun |
| `melatonia.wildMelatoniaFrequency` | 24 | 1 patch in N chunks, 0 = none (needs a restart) |
| `latch_remote.transmitWhileInContainers` | false | Keep transmitting from inside a shulker box or backpack |
| `dreamland.shadeCount` | 28 | Shades per dreamer (×1.6 in Hollow Spires) |
| `dreamland.shadeFleeRadius` | 20 | A Shade vanishes when you come this close |
| `dreamland.maxShadesPerDreamer` | 48 | Hard cap |
| `dreamland.maxShadesPerServer` | 400 | Hard cap |
| `dreamland.dreamCaptions` | true | Odd sounds with odd subtitles |
| `dreamland.timeBubbles` | true | Sandglass time bubbles |
| `dreamland.weatherEvents` | true | Starfall, Petal Drift, Fog Tide, Upward Rain, The Long Silence |
| `dreamland.wrongRain` | true | In some biomes petals, pages and rain fall upward |
| `dreamland.recurringObjects` | true | The object the dream keeps returning to |
| `dreamland.lyingDoors` | true | Lone Doors sometimes lead to a copy of where you were, one block off |
| `dreamland.enableRareEvents` | true | Master switch for the Shade events below |
| `dreamland.events.hush` / `everyoneTurns` / `behindYou` / `counting` / `gravityHiccup` / `farewell` | true | Each Shade event |
| `dreamland.events.mimic` | true | Mimic: your own silhouette copies you, three seconds late |

`config/reverie-client.toml`:

| Option | Default | |
|---|---|---|
| `animatedStick` | true | Animate the gearshift stick |
| `lullaby` / `lullabyVolume` | true / 0.6 | The procedural lullaby in Dreamland (it replaces the game's music there; the Music slider applies too) |
| `ambientVolume` | 0.8 | Reverie's captions and weather sounds |
| `dizzinessIntensity` | 1.0 | Camera sway while Dizzy (0 = off) |
| `customSky` | true | The Dreamland sky (off = plain vanilla sky) |
| `eyelidOverlay` | true | Heavy eyelids while Sleepy |
| `shadeGlow` | true | Pale eyes on a Shade you stare at |
| `reduceMotion` | false | Halves camera roll and FOV wobble |

## Known limitations

- **"Experimental settings" prompt.** Minecraft warns once, when you open a world, that it uses experimental
  settings. That is how vanilla treats any world with a mod-added dimension. Choose *Proceed* (NeoForge
  remembers the answer).
- **Shader packs.** The Dreamland sky is drawn with simple quads. If drawing it fails it switches itself off for
  the session and the vanilla sky is used instead.
- **Gearshifts on contraptions.** A gearshift moved by a Create contraption keeps its gear, which is saved in the
  block entity. Its stick is drawn by a block-entity renderer, and whether it shows on a moving contraption was not
  tested. Contraptions do not transmit rotation through it while moving, as with every Create gearbox.
- **The Impossible Stairs** are a closed loop of floating stairs that seems to climb forever from its plaque. It is
  not a true Penrose illusion: perspective on a block grid cannot hide that seam.
- **Recipe viewers** (checked with JEI) list the extracts, foods, Cooking Pot and Cutting Board recipes. Lacing is
  a special crafting recipe (any food + an Extract), so viewers do not list it.
- The Sleepwalking Sheep and Lucid Moths (stretch goals in the design) are not in this version. The Paper Whale is
  there, as a sky object rather than an entity.

## Building from source

```
./gradlew build
```

The jar goes to `build/libs/`. Useful extras:

- `python3 tools/gen_textures.py`: regenerates every texture (all art is procedural; add `--preview DIR` for a contact sheet)
- `python3 tools/gen_models.py`: regenerates the hand-built gearshift models and blockstates
- `./gradlew runData -Preverie.compat=all`: datagen (recipes, loot, tags, lang, models, advancements, worldgen)
- `tools/devrun.sh client|server|gametest none|create|all [script]`: scripted dev runs with the dev-only test mod
  (`src/devtools`). A dedicated server run of `tools/devscripts/server_tests.txt` executes the Dreamland gametests.

## Credits

- Optional integrations with **Create** (by the Create team) and **Farmer's Delight** (by vectorwing). Reverie
  only talks to them through their public classes and data formats, behind mod-loaded checks.
- All sounds are Minecraft's own, reused through `sounds.json`. All textures are generated by `tools/gen_textures.py`.
  There are no third-party assets.

License: All Rights Reserved (see `gradle.properties`).
