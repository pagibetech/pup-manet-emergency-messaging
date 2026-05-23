# Project Status

Last updated: 2026-05-23

Overall state: STEP 038 remains the current validation task. The gateway/backhaul architecture has changed to Raspberry Pi 3B gateways communicating over Tailscale VPN instead of WiFi router-to-router simulated satellite backhaul.

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed: `6929119 STEP 038 — Real Multi-Hop Relay Validation`

Workbook current milestone: STEP 038 - Real Multi-Hop Relay Validation.

Latest completed workbook step: STEP 037 - Multi-Hop Routing Foundation.

Next design milestone: STEP 039 - Raspberry Pi Tailscale Gateway Backhaul Architecture.

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
- STEP 036 real Chat UI integration passed.
- STEP 037 multi-hop routing foundation passed with `hopCount`, `ttl`, and `previousHop` active.

Current gateway/backhaul design:
- `RPi3B Gateway A <-> Tailscale VPN <-> RPi3B Gateway B`.
- Each MANET network still has 3 ESP32 LoRa nodes paired to Android phones via Bluetooth.
- Each local MANET has a Raspberry Pi 3B gateway with LoRa module.
- Gateway A and Gateway B connect directly to the internet through LAN or WiFi.
- Tailscale provides encrypted tunneling/private networking between the two gateways.
- Router-to-router simulated satellite link is removed.
- WiFi routers are local internet access/router/AP devices only, not inter-network backhaul.
- GSM/cellular remains optional fallback/backhaul.

STEP 035 physical validation evidence:
- Phone A connected to `PUP-MANET-NODE_A` with MAC shown.
- Phone B connected to `PUP-MANET-NODE_B` with MAC shown.
- NODE_A log shows `BT_RX` and LoRa forwarding.
- NODE_B log shows `LORA_RX` and `BT_TX`.
- Phone B displays `Incoming: Emergency message from Phone A`.
- Status includes `RECEIVED_OVER_LORA`.
- ACK path returns `FORWARDED_OVER_LORA`.

Tested branch/HEAD for STEP 037: `step-002-003-esp32-simulation` @ `f7e2501`.

STEP 036 changes:
- Added auto-receive `LaunchedEffect` in `MainActivity.kt` that polls Bluetooth SPP while connected.
- Auto-inserts incoming `MESSAGE` packets into the Chat tab with `Received via LoRa` indicator, source node, and timestamp.
- Adds `[CHAT_RX_LORA]` and `[ANDROID_RX]` logs to the event log.
- Preserves Sim tab diagnostics, manual Check In button, and simulation fallback.

STEP 037 validation evidence:
- Existing 2-node path still works: `Phone A Chat -> NODE_A -> LoRa -> NODE_B -> Phone B Chat`.
- Reverse path also works: `Phone B Chat -> NODE_B -> LoRa -> NODE_A -> Phone A Chat`.
- Logs show `[BT_RX]`, `[LORA_TX]`, `[LORA_RX]`, `[ROUTE_DECISION] deliver_local`, and `[BT_TX]`.
- New multi-hop fields confirmed active: `hopCount`, `ttl`, `previousHop`.
- No regression from STEP 035 / STEP 036.

Current blocker:
- No blocker for continuity update. STEP 038 real multi-hop relay validation remains pending, and Tailscale gateway backhaul implementation is not yet validated.

Future gateway-code requirements:
- Tailscale peer IP configuration.
- Message relay API/socket between gateways.
- Store-and-forward queue.
- Link status monitoring.
- Optional GSM fallback.

Troubleshooting notes resolved before STEP 035 pass:
- stale Bluetooth socket
- `localBridgeNode` vs `destinationNode` separation
- LoRa-to-Bluetooth forwarding
- Android blocking read for full JSON line
- Kotlin `SimMetrics` type mismatch
