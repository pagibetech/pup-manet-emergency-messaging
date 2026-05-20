# Project Status

Last updated: 2026-05-20

Overall state: simulation-first implementation and early physical Android-ESP32-LoRa demo path exist. The next incomplete milestone is physical baseline validation.

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed: `8b4611d Add hybrid AI workflow workbook sheets`

Workbook current milestone: Step 8.1 / STEP 034 - Baseline Test.

Latest completed workbook step: Step 7.3 / STEP 033 - Android Network Selection Validation.

Completed highlights:
- ESP32 simulation and multi-node simulation baseline.
- ESP32 packet parser and Bluetooth service.
- Android app shell, simulation engine, routing visualization, packet abstraction, queue, transport bridge, and Bluetooth readiness layers.
- Android Bluetooth socket layer and live packet test support.
- Step 023 Android-to-LoRa-to-Android demo path.
- Raspberry Pi gateway simulation baseline, LoRa SPI abstraction, router-link simulation, latency simulation, failover, store-and-forward, and recovery simulation.
- Android build validation and network selection validation.
- Workbook AI workflow memory sheets.

Current blocker:
- Step 8.1 needs physical baseline evidence from the hardware test bench.

Do not mark complete until:
- PDR >= 95%.
- MANET forms in less than 30 seconds.
- Satellite path remains inactive.
- Screenshots/logs and GitHub branch/commit evidence are captured.

