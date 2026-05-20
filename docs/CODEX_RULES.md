# Codex Rules

Last updated: 2026-05-20

Project rules:
- Always follow `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`.
- Work on one workbook task at a time.
- Continue only from the next incomplete task.
- Do not rewrite the project for a narrow task.
- Keep ESP32, Raspberry Pi, Android, and docs responsibilities separated.
- Support simulation mode first before hardware-dependent behavior.
- Prioritize documented test procedures and acceptance criteria.
- Log completed tasks in `docs/codex-task-logs/`.
- Report validation commands and results when build, test, or simulation work is performed.

Before editing:
- Identify the current workbook step.
- Identify the expected output.
- Keep changes limited to files allowed by the step.
- Stop and clarify if the requested change conflicts with the workbook.

Current workbook step:
- Step 8.1 / STEP 034 - Baseline Test.

Expected output:
- Test report, phone screenshots, ESP32/RPi terminal logs, GitHub repo URL, branch, and commit SHA.

Codex conservation:
- Codex is reserved for difficult debugging, validation, build/test execution, and final integration.
- Avoid Codex for simple docs, markdown-only updates, boilerplate, repetitive edits, and basic refactors unless explicitly requested.
- For implementation, prefer Kimi as primary model and DeepSeek for cheap/repetitive coding.

