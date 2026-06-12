# Architecture

Last updated: 2026-06-09

Purpose: PUP MANET emergency messaging prototype with Android phones connected to local ESP32 nodes over Bluetooth SPP, LoRa node-to-node MANET transport, Raspberry Pi 3B local gateways, primary internet/Tailscale gateway backhaul, long-range LoRa gateway backup backhaul, and validated ESP32-to-Raspberry Pi USB serial bridge.

Boundaries:
- `esp32-node-platformio/`: ESP32 PlatformIO firmware, Bluetooth SPP phone-to-node access service for normal MANET nodes, gateway Bluetooth-disabled LoRa builds, packet parser, `[GW_JSON]` serial bridge parsing, STEP042A HELLO/node table discovery export fix, STEP042C delivery tracking with exact `ackFor` correlation, STEP045A mesh-wide HELLO propagation, STEP046A controlled multi-hop lab mode, STEP047 Bluetooth access-layer diagnostics, and controlled SX1278 LoRa live-test environments.
- `android-chat-app/`: Kotlin Jetpack Compose Android app, simulation-first UI, Bluetooth permission/readiness flow, Classic Bluetooth SPP phone-to-local-node socket layer, and Step 023 Android-to-LoRa demo controls.
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

Gateway ESP32 Bluetooth policy:
- Gateway ESP32 devices are connected to Raspberry Pi gateways by USB serial.
- Gateway ESP32 builds `gatewayA_lora` and `gatewayB_lora` boot with Bluetooth DISABLED.
- Gateway ESP32 devices must not appear in Android Bluetooth scans or accept Android pairing.
- Normal MANET node ESP32 devices keep Bluetooth enabled for Android phones.
- Validation: Android scan shows only `PUP-MANET-nodeA1` and `PUP-MANET-nodeA2`, not gateway ESP32 devices.

STEP047 Bluetooth access-layer policy:
- Bluetooth is intended only for Android Phone <-> ESP32 node communication.
- Bluetooth must not be added as a node-to-node MANET transport.
- LoRa remains the MANET backbone between ESP32 nodes.
- The phone is only a client/controller connected to one local node.
- STEP047 implementation/build validation updated Android UI diagnostics and ESP32 Bluetooth status output to reflect this boundary.
- STEP047 physical validation PASS: Bluetooth phone-to-node access verified; `NODE_LIST` verified; `PhoneA -> nodeA2` verified; `nodeA2 -> PhoneA` verified; gateway disappearance detection verified; gateway-loss messaging verified.

STEP046B-A Bridge ACK reliability policy:
- Bridge ACK is the user-facing confirmation that a phone-originated message sent through the local ESP32 bridge was acknowledged by the final destination node.
- Destination node ACK is the authoritative user-facing ACK source.
- Forwarding gateway ACK and hop-by-hop ACK are diagnostics only; they prove relay progress but not final delivery.
- ACK packets must correlate with the original message using `ackFor == original packetId`.
- Android should wait up to 12 seconds for a matching `DELIVERY` ACK, show pending/unknown states without false failure, and never show raw JSON as the primary Bridge ACK display.
- STEP046B implementation must preserve LoRa as the MANET backbone and Bluetooth as phone-to-node access only.

STEP046B Bridge ACK implementation:
- Android phone-to-node access now creates a pending ACK state for live Chat sends, correlates ACKs by `ackFor`, waits up to 12 seconds for final destination `DELIVERY` ACKs, and displays `Pending`, `Delivered`, or `Unknown` instead of raw ACK JSON.
- ESP32 destination nodes now generate correlated `ACK` packets after local message delivery. ACK payload metadata includes `ackType=DELIVERY`, `ackFor`, `ackStatus=DELIVERED`, `originNode`, `finalDestinationNode`, `ackSource`, and route data.
- Local bridge forwarding ACKs remain `FORWARD` diagnostics only.
- STEP046B does not change route decisions, route discovery, Bluetooth topology, Raspberry Pi gateway service, or the compact `BT1` LoRa relay format.
- Local build validation is PASS for Android and `gatewayA_lora`, `gatewayB_lora`, `nodeA1_lora`, and `nodeA2_lora`; physical ACK validation is pending.

STEP046C Bridge ACK UI sanitization:
- Hardware validation confirmed STEP046B delivery behavior but found one UI acceptance failure: failed Bridge ACK cards could still display raw ACK JSON.
- Android now sanitizes user-facing Bridge ACK output at the message-card/status-row boundary.
- User-facing Bridge ACK states are limited to `Delivered`, `Pending`, `Unknown`, and `Failed`.
- Raw ACK JSON remains available only to parsing/log/debug flows and is not shown in end-user Bridge ACK sections.
- No firmware, routing, route discovery, Bluetooth topology, Raspberry Pi gateway service, or compact `BT1` protocol changes.
- Physical validation PASS: attached screenshots confirm failed Bridge ACK card display is user-friendly and no raw ACK JSON is shown to the end user.

STEP042A discovery export fix:
- Root cause: compact LoRa relay HELLO packets such as `HELLO-nodeA1` and `HELLO-nodeA2` were parsed as `MESSAGE` unconditionally, then routed, relayed, or duplicate-dropped before node table insertion.
- Fix in `esp32-node-platformio/src/main.cpp`: infer compact relay HELLO packets from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId` payload, learn HELLO packets before route/duplicate handling, store `hopCount` in node table entries, and log `[NEIGHBOR_ADD]`, `[NEIGHBOR_UPDATE]`, and `[NODE_LIST_EXPORT]`.
- NODE_LIST generation now removes expired nodes before export and logs each exported row.
- Build passed for `nodeA1_lora`, `nodeA2_lora`, `gatewayA_lora`, and `gatewayB_lora`.
- Physical A-side discovery validation is PASS for `nodeA1`, `nodeA2`, and `gatewayA`.

STEP042A/STEP042B physical validation checkpoint:
- Flashed/running firmware: `gatewayA_lora` restored as `gatewayA`, `gatewayB_lora` restored as `gatewayB`, `nodeA1_lora` as `nodeA1`, and `nodeA2_lora` as `nodeA2`.
- Android discovery shows Gateway A group with `nodeA1 ONLINE`, `gatewayA ONLINE`, and `nodeA2 ONLINE`, plus Gateway B group with `gatewayB ONLINE` after STEP045A.
- STEP042B bidirectional 2-node Android LoRa messaging is PASS for `nodeA1 -> nodeA2` and `nodeA2 -> nodeA1`.
- Phone A sent `hello from A`; Phone B received it via LoRa on path `nodeA1 -> nodeA2`.
- Phone B sent `hello from b`; Phone A received it via LoRa on path `nodeA2 -> nodeA1`.
- This is a 2-node validation only; `nodeA3` has not been flashed or deployed.
- Temporary Android validation mapping: `peerNodeForConnectedEsp32()` was changed from `"nodeA2" -> "nodeA3"` to `"nodeA2" -> "nodeA1"` because `nodeA3` is not deployed.

STEP043 LoRa HELLO packet format:
- RF receive is confirmed: Gateway B receives Gateway A compact LoRa HELLO payloads.
- Protocol decision: keep compact `BT1` LoRa relay packets as the final current firmware protocol because nodeA1/nodeA2 routing already works with compact packets.
- Initial JSON-oriented STEP043 fix failed physical validation; the actual gateway path still transmitted `BT1|HELLO-...|gatewayA|BROADCAST|{"gatewayId":"A"}|...`.
- Corrected receiver-side fix: compact `BT1` RX now logs `[LORA_RELAY_PARSE] valid`, infers HELLO from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId`, learns the peer through `learnNodeFromHelloPacket()` before duplicate/drop routing, and returns immediately for HELLO.
- Expected discovery evidence: `[LORA_RX] payload=BT1|HELLO-...`, `[LORA_RELAY_PARSE] valid`, `[DISCOVERY] HELLO from gatewayA gateway=A`, and `[NEIGHBOR_ADD]` / `[NEIGHBOR_UPDATE] node=gatewayA` on Gateway B; equivalent logs for `gatewayB` on Gateway A.
- Local builds pass for `gatewayA_lora`, `gatewayB_lora`, `nodeA1_lora`, and `nodeA2_lora`.
- Physical validation PASS: Gateway A and Gateway B discover each other over compact `BT1` HELLO packets, and `TEST_FINAL_001` from `gatewayB` to `gatewayA` was received.

STEP044 strict compact BT1 validation:
- Compact `BT1` remains the current LoRa firmware protocol.
- Corrupted compact packets are rejected before neighbor learning or routing.
- Rejection log format: `[LORA_DROP_CORRUPT] reason=<reason> payload=<short payload>`.
- First physical validation failed after commit `93dd506`: malformed HELLO packets could still create `node=BROADCAST gateway=UNKNOWN` or misspelled neighbors such as `gatlwayB`.
- Corrective validation rules reject bad prefix, wrong field count, blank required fields, invalid source/destination characters, HELLO source `BROADCAST`, HELLO source outside known formats (`nodeA1`-`nodeA3`, `nodeB1`-`nodeB3`, `gatewayA`, `gatewayB`), HELLO packet IDs that do not exactly match `HELLO-<sourceNode>-<numeric timestamp>`, malformed gatewayId JSON, invalid gateway IDs, non-numeric `ttl`/`hopCount`, and `ttl`/`hopCount` outside `0..DEFAULT_TTL`.
- HELLO learning repeats the semantic guards before neighbor-table insertion, so malformed HELLO packets cannot create `UNKNOWN`, `BROADCAST`, or misspelled neighbors.
- Android app and Raspberry Pi gateway service are unchanged.
- Corrective local builds pass for `gatewayA_lora`, `gatewayB_lora`, `nodeA1_lora`, and `nodeA2_lora`.
- Physical validation PASS / COMPLETE after corrective firmware commit `9dba0ef` was flashed and tested under RF stress with four active LoRa nodes: `gatewayA`, `gatewayB`, `nodeA1`, and `nodeA2`.
- Evidence: corrupt packets logged `[LORA_DROP_CORRUPT] reason=bad_prefix`, `[LORA_DROP_CORRUPT] reason=bad_field_count`, and `[LORA_DROP_CORRUPT] reason=malformed_gatewayId_json`; valid HELLO packets still logged `[LORA_RELAY_PARSE] valid` for `HELLO-gatewayB...`, `HELLO-nodeA1...`, and `HELLO-nodeA2...`.
- Result: node table remained stable at 4 nodes (`gatewayA`, `gatewayB`, `nodeA1`, `nodeA2`) with no `gateway=UNKNOWN`, no `node=BROADCAST`, and no malformed names such as `gatlwayB`.

STEP045A mesh-wide presence propagation:
- Valid compact `BT1` HELLO packets are forwarded after strict STEP044 validation and local learning, so gateway presence learned by one node can propagate to Android-connected node tables.
- Forwarding preserves compact `BT1` LoRa protocol and updates bounded routing metadata: `ttl`, `hopCount`, `previousHop`, and `hopPath`.
- A bounded HELLO relay cache prevents infinite HELLO loops; forwarding decisions log `[HELLO_RELAY]`.
- Physical Android validation PASS after flashing `gatewayA`, `gatewayB`, `nodeA1`, and `nodeA2`: Gateway A group shows `nodeA1 ONLINE`, `gatewayA ONLINE`, and `nodeA2 ONLINE`; Gateway B group shows `gatewayB ONLINE`.

STEP045B Android real-network cleanup:
- Android UI and Chat prioritize live discovered node IDs from NODE_LIST.
- Chat destination selection is driven by `discoveredNodes`; the local connected node is excluded.
- Route tab shows live LoRa route information when real discovered nodes exist.
- Physical Android hardware validation PASS.

STEP046A controlled multi-hop lab mode:
- ESP32 firmware includes compile-time flag `TEST_FORCE_GATEWAY_ROUTE`, default `0`.
- Production default is restored to `#define TEST_FORCE_GATEWAY_ROUTE 0`.
- When manually enabled for lab builds, nodeA1/nodeA2 messages are forced through gateway paths so indoor short-range tests can validate multi-hop behavior even when all SX1278 radios can hear each other directly.
- Physical validation PASS with manual test firmware using `TEST_FORCE_GATEWAY_ROUTE=1`.
- A -> B forced route delivered through `nodeA1 -> gatewayA -> gatewayB -> nodeA2`.
- B -> A forced route delivered through `nodeA2 -> gatewayB -> gatewayA -> nodeA1`.
- GatewayA-off test: node count became 3, `gatewayA` disappeared from NODE_LIST, and `nodeA1`/`nodeA2` still delivered messages directly. This proves node discovery expiry and direct fallback/survivability, but not full alternate gateway reroute.
- Production-mode build validation PASS for `gatewayA_lora`, `gatewayB_lora`, `nodeA1_lora`, and `nodeA2_lora`.

Current topology limitations:
- `nodeA3` has not been flashed or deployed.
- GatewayB repeated reset/garbage serial output needs investigation if it recurs.
- Full alternate gateway reroute is not yet implemented or validated.

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
- Gateway ACK tracking.
- Heartbeat monitoring.
- Peer online detection.
- Automatic reconnect.
- Gateway relay mode.
- LoRa backup backhaul mode.

Gateway route state machine (STEP048B) implemented in `rpi-gateway/route_state_machine.py`. 4-state deterministic FSM. 187/187 tests pass.

Firmware status:
- STEP038 ESP32 firmware is not final.
- It is now considered Stable STEP038 baseline firmware with STEP041 serial bridge additions and STEP042C delivery tracking.
- Existing routing logic remains valid: TTL, hopCount, duplicate suppression, Bluetooth bridge, LoRa forwarding, gateway Bluetooth-disable behavior, STEP042A HELLO/node table discovery export fix, STEP042C delivery tracking, STEP044 strict compact packet validation, STEP045A presence propagation, and STEP046A production-safe lab overlay default.
- ESP32 firmware supports both NODE mode and GATEWAY mode via `[GW_JSON]` prefix parsing in `processSerialLine()`.
- Gateway queue engine (STEP042D-B) lives in `rpi-gateway/store_forward_queue.py` as a standalone Python module; it does not touch ESP32 firmware or Android.
- STEP042C delivery tracking notes:
  - `DeliveryEntry` table tracks up to 16 in-flight messages.
  - States: `MESSAGE`, `DELIVERED`, `SEEN`, `FAILED`, `UNKNOWN`.
  - ACK correlation uses exact `ackFor == original packetId`; no substring fallback.
  - `UNKNOWN` timeout (60s) only affects `MESSAGE` state.
  - Serial commands: `DELIVERY_STATUS`, `DELIVERY_SEEN <packetId>`.

Simulation-first rule:
- Prefer simulation and abstraction layers before hardware-specific behavior.
- Do not introduce new real ESP32, Raspberry Pi, or Android logic unless the workbook step explicitly allows it.

Current milestone:
- STEP048B Gateway Route State Machine - IMPLEMENTED / PASS.
- STEP048A Gateway Degradation Awareness - IMPLEMENTED / PASS.
- Next: STEP048C Store-and-Forward Gateway Integration.
- STEP046C Bridge ACK UI Sanitization - COMPLETE / PASS.
- STEP046B-A Bridge ACK Requirements Definition - COMPLETE / Planning Only.
- STEP047 Bluetooth Transport Layer - PASS / COMPLETE after physical validation.
- STEP046A Controlled Multi-Hop Lab Mode - PASS / COMPLETE after physical forced-route validation; production default restored.
- STEP045B Android Real-Network Cleanup - PASS / COMPLETE after physical Android validation.
- STEP045A Mesh-Wide Presence Propagation - PASS / COMPLETE after physical Android validation.
- STEP044 Strict Compact BT1 LoRa Packet Validation - PASS / COMPLETE after RF-stress physical validation.
- STEP043 Fix LoRa HELLO Packet Format - PASS for compact gatewayA/gatewayB discovery and `TEST_FINAL_001`.
- STEP042A Node Discovery and Reachability - PASS for A-side `nodeA1`/`nodeA2`/`gatewayA` discovery.
- STEP042B Destination Messaging - PASS for bidirectional 2-node Android LoRa messaging on `nodeA1 <-> nodeA2`.
- Next validation activity: STEP048B complete. Continue with STEP048C.
- Store-and-Forward requirements are defined in `docs/codex-task-logs/STEP042D_A_STORE_AND_FORWARD_REQUIREMENTS_DEFINITION.md`. Preserve all existing validated STEP042/STEP044/STEP045/STEP046/STEP047 behaviors.
