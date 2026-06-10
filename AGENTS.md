# AGENTS.md

## Project Rules

- Always follow `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`.
- Work on one workbook task at a time.
- Do not rewrite the entire project for a narrow task.
- Keep ESP32, Raspberry Pi, and Android responsibilities separated.
- Support simulation mode first before adding hardware-dependent behavior.
- Prioritize compliance with documented test procedures and acceptance criteria.
- Log every completed task in `docs/codex-task-logs/`.
- Report validation commands and results whenever a task includes build, test, or simulation work.
- Do not implement actual ESP32, Raspberry Pi, or Android logic until the workbook step explicitly allows it.

## Step Discipline

Before editing files, identify the current workbook step and expected output. Keep changes limited to the files allowed by that step. If a requested change conflicts with the workbook, stop and clarify before proceeding.

## Repository Boundaries

- `esp32-node-platformio/` is reserved for ESP32 PlatformIO work.
- `rpi-gateway/` is reserved for Raspberry Pi gateway work.
- `android-chat-app/` is reserved for Android chat app work.
- `docs/` is reserved for workbook, diagrams, test procedures, and Codex task logs.

