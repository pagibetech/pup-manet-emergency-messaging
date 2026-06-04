# AI Session Handoff

Last updated: 2026-06-04

Current milestone: STEP042A - Node Discovery and Reachability - discovery export fix built; physical acceptance pending.

Latest completed milestone: STEP041 - ESP32 ↔ Raspberry Pi Serial Bridge Integration; gateway Bluetooth cleanup validated.

Unfinished task: STEP042A physical validation. Do not mark STEP042A complete and do not start STEP042B messaging yet.

Pending validations:
- Flash `nodeA1_lora` and `nodeA2_lora`, power both nodes, wait 60 seconds, connect Phone A to `PUP-MANET-nodeA1`, connect Phone B to `PUP-MANET-nodeA2`, press Refresh Nodes, and confirm Android NODE_LIST `count>=2`.
- Expected discovered nodes: `nodeA1,A,ONLINE` and `nodeA2,A,ONLINE`.
- Existing 2-node Chat LoRa path and gateway Bluetooth-disable behavior must remain non-regressed.
- ESP32 gateway node to Raspberry Pi USB serial bridge remains VALIDATED with STEP041.
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
- STEP042A physical acceptance is pending. The firmware fix is built, but Android must still show NODE_LIST `count>=2`.
- Do not start STEP042B messaging yet.

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

Gateway Bluetooth cleanup documented:
- Gateway ESP32 devices connected to Raspberry Pi gateways by USB serial boot with Bluetooth DISABLED.
- Android Bluetooth scan now shows only `PUP-MANET-nodeA1` and `PUP-MANET-nodeA2`.
- Gateway ESP32 devices no longer appear in Android scans or accept Android pairing.
- Normal MANET node ESP32 devices keep Bluetooth enabled.

STEP042A discovery export root cause and fix:
- Root cause: compact LoRa relay HELLO packets such as `HELLO-nodeA1` and `HELLO-nodeA2` were parsed as `MESSAGE` unconditionally. They were routed, relayed, and sometimes duplicate-dropped before node table insertion, causing NODE_LIST to export only the local boot entry.
- Fix in `esp32-node-platformio/src/main.cpp`: infer compact relay HELLO packets from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId` payload; learn HELLO packets before route/duplicate handling; add `hopCount` to node table entries; add `[NEIGHBOR_ADD]`, `[NEIGHBOR_UPDATE]`, and `[NODE_LIST_EXPORT]` logs; remove expired nodes before NODE_LIST export and log each exported row.
- Build result after fix: `nodeA1_lora`, `nodeA2_lora`, `gatewayA_lora`, and `gatewayB_lora` all SUCCESS.

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
- Next incomplete task for this thread: STEP042A physical validation.
- STEP042B-D remain queued. Do not start STEP042B until STEP042A is physically accepted.

Expected outputs:
- Android NODE_LIST `count>=2` on Phone A connected to `PUP-MANET-nodeA1` and Phone B connected to `PUP-MANET-nodeA2`.
- Logs include `[NEIGHBOR_ADD]`, `[NEIGHBOR_UPDATE]`, and `[NODE_LIST_EXPORT]` with node id, gateway id, last seen, hop count, and exported node count.
- Local LoRa MANET routing must remain non-regressed during STEP042A validation.

Latest build/test result:
- STEP 038 is treated as Stable STEP038 baseline firmware, not final firmware.
- STEP040B tested branch/HEAD recorded as `step-002-003-esp32-simulation` @ `8e018c0`.
- STEP041 tested on same branch/HEAD with serial bridge integration.
- Gateway-to-gateway encrypted internet tunnel is operational through Tailscale TCP relay.
- ESP32-to-Raspberry Pi USB serial bridge is operational with `[GW_JSON]` protocol.
- Gateway Bluetooth cleanup passed: gateway builds boot Bluetooth DISABLED; normal node builds keep Bluetooth enabled.
- STEP042A fix build passed: `nodeA1_lora`, `nodeA2_lora`, `gatewayA_lora`, and `gatewayB_lora`.
- RPi gateway simulation tests last known passing through failover/recovery baseline.
- No physical STEP042A Android acceptance has been recorded yet.

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

Recommended next model/tool: proceed with local ESP32/Android hardware validation for STEP042A; Codex for focused validation only.
