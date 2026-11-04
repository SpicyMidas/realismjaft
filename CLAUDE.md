# CLAUDE.md

Project: local-LLM AI buddy mod for Minecraft. Read `AGENT_SPEC.md` before doing anything; it is the source of truth.

## Rules
- Minecraft APIs change between versions. Do not rely on memory. Read the decompiled/sources jar of the exact Minecraft + loader version in use before writing code against it.
- All gameplay goes through the `Tool` interface (schema, range, credit cost, cooldown, validation). The LLM never executes raw commands.
- Never block the server thread. LLM/HTTP/process work runs on worker threads; results are scheduled back to the server thread.
- The mod must work with the LLM unavailable. The GUI calls tools directly.
- Reply text is template-first and short (see AGENT_SPEC.md section 5). Do not add chatty or praising text anywhere. The Agent never confirms world facts without a tool result.
- Ignored blocks must be filtered inside the tool layer, before anything reaches the model.
- LLM backend is `LlmBackend`; default is `ManagedServer` (spawns `llama-server` with `--jinja` from `<gamedir>/localLLM/`). No JNI in v1.
- Work milestone by milestone (AGENT_SPEC.md section 8). Do not start the next one until the current one's checks pass.
- Anything I cannot verify by running tests or builds, say so explicitly. You cannot see the game; I will playtest.

## Commands
Fill in once the project is created (build, runClient, run game tests, run brain tests).

## Style
Small focused classes, no speculative abstractions, comments only where the reason is not obvious.
