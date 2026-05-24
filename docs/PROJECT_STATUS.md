# Project Status

Last updated: 2026-05-24

Overall state: STEP040B Tailscale TCP relay validation passed. Internet/Tailscale is the primary gateway backhaul, with long-range LoRa gateway backhaul as backup.

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed: `8e018c0 feat(gateway): add TCP relay service foundation`

Workbook current milestone: STEP041 - ESP32-to-Raspberry Pi Serial Bridge Integration.

Latest completed workbook step: STEP040B - Python Gateway TCP Relay Service.

Implementation note: Tailscale gateway-to-gateway TCP relay is validated. ESP32-to-Raspberry Pi serial integration is not complete yet and must remain STEP041.

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

Current blocker:
- No blocker for continuity update. STEP041 ESP32-to-Raspberry Pi serial bridge integration is pending.

Firmware status:
- STEP038 ESP32 firmware is not final.
- STEP038 is now Stable STEP038 baseline firmware.
- Existing ESP32 routing logic remains valid: TTL, hopCount, duplicate suppression, Bluetooth bridge, and LoRa forwarding.
- Future ESP32 firmware will support NODE mode and GATEWAY mode.

Future gateway-code requirements:
- Store-and-forward queue.
- Gateway ACK tracking.
- Heartbeat monitoring.
- Peer online detection.
- Automatic reconnect.
- Gateway relay mode.
- LoRa backup backhaul mode.

Next incomplete task:
- STEP041 - ESP32-to-Raspberry Pi Serial Bridge Integration.
- Goal: connect ESP32 gateway node to Raspberry Pi via USB serial and pass BT-MANET/LORA protocol JSON lines between ESP32 and Raspberry Pi gateway service.
- Do not mark ESP32 serial integration complete until validated.

Troubleshooting notes resolved before STEP 035 pass:
- stale Bluetooth socket
- `localBridgeNode` vs `destinationNode` separation
- LoRa-to-Bluetooth forwarding
- Android blocking read for full JSON line
- Kotlin `SimMetrics` type mismatch
