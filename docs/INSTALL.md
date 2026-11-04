# Installing Agent Buddy (Windows)

Current build: **0.5.0 = milestone M5**. The Agent can be crafted, placed, picked up again, walks after you, and has a GUI:
rename, follow/stay, "what am I looking at?", find/fetch items from your chests, container snapshots, an ignore list,
waystones with teleport, and a once-per-death return. Ore search is a tool (used by chat in M7).
Talking to it with `@agent ...` comes in M7, so the local LLM is not used yet.

## 1. Minecraft 1.21.1 + NeoForge
1. In the Minecraft Launcher: **Installations → New installation → Version: release 1.21.1** → Create, then Play once and quit. This downloads 1.21.1.
2. Download the NeoForge installer:
   https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.265/neoforge-21.1.265-installer.jar
3. Run it (double-click, or `java -jar neoforge-21.1.265-installer.jar`), choose **Install client** and keep the default folder (`%APPDATA%\.minecraft`).
4. In the launcher, the profile **NeoForge 21.1.265** now exists. Under Edit → More options → JVM arguments, set `-Xmx4G` (the spec keeps the game heap at about 4 GB, so the model gets the VRAM).

## 2. The mod
Copy `agentbuddy-0.5.0.jar` into `%APPDATA%\.minecraft\mods\` (create the folder if needed) and start the NeoForge profile. **Mods** on the title screen should list "Agent Buddy 0.5.0".

To build the jar yourself, run `gradlew.bat build` in the repo. Output: `build\libs\agentbuddy-<version>.jar`.

## 3. In game (M5)
- **Get the Agent:** Creative inventory → Tools & Utilities, or craft it:
  ```
  .  S  .      S = spyglass
  I  R  I      R = redstone
  I  .  I      I = iron ingot
  ```
- **Place:** right-click a block with the item. The Agent belongs to whoever places a fresh item.
- **Pick up:** sneak (Shift) + right-click the Agent. Only its owner can. It becomes the item again, keeping its identity (name and, later, waystones and ignore list).
- **Follows you on foot** (like a copper golem: it walks, pathfinds and potters around near you), and teleports to a safe spot next to you if it falls more than 16 blocks behind or gets stuck. It takes no fall damage and doesn't despawn.
- If it dies, it drops its item, so it is never lost.
- **GUI:** right-click your Agent, or press **C** near it (within 64 blocks). It shows its name, your credits and its mode, and has buttons for Rename, Follow, Stay and "What am I looking at?". The game keeps running while it is open.
  Vanilla also uses C (save hotbar, creative only). Options → Controls shows both in red; rebind "Open Agent" under Agent Buddy if that bothers you.
- **Credits:** you get 1 credit per 4 XP you collect (orbs; `/xp` does not count), up to 999. You keep the XP.
- **Containers** (chests, trapped chests, barrels, shulker boxes in loaded chunks within 32 blocks; a double chest counts as one):
  - *Find* (1 credit, 5 s cooldown): type an item in the GUI box, e.g. `diamond` or `iron ingot`. It reports the container with the most.
  - *Fetch* (2 credits, 10 s cooldown): the Agent flies to that container, takes the real items (names and enchantments kept) and brings them to you.
  - *Snapshot* (1 credit): look at a container and press Snapshot for a read-only view of its slots.
  - *Ignore*: look at a block or container and press Ignore. From then on it does not exist for the Agent (find, fetch, look, snapshot). The GUI lists ignored blocks with Unignore buttons.
  - Unopened loot chests (dungeons etc.) show as "Contents unknown until opened." and are never opened by the Agent.
  - Credits and cooldowns are only charged when the tool succeeds.
- **Waystones** (GUI → Waystones...): up to 5 named spots. *Set here* saves where you stand; *Go* teleports you there
  (1 credit + 1 per 100 blocks, 60 s cooldown, same dimension only); *X* removes it. Your Agent comes along if it is following you.
- **Back to death spot** (10 credits, once per death): teleports you to a safe spot near where you last died, in any
  dimension, even if your Agent is not with you. Falling into the void puts you on the surface above that point.
- **Ore search** (`find_ore`, 1 credit, 5 s): nearest ore of a type within 64 blocks in loaded chunks (modded ores with
  `c:ores/...` tags count). No GUI button; you'll use it by chat in M7 ("where's the nearest iron?").

## 4. Local LLM (needed from M7 on; you can set it up now)
From the repo folder, put the LLM next to your real game install:

```
scripts\setup-local-llm.cmd -GameDir "%APPDATA%\.minecraft"
```

This creates `%APPDATA%\.minecraft\localLLM\{bin,models,logs}`, installs llama-server b12110 (CUDA 12 build on an NVIDIA GPU), and downloads Qwen3.5-4B Q4_K_M (2.55 GB, asks first). Every file is checked by size and sha256.

## Known limitations of 0.5.0
- Items inside shulker boxes that sit inside a chest are not searched yet.
- The model is defined in code (not a Blockbench model). The texture follows your reference image by eye; drop the real PNG into the repo for an exact match.
- Agents created with `/summon` have no owner and cannot be picked up.
- Only tested in the dev environment (`gradlew runClient`, game tests), not through the real launcher. If the profile fails to start, send `%APPDATA%\.minecraft\logs\latest.log`.
