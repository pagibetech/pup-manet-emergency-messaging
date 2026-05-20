# Architecture

Last updated: 2026-05-20

Purpose: PUP MANET emergency messaging prototype with Android phones connected to ESP32 nodes, LoRa node-to-node transport, and a simulation-first Raspberry Pi gateway/failover layer.

Boundaries:
- `esp32-node-platformio/`: ESP32 PlatformIO firmware, Bluetooth SPP service, packet parser, and controlled SX1278 LoRa live-test environments.
- `android-chat-app/`: Kotlin Jetpack Compose Android app, simulation-first UI, Bluetooth permission/readiness flow, Classic Bluetooth SPP socket layer, and Step 023 Android-to-LoRa demo controls.
- `rpi-gateway/`: Python simulation-first Raspberry Pi gateway model, router-link simulation, failover, buffering, recovery, and LoRa SPI abstraction baseline.
- `docs/`: workbook, operational memory, diagrams, test procedures, and Codex task logs.

Current physical baseline path:

`Android Phone A -> Bluetooth SPP -> ESP32 NODE_A -> SX1278 LoRa -> ESP32 NODE_B -> Bluetooth SPP -> Android Phone B`

Reverse direction must also be validated for Step 8.1.

Simulation-first rule:
- Prefer simulation and abstraction layers before hardware-specific behavior.
- Do not introduce new real ESP32, Raspberry Pi, or Android logic unless the workbook step explicitly allows it.

Current next milestone:
- Step 8.1 / STEP 034 - Baseline Test.
- This is a physical validation step, not a coding task.

