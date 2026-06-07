# Active Context

Last updated: 2026-06-07

Project: PUP MANET Emergency Messaging Prototype

Repository: https://github.com/pagibetech/pup-manet-emergency-messaging

Local path: `/Users/macbookm1max321tb/A_Design/A_Coding/ADM_Manet`

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed before STEP043 fix: `e8cf02d STEP042A Gateway node advertisement and serial bridge`

Workbook path: `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

Workbook latest referenced commit before STEP043 fix: `e8cf02d`

Latest completed workbook step: STEP045A - Mesh-Wide Presence Propagation.

Current milestone: STEP045A - Mesh-Wide Presence Propagation. Physical Android validation PASS / COMPLETE after firmware was flashed to `gatewayA`, `gatewayB`, `nodeA1`, and `nodeA2`.

Current feature: mesh-wide valid compact `BT1` HELLO presence propagation while preserving strict STEP044 corrupt-packet rejection.

Active implementation files: `esp32-node-platformio/src/main.cpp` contains the STEP042A discovery export fix and preserves gateway Bluetooth-disable behavior. `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt` has a temporary validation mapping change in `peerNodeForConnectedEsp32()` from `"nodeA2" -> "nodeA3"` to `"nodeA2" -> "nodeA1"` because `nodeA3` has not been flashed or deployed.

Resolved issue: corrupted compact `BT1` LoRa packets are now rejected before neighbor learning and no longer create malformed neighbors such as `gateway=UNKNOWN`, `node=BROADCAST`, or misspelled node IDs.

Current testing state: `gatewayA_lora` restored and running as `gatewayA`; `gatewayB_lora` restored and running as `gatewayB`; `nodeA1_lora` flashed/running as `nodeA1`; `nodeA2_lora` flashed/running as `nodeA2`. Android discovery now shows `nodeA1 ONLINE`, `nodeA2 ONLINE`, and `gatewayA ONLINE`. STEP042A is physically validated for this A-side discovery scope.

STEP042B physical validation: PASS for bidirectional 2-node Android-to-Android LoRa bridge after the temporary Android mapping fix. Phone A sent `hello from A`; Phone B received `hello from A` via LoRa on path `nodeA1 -> nodeA2`. Phone B sent `hello from b`; Phone A received `hello from b` via LoRa on path `nodeA2 -> nodeA1`.

STEP043 correction result: physical validation after flashing `gatewayA_lora` and `gatewayB_lora` showed Gateway A still transmitting compact HELLO frames: `BT1|HELLO-gatewayA-...|gatewayA|BROADCAST|{"gatewayId":"A"}|...`. The project protocol decision is to keep compact `BT1` LoRa relay packets because nodeA1/nodeA2 routing already works with compact packets.

STEP043 corrected fix: `esp32-node-platformio/src/main.cpp` keeps `sendHelloBroadcast()` and all LoRa relay TX on compact `BT1` format. The receiver-side compact parser now logs `[LORA_RELAY_PARSE] valid`, infers `HELLO` from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId`, learns the node through `learnNodeFromHelloPacket()` before duplicate/drop routing, and returns immediately for HELLO so duplicate handling does not block discovery. Discovery logging includes `[HELLO] discovered <nodeId>` and `[DISCOVERY] HELLO from <nodeId> gateway=<gatewayId>`.

STEP043 physical validation: PASS. Gateway A and Gateway B discover each other over compact `BT1` HELLO packets, and `TEST_FINAL_001` from `gatewayB` to `gatewayA` was received.

STEP044 first physical validation failed after commit `93dd506`: corrupt `BT1|HELLO-nodeA1-40000zno4eA1|...` was accepted and created `[NEIGHBOR_ADD] node=BROADCAST gateway=UNKNOWN`; corrupt source `gatlwayB` was accepted as `[NEIGHBOR_ADD] node=gatlwayB gateway=B`.

STEP044 corrective fix: `esp32-node-platformio/src/main.cpp` now performs strict compact `BT1` validation before packet learning/routing and adds HELLO semantic validation. HELLO learning rejects sources outside known formats (`nodeA1`-`nodeA3`, `nodeB1`-`nodeB3`, `gatewayA`, `gatewayB`), rejects `sourceNode=BROADCAST`, requires packet ID exactly `HELLO-<sourceNode>-<numeric timestamp>`, and requires strict gateway payload JSON of `{"gatewayId":"A"}` or `{"gatewayId":"B"}`. Invalid compact packets log `[LORA_DROP_CORRUPT] reason=<reason> payload=<short payload>` and return before `learnNodeFromHelloPacket()`.

STEP044 physical validation: PASS / COMPLETE. Corrective firmware commit `9dba0ef` was flashed and tested under RF stress with four active LoRa nodes: `gatewayA`, `gatewayB`, `nodeA1`, and `nodeA2`.

STEP044 evidence: corrupt LoRa packets were rejected with `[LORA_DROP_CORRUPT] reason=bad_prefix`, `[LORA_DROP_CORRUPT] reason=bad_field_count`, and `[LORA_DROP_CORRUPT] reason=malformed_gatewayId_json`. Valid packets still parsed with `[LORA_RELAY_PARSE] valid packet_id=HELLO-gatewayB...`, `[LORA_RELAY_PARSE] valid packet_id=HELLO-nodeA1...`, and `[LORA_RELAY_PARSE] valid packet_id=HELLO-nodeA2...`. No ghost neighbors were observed: no `gateway=UNKNOWN`, no `node=BROADCAST`, and no malformed names like `gatlwayB`. Node table remained stable at 4 nodes: `gatewayA`, `gatewayB`, `nodeA1`, `nodeA2`.

STEP044 local validation: PlatformIO builds PASS for `gatewayA_lora`, `gatewayB_lora`, `nodeA1_lora`, and `nodeA2_lora` after the corrective fix.

STEP045A root cause: `gatewayB` was missing from Android discovery because valid compact HELLO discovery was learned locally and then `processIncomingLoRaRelayPacket()` returned immediately, so learned gateway presence was not propagated onward to `nodeA1`/`nodeA2` node tables.

STEP045A firmware fix: `esp32-node-platformio/src/main.cpp` now forwards only validated HELLO presence packets after successful STEP044 parsing and local learning. Forwarding preserves compact `BT1`, decrements `ttl`, increments `hopCount`, updates `previousHop`, appends `hopPath`, uses a bounded HELLO relay cache to avoid loops, and logs `[HELLO_RELAY]`.

STEP045A build validation: `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora` PASS.

STEP045A physical validation: PASS. Firmware was flashed to `gatewayA`, `gatewayB`, `nodeA1`, and `nodeA2`. Android Nodes screen now shows Gateway A group with `nodeA1 ONLINE`, `gatewayA ONLINE`, and `nodeA2 ONLINE`, plus Gateway B group with `gatewayB ONLINE`.

Confirmed working flow: `Phone A Chat -> NODE_A -> LoRa -> NODE_B -> Phone B Chat`, plus reverse `Phone B Chat -> NODE_B -> LoRa -> NODE_A -> Phone A Chat`.

Confirmed gateway serial bridge flow: `Gateway B TCP TX -> Tailscale VPN -> Gateway A TCP RX -> SERIAL_TX -> ESP32 USB serial -> GATEWAY_SERIAL_PARSE -> ROUTE_DECISION -> LORA_TX`.

Evidence summary: logs show `[BT_RX]`, `[LORA_TX]`, `[LORA_RX]`, `[ROUTE_DECISION] deliver_local`, `[BT_TX]`, `[SERIAL_RX]`, `[SERIAL_TX]`, `[GATEWAY_SERIAL_PARSE]`, and `[TCP_RX]`; new multi-hop fields `hopCount`, `ttl`, and `previousHop` are active; no regression from prior steps.

Current design direction: `Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B`.

Architecture update: replace `WiFi Router A <-> WiFi Router B simulated satellite link` with `Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B`. Internet backhaul is now the primary gateway transport; Tailscale VPN is the primary encrypted tunnel; Ethernet/WiFi internet connectivity is preferred. LoRa-to-LoRa gateway backhaul is the backup path if internet is unavailable. GSM/cellular remains optional future fallback.

Architecture priority order: Priority 1 Local LoRa MANET; Priority 2 Tailscale VPN gateway tunnel; Priority 3 Long-range LoRa gateway backup; Priority 4 GSM/cellular optional fallback.

Existing ESP32 routing logic remains valid: TTL, hopCount, duplicate suppression, Bluetooth bridge, and LoRa forwarding. ESP32 firmware now supports both NODE mode and GATEWAY mode serial bridging via `[GW_JSON]` prefix parsing.

STEP041 bug fix: `gateway_service.py` now ignores non-`[GW_JSON]` serial lines in `_serial_read_loop`, preventing ESP32 boot/debug logs from being parsed as JSON. Only lines prefixed with `[GW_JSON]` are deserialized and relayed.

STEP040B evidence summary: Gateway B listened on `0.0.0.0:5050`; Gateway A connected to `100.79.214.18:5050`; Gateway A sent `{"type":"test","message":"HELLO_FROM_GATEWAY_A"}`; Gateway B logged `[TCP_RX]` from `100.123.79.41:<port>`; ACK returned; heartbeat packets worked.

STEP041 goal achieved: connect ESP32 gateway node to Raspberry Pi via USB serial and pass BT-MANET/LORA protocol JSON lines bidirectionally. Validated end-to-end.

Gateway Bluetooth cleanup status: complete and validated. Gateway ESP32 devices connected to Raspberry Pi gateways by USB serial must not appear in Android Bluetooth scans or accept Android pairing. Normal MANET node ESP32 devices keep Bluetooth enabled.

STEP042A discovery export root cause: compact LoRa relay HELLO packets such as `HELLO-nodeA1` and `HELLO-nodeA2` were parsed as `MESSAGE` unconditionally. They were routed, relayed, and sometimes duplicate-dropped before being inserted into the node table, so NODE_LIST exported only the local boot entry and Android showed `count=1`.

STEP042A discovery export fix in `esp32-node-platformio/src/main.cpp`: infer compact relay HELLO packets from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId` payload; learn HELLO packets before route/duplicate handling; add `hopCount` to node table entries; add `[NEIGHBOR_ADD]`, `[NEIGHBOR_UPDATE]`, and `[NODE_LIST_EXPORT]` logs; remove expired nodes before NODE_LIST export and log each exported row.

STEP042A physical acceptance procedure:
1. Completed for mesh-wide nodeA1/nodeA2/gatewayA/gatewayB discovery propagation.
2. Flashed/running: `nodeA1_lora`, `nodeA2_lora`, `gatewayA_lora`, `gatewayB_lora`.
3. Android discovered nodes: `nodeA1 ONLINE`, `nodeA2 ONLINE`, `gatewayA ONLINE`, and `gatewayB ONLINE`.
4. Remaining discovery scope: `nodeA3` has not been flashed or validated.

Open issues:
- This is a 2-node validation only; `nodeA3` has not been flashed or validated.
- Android Nodes/Route/Topology UI still partly uses simulated Node Alpha/Bravo/Charlie/Delta labels.
- Route tab can show `No route` even while real LoRa messages are delivered.
- Discovered node list works, but send destination is still not fully driven by live discovered nodes.
- GatewayB repeated reset/garbage serial output needs investigation.

Future gateway code must support store-and-forward queue, gateway ACK tracking, heartbeat monitoring, peer online detection, automatic reconnect, gateway relay mode, and LoRa backup backhaul mode.

Recommended model/tool: proceed with STEP045B Android Real-Network Cleanup; use Codex for focused Android cleanup only after studying workbook and continuity files.

Escalation guidance: do not start STEP042C/D. Do not change delivery tracking or store-and-forward until STEP045B Android real-network cleanup is complete.

Clarified requirements (2026-06-02):
- Default node IDs: nodeA1, nodeA2, nodeA3, nodeB1, nodeB2, nodeB3.
- Android shows only online/reachable nodes; nodes remain visible even without Android phones attached.
- One Android phone maximum per node.
- Node list grouped by mesh/gateway in Android UI.
- Presence protocol: HELLO interval = 10 sec; offline timeout = 30 sec.
- STEP042 split into: STEP042A Node Discovery and Reachability; STEP042B Destination Messaging; STEP042C Delivery Tracking; STEP042D Store-and-Forward.
- Delivery tracking statuses: MESSAGE (sent), DELIVERED (received at destination node), SEEN (read by recipient Android user).

STEP044 validation rules validated:
- Reject packets without `BT1|` prefix.
- Reject compact packets with field count other than 10.
- Reject blank `packetId`, `sourceNode`, or `destinationNode`.
- Reject invalid `sourceNode` or `destinationNode` characters.
- Reject HELLO packets whose `sourceNode` is `BROADCAST`.
- Reject HELLO packets whose `sourceNode` is not a known firmware node/gateway format.
- Reject HELLO packets whose `packetId` is not exactly `HELLO-<sourceNode>-<numeric timestamp>`.
- Reject HELLO packets unless payload is strict gateway JSON with `gatewayId` `A` or `B`.
- Reject non-numeric `ttl` or `hopCount`.
- Reject `ttl` or `hopCount` outside `0..DEFAULT_TTL`.

Next validation activity: STEP045B Android Real-Network Cleanup. Replace temporary hardcoded destination mapping with live discovered-node selection and clean simulated labels/route display without changing compact BT1 firmware behavior.
