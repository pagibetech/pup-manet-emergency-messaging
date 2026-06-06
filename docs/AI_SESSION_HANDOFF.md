# AI Session Handoff

Last updated: 2026-06-06

Current milestone: STEP043 - Fix LoRa HELLO Packet Format. Initial JSON-oriented fix failed physical validation; corrected compact `BT1` receiver-side fix is built and physical validation is pending.

Latest completed milestone: STEP042B - Destination Messaging physical validation, limited to `nodeA1 <-> nodeA2`.

Unfinished task: flash/validate `gatewayA_lora` and `gatewayB_lora` with compact `BT1` HELLO packets, then confirm both ESP32s discover each other through `STATUS` and `NEIGHBORS`.

Pending validations:
- STEP042A A-side discovery is physically validated: Android shows `nodeA1 ONLINE`, `nodeA2 ONLINE`, and `gatewayA ONLINE`.
- STEP042B 2-node messaging is physically validated: Phone A `hello from A` reached Phone B via `nodeA1 -> nodeA2`; Phone B `hello from b` reached Phone A via `nodeA2 -> nodeA1`.
- Existing 2-node Chat LoRa path and gateway Bluetooth-disable behavior must remain non-regressed.
- `gatewayB` boots/broadcasts `HELLO-gatewayB` but does not yet appear in Android discovery.
- `nodeA3` has not been flashed or validated.
- ESP32 gateway node to Raspberry Pi USB serial bridge remains VALIDATED with STEP041.
- LoRa-to-LoRa gateway backup backhaul is not yet validated.
- Initial STEP043 JSON-oriented code path failed physical validation. Corrected compact `BT1` receiver-side fix is built locally, but reciprocal gateway RF discovery has not yet been physically accepted.

Clarified requirements recorded 2026-06-02 (no source changes yet):
- Default node IDs: nodeA1, nodeA2, nodeA3, nodeB1, nodeB2, nodeB3.
- Android shows only online/reachable nodes; nodes remain visible even without Android phones attached.
- One Android phone maximum per node.
- Node list grouped by mesh/gateway in Android UI.
- Presence protocol: HELLO interval = 10 sec; offline timeout = 30 sec.
- STEP042 split into: STEP042A Node Discovery and Reachability; STEP042B Destination Messaging; STEP042C Delivery Tracking; STEP042D Store-and-Forward.
- Delivery tracking statuses: MESSAGE, DELIVERED, SEEN.

Blockers:
- STEP043 physical validation is incomplete: Gateway A and Gateway B must be flashed and monitored until compact `BT1` HELLO RX logs `[LORA_RELAY_PARSE] valid`, `[DISCOVERY] HELLO from <peerGateway>`, and `[NEIGHBOR_ADD]` / `[NEIGHBOR_UPDATE]`.
- Prior `gatewayB` discovery is incomplete: firmware boots and broadcasts `HELLO-gatewayB`, but Android does not show `gatewayB` in the discovered node list.
- Android send destination is still not fully driven by live discovered nodes; `MainActivity.kt` currently uses a temporary validation mapping from `nodeA2` to `nodeA1` because `nodeA3` is not deployed.
- Android Nodes/Route/Topology UI still partly uses simulated Node Alpha/Bravo/Charlie/Delta labels.
- Route tab can show `No route` even while real LoRa messages are delivered.
- GatewayB repeated reset/garbage serial output needs investigation.

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
- Android Bluetooth scan shows normal node ESP32 devices only; gateway ESP32 devices do not appear for pairing.
- Gateway ESP32 devices no longer appear in Android scans or accept Android pairing.
- Normal MANET node ESP32 devices keep Bluetooth enabled.

STEP042A discovery export root cause and fix:
- Root cause: compact LoRa relay HELLO packets such as `HELLO-nodeA1` and `HELLO-nodeA2` were parsed as `MESSAGE` unconditionally. They were routed, relayed, and sometimes duplicate-dropped before node table insertion, causing NODE_LIST to export only the local boot entry.
- Fix in `esp32-node-platformio/src/main.cpp`: infer compact relay HELLO packets from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId` payload; learn HELLO packets before route/duplicate handling; add `hopCount` to node table entries; add `[NEIGHBOR_ADD]`, `[NEIGHBOR_UPDATE]`, and `[NODE_LIST_EXPORT]` logs; remove expired nodes before NODE_LIST export and log each exported row.
- Build result after fix: `nodeA1_lora`, `nodeA2_lora`, `gatewayA_lora`, and `gatewayB_lora` all SUCCESS.

STEP042A physical validation result:
- PASS for A-side discovery only.
- Flashed/running firmware: `gatewayA_lora` as `gatewayA`, `gatewayB_lora` as `gatewayB`, `nodeA1_lora` as `nodeA1`, and `nodeA2_lora` as `nodeA2`.
- Android discovered nodes: `nodeA1 ONLINE`, `nodeA2 ONLINE`, and `gatewayA ONLINE`.
- Remaining gap: `gatewayB` is flashed and broadcasts `HELLO-gatewayB`, but is not yet visible in Android discovery.

STEP042B physical validation result:
- PASS for bidirectional 2-node Android-to-Android LoRa messaging only.
- Validated paths: `nodeA1 -> nodeA2` and `nodeA2 -> nodeA1`.
- Phone A sent `hello from A`; Phone B received it via LoRa.
- Phone B sent `hello from b`; Phone A received it via LoRa.
- Temporary Android validation fix: `peerNodeForConnectedEsp32()` in `MainActivity.kt` was changed from `"nodeA2" -> "nodeA3"` to `"nodeA2" -> "nodeA1"` because `nodeA3` has not been deployed.

STEP043 LoRa HELLO packet-format correction:
- Physical validation finding: after flashing `gatewayA_lora` and `gatewayB_lora`, Gateway A still transmitted compact HELLO frames: `BT1|HELLO-gatewayA-...|gatewayA|BROADCAST|{"gatewayId":"A"}|...`.
- Protocol decision: keep compact `BT1` LoRa relay packets because nodeA1/nodeA2 routing is already validated on compact packets.
- Corrected fix in `esp32-node-platformio/src/main.cpp`: `sendHelloBroadcast()` and all LoRa relay TX remain compact `BT1`; compact HELLO RX logs `[LORA_RELAY_PARSE] valid`; `parseLoRaRelayPacket()` infers HELLO from `HELLO-*` or `BROADCAST` + `gatewayId`; `processIncomingLoRaRelayPacket()` learns HELLO before duplicate/drop routing and returns immediately for HELLO; discovery logging includes `[HELLO] discovered <nodeId>` and `[DISCOVERY] HELLO from <nodeId> gateway=<gatewayId>`.
- Local build validation: `pio run -e gatewayA_lora -e gatewayB_lora` PASS; `pio run -e nodeA1_lora -e nodeA2_lora` PASS.
- Physical validation pending: Gateway B should log `[LORA_RELAY_PARSE] valid`, `[DISCOVERY] HELLO from gatewayA gateway=A`, and `[NEIGHBOR_ADD] node=gatewayA` or `[NEIGHBOR_UPDATE] node=gatewayA`; Gateway A should log the equivalent for `gatewayB`; `STATUS` / `NEIGHBORS` should list both gateways.

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
- Next incomplete activity for this thread: physically validate corrected STEP043 compact HELLO discovery.
- STEP042C-D remain queued. Do not start delivery tracking or store-and-forward work yet.

Expected outputs:
- Gateway B log includes `[LORA_RX] payload=BT1|HELLO-...`, `[LORA_RELAY_PARSE] valid`, `[DISCOVERY] HELLO from gatewayA gateway=A`, and `[NEIGHBOR_ADD]` / `[NEIGHBOR_UPDATE] node=gatewayA`.
- Gateway A log includes the equivalent discovery sequence for `gatewayB`.
- `STATUS` and `NEIGHBORS` on both ESP32s list both gateway nodes.
- Local LoRa MANET routing and validated `nodeA1 <-> nodeA2` Chat delivery remain non-regressed.

Latest build/test result:
- STEP 038 is treated as Stable STEP038 baseline firmware, not final firmware.
- STEP040B tested branch/HEAD recorded as `step-002-003-esp32-simulation` @ `8e018c0`.
- STEP041 tested on same branch/HEAD with serial bridge integration.
- Gateway-to-gateway encrypted internet tunnel is operational through Tailscale TCP relay.
- ESP32-to-Raspberry Pi USB serial bridge is operational with `[GW_JSON]` protocol.
- Gateway Bluetooth cleanup passed: gateway builds boot Bluetooth DISABLED; normal node builds keep Bluetooth enabled.
- STEP042A A-side physical discovery PASS: Android shows `nodeA1 ONLINE`, `nodeA2 ONLINE`, and `gatewayA ONLINE`.
- STEP042B 2-node physical messaging PASS: Android Chat delivery validated both directions between `nodeA1` and `nodeA2`.
- STEP043 corrected local builds PASS: `gatewayA_lora`, `gatewayB_lora`, `nodeA1_lora`, and `nodeA2_lora`.
- RPi gateway simulation tests last known passing through failover/recovery baseline.
- Full 6-node/gateway discovery is not complete: `nodeA3` is not deployed and `gatewayB` is not yet visible in Android discovery.

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

Recommended next model/tool: proceed with local ESP32/RPi hardware validation for corrected STEP043 compact HELLO discovery; Codex for focused log analysis if either gateway still fails to discover the peer.
