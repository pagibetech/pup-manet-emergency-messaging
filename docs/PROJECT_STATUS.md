# Project Status

Last updated: 2026-06-05

Overall state: STEP043 LoRa HELLO packet-format fix is built. STEP042A remains physically validated for A-side discovery only, and STEP042B remains physically validated for bidirectional 2-node Android LoRa messaging only.

Clarified requirements recorded 2026-06-02 (no source changes yet):
- Default node IDs: nodeA1, nodeA2, nodeA3, nodeB1, nodeB2, nodeB3.
- Android shows only online/reachable nodes; nodes remain visible even without Android phones attached.
- One Android phone maximum per node.
- Node list grouped by mesh/gateway in Android UI.
- Presence protocol: HELLO interval = 10 sec; offline timeout = 30 sec.
- STEP042 split into: STEP042A Node Discovery and Reachability; STEP042B Destination Messaging; STEP042C Delivery Tracking; STEP042D Store-and-Forward.
- Delivery tracking statuses: MESSAGE (sent), DELIVERED (received at destination node), SEEN (read by recipient Android user).

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed before STEP043 fix: `e8cf02d STEP042A Gateway node advertisement and serial bridge`

Workbook current milestone: STEP043 - Fix LoRa HELLO Packet Format.

Latest completed workbook step: STEP042B - Destination Messaging physical validation, limited to `nodeA1 <-> nodeA2`.

Implementation note: ESP32-to-Raspberry Pi USB serial bridge is validated end-to-end. Gateway Bluetooth-disable cleanup is validated and must not be undone. The current Android mapping change is temporary for 2-node validation because `nodeA3` has not been deployed.

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
- Gateway Bluetooth cleanup passed: `gatewayA_lora` and `gatewayB_lora` boot with Bluetooth DISABLED; gateway ESP32 devices do not appear for Android pairing.
- STEP042A discovery export root cause found and fixed in `esp32-node-platformio/src/main.cpp`; A-side physical discovery validation passed for `nodeA1`, `nodeA2`, and `gatewayA`.
- STEP042B bidirectional 2-node Android LoRa messaging passed for `nodeA1 -> nodeA2` and `nodeA2 -> nodeA1`.
- STEP043 LoRa HELLO packet-format fix built: gateway HELLO packets now transmit over LoRa as BT-MANET protocol JSON instead of compact `BT1|...`.

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

Gateway Bluetooth cleanup validation:
- Gateway ESP32 devices are connected to Raspberry Pi gateways by USB serial and must not appear in Android Bluetooth scans or accept Android pairing.
- `gatewayA_lora` and `gatewayB_lora` boot with `Bluetooth: DISABLED`.
- Normal MANET node ESP32 devices still keep Bluetooth enabled.

STEP042A discovery export root cause:
- Compact LoRa relay HELLO packets such as `HELLO-nodeA1` and `HELLO-nodeA2` were parsed as `MESSAGE` unconditionally.
- They were routed, relayed, and sometimes duplicate-dropped before node table insertion.
- NODE_LIST exported only the local boot entry, so Android showed `count=1` even while LoRa HELLO traffic was working.

STEP042A discovery export fix:
- Infer compact relay HELLO packets from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId` payload.
- Learn HELLO packets before route/duplicate handling.
- Add `hopCount` to node table entries.
- Add debug logs: `[NEIGHBOR_ADD]`, `[NEIGHBOR_UPDATE]`, `[NODE_LIST_EXPORT]`.
- NODE_LIST generation removes expired nodes before export and logs each exported row.
- Build passed for `nodeA1_lora`, `nodeA2_lora`, `gatewayA_lora`, and `gatewayB_lora`.

STEP042A physical validation status:
- PASS for A-side discovery only.
- Flashed/running: `gatewayA_lora` as `gatewayA`, `gatewayB_lora` as `gatewayB`, `nodeA1_lora` as `nodeA1`, and `nodeA2_lora` as `nodeA2`.
- Android phones show `nodeA1 ONLINE`, `nodeA2 ONLINE`, and `gatewayA ONLINE`.
- This is not full topology validation: `nodeA3` has not been flashed or validated, and `gatewayB` is flashed/broadcasting `HELLO-gatewayB` but is not yet visible in Android discovery.

STEP042B physical validation status:
- PASS for bidirectional 2-node Android-to-Android LoRa bridge only.
- Validated paths: `nodeA1 -> nodeA2` and `nodeA2 -> nodeA1`.
- Phone A sent `hello from A`; Phone B received it via LoRa on path `nodeA1 -> nodeA2`.
- Phone B sent `hello from b`; Phone A received it via LoRa on path `nodeA2 -> nodeA1`.
- Temporary Android validation fix: `peerNodeForConnectedEsp32()` in `MainActivity.kt` was changed from `"nodeA2" -> "nodeA3"` to `"nodeA2" -> "nodeA1"` because `nodeA3` is not deployed.

Current blockers / open issues:
- STEP043 physical reciprocal gateway discovery validation is pending.
- `gatewayB` firmware boots and broadcasts `HELLO-gatewayB`, but `gatewayB` does not yet appear in Android discovered nodes.
- Android Nodes/Route/Topology UI still partly uses simulated Node Alpha/Bravo/Charlie/Delta labels.
- Route tab can show `No route` while real LoRa messages are delivered.
- Discovered node list works, but send destination is still not fully driven by live discovered nodes.
- `nodeA3` has not been flashed or validated.
- GatewayB repeated reset/garbage serial output needs investigation.

STEP043 - LoRa HELLO Packet Format:
- Validation finding: Gateway A transmitted `BT1|HELLO-gatewayA-...|gatewayA|BROADCAST|{"gatewayId":"A"}` and Gateway B received RF payload, proving LoRa communication is working.
- Root cause: HELLO advertisements were transmitted in compact relay format while the LoRa protocol parser path expects full BT-MANET JSON.
- Expected parser format: JSON with `protocolVersion`, `packetType`, `packetId`, `sourceNode`, `destinationNode`, `payload`, `hopPath`, `hopCount`, `ttl`, `previousHop`, `retryCount`, `timestamp`, `status`, and `checksum`.
- Fix in `esp32-node-platformio/src/main.cpp`: `sendHelloBroadcast()` now sends `serializeProtocolPacket(packet)` over LoRa; relayed HELLO packets also remain JSON; non-HELLO relay messages still use existing compact relay format; discovery logs include `[HELLO] discovered <nodeId>`.
- Build validation: `pio run -e gatewayA_lora -e gatewayB_lora` PASS; `pio run -e nodeA1_lora -e nodeA2_lora` PASS.
- Physical validation pending: Gateway B should log `[HELLO] discovered gatewayA`; Gateway A should log `[HELLO] discovered gatewayB`; `STATUS` / `NEIGHBORS` should list both gateways.

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
- Physically validate STEP043 reciprocal gateway HELLO discovery.
- After STEP043 is accepted, continue with gatewayB Android discovery and replacement of the hardcoded Android destination mapping with live discovered-node selection.
- Do not start STEP042C Delivery Tracking or STEP042D Store-and-Forward yet.

Troubleshooting notes resolved before STEP 035 pass:
- stale Bluetooth socket
- `localBridgeNode` vs `destinationNode` separation
- LoRa-to-Bluetooth forwarding
- Android blocking read for full JSON line
- Kotlin `SimMetrics` type mismatch
