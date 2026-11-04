# AGENT_SPEC.md — Local AI buddy for Minecraft

A flying camera-lens golem ("the Agent") that you talk to with `@agent ...` or control through a GUI (C key / right-click). A local LLM turns natural language into calls to a fixed set of game tools. Everything runs on the player's machine.

## 0. Decisions
- Minecraft **1.21.1**, loader **NeoForge**, **Java 21**. Mod id: `agentbuddy` (placeholder).
- Target hardware: laptop RTX 4050 with **6 GB VRAM**, 16 GB RAM. The game and the model share that VRAM, so the model budget is about 3.5 GB in total (weights + context). Keep game heap modest (about 4 GB).
- **Default model: Qwen3.5-4B, Q4_K_M** (Unsloth GGUF, because stock templates had tool-call bugs). Roughly 2.8 GB file.
  - Run with thinking OFF (`--chat-template-kwargs '{"enable_thinking":false}'`), `-ngl 99`, `-c 4096`. The context block is tiny, so a small context saves VRAM.
  - Alternatives to compare in M1: Gemma 4 E2B Q4_K_M (about 3.1 GB). Gemma 4 E4B Q4_K_M is about 5.3 GB of weights and will not fit next to the game without heavy CPU offload; test only as a stretch.
  - Too big for this machine: Qwen3.5-9B, Gemma 4 26B/31B.
  - The M1 eval, not this paragraph, makes the final call.

## 1. Principles
1. **The LLM is only a translator.** It picks a tool and fills in arguments. All game logic is deterministic Java.
2. **The mod works without the LLM.** The GUI calls the same tools directly, no model involved.
3. **Truth over politeness.** The Agent only states what a tool result or the context block says. See section 5.
4. **Short.** Replies are a few words, not paragraphs. See section 5.
5. **Not OP.** Every tool has a range, credit cost and cooldown. Tune in config, not code.
6. **Server-authoritative.** Clients send intents; the server validates and executes.
7. **Never block the server tick.** LLM calls run on worker threads. Tool execution is scheduled back onto the server thread.

## 2. LLM backend (decision: managed subprocess)
Interface `LlmBackend` with one method: chat completion with tools (OpenAI-compatible request/response).

Implementations:
- **`ManagedServer` (default, v1).** The mod starts and owns a `llama-server` process.
  - Folder (inside the game directory, i.e. `.minecraft/`):
    ```
    localLLM/
      bin/        llama-server binary for the player's OS/GPU (user-provided or fetched by a setup screen)
      models/     *.gguf files
      config.json model filename, port, gpuLayers, ctxSize, temperature, maxTokens
      logs/
    ```
  - Launch flags: `--jinja` (needed for the model's tool-calling template), `-ngl <gpuLayers>`, `-c <ctxSize>`, loopback port only.
  - Lifecycle: start lazily on first `@agent` / LLM use (or on world join if configured), health-check until ready, show "loading" state on the Agent, kill the process on game exit and on crash of the parent. Pick a free port. Capture stdout to `logs/`.
  - Failure behavior: if the binary or model is missing, or the server dies, the Agent says so in one short line and the GUI keeps working.
- **`External` (optional).** Any OpenAI-compatible base URL (Ollama, LM Studio, etc.). Same code path.
- **`InProcessJni` (NOT in v1).** Possible later through JNI bindings to llama.cpp. Not planned: a native crash would take the whole game down, and GPU builds per OS are costly. Do not build until M1–M7 are done.

Rules for the brain:
- Temperature low (default 0.2). Max reply tokens small (default 80). Thinking mode off for latency.
- The model stays resident in VRAM while the server runs. Start lazily on first use and shut the server down after an idle timeout (default 10 min) to give the VRAM back to the game.
- Tool loop capped at 4 steps. Conversation history: last 4 turns only.
- Expose at most ~8 tools per request. Route by category first (inventory / world / travel / settings) so small models are not shown 30 tools at once.
- Validate every tool call against its JSON schema. Malformed call: retry once with a short corrective message, then fall back to "Didn't understand."
- Be tolerant of known model quirks (tool calls emitted as plain text, XML-style calls). Parse them defensively.
- Consider grammar-constrained output (JSON schema) for the tool call if native tool calling proves unreliable in the M1 eval.

## 3. Context block (sent with every request, kept tiny)
Built server-side, never from client claims:
```
player: <name>, hp, held item
looking_at: <block id> @ x,y,z  (or "nothing in reach")
dimension, biome, time of day
agent: pos, state, credits
nearby_containers: count only (ignored ones are excluded entirely)
```
"This / that / there / here" always resolves to `looking_at`. If `looking_at` is empty, the Agent says so instead of guessing.

## 4. Tools (all typed; the LLM never runs raw commands)
Costs/ranges below are starting values. Tune in config.

| Tool | What it does | Range | Credits | Cooldown |
|---|---|---|---|---|
| `look_at` | Report block/entity the player is looking at | reach | 0 | 0 |
| `find_ore` | Nearest ore of a type in loaded chunks | 64 | 1 | 5 s |
| `list_container` | Contents of one container | 32 | 0 | 0 |
| `find_item` | Which known containers hold item X | 32 | 1 | 5 s |
| `fetch_item` | Fly to container, withdraw, bring to player | 32 | 2 | 10 s |
| `snapshot_container` | Open a read-only "photo" screen of a container's contents | 32 | 1 | 5 s |
| `set_waystone` / `remove_waystone` | Virtual waystones (max N per player) | - | 0 | 0 |
| `teleport_waystone` | Teleport player to a waystone | - | scales with distance | 60 s |
| `death_return` | Teleport near the last death position (safe spot search) | - | high | once per death |
| `pickup_container` | Carry a chest/double chest/barrel with full data | 8 | 5 | 30 s |
| `place_container` | Place the carried container where told | 16 | 0 | 0 |
| `ignore_block` / `unignore_block` | Hide a container/block from all perception | - | 0 | 0 |
| `set_name`, `follow`, `stay` | Settings | - | 0 | 0 |

Rules:
- **Ignored blocks do not exist** for the Agent: excluded from scans, lists, counts, and the context block. The model never sees them, so it cannot leak or hint at them.
- Ore scanning only covers loaded chunks within range. No x-ray beyond that.
- Carrying: one container at a time; the Agent can be killed and drops it intact.

## 5. Voice and honesty rules (enforced in code, not only in the prompt)

### 5.1 Short replies
- **Template-first.** After a tool runs, the mod builds the reply from a per-tool template using the tool result. No second LLM call.
  - `Iron: 23 blocks NE, y=14.`
  - `No iron within 64.`
  - `12 diamonds in chest at -120, 64, 33.`
  - `Need 5 credits, have 2.`
  - `Done.`
- The LLM writes free text only when no tool applies (small talk, refusal, clarification). Then: **one sentence, max ~15 words**, no emoji.
- Hard caps: `maxTokens` (default 80) and a post-filter that truncates to the first sentence/line limit.
- Post-filter strips filler openers and praise ("Sure!", "Absolutely", "Great question", "You're right") and apologies.

### 5.2 No agreeing with things he can't see
- **Never confirm a claim about the world without a tool result or context-block fact.**
- If the player asserts something and the data says otherwise, say so plainly:
  - Player: "this is a wood block, right?" with `looking_at: stone` → `No. That's stone.`
  - Player: "bring the diamonds from the chest by the door" with no such container known → `I don't see that chest.`
  - Player: "what's that over there?" with nothing in sight → `Can't see anything there.`
- Empty tool results are explicit (`found: false`, `reason`), and the reply states the absence directly. No hedging, no invented positions, no guessing.
- A tool that can verify (`look_at`, `list_container`) is called before answering a question about the world. If the model answers such a question without a tool call, reject the answer and re-run with the tool forced.
- Ignored blocks are simply absent from perception (see section 4). That is a game rule, not deception.
- System prompt (short, 6–8 lines) restates these rules and includes 6–8 few-shot examples of terse, factual replies and false-premise corrections.

## 6. Entity, item, GUI
- Custom flying entity (Allay-like movement, so no ground pathfinding). Golem-style body with a large camera lens as the face. Model/texture made in Blockbench.
- Shift + right-click: pick up as an item. Entity data (name, waystones, ignore list, credits) is stored server-side under a UUID; the item holds only the UUID reference. Placing the item restores the entity.
- Right-click on the entity or the C key opens the GUI. The GUI covers: waystone list and teleport, ignore list, rename, credit balance, container search, and a chat/command box. Every action is a packet that calls a tool directly.
- Credits: earned from XP collection. Stored per player (server data).

## 7. Carrying containers
- Serialize the block entity (full data, including nested shulker boxes and enchanted/named items), store server-side under a UUID, then clear the inventory and remove the block with no item drops.
- Double chests: handle both halves, facing and chest type; carry as one unit.
- Placement: rebuild block state and block entity, restore contents, delete the stored record.
- Edge cases to test: unopened loot-table chests, modded containers (refuse politely), locked containers, shulker boxes inside chests, very large contents.

## 8. Milestones (each ends in something verifiable without playing)
- **M0** Skeleton mod boots; build + run commands documented in CLAUDE.md.
- **M1** Brain outside Minecraft: plain Java module + fake tools + replay tests against a real `llama-server`. Includes the **false-premise and absence tests** from section 5.2 and a brevity check. Output: a comparison table of candidate models. Pick the default model here.
- **M2** Entity + item + pickup/place + model.
- **M3** Tool layer, credits, cooldowns, GUI + packets (no LLM yet).
- **M4** Containers: index, `find_item`, `fetch_item`, ignore list, snapshot screen.
- **M5** Ores, waystones, `death_return`.
- **M6** `pickup_container` / `place_container` with in-game tests (double chest + nested shulker + enchanted items round-trip identically).
- **M7** `@agent` chat, context block, tool routing, template replies, post-filters.
- **M8** `localLLM` runtime manager, setup screen, config, balance pass.

## 9. Testing
- Unit tests for every tool (argument validation, range, credits, cooldowns).
- In-game tests (the loader's game-test framework) for containers, entity, waystones, death position.
- Brain replay suite: fixed prompts → expected tool + args; false-premise prompts → reply must contain the true fact and must not contain agreement; reply length limits.
- Playtesting is manual; log tool calls and replies to `localLLM/logs/` so problems can be pasted back.

## 10. Non-goals for v1
Dedicated-server support (singleplayer/LAN only), in-process JNI inference, true camera-render screenshots, voice, building structures, combat.
