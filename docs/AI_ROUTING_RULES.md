# AI Routing Rules

Last updated: 2026-05-20

Routing order:
1. Gemini/Gemma for simple documentation and helper work.
2. DeepSeek for cheap/repetitive coding.
3. Kimi K2.6 for primary heavy coding.
4. Codex for premium repo execution, validation, difficult debugging, and final integration.

Current routing policy:
- Use local machine + hardware test bench for Step 8.1.
- Use Kimi for implementation if a later workbook task requires coding.
- Use DeepSeek for simple or repetitive implementation tasks.
- Use Codex only for validation, difficult blockers, dependency-aware debugging, build/test execution, or final milestone integration.

Codex conservation rules:
- Do not default to Codex for implementation.
- Avoid Codex for simple docs, workbook-only updates, markdown-only updates, simple snippets, UI tweaks, boilerplate, repetitive edits, or basic refactoring.
- Preserve remaining Codex capacity for tasks that require terminal-aware repo validation.

Escalation path:
- Kimi fails -> ChatGPT analysis -> Codex validation/fix.
- Release/final validation -> Codex.
- Codex low or exhausted -> Roo Code with Kimi primary and DeepSeek backup.

