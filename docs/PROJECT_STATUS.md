# Project Status

Last updated: 2026-06-07

Overall state: STEP047 Bluetooth Transport Layer is physically validated PASS / COMPLETE with Bluetooth scoped to Android phone-to-local ESP32 access only. STEP046B delivery ACK hardware behavior passed, but UI acceptance initially failed because a failed card displayed raw Bridge ACK JSON. STEP046C Android UI sanitization is now COMPLETE / PASS based on attached physical validation screenshots. Production firmware mode remains restored to `TEST_FORCE_GATEWAY_ROUTE=0`.

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

Workbook current milestone: STEP046C - Bridge ACK UI Sanitization COMPLETE / PASS.

Latest physically completed workbook step: STEP047 - Bluetooth Transport Layer.

Implementation note: ESP32-to-Raspberry Pi USB serial bridge is validated end-to-end. Gateway Bluetooth-disable cleanup is validated and must not be undone. Android live discovered-node destination selection is validated; `nodeA3` has not been deployed.

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
- STEP043 PASS: gatewayA/gatewayB discover each other over compact `BT1` HELLO packets; `TEST_FINAL_001` from `gatewayB` to `gatewayA` was received.
- STEP044 PASS / COMPLETE: corrective strict compact packet validation was physically validated under RF stress with `gatewayA`, `gatewayB`, `nodeA1`, and `nodeA2`.
- STEP045A PASS / COMPLETE: mesh-wide HELLO presence propagation fixed Android discovery for `gatewayB` while preserving compact `BT1` and STEP044 corrupt-packet rejection.
- STEP045B PASS / COMPLETE: Android real-network cleanup physically validated. UI and Chat now prioritize live discovered node IDs, and Chat send uses selected live destination instead of the temporary hardcoded peer mapping.
- STEP046A PASS / COMPLETE: controlled multi-hop lab mode physically validated with manual firmware built using `TEST_FORCE_GATEWAY_ROUTE=1`, then production default restored to `TEST_FORCE_GATEWAY_ROUTE=0`.
- STEP047 PASS / COMPLETE: Bluetooth phone-to-node access verified; `NODE_LIST` verified; `PhoneA -> nodeA2` verified; `nodeA2 -> PhoneA` verified; gateway disappearance detection verified; gateway-loss messaging verified.
- STEP046B-A COMPLETE: Bridge ACK requirements defined without source changes. User-facing Bridge ACK means final destination-node delivery ACK; forwarding gateway and hop ACKs are diagnostics only.
- STEP046B IMPLEMENTED / LOCAL BUILD PASS: Android now correlates Bridge ACKs by `ackFor`, waits 12 seconds for final destination `DELIVERY` ACKs, displays `Pending`, `Delivered`, or `Unknown`, and keeps forwarding/hop ACKs diagnostic-only. ESP32 destination nodes now generate correlated delivery ACKs after local message delivery. Physical validation remains pending.
- STEP046B HARDWARE VALIDATION PARTIAL: delivery behavior PASS for `nodeA1 -> nodeA2`, `nodeA2 -> nodeA1`, timeout detection, failed delivery display, and no incorrect Delivered state. UI acceptance FAIL because failed card showed raw Bridge ACK JSON.
- STEP046C COMPLETE / PASS: Android UI sanitizes Bridge ACK card/status/payload display to show only `Delivered`, `Pending`, `Unknown`, or `Failed` states to end users. Attached physical validation screenshots confirm no raw Bridge ACK JSON is shown on the failed card.

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
- PASS for nodeA1/nodeA2/gatewayA/gatewayB discovery propagation.
- Flashed/running: `gatewayA_lora` as `gatewayA`, `gatewayB_lora` as `gatewayB`, `nodeA1_lora` as `nodeA1`, and `nodeA2_lora` as `nodeA2`.
- Android Nodes screen shows Gateway A group with `nodeA1 ONLINE`, `gatewayA ONLINE`, and `nodeA2 ONLINE`, plus Gateway B group with `gatewayB ONLINE`.
- This is not full 6-node topology validation: `nodeA3` has not been flashed or validated.

STEP042B physical validation status:
- PASS for bidirectional 2-node Android-to-Android LoRa bridge only.
- Validated paths: `nodeA1 -> nodeA2` and `nodeA2 -> nodeA1`.
- Phone A sent `hello from A`; Phone B received it via LoRa on path `nodeA1 -> nodeA2`.
- Phone B sent `hello from b`; Phone A received it via LoRa on path `nodeA2 -> nodeA1`.
- Temporary Android validation fix: `peerNodeForConnectedEsp32()` in `MainActivity.kt` was changed from `"nodeA2" -> "nodeA3"` to `"nodeA2" -> "nodeA1"` because `nodeA3` is not deployed.

Current blockers / open issues:
- STEP044 corrupt compact packet physical validation PASS after corrective firmware commit `9dba0ef`.
- `nodeA3` has not been flashed or validated.
- GatewayB repeated reset/garbage serial output needs investigation.

STEP043 - LoRa HELLO Packet Format:
- Initial validation finding: Gateway A transmitted `BT1|HELLO-gatewayA-...|gatewayA|BROADCAST|{"gatewayId":"A"}|...` and Gateway B received RF payload, proving LoRa communication is working.
- Initial JSON-oriented STEP043 fix failed physical validation because the actual flashed gateway path still emitted compact HELLO frames.
- Protocol decision: keep compact `BT1` LoRa relay packets because nodeA1/nodeA2 routing already works with compact packets.
- Corrected fix in `esp32-node-platformio/src/main.cpp`: `sendHelloBroadcast()` and all LoRa relay TX remain compact `BT1`; compact RX now logs `[LORA_RELAY_PARSE] valid`; `parseLoRaRelayPacket()` infers HELLO from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId`; `processIncomingLoRaRelayPacket()` learns HELLO before duplicate/drop routing and returns immediately for HELLO; discovery logs include `[DISCOVERY] HELLO from <nodeId> gateway=<gatewayId>`.
- Build validation: `pio run -e gatewayA_lora -e gatewayB_lora` PASS; `pio run -e nodeA1_lora -e nodeA2_lora` PASS.
- Physical validation PASS: Gateway A and Gateway B discover each other, and `TEST_FINAL_001` from `gatewayB` to `gatewayA` was received.

STEP044 - Strict Compact BT1 LoRa Packet Validation:
- Problem: corrupted LoRa packets can sometimes be parsed as valid and create bad neighbors such as `gateway=UNKNOWN` or corrupted node names.
- Constraint: keep compact `BT1` LoRa relay packet format; do not convert LoRa packets to JSON; do not change Android or Raspberry Pi gateway service.
- First physical validation failed after commit `93dd506`: corrupt `BT1|HELLO-nodeA1-40000zno4eA1|...` was accepted and created `node=BROADCAST gateway=UNKNOWN`; corrupt source `gatlwayB` was accepted and added as `gateway=B`.
- Corrective fix in `esp32-node-platformio/src/main.cpp`: strict validation now runs before compact packet learning/routing and HELLO learning now requires known source formats, non-`BROADCAST` source, exact `HELLO-<sourceNode>-<numeric timestamp>` packet ID, and strict gateway payload JSON.
- Rejects: non-`BT1|` prefix, wrong field count, blank `packetId`, blank `sourceNode`, blank `destinationNode`, invalid source/destination characters, HELLO source `BROADCAST`, unknown HELLO source, invalid HELLO packet ID, malformed gatewayId JSON, invalid gateway ID, non-numeric `ttl`, non-numeric `hopCount`, `ttl` outside `0..DEFAULT_TTL`, and `hopCount` outside `0..DEFAULT_TTL`.
- `learnNodeFromHelloPacket()` repeats the HELLO semantic guards before `upsertNodeEntry()`, so invalid HELLO packets cannot create `UNKNOWN`, `BROADCAST`, or misspelled neighbors.
- Build validation after corrective fix: `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora` PASS.
- Physical validation PASS: corrective firmware commit `9dba0ef` was flashed and tested under RF stress with four active LoRa nodes: `gatewayA`, `gatewayB`, `nodeA1`, and `nodeA2`.
- Evidence: corrupt packets logged `[LORA_DROP_CORRUPT] reason=bad_prefix`, `[LORA_DROP_CORRUPT] reason=bad_field_count`, and `[LORA_DROP_CORRUPT] reason=malformed_gatewayId_json`; valid packets still parsed with `[LORA_RELAY_PARSE] valid packet_id=HELLO-gatewayB...`, `HELLO-nodeA1...`, and `HELLO-nodeA2...`.
- Result: no ghost neighbors were observed; no `gateway=UNKNOWN`, no `node=BROADCAST`, no malformed names like `gatlwayB`; node table remained stable at 4 nodes: `gatewayA`, `gatewayB`, `nodeA1`, `nodeA2`.

STEP045A - Mesh-Wide Presence Propagation:
- Root cause: valid compact HELLO discovery was learned locally and then `processIncomingLoRaRelayPacket()` returned immediately, so learned gateway presence was not propagated to Android-connected `nodeA1`/`nodeA2` node tables.
- Firmware fix in `esp32-node-platformio/src/main.cpp`: forward only validated HELLO presence packets after STEP044 parsing and local learning; preserve compact `BT1`; decrement `ttl`; increment `hopCount`; update `previousHop`; append `hopPath`; use a bounded HELLO relay cache; add `[HELLO_RELAY]` logs.
- Build validation PASS: `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora`.
- Physical Android validation PASS after flashing `gatewayA`, `gatewayB`, `nodeA1`, and `nodeA2`: Android Nodes shows `nodeA1 ONLINE`, `gatewayA ONLINE`, `nodeA2 ONLINE`, and `gatewayB ONLINE`.

STEP045B - Android Real-Network Cleanup:
- Android-only change in `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`.
- Replaced simulation-era current-state labels with real discovered node IDs when `discoveredNodes` is available.
- Added selected live destination state sourced from `discoveredNodes`; local connected ESP32 node is excluded from selectable destinations.
- Chat send now uses the selected discovered node instead of `peerNodeForConnectedEsp32()` hardcoded mapping.
- Safe fallback destination remains only when `discoveredNodes` is empty and is clearly labeled as fallback.
- Route tab now shows a live LoRa route summary when real discovered nodes exist instead of misleading simulated `No route`.
- Local build PASS: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`.
- Physical Android hardware validation PASS.

STEP046A - Controlled Multi-Hop Lab Mode:
- ESP32-only change in `esp32-node-platformio/src/main.cpp`.
- Added compile-time flag `TEST_FORCE_GATEWAY_ROUTE`, default `0`, so production behavior remains unchanged unless lab mode is explicitly enabled at build time.
- When enabled, `nodeA1 -> nodeA2` messages are forced through `nodeA1 -> gatewayA -> gatewayB -> nodeA2`; reverse messages are forced through `nodeA2 -> gatewayB -> gatewayA -> nodeA1`.
- Direct overheard forced-route packets are ignored before duplicate-cache insertion, preserving delivery of the valid gateway-relayed copy.
- Added `[FORCED_ROUTE]` and `[FORWARD]` logs plus boot/status visibility for `test_force_gateway_route`.
- Physical validation PASS: A -> B delivered through `nodeA1 -> gatewayA -> gatewayB -> nodeA2`.
- Physical validation PASS: B -> A delivered through `nodeA2 -> gatewayB -> gatewayA -> nodeA1`.
- Additional gatewayA-off test: node count became 3, `gatewayA` disappeared from NODE_LIST, and `nodeA1`/`nodeA2` still delivered messages directly. This proves node discovery expiry and direct fallback/survivability, but not full alternate gateway reroute.
- Production default restored to `#define TEST_FORCE_GATEWAY_ROUTE 0`.
- Production-mode build validation PASS: `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora`.
- `git diff --check` PASS.

STEP047 - Bluetooth Transport Layer:
- Architecture clarification: Bluetooth is only for Android Phone <-> ESP32 node communication.
- Bluetooth must not be added as a node-to-node MANET transport.
- LoRa remains the MANET backbone; the phone is only a client/controller connected to one local node.
- Android updates in `MainActivity.kt`: simulation-era Bluetooth transport wording now labels Bluetooth SPP as the phone-to-node access layer; readiness/checklist/route notes no longer imply Bluetooth carries MANET node-to-node traffic.
- ESP32 updates in `src/main.cpp`: `BT_STATUS`, invalid protocol errors, startup logs, and status payload metadata identify Bluetooth as `PHONE_NODE_ACCESS`.
- Build validation PASS: Android `:app:assembleDebug`; ESP32 `gatewayA_lora`, `gatewayB_lora`, `nodeA1_lora`, and `nodeA2_lora`.
- Physical validation PASS: Bluetooth phone-to-node access verified; `NODE_LIST` verified; `PhoneA -> nodeA2` verified; `nodeA2 -> PhoneA` verified; gateway disappearance detection verified; gateway-loss messaging verified.

STEP046B-A - Bridge ACK Requirements Definition:
- Problem: Bridge ACK display/reliability is inconsistent (`Bridge ACK: No response` or raw JSON) while message delivery can still succeed.
- Bridge ACK definition: user-facing confirmation that a phone-originated message sent through the local ESP32 bridge was acknowledged by the final destination node.
- ACK source decision: destination node ACK is authoritative; forwarding gateway ACK and hop-by-hop ACK are diagnostics only.
- Safest design: two-tier ACK model. `DELIVERY` ACK drives the Chat/Bridge ACK display; `FORWARD` and `HOP` ACKs update diagnostics only.
- ACK packet requirement: include `packetType=ACK`, `packetId=ACK-<ackFor>-<ackSource>-<timestamp>`, `ackFor=<originalMessagePacketId>`, `ackType`, `ackStatus`, `originNode`, `finalDestinationNode`, `ackSource`, and route/hop metadata.
- Timeout/retry rule: Android waits up to 12 seconds for a matching destination `DELIVERY` ACK, retries ACK read/parsing during that window, and does not automatically retransmit the original MESSAGE in STEP046B unless explicitly approved later.
- UI rule: show `Pending`, `Delivered to <nodeId>`, `Delivery ACK pending`, `Unknown`, or explicit correlated failure. Do not show raw JSON as the primary Bridge ACK and do not treat timeout as proof of delivery failure.

STEP046B - Bridge ACK Reliability Improvement:
- Android implementation in `MainActivity.kt` adds `Pending` and `Unknown` states, structured ACK parsing, pending ACK correlation by original packet ID, 12-second delivery ACK wait, and user-friendly Bridge ACK display.
- ESP32 implementation in `src/main.cpp` emits final destination `DELIVERY` ACK packets with `ackFor`, `ackStatus=DELIVERED`, `ackSource`, `originNode`, `finalDestinationNode`, and route metadata.
- Local forwarding ACKs remain `FORWARD` diagnostics and no longer mark Chat messages delivered.
- Timeout without a final destination ACK becomes `Unknown`, not `Failed`.
- No routing decisions, route discovery logic, Bluetooth architecture, Raspberry Pi gateway service, or Android destination-selection architecture were changed.
- Build validation PASS: Android `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`.
- Build validation PASS: ESP32 `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora`.
- Hardware validation result: delivery behavior PASS but UI acceptance FAIL because the failed card still displayed raw Bridge ACK JSON.

STEP046C - Bridge ACK UI Sanitization:
- Android-only fix in `MainActivity.kt`.
- User-facing message cards/status rows/protocol payload text now sanitize Bridge ACK content to `Bridge ACK: Delivered`, `Bridge ACK: Pending`, `Bridge ACK: Unknown`, or `Bridge ACK: Failed`.
- Raw ACK JSON payloads remain available for parsing/log/debug only and are not rendered to end users in Bridge ACK sections.
- Android build validation PASS: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`.
- No firmware, routing, route discovery, Bluetooth architecture, Raspberry Pi gateway service, or compact `BT1` protocol changes.
- Physical validation PASS: attached screenshots show a user-friendly failed Bridge ACK state and no raw ACK JSON displayed to the end user.

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

- STEP046C is complete. Continue only from the next workbook-approved task. Preserve routing core and do not start STEP042C Delivery Tracking or STEP042D Store-and-Forward unless explicitly routed by the workbook/user.

Troubleshooting notes resolved before STEP 035 pass:
- stale Bluetooth socket
- `localBridgeNode` vs `destinationNode` separation
- LoRa-to-Bluetooth forwarding
- Android blocking read for full JSON line
- Kotlin `SimMetrics` type mismatch
