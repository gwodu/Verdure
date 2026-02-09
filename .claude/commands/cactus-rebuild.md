Read `CLAUDE.md`, `README.md`, and `DEVLOG.md` to understand Verdure's architecture and current state. Then produce a concrete migration plan to rebuild Verdure on Cactus Compute (YC S25) using the Kotlin SDK.

**Your task:**
1. Summarize the current Verdure architecture in 5-8 bullets (LLM engine, tools, services, UI, widget, notification flow).
2. Identify which parts must change to use Cactus (LLM engine, model lifecycle, permissions, build deps, threading/memory, tool calling).
3. Choose a Cactus model strategy (local-only vs local-first, model slug, context size, token limits) and justify the choice for a privacy-first mobile assistant.
4. Propose a phased migration plan with explicit deliverables per phase. Keep phases small and shippable.
5. List the exact files likely to change or be added, with brief reasons.
6. Call out risks and validation steps (battery, memory, latency, background service interactions, widget updates).

**Constraints:**
- Preserve Verdure’s core principles: privacy-first, on-device processing, "silent partner" UX, tool-based architecture.
- Prefer incremental changes over a full rewrite when possible.
- Keep plan actionable for a single developer.

Output should be a concise, ordered plan with concrete steps and file targets.
