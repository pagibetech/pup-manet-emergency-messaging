# Architecture

Last updated: 2026-06-02

Purpose: PUP MANET emergency messaging prototype with Android phones connected to ESP32 nodes, LoRa node-to-node transport, Raspberry Pi 3B local gateways, primary internet/Tailscale gateway backhaul, long-range LoRa gateway backup backhaul, and validated ESP32-to-Raspberry Pi USB serial bridge.

Boundaries:
- `esp32-node-platformio/`: ESP32 PlatformIO firmware, Bluetooth SPP service, packet parser, `[GW_JSON]` serial bridge parsing, and controlled SX1278 LoRa live-test environments.
- `android-chat-app/`: Kotlin Jetpack Compose Android app, simulation-first UI, Bluetooth permission/readiness flow, Classic Bluetooth SPP socket layer, and Step 023 Android-to-LoRa demo controls.
- `raspberry-pi-gateway/`: Python gateway TCP relay service with Tailscale VPN backhaul, USB serial bridge to ESP32 via `[GW_JSON]` protocol, LoRa SPI abstraction baseline, failover, buffering, recovery.
- `docs/`: workbook, operational memory, diagrams, test procedures, and Codex task logs.

Validated local physical path:

`Android Phone A -> Bluetooth SPP -> ESP32 NODE_A -> SX1278 LoRa -> ESP32 NODE_B -> Bluetooth SPP -> Android Phone B`

Reverse direction is validated through STEP 037.

Validated gateway serial bridge path (STEP041):

`ESP32 Gateway Node -> USB Serial [GW_JSON] -> Raspberry Pi Gateway Service -> Tailscale VPN TCP -> Peer Gateway Service -> USB Serial [GW_JSON] -> ESP32 Gateway Node -> SX1278 LoRa`

Final STEP039 gateway/backhaul design:

`Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B`

Confirmed STEP040B gateway relay validation:
- Gateway A: `pup-gateway-a` / `100.123.79.41`.
- Gateway B: `pup-gateway-b` / `100.79.214.18`.
- TCP relay port: `5050`.
- Tailscale ping between gateways: PASS.
- TCP relay test over Tailscale: PASS.
- Python gateway TCP relay successfully transferred JSON messages from Gateway A to Gateway B through Tailscale VPN.
- ACK and heartbeat behavior worked.

Confirmed STEP041 serial bridge validation:
- Gateway service opened `/dev/ttyUSB0` on both Raspberry Pis.
- Gateway B sent `STEP041-FINAL-001` over TCP to Gateway A.
- Gateway A logged `[TCP_RX]` and wrote to ESP32 via `[SERIAL_TX]`.
- ESP32 logged `[GATEWAY_SERIAL_PARSE]` with `valid=true`.
- ESP32 executed `[ROUTE_DECISION]` followed by `[LORA_TX]`.
- Full bidirectional serial bridge validated.

STEP041 bug fix: `gateway_service.py` ignores non-`[GW_JSON]` serial lines, preventing ESP32 boot/debug logs from being parsed as JSON.

Architecture change:
- The previous `WiFi Router A <-> WiFi Router B simulated satellite link` is removed.
- Internet backhaul is now the primary gateway transport.
- Tailscale VPN is the primary encrypted tunnel.
- Ethernet/WiFi internet connectivity is preferred.
- LoRa-to-LoRa gateway backhaul becomes the backup path if internet is unavailable.
- WiFi routers are local internet access/router/AP devices only, not the inter-network backhaul.
- GSM/cellular can remain an optional future fallback.
- The objective is RPi3B-to-RPi3B communication over the internet.
- ESP32-to-RPi USB serial bridge uses `[GW_JSON]` prefix protocol for JSON line framing.

Architecture priority order:
- Priority 1: Local LoRa MANET.
- Priority 2: Tailscale VPN gateway tunnel.
- Priority 3: Long-range LoRa gateway backup.
- Priority 4: GSM/cellular optional fallback.

Clarified requirements (2026-06-02):
- Default node IDs: nodeA1, nodeA2, nodeA3, nodeB1, nodeB2, nodeB3.
- Android shows only online/reachable nodes; nodes remain visible even without Android phones attached.
- One Android phone maximum per ESP32 node.
- Node list grouped by mesh/gateway in Android UI.
- Presence protocol: HELLO interval = 10 sec; offline timeout = 30 sec.
- STEP042 split into: STEP042A Node Discovery and Reachability; STEP042B Destination Messaging; STEP042C Delivery Tracking; STEP042D Store-and-Forward.
- Delivery tracking statuses: MESSAGE (sent), DELIVERED (received at destination node), SEEN (read by recipient Android user).

Future gateway code should support:
- Store-and-forward queue.
- Gateway ACK tracking.
- Heartbeat monitoring.
- Peer online detection.
- Automatic reconnect.
- Gateway relay mode.
- LoRa backup backhaul mode.

Firmware status:
- STEP038 ESP32 firmware is not final.
- It is now considered Stable STEP038 baseline firmware with STEP041 serial bridge additions.
- Existing routing logic remains valid: TTL, hopCount, duplicate suppression, Bluetooth bridge, and LoRa forwarding.
- ESP32 firmware supports both NODE mode and GATEWAY mode via `[GW_JSON]` prefix parsing in `processSerialLine()`.

Simulation-first rule:
- Prefer simulation and abstraction layers before hardware-specific behavior.
- Do not introduce new real ESP32, Raspberry Pi, or Android logic unless the workbook step explicitly allows it.

Current milestone:
- STEP041 — ESP32 ↔ Raspberry Pi Serial Bridge Integration — COMPLETE/PASS.
- Goal achieved: connect ESP32 gateway node to Raspberry Pi via USB serial and pass BT-MANET/LORA protocol JSON lines bidirectionally.
- Next incomplete step: 8.2 Degradation Test (RSSI < -78 dBm for 10 sec detection).
- STEP042A-D are queued as the next design/implementation milestones after clarified requirements.
