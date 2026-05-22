# Project Status

Last updated: 2026-05-22

Overall state: STEP 035 is the first confirmed real RF end-to-end messaging milestone. The next incomplete milestone is STEP 036 - Real Chat UI Integration.

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed: `147431c fix(firmware): clarify ACK status for LoRa-not-ready case`

Workbook current milestone: STEP 036 - Real Chat UI Integration.

Latest completed workbook step: STEP 035 - Real End-to-End LoRa Message Delivery.

Completed highlights:
- ESP32 simulation and multi-node simulation baseline.
- ESP32 packet parser and Bluetooth service.
- Android app shell, simulation engine, routing visualization, packet abstraction, queue, transport bridge, and Bluetooth readiness layers.
- Android Bluetooth socket layer and live packet test support.
- Step 023 Android-to-LoRa-to-Android demo path.
- Raspberry Pi gateway simulation baseline, LoRa SPI abstraction, router-link simulation, latency simulation, failover, store-and-forward, and recovery simulation.
- Android build validation and network selection validation.
- Workbook AI workflow memory sheets.
- STEP 035 real RF flow passed: Phone A -> Bluetooth SPP -> NODE_A ESP32 -> LoRa RF -> NODE_B ESP32 -> Bluetooth SPP -> Phone B.

STEP 035 physical validation evidence:
- Phone A connected to `PUP-MANET-NODE_A` with MAC shown.
- Phone B connected to `PUP-MANET-NODE_B` with MAC shown.
- NODE_A log shows `BT_RX` and LoRa forwarding.
- NODE_B log shows `LORA_RX` and `BT_TX`.
- Phone B displays `Incoming: Emergency message from Phone A`.
- Status includes `RECEIVED_OVER_LORA`.
- ACK path returns `FORWARDED_OVER_LORA`.

Tested branch/HEAD: `step-002-003-esp32-simulation` @ `147431c4da544c6f0b0cc972f83bcc124ac2a069`. Existing uncommitted Android/ESP32 validation fixes were present and are intentionally not staged in this docs-only commit.

Current blocker:
- No blocker for continuity update. STEP 036 implementation/validation is next.

Troubleshooting notes resolved before STEP 035 pass:
- stale Bluetooth socket
- `localBridgeNode` vs `destinationNode` separation
- LoRa-to-Bluetooth forwarding
- Android blocking read for full JSON line
- Kotlin `SimMetrics` type mismatch
