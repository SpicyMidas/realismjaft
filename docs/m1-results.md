# M1 eval results (cloud container, CPU only)

Run: 2026-11-04, `gradlew :brain:replay -Pllm.model=all`, llama.cpp `b12110` (cd959bae9), Linux x64, 4 vCPU Xeon (AVX-512), **no GPU**.
46 cases from `brain/src/test/resources/replay/cases.json`, one pass each, temperature 0.2, seed 42.

## Pass rate per group

| Model | tool_selection | false_premise | context_premise | absence | brevity | total |
|---|---|---|---|---|---|---|
| Qwen3.5-4B Q4_K_M (Unsloth) | 19/19 | 7/8 | 4/4 | 8/8 | 6/7 | 44/46 |
| Gemma 4 E2B-it Q4_K_M (Unsloth) | 19/19 | 7/8 | 4/4 | 8/8 | 7/7 | 45/46 |

No case agreed with a false premise or leaked an ignored container.
- Both models fail `fp_chest_by_door` on tool choice (find_item / list_container instead of fetch_item). Their replies ("I don't see any containers." / "No container there.") are still true.
- Qwen also fails `br_name`: "what's your name?" -> "Hi.".

## Latency and memory (CPU, not representative of the RTX 4050)

| Model | per LLM call median / p90 | per turn median | prompt tokens | prompt tok/s | gen tok/s | llama.cpp buffers at -c 4096 |
|---|---|---|---|---|---|---|
| Qwen3.5-4B | 7.0 s / 21.7 s | 7.1 s | ~910 | 48 | 5.3 | model 3149 + KV 128 + recurrent 50 + compute 83 MiB |
| Gemma 4 E2B | 1.4 s / 3.1 s | 1.7 s | ~660 | 214 | 11.6 | model 3194 + KV 36 + compute 85 MiB |

VRAM was **not measured** (no NVIDIA GPU here). The buffer sizes are llama.cpp's own allocation log on CPU. With `-ngl 99` on CUDA, expect roughly the same model + KV sizes and a different compute buffer.

## Protocol checks (both models PASS)

- `--reasoning off`:
  - The rendered prompt has thinking disabled (Qwen ends `<think>\n\n</think>\n\n`; Gemma has no `<|think|>`).
  - Responses carry no `reasoning_content`.
- Control run without the flag (llama-server default `auto`): thinking is **on** for both models. Qwen ends in an open `<think>\n`; Gemma has `<|think|>` in the system turn.
- The spec's `--chat-template-kwargs '{"enable_thinking":false}'` also disables Qwen thinking on b12110, but llama-server logs it as deprecated in favor of `--reasoning off`. It was not tested on Gemma.
- With `--jinja`, a plain tool request returns structured `tool_calls` for both models.

## How tool calls arrived

| Model | structured tool_calls | recovered from text | malformed | free text | forced re-runs | corrective retries |
|---|---|---|---|---|---|---|
| Qwen3.5-4B | 36 | 1 | 0 | 10 | 0 | 1 |
| Gemma 4 E2B | 1 | 37 | 0 | 12 | 1 | 3 |

With the full system prompt, Gemma copies the few-shot text format (`find_ore {"ore":"iron"}`) instead of emitting native tool calls. `ToolCallParser` recovers these, so Gemma's score depends on that parser.

## Caveats

- The suite and the brain were developed together, so these pass rates are optimistic. Three fixes were driven by earlier runs of this same suite:
  - no tools for unrouted messages;
  - no running of text calls in text-only requests;
  - `name`-argument recovery, plus knowledge-question routing.

  Earlier runs: Qwen 43/46, Gemma 38/46.
- One sample per case. A one-case difference between the models is noise.

## Rerun on the laptop (Windows)

```
scripts\setup-local-llm.cmd -Models both
gradlew.bat :brain:replay -Pllm.model=all
```

The same report then gets real per-call latency and nvidia-smi VRAM (peak and delta over baseline), in `brain\build\reports\replay\summary.md`.
