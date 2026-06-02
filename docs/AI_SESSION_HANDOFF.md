# AI Session Handoff

Last updated: 2026-06-02

Current milestone: STEP041 — ESP32 ↔ Raspberry Pi Serial Bridge Integration — COMPLETE.

Latest completed milestone: STEP041 - ESP32 ↔ Raspberry Pi Serial Bridge Integration.

Unfinished task: 8.2 Degradation Test — RSSI < -78 dBm for 10 sec detection. STEP041 is complete.

Pending validations:
- Real multi-hop relay behavior beyond the confirmed 2-node Chat LoRa path.
- Route logs show relay decisions, ttl handling, hopCount updates, and previousHop tracking.
- Existing 2-node Chat LoRa path remains non-regressed.
- ESP32 gateway node to Raspberry Pi USB serial bridge: VALIDATED with STEP041.
- LoRa-to-LoRa gateway backup backhaul is not yet validated.

Clarified requirements recorded 2026-06-02 (no source changes yet):
- Default node IDs: nodeA1, nodeA2, nodeA3, nodeB1, nodeB2, nodeB3.
- Android shows only online/reachable nodes; nodes remain visible even without Android phones attached.
- One Android phone maximum per node.
- Node list grouped by mesh/gateway in Android UI.
- Presence protocol: HELLO interval = 10 sec; offline timeout = 30 sec.
- STEP042 split into: STEP042A Node Discovery and Reachability; STEP042B Destination Messaging; STEP042C Delivery Tracking; STEP042D Store-and-Forward.
- Delivery tracking statuses: MESSAGE, DELIVERED, SEEN.

Blockers:
- No blocker. STEP041 serial bridge integration is complete and validated.
- No blocker. Next incomplete step: 8.2 Degradation Test.

Architecture change:
- Old backhaul removed: `WiFi Router A <-> WiFi Router B simulated satellite link`.
- Finalized STEP039 backhaul direction: `Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B`.
- Each MANET still has 3 ESP32 LoRa nodes paired to Android phones.
- Each local MANET has one Raspberry Pi 3B gateway with LoRa module.
- Internet backhaul is now the primary gateway transport.
- Tailscale VPN is the primary encrypted tunnel.
- Ethernet/WiFi internet connectivity is preferred.
- LoRa-to-LoRa gateway backhaul is the backup path if internet is unavailable.
- WiFi routers are local internet access/router/AP devices only.
- GSM/cellular remains optional future fallback.

Confirmed STEP041 evidence:
- Gateway service opened `/dev/ttyUSB0` on both Raspberry Pis.
- Tailscale TCP relay remained working throughout the serial bridge integration.
- Gateway B sent `STEP041-FINAL-001` over TCP to Gateway A.
- Gateway A logged `[TCP_RX]` for the incoming packet.
- Gateway A wrote the packet to ESP32 via `[SERIAL_TX]`.
- ESP32 logged `[GATEWAY_SERIAL_PARSE]` for the received serial packet.
- ESP32 parser returned `valid=true` for the protocol packet.
- ESP32 route engine executed `[ROUTE_DECISION]` for the packet.
- ESP32 LoRa transmit path executed `[LORA_TX]`.
- Full bidirectional path: `ESP32 → SERIAL_RX → Gateway → TCP → Peer Gateway → SERIAL_TX → ESP32 → LORA_TX`.

STEP041 bug fix documented:
- `gateway_service.py` now ignores non-`[GW_JSON]` serial lines in `_serial_read_loop`.
- This prevents ESP32 boot/debug logs from being parsed as JSON, which previously caused `json.JSONDecodeError` noise.
- Only lines prefixed with `[GW_JSON]` are deserialized and relayed.
- Backup of pre-STEP041 gateway service preserved at `raspberry-pi-gateway/gateway_service.py.backup-step041`.

Confirmed STEP040B evidence:
- Gateway A `pup-gateway-a` Tailscale IP: `100.123.79.41`.
- Gateway B `pup-gateway-b` Tailscale IP: `100.79.214.18`.
- TCP relay port: `5050`.
- Tailscale ping between gateways: PASS.
- TCP relay test over Tailscale: PASS.
- Gateway B server logged `[GATEWAY_START] Starting in SERVER mode` and `[TCP_SERVER] Listening on 0.0.0.0:5050`.
- Gateway A client logged `[GATEWAY_START] Starting in CLIENT mode` and `[PEER_STATUS] Connected to 100.79.214.18:5050`.
- Gateway A sent `{"type":"test","message":"HELLO_FROM_GATEWAY_A"}` and logged `[TCP_TX] Sent type=test to peer`.
- Gateway B logged `[TCP_RX] From 100.123.79.41:<port> type=test body={"type":"test","message":"HELLO_FROM_GATEWAY_A"}`.
- ACK returned successfully and heartbeat packets worked.

Confirmed STEP 037 evidence:
- Existing 2-node path still works after multi-hop foundation: `Phone A Chat -> NODE_A -> LoRa -> NODE_B -> Phone B Chat`.
- Reverse path also works: `Phone B Chat -> NODE_B -> LoRa -> NODE_A -> Phone A Chat`.
- Logs show `[BT_RX]`, `[LORA_TX]`, `[LORA_RX]`, `[ROUTE_DECISION] deliver_local`, and `[BT_TX]`.
- New multi-hop fields confirmed active: `hopCount`, `ttl`, `previousHop`.
- No regression from STEP 035 / STEP 036.

Latest implementation instructions:
- Follow `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`.
- Work on one workbook task at a time.
- Keep ESP32, Raspberry Pi, Android, and docs responsibilities separated.
- Do not add hardware-dependent logic unless the workbook step explicitly allows it.
- Follow hybrid AI workflow Codex conservation rules.
- Next incomplete step: 8.2 Degradation Test.
- STEP042A-D are queued as the next design/implementation milestones after clarified requirements.

Expected outputs:
- STEP041 is complete and validated.
- Next expected outputs (8.2): RSSI degradation detection log showing RSSI < -78 dBm for 10 seconds, detection time recorded, route change triggered.
- Local LoRa MANET routing must remain non-regressed during 8.2.

Latest build/test result:
- STEP 038 is treated as Stable STEP038 baseline firmware, not final firmware.
- STEP040B tested branch/HEAD recorded as `step-002-003-esp32-simulation` @ `8e018c0`.
- STEP041 tested on same branch/HEAD with serial bridge integration.
- Gateway-to-gateway encrypted internet tunnel is operational through Tailscale TCP relay.
- ESP32-to-Raspberry Pi USB serial bridge is operational with `[GW_JSON]` protocol.
- RPi gateway simulation tests last known passing through failover/recovery baseline.
- No build, test, or simulation run was performed during this continuity documentation update.

Architecture priority order:
- Priority 1: Local LoRa MANET.
- Priority 2: Tailscale VPN gateway tunnel.
- Priority 3: Long-range LoRa gateway backup.
- Priority 4: GSM/cellular optional fallback.

ESP32 firmware status:
- STEP038 ESP32 firmware is not final.
- It is now considered Stable STEP038 baseline firmware with STEP041 serial bridge additions.
- Existing TTL, hopCount, duplicate suppression, Bluetooth bridge, and LoRa forwarding logic remains valid.
- ESP32 firmware supports both NODE mode and GATEWAY mode (serial bridging via `[GW_JSON]` prefix).

Future gateway-code requirements:
- Store-and-forward queue.
- Gateway ACK tracking.
- Heartbeat monitoring.
- Peer online detection.
- Automatic reconnect.
- Gateway relay mode.
- LoRa backup backhaul mode.

Recommended next model/tool: proceed with 8.2 Degradation Test. Use local ESP32/RPi hardware; Codex for focused validation only.
