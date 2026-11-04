# REVERIE — Mod Specification (DREAMLAND_SPEC.md)

> Working name: **Reverie** (mod id `reverie`). Rename freely; keep the id consistent everywhere.
> Target: **Minecraft 1.21.1 · NeoForge 21.1.x · Java 21**.
> Hard goal: **zero crashes** — with or without Create / Farmer's Delight installed.

---

## 0. Instructions for Claude Code (read first)

1. Read this whole file before writing any code. Work in the **phases of section 11**. After every phase the project must compile and `runClient` + `runServer` must start and load a world with no errors in the log. Do not start a phase until the previous one passes its acceptance checks.
2. **Never trust API names written in this file from memory.** Class names, method signatures and event names for Create, Farmer's Delight and NeoForge below are my best recollection. Before using one, verify it against the real sources (decompile/inspect the dependency jars, or the `-sources` jars). Where reality differs, follow reality and keep the *behaviour* described here.
3. Look up the **latest 1.21.1 builds** yourself. Known: Farmer's Delight for 1.21.1 NeoForge is at least **1.3.2** (CurseMaven: `curse.maven:farmers-delight-398521:8083481`). For Create, use the official Create maven (`maven.createmod.net`) and pick the newest 1.21.1 / NeoForge build (Create 6.x); it pulls Flywheel, Ponder and Registrate transitively. NeoForge + ModDevGradle: use the newest 21.1.x.
4. Prefer **events and data-driven content over Mixins**. Add a Mixin only if there is no event/API alternative, and say why in a comment.
5. Use **data generation** (`runData`) for blockstates, models, lang, loot tables, recipes and tags wherever practical. Provide `en_us` and `it_it` lang files.
6. Textures: generate placeholder-but-decent **16×16 pixel-art PNGs procedurally** (Python/Pillow script kept in `tools/`). Style: vanilla-like, readable. Do not leave missing-texture magenta anywhere.
7. Everything tunable goes in a **config** (`ModConfigSpec`): see section 10.
8. Mark every item in this spec with its priority: **[P0]** must ship, **[P1]** should ship, **[P2]** stretch. Finish all P0 and P1 before touching any P2.

---

## 1. Concept in one paragraph

Reverie adds a night-blooming plant, **Melatonia**, whose petals can be cooked into food that makes you **Dizzy**, or boiled down into a **Concentrated Extract** that gives the **Sleepyness** effect. With Sleepyness you can sleep in a bed at any time of day, and when you do, you do not skip the night — you **dream**: you are teleported into **Dreamland**, a surreal dimension of floating islands where you own nothing but the item in your hand, silhouettes of **Shades** watch you from just too far away, and almost nothing you collect survives waking up. As a Create add-on it also adds **Tuff Alloy**, the **Tuff Gearshift** and **Sport Tuff Gearshift** (multi-ratio gearboxes with an interactive 2D gear-stick GUI), and a **Latch Remote** (a Linked Controller that latches a redstone signal on/off with one click).

Content modules:

| Module | Needs | Priority |
|---|---|---|
| A. Tuff Alloy, Tuff Gearshift, Sport Tuff Gearshift | Create | P0 |
| B. Latch Remote | Create | P0 |
| C. Melatonia plant, extracts, foods, lacing, Dizziness | none | P0 |
| D. Sleepyness + dream session logic | none | P0 |
| E. Dreamland dimension | none | P0 |
| F. Shades + dream fauna | none | P1 (Shades) / P2 (fauna) |
| G. Farmer's Delight integration | Farmer's Delight | P1 |

---

## 2. Project setup & dependency rules

- Mod id `reverie`, base package `com.seba.reverie` (change if you like).
- `neoforge.mods.toml`:
  - `neoforge` dependency: required, `[21.1.0,)`; `minecraft`: required `[1.21.1]`.
  - `create` and `farmersdelight`: **`type = "optional"`**, `ordering = "AFTER"`, side `BOTH`.
- **Soft-dependency discipline (critical for no-crash):**
  - The `@Mod` entry class and all always-on code must **never import, reference or even mention-by-signature** any Create or Farmer's Delight class.
  - All Create code lives in package `com.seba.reverie.compat.create`, all FD code in `com.seba.reverie.compat.fd`. These packages are only touched from code behind `ModList.get().isLoaded("create")` / `("farmersdelight")`, ideally via a tiny bootstrap class that is only loaded when the check passes.
  - Create-dependent registries (Tuff Gearshifts, Latch Remote) are registered **only if Create is loaded**. If Create is absent, those items/blocks simply do not exist (no placeholders). Tuff Alloy is a plain item and **always exists**.
  - Recipes/loot/tags that reference optional-mod content use NeoForge load conditions (`neoforge:mod_loaded`) or `"required": false` tag entries.
  - Client-only code (screens, renderers, sky, shaders) lives in `client` packages and is only referenced from client event subscribers (`Dist.CLIENT`).
- Dev environment: add Create and Farmer's Delight as `runtimeOnly`/`compileOnly` so `runClient` can test all three combinations: (1) neither, (2) Create only, (3) both. Provide Gradle run configs or a property toggle for these.

---

## 3. Module A — Tuff Alloy & Tuff Gearshifts (Create add-on) [P0]

### 3.1 Tuff Alloy [P0]
- Item `reverie:tuff_alloy` (grey-green, tuff-flecked ingot-like item with gold and iron specks).
- Crafting (shapeless): **1 Tuff + 1 Iron Nugget + 1 Gold Nugget → 1 Tuff Alloy** (modeled on Create's Andesite Alloy).
- If Create is loaded: also a `create:mixing` recipe (no heat): same inputs → **2 Tuff Alloy** (reward for automation).
- Tag it `c:ingots/tuff_alloy`.

### 3.2 Block variants [P0]
Two blocks, one shared block-entity class:

| Block | Id | Gears |
|---|---|---|
| Tuff Gearshift | `reverie:tuff_gearshift` | **R, N, 1, 2, 3, 4** |
| Sport Tuff Gearshift | `reverie:sport_tuff_gearshift` | **R, N, 1, 2, 3, 4, 5, 6** |

Ratios (output speed = input speed × ratio). Put them in the config as a list so they can be tuned:

| Gear | R | N | 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|---|---|
| Ratio | −1 | 0 | ×1 | ×1.5 | ×2 | ×2.5 | ×3 | ×4 |

- "R" = same speed as gear 1 but **reversed direction** (configurable `reverseRatio`, default −1).
- "N" = neutral: the output side does not rotate (modifier 0, the same trick Create's Clutch uses).
- Gear 1 = output speed equals input speed.

### 3.3 Kinetic behaviour [P0]
- Model it on Create's own **Gearshift**: an encased-shaft-style block with an `AXIS` property and two shaft ends, built on `SplitShaftBlockEntity` (verify the real class names in the Create version you target). Input = whichever side the kinetic source arrives from; output = the opposite side, multiplied by the current ratio via `getRotationSpeedModifier(Direction face)` (return 1 for the source face, the ratio for the other).
- Changing gear (or redstone state) must correctly update the kinetic network — look at how Create's Clutch/Gearshift trigger `detachKinetics()`/`attachKinetics()` and mirror that. No stale speeds, no rotation-propagation errors in the log.
- **Do not hard-clamp speeds.** Create's own max-RPM / overstress rules should apply naturally (e.g. 256 RPM × 4 will overspeed — that is the player's problem and expected). Just verify it behaves like any other over-speed network and does not crash.
- Support Create wrench (rotate, dismantle), goggles tooltip (`IHaveGoggleInformation`: shows gear, input RPM, output RPM, "locked by redstone"), super glue, and being movable if Create allows it for the vanilla Gearshift (copy whatever Create does; skip contraption support if it needs heavy work — mention it in the README).
- Rendering: spinning shaft halves like Create's Gearshift (use Create's own approach for its split shaft — Flywheel visual if that is what Create uses, otherwise a block-entity renderer). **If the visual path proves fragile, fall back to a plain BlockEntityRenderer; never ship a crashing visual.** Full model and gearstick detail: **section 3.7**.
- **Gear indicator on the block:** blockstate property `gear` (0=N, 1–6, 7=R) selects a model whose small gear window shows the engraved digit/letter; `powered` lights a red dot (details in 3.7).

### 3.4 Redstone behaviour [P0]
- The block stores two gears: **selected gear** (what the player chose, remembered, saved to NBT) and **effective gear** (what is actually applied).
- While the block receives **any redstone signal**: effective gear = **R**. The selected gear is untouched and remembered.
- When the signal stops: effective gear returns to the remembered selected gear.
- While powered the GUI lever is **locked in R** (shown with a red "REDSTONE" lamp); the player cannot change the selection until power drops. (Server rejects change packets while powered.)

### 3.5 The Gearshift GUI [P0] — interactive 2D gear stick
Opened by right-click (no sneak, no wrench in hand). Client opens the screen directly; **all state changes go through a server-bound payload that the server validates** (block exists, player within 8 blocks, not redstone-locked, gear valid for this variant, only bump state if changed).

Design: a car-style **gate plate** with a draggable **gear knob** (leather-boot gate visual optional). Logical layout (columns left→right; the horizontal line is the neutral rail):

```
Sport (6-speed)                    Regular (4-speed)

 1     3     5     R                1     3     R
 |     |     |     |                |     |     |
 +-----+--N--+-----+                +-----N-----+
 |     |     |                      |     |
 2     4     6                      2     4
```

- Odd gears on the top row, even gears on the bottom row; R is a short column at the far right with only its top slot.
- N is the rest position on the middle of the rail.
- Interaction: **mouse-drag the knob**. The knob is constrained to the gate path (rail + vertical column slots); it cannot cut corners — to change column it must travel along the rail. Dragging into a slot end commits that gear (single payload, not per-frame). Releasing on the rail commits N and snaps to the rest position. Releasing mid-column returns to the previously committed gear (animated spring-back).
- Keyboard shortcuts while open: `R`, `N`, `1`–`6` jump the knob to that gear (animated along the path).
- Right of the gate: a small readout panel — **IN rpm → OUT rpm**, ratio, redstone lamp. Gear labels glow on the active slot.
- Feedback: a mechanical "clunk" on each commit (reuse vanilla sounds with pitch variation, e.g. iron trapdoor / piston), subtle knob shake when dragged against a wall of the gate.
- Draw it with `GuiGraphics` primitives plus one small texture (metal plate, knob, lamp). Keep it resolution-independent (use GUI scale, not hard-coded pixels).
- Screen must close cleanly if the block is removed, the player moves out of range, or the dimension changes.

### 3.6 Recipes [P0] (tunable)
- **Tuff Gearshift** (shaped):
  ```
  .  R  .      R = redstone dust
  A  C  A      A = tuff_alloy, C = create:andesite_casing
  .  G  .      G = create:cogwheel
  ```
- **Sport Tuff Gearshift** (shapeless): 1 Tuff Gearshift + 2 `c:ingots/brass` + 1 redstone dust.
- Both appear in the Reverie creative tab and in recipe viewers via normal data (no special JEI code needed).

### 3.7 Model, detail & the animated gearstick [P0 — the visual centrepiece]

Put real effort here: the block must read as a small piece of **machinery with a gearstick on top**, not as a textured cube. Author it as a multi-element JSON block model (datagen or hand-written), with blockstate rotations for the three axes.

**Casing (both variants)**
- Core body 14×14×12 px centred on the shaft axis, with chamfered edges (thin 45° elements), plus a 16×16×2 px **end flange** at each end along the axis, so it reads as a gearbox housing.
- Each flange has four **bolt heads** (2×2×1 px) at the corners and a brass-coloured **collar ring** (6×6×1 px) around the shaft hole. The Create-style shafts that stick out of both ends are the animated, spinning part (see 3.3).
- Panels: tuff-textured (grey-green with pale flecks), recessed panel lines, and a 1 px **gold inlay strip** along each long edge (nod to the gold in Tuff Alloy). A small vent grille (3 slits) on the underside.
- **Gear window:** on each of the two side faces that carry no stick, a 4×4 px recessed window with the engraved gear symbol (blockstate `gear`: N, 1–6, R). Emissive/fullbright pixels: **green** forward gears, **amber** N, **red** R. `powered` adds a small emissive red dot next to it.

**Mount face & gate plate**
- The stick sits on the **top (+Y) face** when the shaft axis is horizontal. When `AXIS=Y` it sits on the **north (−Z) face** instead.
- A **recessed gate plate** (Regular 10×6 px, Sport 12×6 px, sunk 1 px) with the real H-pattern from section 3.5 engraved as dark grooves and tiny numerals 1–4 (Sport 1–6) and R. The plate texture may be 32×32 or 64×64 so the numerals stay crisp (use a separate texture file for it).
- The gate's long axis is aligned with the **shaft axis**; the neutral rail runs along it, columns across it.

**The gearstick (the small lever on top)**
- Rod ≈ 1.5×1.5 px square-ish (use 2 crossed thin elements for a round look), ~5 px tall (Sport ~6 px).
- **Rubber boot** around the base: three stacked tapering cuboids (6×1×6, 5×1×5, 4×1×4 px) in dark stitched leather.
- **Knob**: Regular = black 3×3×3 px knob with a thin gold collar; Sport = round metal-grey 3.5 px ball with a red band and one pixel "S" emblem. Sport casing also gets a red stripe along the side panels and a polished-brass shaft collar.
- Item/inventory model: complete block with the stick at N; display transforms tuned for gui (30° isometric), hand, ground, frame, fixed.

**Animation (block entity renderer; the stick + upper boot is a separate baked model)**
- Register the stick as an additional baked model (`ModelEvent.RegisterAdditional`) and render it with a `BlockEntityRenderer`, pivoting at the plate. Do **not** put the stick in the static block model (keep only plate + boot base there).
- **One source of truth:** a shared `GateLayout` class (slot coordinates, rail, columns, path graph) used by BOTH the GUI (3.5) and this renderer, so they can never disagree.
- On a gear change the stick travels along the **real path**: pull out of its slot → back to the rail → slide along the rail → push into the new slot. 8–12 ticks total, ease-in-out, then a 2-tick settle with a tiny overshoot (the "clunk"), synchronised with the shift sound. Redstone-forced R: much faster (≈3 ticks), harder snap, then a 4-tick vibration.
- **Alive when running:** a tiny sinusoidal tilt jitter ±0.6° proportional to output RPM (capped); in **N while the input still turns** the stick sways loosely in the rail (±2°, "idle rattle"). Locked-by-redstone + a player trying the GUI → short refusal shake.
- Boot flex: the upper boot segments rotate/scale toward the tilt (no mesh deformation needed).
- Client-side animation state derives from the BE's synced `effectiveGear`; it is cosmetic only, never used for logic. Smooth with partial ticks. Skip rendering beyond ~48 blocks. Config `animatedStick` (default true); when off, bake the stick statically at the committed gear.
- The GUI draws the same knob/boot art, so GUI and block match.
- Sounds (reuse vanilla events, vary pitch): travel along the rail = soft ratchet ticks; commit = metallic clunk, pitch rising slightly with gear number; R = heavier snap; N = soft click.


---

## 4. Module B — Latch Remote (Create add-on) [P0]

> By definition the Latch Remote needs a Redstone Link receiver, and the Tuff Gearshifts are Create kinetic blocks. **They are Create-only content by design:** without Create they simply do not exist. That is intended — this mod is partly a Create add-on. Do not add stand-alone replacements.

An item that behaves like Create's **Linked Controller**, except it **latches**: one right-click starts sending a redstone signal permanently; the next right-click stops it.

- Item `reverie:latch_remote`, stack size 1. Texture: a small remote with an antenna and an indicator lamp. Item model has an `active` variant (lamp lit) driven by an item-model predicate.
- **One channel** (a pair of frequency items, same as a Redstone Link). [P1: an optional 6-channel version is *not* needed — keep one channel.]
- **Right-click (air or block, not sneaking):** toggle ON/OFF. Show an action-bar message ("Remote ON"/"Remote OFF"), play a lever click (pitch up for ON, down for OFF), swing the arm.
- **Sneak + right-click (air):** open the frequency menu — two ghost item slots (mirror the Linked Controller's frequency menu; read Create's source and copy the structure: menu, screen, ghost slots).
- **Sneak + right-click on a Redstone Link block:** copy that link's frequency into the remote.
- **Transmission:** while ON, the remote transmits **strength 15** on its frequency pair into Create's Redstone Link network, so any Redstone Link *receiver* with the same frequency outputs a signal. Implement with an `IRedstoneLinkable` registered in Create's `RedstoneLinkNetworkHandler` (study how `LinkedControllerServerHandler` and the Linked Controller register/unregister their linkables, and copy that pattern, including the range check and the per-level handling).
- **Lifecycle (server-side, must be airtight):**
  - Each ON remote has a UUID in an item data component so duplicates/moves don't double-register.
  - Every ~20 ticks, for every online player, scan their inventory for ON remotes → register new ones, update the player's position (used for the link-range check), unregister ones no longer carried.
  - The signal stops when the remote leaves the inventory (dropped, put in a chest, player dies, logs out) and **resumes** automatically if the item is ON when it comes back into a player's inventory (stored state stays ON). Config `transmitWhileInContainers` default false.
  - Handle dimension changes (move the linkable to the new level's network) and player disconnect (unregister). Clear everything on server stop.
  - **Dreams interrupt it:** while the player is dreaming their real inventory is parked, so the remote naturally stops transmitting; it resumes on waking. (No special code beyond the above.)
- Tooltip shows state and frequency items.
- Recipe (shapeless): 1 `create:linked_controller` + 1 Tuff Alloy + 1 Lever → Latch Remote. [P0]

---

## 5. Module C — Melatonia plant, extracts, food, lacing [P0]

Name: **Melatonia** (deliberately one letter off the real hormone's name). Plant = "Melatonia Bloom".

### 5.1 Plant [P0]
- Crop block `reverie:melatonia` planted from **Melatonia Seeds** on farmland. 4 growth stages (override the age property correctly — CropBlock defaults to 8 stages; handle `getAgeProperty`, `getMaxAge`, and the blockstate/model/loot accordingly). Final stage: a soft violet-blue bell/star flower.
- **Night-bloomer:** random-tick growth is boosted at night (e.g. ×2–3 chance while it is night or light level ≤ 7) and halved in full daylight. Never fully stops. Config toggle.
- Drops at maturity: 1–2 **Melatonia Petals** + 1–2 seeds (loot table with fortune support). Immature drops just a seed.
- Plantable on `#reverie:melatonia_plantable` = farmland + (optional, `required:false`) `farmersdelight:rich_soil_farmland`.
- **Wild Melatonia** [P1]: a small decorative flower block (`reverie:wild_melatonia`, tall-flower style) that generates rarely in the Overworld (forests, plains, dark forests) via a NeoForge biome modifier; breaking it gives seeds + 0–1 petals. This is how players get the first seeds. Also generate it in Dreamland.
- Compostable (petals 65%, seeds 30%).

### 5.2 Extract chain [P0]
| Item | How | Use |
|---|---|---|
| Melatonia Petals | harvest | ingredient |
| **Melatonia Extract** (bottle) | 3 Petals + Glass Bottle (shapeless craft) | Drinkable → **Dizziness** 20 s; food ingredient; lacing ingredient |
| **Concentrated Melatonia Extract** (bottle) | shapeless craft: 3 Melatonia Extract + 1 Glowstone Dust → 1 Concentrated Extract (returns 2 empty bottles) | Drinkable → **Sleepyness** (4:00) + Dizziness 10 s. Can lace food (strong). |

(With Farmer's Delight, both extracts also have Cooking Pot recipes — see G.)
- Bottles: use a drink animation, return an empty Glass Bottle (`usingConvertsTo`).
- Tooltips in italic purple describing the effect.

### 5.3 Base foods (no Farmer's Delight needed) [P0]
| Food | Recipe | Effect |
|---|---|---|
| Melatonia Cookie | wheat + cocoa + petals (like a cookie) ×4 | hunger 2, 60% chance Dizziness 8 s |
| Drowsy Tea | Extract + sugar | hunger 3, Dizziness 12 s, returns bottle |
| Sleepy Stew | bowl + petals ×2 + potato + carrot | hunger 7, saturation good, Dizziness 15 s |

### 5.4 Lacing food [P0]
"Lace any food with Melatonia."
- A **custom shapeless recipe type** (`CustomRecipe`): any item that has a `FOOD` data component **+ 1 Melatonia Extract** → the same item, **Laced**. (A Concentrated Extract instead → **Strongly Laced**, which also grants Sleepyness 2:00.) Returns the empty bottle.
- Implementation: copy the stack, store a mod data component `reverie:laced` (level 1 or 2), and rewrite the stack's `FoodProperties` to append the extra `PossibleEffect`s (Dizziness 100% for level 1/2; Sleepyness 100% for level 2). Never lace the same stack twice at the same level; lacing level 1 → level 2 upgrades. Do not modify stacks that have `usingConvertsTo` weirdness without checking it still works.
- Visuals: a tooltip line ("Laced with Melatonia" / "Strongly laced", purple) — **deliberately subtle**, no glint — and a tiny violet particle burst on eating. Laced items stack only with identical laced items (data components make this automatic).
- Works with Farmer's Delight dishes and modded foods because it only needs the `FOOD` component. Edge cases to handle: items with `FOOD` component but `can_always_eat`, items that stack to 1, cake-like blocks (not items → ignore).
- Optional [P2]: dispensers/droppers can't lace; no need.

### 5.5 Dizziness effect [P0]
- `reverie:dizziness`, category HARMFUL, icon: a swirl. Not vanilla Nausea.
- Client: camera **roll ±3°** and FOV wobble ±4°, sinusoidal, slow, strengthening for the first second and fading out in the last second (use `ViewportEvent.ComputeCameraAngles` / `ComputeFov`). A faint horizontal "double-vision" shimmer is [P2].
- Light movement drift: slightly rotate the strafe input (`MovementInputUpdateEvent`, client-side), max ~10°. Never blocks the player completely.
- Milk and totems clear it as usual.

---

## 6. Module D — Sleepyness & the dream session [P0]

### 6.1 Sleepyness effect [P0]
- `reverie:sleepyness` (keep the user's spelling), category NEUTRAL, icon: a "Zzz" cloud. Obtained from Concentrated Extract (4:00) or strongly laced food (2:00).
- While active and **awake**: mild slowness (−10% movement via attribute modifier), periodic "heavy eyelids" overlay (eyelid vignette slowly closing/opening every ~20 s), occasional yawn particle, subtle sound. [Overlay is P1; slowness is P0.]
- **Lets you sleep in a bed at any time of day.** Hook the sleep-time check using NeoForge's sleep events (look for `SleepingTimeCheckEvent` / `CanPlayerSleepEvent` / `CanContinueSleepingEvent` in the NeoForge 21.1 source; if no event can allow day sleeping, add one minimal, well-commented Mixin). Other vanilla sleep restrictions (monsters nearby, wrong dimension, bed obstructed) stay as they are.
- A player sleeping with Sleepyness **never causes the world to skip the night/day**. Sleeping players with Sleepyness must not count towards the "enough players sleeping" percentage and must not block others from sleeping.

### 6.2 Falling asleep → Dreamland [P0]
Trigger: a player with Sleepyness has been lying in the bed long enough (vanilla's 100-tick "slept long enough" moment). Then:

1. **Capture state** (server-side, into a player **data attachment** `reverie:dream_session`, serialized with the player so it survives logout/crash):
   - full original inventory (main, armor, offhand) serialized with `ItemStack.OPTIONAL_CODEC`, selected hotbar slot;
   - **the item that was in the main hand when they lay down** (snapshot taken at `PlayerSleepInBedEvent`, not at teleport time);
   - health, food, saturation, XP, active effects (except Sleepyness, which is consumed);
   - return point: bed position + dimension, plus a fallback (respawn point → world spawn);
   - dream start tick, planned end tick;
   - **a snapshot of the blocks around the bed** (block states only, a 9×6×9 box around the bed, max ~500 non-air blocks, **no block entities, no inventories, no sign text**) — used to build the *Memory Island* (7.8).
   - **Also write a backup file** `<world>/reverie_backups/<uuid>-<tick>.nbt` containing the original inventory. Safety net for every possible failure.
2. Play a fade-to-black transition (S2C payload → full-screen overlay fading in over ~2 s with a muffled "falling" sound).
3. **Teleport** to Dreamland at the player's **Home Island** (section 7.5), fully healed, food full, all items cleared **except the captured main-hand item, placed in the same hotbar slot**. Armor gone, offhand empty, XP bar hidden/zero, effects cleared.
4. Apply dream rules (6.3).

### 6.3 While dreaming [P0]
- **Cannot die.** Intercept lethal damage (`LivingIncomingDamageEvent` and, as a safety net, `LivingDeathEvent`): instead of dying, the player **wakes up**. Falling below the dimension's void line also wakes them up. `/kill` too.
- No hunger loss, no fall damage, **dream gravity**: reduce the `minecraft:gravity` attribute (~×0.55) and raise `safe_fall_distance` via attribute modifiers (UUID/ids removed on wake). Jump feels floaty, falling is slow.
- Pickup is allowed, crafting allowed, placing allowed — everything works normally on the *dream* inventory, because it is just a temporary inventory.
- Dreams last **`dreamLengthTicks` (default 6000 = 5 min)**. Soft warning: the screen slowly desaturates/vignettes during the last 15 s. Then they wake.
- **Sleeping in any bed inside Dreamland ends the dream** (dreaming inside a dream resolves to waking). Beds in Dreamland never set the respawn point. This is the in-world way to wake early.
- Players can also wake early with `/reverie wake` (self) — and ops can force with `/reverie wake <player>`, start with `/reverie dream <player>`.
- If the player **logs out in Dreamland**, state stays in the attachment; on next login: if time remains resume the dream, otherwise wake them immediately. If the server crashed mid-dream, the same logic on login restores them. **Never lose the original inventory** — write a unit-like self-test that simulates sleep → logout → login → wake.
- Dying in the dream via `/kill` must still wake rather than leaving a deathless limbo.

### 6.4 Waking up [P0]
1. Fade-out transition.
2. Restore original inventory exactly (including empty slots), health, food, XP, effects.
3. **Keepsakes**: each stack in the *dream inventory* (excluding the originally held item, which is gone either way) has an independent **`keepsakeChance` (default 5%)** to survive; at most **`maxKeepsakes` (default 1)** stacks per dream; items tagged `#reverie:lucid_keepsakes` (Lucid Shards, Dream Dust…) use `luckyKeepsakeChance` (default 35%). Surviving stacks are added to the restored inventory (drop at the player's feet if full). Everything else vanishes — **most of the time you bring nothing back.**
4. Teleport to the bed. If the bed still exists, briefly put the player in the sleeping pose for ~30 ticks, then `stopSleeping` (wrap in try/catch; on any failure just teleport). If the bed was destroyed, use respawn point → world spawn.
5. After-effect: 8 s of Slowness I + a soft "waking" ambient; **dream cooldown** `dreamCooldownTicks` (default 3000) during which Sleepyness doesn't allow dreaming (they just sleep normally if it's night, otherwise cannot sleep).
6. The original held item that went to the dream and anything the player built/placed stays in Dreamland.

### 6.5 Multiplayer notes [P0]
- Dreamland is a single shared persistent dimension. Dreamers see each other normally.
- Only chunks near dreamers are loaded; nothing ticks when empty.
- All of this must work on a dedicated server (no client-only calls in server paths).

---

## 7. Module E — Dreamland dimension [P0]

Dimension key `reverie:dreamland` ("Dreamland"). Fully data-driven where possible, one custom chunk generator in Java.

### 7.1 Dimension type [P0]
`has_skylight: true`, `has_ceiling: false`, `natural: false`, **`bed_works: true`** (beds must not explode), `respawn_anchor_works: false`, `ultrawarm: false`, fixed time **~13000 (perpetual blue twilight)**, `ambient_light: 0.15`, vertical range **min_y −64, height 448** (islands span roughly y=40…340), `monster_spawn_light_level 0`, `piglin_safe: true`, `effects: reverie:dreamland`.

### 7.2 Chunk generator `reverie:dream_islands` [P0]
A custom `ChunkGenerator` with a registered codec, deterministic from the world seed. Do **not** try to coax the vanilla noise router into floating islands — write it directly:

- Space is divided into **vertical layers** (e.g. centred around y≈90, 170, 250) and a coarse horizontal grid (cell ≈ 96 blocks). For each (cell, layer) a hash of seed+cell+layer decides: **island present (~55%)**, centre with jitter, radius 14–64, top thickness 3–9, bottom depth 0.6–1.3 × radius (inverted-cone underside), and a **shape type**:
  - *Plateau* (flat top, ragged cone underside) — most common
  - *Spire cluster* (tall twisted pillars of lucid stone with floating rocks)
  - *Ring* (donut with a lake in the middle)
  - *Upside-down* island (grass on the underside, with its flat top bare) [10%] — surreal
  - *Stair-to-nowhere*: a straight staircase of blocks climbing into the void and ending [P1]
  - *Archipelago* of tiny islets around a big one
- Add 3D noise to the underside and edges so they look natural, plus occasional small free-floating rocks and "dust motes" (single floating blocks) in between.
- Generation must be **fast and chunk-local**: for a chunk, only evaluate cells whose islands can overlap it (cache per-cell data in a small LRU). No global state, no cross-chunk dependencies, thread-safe.
- Implement all abstract methods properly (`fillFromNoise`, `buildSurface`, `applyCarvers` as no-op, `spawnOriginalMobs`, `getGenDepth`, `getSeaLevel` (return something low), `getMinY`, `getBaseHeight`, `getBaseColumn`, `addDebugScreenInfo`) so that `/locate`, heightmaps, structure/feature placement and the F3 screen never throw. `getBaseHeight` returns the top of the highest island in that column (or min_y if none).
- Biomes via the vanilla `multi_noise` biome source defined in JSON (no custom BiomeSource unless needed).

### 7.3 Biomes — each with its own dream logic [P0 first two, P1 the rest]
All use a fixed twilight palette (indigo/violet base, each biome shifts the tint), no rain, no vanilla mob spawns, custom ambient/additions sounds (vanilla events, slowed/pitched). Every biome has **one signature rule** that makes it feel dreamed rather than designed:

1. **Pillow Fields** [P0] — soft pastel plateaus of *Dream Grass*. Signature: the grass **colour drifts with position** into slow pink → lavender → powder-blue gradients (client block colour handler; no biome-colour hacks). Hanging trees grow **downward from island undersides**, scattered Wild Melatonia, floating lanterns, and ground-hugging mist.
2. **Hollow Spires** [P0] — grey-violet lucid stone, tall twisting spires that are **hollow** (you can walk up their spiral inside), crystal clusters that hum softly, inverted spires hanging below islands. Signature: higher Shade density and a faint low drone.
3. **Mirror Marsh** [P1] — still "pools" of **Mirror Water**: a solid, walkable, sky-reflecting floor block (animated texture of the sky, ripples spawn where you step, no drowning). Signature: the reflection shows a **slightly wrong sky** (different moon phase). Fireflies, lilies, low fog.
4. **Sandglass Dunes** [P1] — pale hourglass-sand islands with half-buried Melted Clocks and tall glass hourglasses (decorative structures). Signature: **time bubbles** — spheres of ~12 blocks where grains of sand and particles fall *upward* and your movement slows; leaving restores it (a short, harmless "Drift" effect).
5. **Eye Meadow** [P1] — flower fields where each flower is an **Eyebloom** (a flower with a single eye at its centre): a tiny block-entity renderer rotates the pupil to follow the nearest player within 24 blocks. Cute and unsettling; purely cosmetic.
6. **Upside-Down Town** [P1] — clusters of little houses, lamp posts and chimneys built **hanging from an island's underside**, with furniture on the "ceilings" (procedural builders, see 7.8). Signature: a quiet music-box lullaby plays only here.
7. **Cloudreach** [P1] — the highest layer: very sparse tiny islets, huge sky objects, bounce-nets of Pillow Cloud strung between islets. Signature: the sky is closest; shooting stars are frequent.
8. **The Void Between** [P0] — the open space between everything: far fog, drifting motes and petals, big silent distances. Safe because falling means waking.

Biome layout uses `multi_noise` climate points so biomes form large coherent regions with soft borders.

### 7.4 Dream blocks & items [P0 core, P1 rest]
- [P0] **Dream Grass Block**, **Lucid Stone** (+ cobbled/bricks), **Dreamwood** (log, planks, two-tint leaves), **Lucid Crystal Ore** (drops **Lucid Shards**), **Dream Dust**, **Pillow Cloud** (soft cloud block: no fall damage, mild bounce, translucent, no suffocation; craftable from Dream Dust).
- [P1] **Mirror Water** (7.3), **Hourglass Sand**, **Floating Lantern** (light 15, gentle bobbing renderer), **Melted Clock** (decorative drooping clock face with slowly turning hands in the renderer), **Eyebloom**, **Lone Door** (7.8), **Sleep Bell** (decor: rings softly when a dreamer is within 6 blocks).
- [P2] **Dream Catcher** item: if it is the item you held when falling asleep, it raises the keepsake chance for one item to 50%.
- Lucid Shards and Dream Dust are tagged `#reverie:lucid_keepsakes` (higher survival chance).
- Vanilla **compasses and clocks spin randomly** in Dreamland (that is vanilla behaviour in non-natural dimensions — keep `natural: false` and let it happen; do not "fix" it).

### 7.5 Spawning in the dream: the Home Island [P0]
- Each player gets a deterministic **Home** column in Dreamland derived from `hash(playerUUID)` within ~3000 blocks. On arrival: if a safe standing spot exists nearby (solid ground, 2 air above, within ±40 blocks vertically), use it; otherwise **place a small safe Home platform** (7×7 Dream Grass disc with one tree and a lantern) at a suitable height. The same player always wakes up "at home" in the dream, and builds persist across dreams.
- Teleport with the correct 1.21.1 APIs (`DimensionTransition`), no portal frames, no terrain-loading glitches. Chunk-load the destination before moving the player.
- **Memory Island** [P1]: next to the Home Island, generate a **warped echo of the room they fell asleep in** from the bed snapshot (6.2): the blocks are scrambled in dream logic — chunks of the room rotated 90°, some parts turned upside-down, some floating a few blocks off, doors swapped for Lone Doors, ordinary blocks randomly swapped for a ghostly variant (glass, Pillow Cloud, Lucid Stone), the bed itself always at the centre but twice as big. Fully inert (no inventories, no block entities, no redstone). Replaced by the next dream's snapshot. (If the snapshot is empty or fails, skip silently.)

### 7.6 Sky, fog, atmosphere (client) [P1]
- Register `DimensionSpecialEffects` for `reverie:dreamland` (`RegisterDimensionSpecialEffectsEvent`): constant twilight gradient, thick blue-violet fog in the Void Between, no vanilla clouds.
- **Sky objects** drawn with simple textured quads (trivial GPU cost): 2–3 **moons of wrong sizes and phases**, a huge slow-turning **ringed planet**, a field of stars that **slowly rearrange into constellations that are never the same twice**, and a distant **closed giant eye** on the horizon. [Aurora ribbons P2.]
- **The Moon Follows You:** the big moon's azimuth is tied to your travelled distance with lag, so walking makes it seem to trail you at half your speed; stop and it slowly settles.
- **The Eye:** the giant eye on the horizon is closed. As the dream nears its end (last 40 s) it slowly opens; at wake it blinks shut. While Shades are near it stays closed.
- Ambient particles near the player: motes, drifting petals, occasional falling paper pages, soft glints.
- If the custom sky renderer fails for any reason (including shader mods), **fall back to a plain gradient** without crashing (catch, log once, disable).

### 7.7 Dream logic: global rules & phenomena [P1; start with the first three]
Small server-light rules that make Dreamland behave like a dream. Each has a config toggle.
1. **Sleeping in a dream wakes you** (6.3).
2. **Dream captions:** custom sound events (reusing vanilla sound files via `sounds.json`) with deliberately odd **subtitles**, played rarely and quietly from nowhere: "Someone is counting", "A door closes far away", "A bed creaks", "Footsteps stop", "Something is being folded", "A clock ticks backwards". Players with subtitles on get the full effect.
3. **Dream gravity** (6.3) plus **Gravity Hiccup** (8.3).
4. **Wrong rain:** in some biomes, petals/paper/rain particles fall **upward**.
5. **Time bubbles** (Sandglass Dunes).
6. **Recurring objects:** a specific item (a dandelion, a brass key, a paper boat) appears in the open at a different place in each biome during one dream, as if the dream keeps returning to it. Pickup is harmless; it is a keepsake-eligible curiosity.
7. **Doors that lie:** Lone Doors (7.8) sometimes lead to a spot that looks exactly like the one you left — one block off.

### 7.8 Structures & landmarks (procedural builders, no NBT files) [P1 unless noted]
Written as small Java builders (`Feature` or placement code) so they work with the custom generator. Keep each under ~1500 blocks, place only on islands.
- **Lone Door** [P0 of the structures]: a freestanding doorframe. Walk through → brief white flash → teleported to a random safe spot on another island within 300 blocks.
- **Melted Clock:** Dalí-style clock draped over an island edge (a flat disc bending over and dripping down the side).
- **Impossible Stairs:** four staircases arranged so, from one specific viewpoint, they form a never-ending loop (Penrose illusion built with depth offsets). A tiny plaque tile marks the viewpoint.
- **Staircase to Nowhere:** a straight climb into the void that ends at a single door in the sky which leads back to the island it started from.
- **Upside-Down House / Town:** small houses, lamp post, chimney, all hung from an island underside, furnished on the ceiling.
- **The Great Bed:** a gigantic floating bed island (pillows of Pillow Cloud); sleeping in it wakes you (7.7 rule 1) and it is the safest place to end a dream with a smile.
- **Sea of Doors:** a flat island carpeted with hundreds of closed doors lying down (rare). Walking over them plays a soft creak.
- **Hollow Spire interiors:** spiral stairs inside spires with a window every few turns that frames the sky.
- **Sleep Bell Grove, Sandglass Pillars, Eye Meadow clearings, bridges of Pillow Cloud between near islets.**
- Rare landmark: a **giant floating clock face** (stationary, hands set to a time it never is).

### 7.9 Dream weather & sky events [P1; Starfall first]
Per-dreamer, client-visual with tiny server involvement, random with cooldowns, never during the first 15 s or last 15 s.
- **Starfall:** shooting stars streak across the sky; rarely one lands on an island, making a small glowing crater and leaving a **Lucid Shard**.
- **Petal Drift:** a long drift of petals across the whole view, with a soft rising chime.
- **Fog Tide:** a thick glowing fog rolls in for ~30 s; Shades in it are only silhouettes and dim lights.
- **Upward Rain:** for ~20 s all rain particles fall up; harmless.
- **The Long Silence:** all ambient sound ducks to near-nothing for 20 s except a single far-off sound (works with, and can trigger, The Hush from 8.3).

### 7.10 Sound & music [P1]
- No new audio files required: reuse vanilla ambient/music-box/note-block/bell samples.
- **Procedural lullaby (`LullabyPlayer`, client):** plays slow, sparse notes from a pentatonic minor scale with `note_block` chime/bell/harp samples, randomly detuned a few cents, with a rule-based melody generator (never a real song). Tempo and register drift with context: slower in Pillow Fields, ghostly and sparse near Shades, music-box in Upside-Down Town, silent during The Hush/Long Silence.
- Custom ambient mix per biome (wind, far bells, low drones from pitched vanilla sounds). All volumes configurable.

---

## 8. Module F — Shades & dream fauna

### 8.1 Shade entity [P1]
Entity `reverie:shade`, MISC category. **Harmless, untargetable, unhittable, invulnerable, never saved** (`noSave`, discard on chunk unload / on any load from disk), `noPhysics`, never persistent, no pathfinding, no goals. Players cannot interact with them. They render as **flat black silhouettes** (translucent edges, no lighting, optional pale pinprick eyes that only appear while the dreamer looks directly at that shade).

Variants (synced entity data, chosen at spawn): **Onlooker** (humanoid), **Tall One** (×2.3, thin), **Stooped One**, **Wide-Armed**, **Headless**, **Hanging** (upside-down, floating), **Doorframe** (door-shaped, no body). More = [P2].

### 8.2 Crowd behaviour — the core rule [P1]
Managed server-side by a `ShadeCrowdManager` that runs **per dreamer** every 10 ticks (cheap) — shades are *not* naturally spawned.

- Keep a crowd of **`shadeCount` (default 28, more in Hollow Spires)** around each dreamer, placed in the band **28–52 blocks** away (mostly on distant island surfaces; otherwise floating in the void for Hanging variants), arranged in loose **clusters** (Poisson-ish, min spacing ~2.5 blocks) so it feels "almost crowded".
- Every shade **faces the dreamer** (smoothed body + head turn; client interpolates).
- **Approach rule:** if the dreamer comes within **`shadeFleeRadius` (default 20)** of a shade, it **vanishes** (≈10-tick fade-out with a tiny smoke wisp) and a replacement later **reappears at ≥ 30 blocks** from the dreamer. Net effect: you can never close the distance; the crowd just keeps its distance, always watching.
- **Blinking closer:** when the dreamer is standing still, a shade that is outside the dreamer's view cone may re-appear 1–3 blocks closer — never inside 24 blocks.
- Spawn/despawn with a short fade (alpha via entity data / ticks since spawn) so nothing pops.
- Hard cap on shades per dreamer and per server (config). Despawn all shades when the dreamer wakes/leaves. Shades near **no** dreamers are discarded.

### 8.3 Rare surreal events [P1 first two, P2 the rest]
Each has an independent cooldown and a config toggle; at most one runs at a time; never while the dreamer is in the first 20 s or last 15 s of the dream.
1. **The Hush** — all shades vanish at once; ambient/music cut for ~12–20 s (S2C payload); then they all fade back in, facing *away*.
2. **Everyone Turns** — all shades turn their backs; whenever the dreamer looks at them, they instantly face away again; whenever the dreamer looks away, they turn back.
3. **Behind You** — for about half a second a single shade stands 7–9 blocks behind the dreamer; it vanishes the moment the dreamer turns to look.
4. **Mimic** — a silhouette in the dreamer's own skin shape, ~30 blocks away, copies the dreamer's movement with a ~3 s delay, then walks off the edge of its island.
5. **Counting** — the crowd lines up in a perfect row/grid, all pointing the same direction; then snaps back to loose positions.
6. **Gravity Hiccup** — for 5 s dream gravity inverts (floaty upward drift) with a soft chime. Safe: never over void with fast fall.
7. **Farewell** (not random — always plays at dream end): as the dream's last 40 s begin, the Shades slowly turn their backs and walk away, one by one, into the distance while the sky's Eye opens; when the dreamer wakes, every Shade is gone.

### 8.4 Dream fauna [P2]
- *Sleepwalking Sheep* — pale sheep drifting a few blocks above the ground, snoring "Z" particles; drop Dream Dust.
- *Lucid Moths* — glowing moths orbiting lanterns; drop Dream Dust.
- *Paper Whale* — huge, slow, flat flying silhouette far in the sky (purely visual, no hitbox beyond a tiny one).
All passive, no attack, and no natural spawning outside Dreamland.

---

## 9. Module G — Farmer's Delight integration [P1]

All inside `compat.fd` + conditional data. Without Farmer's Delight none of this exists and nothing breaks. (Verify FD ≥ 1.3.2 behaviour for 1.21.1.)

- **Farming:** Melatonia plantable on FD **Rich Soil Farmland** and gets the usual FD growth boost (verify how FD's Rich Soil ticks plants; use the tag/mechanism FD exposes rather than hacks). Compatible with FD's Organic Compost where it applies.
- **Cutting Board:** Melatonia Bloom (the plant block item if it has one, or Petals) → with a Knife: extra Petals + chance of Seeds. Wild Melatonia can also be cut.
- **Cooking Pot recipes** (FD `cooking` type, with container where needed):
  - Melatonia Extract: 3 Petals + Water Bottle → Extract (container: glass bottle).
  - Concentrated Extract: 3 Extract + Glowstone Dust (container: bottle) → Concentrated Extract.
  - **Lullaby Stew** — hearty; gives FD's *Comfort* effect + Dizziness, long-lasting.
  - **Dozing Dumplings** — Dizziness 20 s, good saturation.
  - **Dreamy Pie** — an FD-style pie block (4 slices) using FD's `PieBlock` pattern; each slice = Dizziness; crafting like FD pies (crust + petals + sugar + eggs).
  - **Night-Bloom Roll** — handheld snack.
  - **Melatonia Syrup** (bottle) — sweetener; also a lacing item equivalent to a normal Extract.
- All FD dishes should be **lace-able** automatically through section 5.4 (they carry the `FOOD` component) and tagged correctly for FD's food tags/Appleskin.
- Add the new FD dishes to the Reverie creative tab *when FD is loaded*.
- Add the **"Master Chef"-style compat advancement** [P2]: eat all Reverie dishes.

---

## 10. Config, networking, commands, saving

### Config (`ModConfigSpec`)
COMMON: dreamLengthTicks=6000, dreamCooldownTicks=3000, keepsakeChance=0.05, luckyKeepsakeChance=0.35, maxKeepsakes=1, gearRatios=[1,1.5,2,2.5,3,4], reverseRatio=-1.0, nightBloomerGrowthBoost=true, wildMelatoniaFrequency, shadeCount=28, shadeFleeRadius=20, dreamCaptions=true, timeBubbles=true, weatherEvents=true, memoryIsland=true, enableRareEvents=true (+ per-event toggles), maxShadesPerServer, transmitWhileInContainers=false.
CLIENT: animatedStick=true, lullaby=true, lullabyVolume=0.6, dizzinessIntensity=1.0, customSky=true, eyelidOverlay=true, shadeGlow=true, reduceMotion (halves roll/FOV wobble).

### Networking (NeoForge payloads, `RegisterPayloadHandlersEvent`)
- C2S `SelectGearPayload(BlockPos, int gear)` — validated as in 3.5.
- C2S `RemoteSetFrequencyPayload` (if the menu needs one) — validated.
- S2C `DreamTransitionPayload(type)` — fade in/out.
- S2C `DreamStatePayload(active, ticksLeft)` — drives the desaturation vignette.
- S2C `ShadeEventPayload(event, durationTicks)` — Hush audio cut etc.
- All handlers re-validate on the server; unknown/garbage input never throws.

### Commands (`/reverie`, op level 2)
`dream <player>`, `wake <player>`, `home <player>` (show/teleport to dream-home), `shades clear`, `debug dreamstate <player>`. `/reverie wake` (no arg, any player) wakes yourself.

### Persistence
- Dream session: player **data attachment** + backup NBT file (see 6.2). Gearshift: `selectedGear` + `effectiveGear` in the block entity's NBT. Remote: item data components (`active`, `remote_id`, frequencies).

---

## 11. Build phases & acceptance checks

**Phase 0 — Skeleton.** Gradle (ModDevGradle), `neoforge.mods.toml` with optional deps, creative tab, config, lang, a placeholder item. ✅ Starts with: neither / Create / Create+FD.

**Phase 1 — Tuff Alloy + Tuff Gearshifts.** Alloy recipes, both blocks, BE, ratios, redstone→R with memory, goggle info, rendering, GUI + payload. ✅ In a test world: input 64 RPM from a water wheel/motor, verify ×1, ×1.5, ×2, ×2.5, ×3, ×4, R (reversed), N (stops); redstone forces R and releases back to the remembered gear; GUI lever follows the path, locks while powered; breaking/rotating with wrench doesn't error; save/reload keeps gear.

**Phase 2 — Latch Remote.** ✅ Link a Redstone Link receiver to a lamp: right-click ON → lamp on permanently; right-click OFF → lamp off; works across a few hundred blocks within Create's range; stops when the item is dropped; resumes when picked up; survives relog; no double-register after hundreds of toggles.

**Phase 3 — Melatonia, extracts, foods, lacing, Dizziness.** ✅ Wild plant → seeds → crop grows faster at night → petals → extracts → foods; lacing recipe works on bread, a steak, a modded food; Dizziness camera effect visible and fades; milk clears it.

**Phase 4 — Sleepyness + dream session against a temporary void test dimension.** (Use a trivial dimension first so the dream *logic* can be proven before the worldgen exists.) ✅ Drink concentrated extract at noon, sleep in a bed → teleported, inventory = only the held item; die/fall/`/kill` → wake with original inventory perfectly restored; log out mid-dream → relog resumes or wakes; kill the server process mid-dream → on restart items are restored; keepsake chance and caps behave; world time never skipped; multiplayer sleeping percentage unaffected.

**Phase 5 — Dreamland generation, biomes and landmarks.** Swap in the real dimension: dimension type, chunk generator, biomes, blocks, Home Island, Lone Door [P1]. ✅ Fly around (spectator) for 10+ minutes in several directions: no exceptions, no stuck chunk loads, stable TPS; `/locate biome` and F3 don't crash; islands vary in type; Home Island always valid; structures/features place only on islands; each biome's signature rule is visible; Lone Door, Great Bed (sleep → wake), Memory Island and at least three other landmarks generate without errors; chunk-gen time per chunk stays low and constant.

**Phase 6 — Shades + events.** ✅ In Dreamland the crowd forms at distance, faces you, vanishes when you approach and re-forms at range; Hush/Everyone Turns/Behind You work; entities never persist to disk, never leak after waking; no entity ID/sync errors; dedicated server test with two dreamers.

**Phase 7 — Farmer's Delight integration.** ✅ With FD: all cooking/cutting recipes load, pie works, Rich Soil boosts growth; without FD: no errors/warnings about missing items.

**Phase 8 — Polish.** Sky (moons, planet, constellations, the Eye, Moon-Follows-You), weather events, procedural lullaby, dream captions, Gearshift stick animation tuning (travel path, clunk, idle rattle), eyelid overlay, tooltips, creative tab ordering, advancement tree (a few: *"Counting Sheep"*, *"Wide Awake"*, *"Gear Head"*), README with credits and config docs, recipe-viewer sanity check, final test of the 3 install combos on both client and dedicated server.

---

## 12. Global rules for no-crash

- Every server event handler for dream/gearshift/remote logic is defensive: null-check levels, players, block entities; wrap teleport/wake in try/catch that falls back to *restoring the player to the bed with their items*, logging once.
- Never hold references to `Level`/`Player` objects in static fields; key by UUID/dimension key and clear on server stop.
- No client classes (`Minecraft`, `Screen`, renderers) reachable from common code.
- Every data attachment / component codec must tolerate missing fields (old saves).
- If this mod is removed from a world mid-dream, the player's items still exist in `reverie_backups`; document that in the README.
- Run each phase's acceptance checks on a **fresh world** and on a **world saved by the previous phase**.

---

## 13. Decisions I made on your behalf (change anything)

- Mod name **Reverie**, plant name **Melatonia**, remote name **Latch Remote**.
- "Dizziness" is a custom effect (not vanilla Nausea); Sleepyness keeps your spelling.
- Tuff Alloy = Tuff + Iron Nugget + Gold Nugget (I read "gold and iron nugget" as both nuggets).
- The "Reverse" gear is −1× (same magnitude as gear 1). N outputs nothing.
- While redstone-powered the Gearshift locks to R and the GUI lever is locked; the remembered gear is restored on power loss.
- The Latch Remote has a single channel and transmits only while carried.
- The Latch Remote and the Gearshifts are Create-only content by design.
- The gearstick sits on top (north face when the axis is vertical) and animates along the same gate path the GUI uses.
- Sleeping inside a dream wakes you; the dream ends with the Shades walking away and the sky's Eye opening.
- Dreams last 5 minutes by default; you cannot die in them (you wake up); 5% per-stack keepsake chance, max one per dream.
- You always return to your own **Home Island** in Dreamland, so what you build there persists.
- Create and Farmer's Delight are *optional*: without Create there are no gearshifts/remote, without FD there are no FD dishes, everything else works.
