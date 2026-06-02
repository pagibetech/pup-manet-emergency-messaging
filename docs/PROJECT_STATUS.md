# Project Status

Last updated: 2026-06-02

Overall state: STEP041 — ESP32 ↔ Raspberry Pi Serial Bridge Integration is COMPLETE. Gateway service opens /dev/ttyUSB0, Tailscale TCP relay remains working, and BT-MANET/LORA protocol JSON lines pass bidirectionally between ESP32 gateway node and Raspberry Pi gateway service.

Clarified requirements recorded 2026-06-02 (no source changes yet):
- Default node IDs: nodeA1, nodeA2, nodeA3, nodeB1, nodeB2, nodeB3.
- Android shows only online/reachable nodes; nodes remain visible even without Android phones attached.
- One Android phone maximum per node.
- Node list grouped by mesh/gateway in Android UI.
- Presence protocol: HELLO interval = 10 sec; offline timeout = 30 sec.
- STEP042 split into: STEP042A Node Discovery and Reachability; STEP042B Destination Messaging; STEP042C Delivery Tracking; STEP042D Store-and-Forward.
- Delivery tracking statuses: MESSAGE (sent), DELIVERED (received at destination node), SEEN (read by recipient Android user).

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed: `8e018c0 feat(gateway): add TCP relay service foundation`

Workbook current milestone: STEP041 marked COMPLETE/PASS. Next incomplete steps: 8.2 Degradation Test; STEP042A Node Discovery and Reachability.

Latest completed workbook step: STEP041 - ESP32 ↔ Raspberry Pi Serial Bridge Integration.

Implementation note: ESP32-to-Raspberry Pi USB serial bridge is validated end-to-end. Gateway B → TCP → Gateway A → SERIAL_TX → ESP32 → GATEWAY_SERIAL_PARSE → ROUTE_DECISION → LORA_TX path works.

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
- STEP 038 stable baseline firmware preserves TTL, hopCount, duplicate suppression, Bluetooth bridge, and LoRa forwarding.
- STEP040B Python gateway TCP relay passed over Tailscale VPN with JSON relay, ACK, and heartbeat behavior working.
- STEP041 ESP32 ↔ Raspberry Pi Serial Bridge Integration complete with validated bidirectional flow.

Current gateway/backhaul design:
- `Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B`.
- Each MANET network still has 3 ESP32 LoRa nodes paired to Android phones via Bluetooth.
- Each local MANET has a Raspberry Pi 3B gateway with LoRa module.
- Internet backhaul is the primary gateway transport.
- Tailscale VPN is the primary encrypted tunnel.
- Ethernet/WiFi internet connectivity is preferred.
- LoRa-to-LoRa gateway backhaul is the backup path if internet is unavailable.
- `WiFi Router A <-> WiFi Router B simulated satellite link` is removed.
- WiFi routers are local internet access/router/AP devices only, not inter-network backhaul.
- GSM/cellular remains optional future fallback.

Architecture priority order:
- Priority 1: Local LoRa MANET.
- Priority 2: Tailscale VPN gateway tunnel.
- Priority 3: Long-range LoRa gateway backup.
- Priority 4: GSM/cellular optional fallback.

STEP040B Tailscale TCP relay validation:
- Gateway A: `pup-gateway-a` / `100.123.79.41`.
- Gateway B: `pup-gateway-b` / `100.79.214.18`.
- TCP relay port: `5050`.
- Tailscale ping between gateways: PASS.
- TCP relay test over Tailscale: PASS.
- Gateway B server logs include `[GATEWAY_START] Starting in SERVER mode` and `[TCP_SERVER] Listening on 0.0.0.0:5050`.
- Gateway A client logs include `[GATEWAY_START] Starting in CLIENT mode` and `[PEER_STATUS] Connected to 100.79.214.18:5050`.
- Test JSON packet `{"type":"test","message":"HELLO_FROM_GATEWAY_A"}` transferred from Gateway A to Gateway B.
- ACK returned successfully and heartbeat packets worked.

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

STEP041 — ESP32 ↔ Raspberry Pi Serial Bridge Integration validation evidence:
- Gateway service opened `/dev/ttyUSB0` on both Raspberry Pis.
- Tailscale TCP relay remained working throughout serial integration.
- Gateway B sent `STEP041-FINAL-001` over TCP to Gateway A.
- Gateway A logged `[TCP_RX]` for the incoming packet.
- Gateway A wrote the packet to ESP32 via `[SERIAL_TX]`.
- ESP32 logged `[GATEWAY_SERIAL_PARSE]` for the received serial packet.
- ESP32 parser returned `valid=true` for the protocol packet.
- ESP32 route engine executed `[ROUTE_DECISION]` for the packet.
- ESP32 LoRa transmit path executed `[LORA_TX]`.
- Full bidirectional path validated: `ESP32 → SERIAL_RX → Gateway → TCP → Peer Gateway → SERIAL_TX → ESP32 → LORA_TX`.

STEP041 bug fix documented:
- `gateway_service.py` now ignores non-`[GW_JSON]` serial lines in `_serial_read_loop`.
- This prevents ESP32 boot/debug logs from being parsed as JSON, which previously caused `json.JSONDecodeError` noise.
- The `_serial_read_loop` only attempts JSON parsing on lines prefixed with `[GW_JSON]`.
- Backup of pre-STEP041 gateway service preserved at `raspberry-pi-gateway/gateway_service.py.backup-step041`.

Current blocker:
- No blocker. STEP041 is complete and validated.

Firmware status:
- STEP038 ESP32 firmware is not final.
- STEP038 is now Stable STEP038 baseline firmware.
- Existing ESP32 routing logic remains valid: TTL, hopCount, duplicate suppression, Bluetooth bridge, and LoRa forwarding.
- ESP32 firmware now supports both NODE mode and GATEWAY mode serial bridging via `[GW_JSON]` prefix parsing in `processSerialLine()`.

Future gateway-code requirements:
- Store-and-forward queue.
- Gateway ACK tracking.
- Heartbeat monitoring.
- Peer online detection.
- Automatic reconnect.
- Gateway relay mode.
- LoRa backup backhaul mode.

Next incomplete task:
- 8.2 Degradation Test — RSSI < -78 dBm for 10 sec detection. Run DOCX degradation detection test per workbook.
- STEP042A Node Discovery and Reachability — queued after clarified requirements.

Troubleshooting notes resolved before STEP 035 pass:
- stale Bluetooth socket
- `localBridgeNode` vs `destinationNode` separation
- LoRa-to-Bluetooth forwarding
- Android blocking read for full JSON line
- Kotlin `SimMetrics` type mismatch
