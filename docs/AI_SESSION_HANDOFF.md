# AI Session Handoff

Last updated: 2026-06-09

Current milestone: STEP042 COMPLETE / PASS. All sub-tasks (A/B/C/D) closed.

Latest physically completed milestone: STEP047 - Bluetooth Transport Layer. STEP042 is fully complete and closed. Next milestone: continue from workbook.

Unfinished task: none. STEP042 is fully closed. All sub-tasks complete. Closure summary at `docs/codex-task-logs/STEP042_COMPLETE_SUMMARY.md`.

Pending validations:
- STEP042D-A COMPLETE / Planning Only: Store-and-Forward requirements defined.
- STEP042D-B IMPLEMENTED / PASS: Queue engine.
- STEP042D-C IMPLEMENTED / PASS: Replay engine.
- STEP042D-D COMPLETE / PASS: Validation. 8 scenarios validated (MESSAGE/DELIVERY ACK/SEEN ACK queued-replayed, TTL expiration diagnostic-only, duplicate rejection, link flap, mark_acked skip, STEP042C state machine). 23 validation tests + 82 existing = 105/105 total. Zero regressions.
- STEP042C COMPLETE / PASS: ACK correlation hardened using exact `ackFor` matching; substring fallback removed; `DELIVERY_SEEN` uses exact `packetId`; `MESSAGE -> DELIVERED -> SEEN` validated; `UNKNOWN` timeout only affects `MESSAGE` state; builds passed for `nodeA1` and `nodeA1_lora`.
- STEP042A A-side discovery is physically validated: Android shows `nodeA1 ONLINE`, `nodeA2 ONLINE`, and `gatewayA ONLINE`.
- STEP042B 2-node messaging is physically validated: Phone A `hello from A` reached Phone B via `nodeA1 -> nodeA2`; Phone B `hello from b` reached Phone A via `nodeA2 -> nodeA1`.
- Existing 2-node Chat LoRa path and gateway Bluetooth-disable behavior must remain non-regressed.
- `gatewayB` now appears in Android discovery after STEP045A mesh-wide HELLO propagation.
- `nodeA3` has not been flashed or validated.
- ESP32 gateway node to Raspberry Pi USB serial bridge remains VALIDATED with STEP041.
- LoRa-to-LoRa gateway backup backhaul is not yet validated.
- STEP043 is physically validated: Gateway A and Gateway B discover each other and `TEST_FINAL_001` from `gatewayB` to `gatewayA` was received.
- STEP044 strict corrupt-packet rejection is physically validated PASS after corrective firmware commit `9dba0ef`.
- STEP045A mesh-wide presence propagation is physically validated PASS: Android Nodes now shows `nodeA1 ONLINE`, `gatewayA ONLINE`, `nodeA2 ONLINE`, and `gatewayB ONLINE`.
- STEP045B Android Real-Network Cleanup is physically validated PASS: Android UI and Chat now use live discovered node IDs and selected live destination state.
- STEP046A Controlled Multi-Hop Lab Mode is physically validated PASS / COMPLETE. Production source default is restored to `TEST_FORCE_GATEWAY_ROUTE=0`.
- STEP047 Bluetooth Transport Layer is physically validated PASS / COMPLETE. Bluetooth is scoped to Android phone-to-local ESP32 access only; LoRa remains the MANET backbone.
- STEP046B-A Bridge ACK Requirements Definition is complete.
- STEP046B implementation is complete locally and build-validated.
- STEP046B hardware validation confirmed delivery behavior but exposed a UI acceptance failure: failed card displayed raw Bridge ACK JSON.
- STEP046C Android UI sanitization is COMPLETE / PASS based on attached physical validation screenshots.

Clarified requirements recorded 2026-06-02 (no source changes yet):
- Default node IDs: nodeA1, nodeA2, nodeA3, nodeB1, nodeB2, nodeB3.
- Android shows only online/reachable nodes; nodes remain visible even without Android phones attached.
- One Android phone maximum per node.
- Node list grouped by mesh/gateway in Android UI.
- Presence protocol: HELLO interval = 10 sec; offline timeout = 30 sec.
- STEP042 split into: STEP042A Node Discovery and Reachability; STEP042B Destination Messaging; STEP042C Delivery Tracking; STEP042D Store-and-Forward.
- Delivery tracking statuses: MESSAGE, DELIVERED, SEEN.

Blockers:
- STEP044 first physical validation failed once: corrupt `HELLO-nodeA1-40000zno4eA1` created `node=BROADCAST gateway=UNKNOWN`, and corrupt source `gatlwayB` created `node=gatlwayB gateway=B`. Corrective firmware commit `9dba0ef` fixed this and passed physical validation.
- GatewayB repeated reset/garbage serial output needs investigation.
- STEP046B source implementation addresses the previous ACK display/reliability issue by correlating delivery ACKs with `ackFor`, replacing raw JSON with user-friendly states, and treating forwarding ACKs as diagnostics only. Physical validation still needs to confirm timing on real hardware.

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
- STEP045A follow-up result: `gatewayB` is now visible in Android discovery after valid HELLO propagation from gateway-side discovery into Android-connected node tables.

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
- Physical acceptance target: Gateway B logs `[LORA_RELAY_PARSE] valid`, `[DISCOVERY] HELLO from gatewayA gateway=A`, and `[NEIGHBOR_ADD] node=gatewayA` or `[NEIGHBOR_UPDATE] node=gatewayA`; Gateway A logs the equivalent for `gatewayB`; `STATUS` / `NEIGHBORS` lists both gateways.

STEP043 physical result:
- PASS: Gateway A and Gateway B discover each other over compact `BT1` HELLO packets.
- PASS: `TEST_FINAL_001` from `gatewayB` to `gatewayA` was received.

STEP044 strict compact packet validation:
- Fix in `esp32-node-platformio/src/main.cpp`: compact `BT1` packets are validated before node learning or routing.
- Added `[LORA_DROP_CORRUPT] reason=<reason> payload=<short payload>`.
- Initial physical failure: corrupt packet `BT1|HELLO-nodeA1-40000zno4eA1|...` was accepted and created `node=BROADCAST gateway=UNKNOWN`; corrupt source `gatlwayB` was accepted and added as `gateway=B`.
- Corrective fix: HELLO source must be a known firmware node/gateway format (`nodeA1`-`nodeA3`, `nodeB1`-`nodeB3`, `gatewayA`, `gatewayB`), source cannot be `BROADCAST`, packet ID must exactly match `HELLO-<sourceNode>-<numeric timestamp>`, and gateway payload must be strict `{"gatewayId":"A"}` or `{"gatewayId":"B"}` JSON.
- Rejects: bad prefix, wrong field count, blank `packetId`, blank `sourceNode`, blank `destinationNode`, invalid node ID characters, HELLO source `BROADCAST`, unknown HELLO source, invalid HELLO packet ID, malformed gatewayId JSON, invalid gateway ID, non-numeric `ttl`, non-numeric `hopCount`, `ttl` outside `0..DEFAULT_TTL`, and `hopCount` outside `0..DEFAULT_TTL`.
- `learnNodeFromHelloPacket()` now repeats the HELLO semantic guards before `upsertNodeEntry()`, preventing `UNKNOWN`, `BROADCAST`, or misspelled neighbors from malformed HELLO payloads.
- Local build validation: `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora` PASS.
- Physical validation PASS: corrective firmware commit `9dba0ef` was flashed and tested under RF stress with four active LoRa nodes: `gatewayA`, `gatewayB`, `nodeA1`, and `nodeA2`.
- Physical evidence: corrupt packets logged `[LORA_DROP_CORRUPT] reason=bad_prefix`, `[LORA_DROP_CORRUPT] reason=bad_field_count`, and `[LORA_DROP_CORRUPT] reason=malformed_gatewayId_json`; valid HELLO packets still logged `[LORA_RELAY_PARSE] valid packet_id=HELLO-gatewayB...`, `HELLO-nodeA1...`, and `HELLO-nodeA2...`; node table stayed stable at 4 nodes with no `gateway=UNKNOWN`, no `node=BROADCAST`, and no malformed `gatlwayB`.

STEP045A mesh-wide presence propagation:
- Root cause: valid compact HELLO discovery was learned locally and then `processIncomingLoRaRelayPacket()` returned immediately, preventing learned gateway presence from propagating to Android-connected node tables.
- Fix in `esp32-node-platformio/src/main.cpp`: validated HELLO packets are forwarded after STEP044 parsing and local learning, preserving compact `BT1`, bounded `ttl`/`hopCount`, `previousHop`, `hopPath`, and a HELLO relay cache to avoid loops.
- Added `[HELLO_RELAY]` logging for forwarded, duplicate, and TTL-exhausted HELLO propagation decisions.
- Local build validation PASS: `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora`.
- Physical Android validation PASS after flashing `gatewayA`, `gatewayB`, `nodeA1`, and `nodeA2`: Gateway A group shows `nodeA1 ONLINE`, `gatewayA ONLINE`, and `nodeA2 ONLINE`; Gateway B group shows `gatewayB ONLINE`.

STEP045B Android Real-Network Cleanup:
- Android-only change in `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`.
- Added selected live destination state sourced from `discoveredNodes`; local connected node is excluded.
- Chat send uses the selected discovered node instead of the temporary hardcoded `peerNodeForConnectedEsp32()` peer mapping.
- Safe fallback remains only when `discoveredNodes` is empty and is labeled as fallback.
- Nodes and Route UI prioritize live discovered node IDs; simulation-era labels are removed or marked as fallback/lab state.
- Route tab shows live LoRa route status when real discovered nodes exist instead of misleading simulated `No route`.
- Local build PASS with Android Studio JBR: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`.
- Physical Android hardware validation PASS.

STEP046A Controlled Multi-Hop Lab Mode:
- ESP32-only change in `esp32-node-platformio/src/main.cpp`.
- Added compile-time flag `TEST_FORCE_GATEWAY_ROUTE`, default `0`, preserving current production firmware behavior when disabled.
- When enabled, only `nodeA1 -> nodeA2` and `nodeA2 -> nodeA1` `MESSAGE` traffic is path-gated through the gateways.
- Forced paths: `nodeA1 -> gatewayA -> gatewayB -> nodeA2` and `nodeA2 -> gatewayB -> gatewayA -> nodeA1`.
- Direct overheard forced-route packets are ignored before duplicate-cache insertion so gateway-relayed copies can still be delivered.
- Preserves compact `BT1`, TTL, hopCount, duplicate suppression, relay cache, loop prevention, and STEP044 validation.
- Added `[FORCED_ROUTE]`, `[FORWARD]`, and boot/status visibility for `test_force_gateway_route`.
- Physical validation PASS with manual test firmware built using `TEST_FORCE_GATEWAY_ROUTE=1`.
- A -> B forced route delivered through `nodeA1 -> gatewayA -> gatewayB -> nodeA2`.
- B -> A forced route delivered through `nodeA2 -> gatewayB -> gatewayA -> nodeA1`.
- GatewayA-off test: node count became 3, `gatewayA` disappeared from NODE_LIST, and `nodeA1`/`nodeA2` still delivered messages directly. Treat this as node discovery expiry plus direct fallback/survivability evidence, not proof of full alternate gateway reroute.
- Production default restored to `#define TEST_FORCE_GATEWAY_ROUTE 0`.
- Production-mode build validation PASS: `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora`.
- `git diff --check` PASS.

STEP047 Bluetooth Transport Layer:
- User clarification: Bluetooth is intended only for Android Phone <-> ESP32 node communication.
- Bluetooth must not be added as a node-to-node MANET transport.
- LoRa remains the MANET backbone.
- The phone is only a client/controller connected to one local node.
- Android update: `MainActivity.kt` now labels Bluetooth SPP as phone-to-node access in UI diagnostics, readiness text, validation labels, and route notes.
- ESP32 update: `src/main.cpp` now reports Bluetooth as phone-node access in `BT_STATUS`, startup logs, invalid-protocol error text, and `bluetoothRole=PHONE_NODE_ACCESS` status metadata.
- Build validation PASS: Android `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`; ESP32 `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora`.
- Physical validation PASS: Bluetooth phone-to-node access verified; `NODE_LIST` verified; `PhoneA -> nodeA2` verified; `nodeA2 -> PhoneA` verified; gateway disappearance detection verified; gateway-loss messaging verified.

STEP046B-A Bridge ACK Requirements Definition:
- Bridge ACK means final destination-node delivery acknowledgment for the original message, not Bluetooth write success, local ESP32 acceptance, gateway forwarding, or hop acceptance.
- Authoritative ACK source: destination node.
- Diagnostic-only ACK sources: forwarding gateway ACK and hop-by-hop ACK.
- Recommended design: two-tier ACK model. `DELIVERY` ACK drives user-facing Chat/Bridge ACK state; `FORWARD` and `HOP` ACKs update diagnostics only.
- ACK packet must include `ackFor` matching the original message packet ID, `ackType`, `ackStatus`, `originNode`, `finalDestinationNode`, `ackSource`, and route/hop metadata.
- Android display waits up to 12 seconds for a matching destination delivery ACK; it shows `Pending`, `Delivered to <nodeId>`, `Delivery ACK pending`, `Unknown`, or a correlated explicit failure. Raw JSON must not be the primary user-facing Bridge ACK display.
- STEP046B should not automatically retransmit the original MESSAGE unless explicitly approved later.
- Acceptance criteria are recorded in `docs/codex-task-logs/STEP046B_A_BRIDGE_ACK_REQUIREMENTS_DEFINITION.md`.

STEP046B Bridge ACK Reliability Improvement:
- Android change in `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`: Chat send now creates `Pending` messages, tracks the original packet ID, waits up to 12 seconds for a matching `DELIVERY` ACK, marks `Delivered` only for final destination ACKs, and marks timeout as `Unknown` instead of false failure.
- Android ACK parsing now uses JSON parsing for BT-MANET packets, supports structured ACK payloads, hides raw ACK JSON from the primary Chat Bridge ACK display, and logs `FORWARD` / `HOP` ACKs as diagnostics only.
- ESP32 change in `esp32-node-platformio/src/main.cpp`: final destination nodes generate correlated `ACK` packets with `ackType=DELIVERY`, `ackFor`, `ackStatus=DELIVERED`, `originNode`, `finalDestinationNode`, `ackSource`, and route metadata after local message delivery.
- ESP32 local bridge acceptance ACK remains a `FORWARD` diagnostic and must not drive Android delivered state.
- Routing decisions, route discovery, Bluetooth architecture, LoRa backbone behavior, gateway Bluetooth-disable behavior, STEP044 strict compact `BT1` validation, STEP045A discovery, STEP045B live destination selection, and STEP047 phone-node access boundary are preserved.
- Local validation PASS: Android `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`.
- Local validation PASS: ESP32 `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora`.

STEP046B hardware validation result:
- PASS: `nodeA1 -> nodeA2` Delivered.
- PASS: `nodeA2 -> nodeA1` Delivered.
- PASS: timeout condition detected.
- PASS: failed delivery shown correctly.
- PASS: message not incorrectly marked Delivered.
- FAIL: failed card still displayed raw Bridge ACK JSON.

STEP046C Bridge ACK UI Sanitization:
- Android-only fix in `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`.
- Message cards, status rows, and protocol payload display now sanitize Bridge ACK content into user-facing states only: `Bridge ACK: Delivered`, `Bridge ACK: Pending`, `Bridge ACK: Unknown`, or `Bridge ACK: Failed`.
- Raw ACK JSON payloads remain available to logs/debug parsing but are not rendered in user-facing Bridge ACK sections.
- Android build PASS: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`.
- Physical validation PASS: attached screenshots show the failed Bridge ACK card rendered as a user-friendly failed state with no raw Bridge ACK JSON displayed to the end user.

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

STEP042C delivery tracking implementation summary:
- ESP32-only change in `esp32-node-platformio/src/main.cpp`.
- Added `DeliveryEntry` table, `initDeliveryTracking()`, `updateDeliveryState()`, `processDeliveryTimeouts()`, `printDeliveryTracking()`, and serial commands `DELIVERY_STATUS` / `DELIVERY_SEEN <packetId>`.
- ACK correlation now uses exact `ackFor` match instead of the ACK's own `packetId`.
- Removed dangerous substring `indexOf` fallback from `updateDeliveryState()`.
- `UNKNOWN` timeout only transitions `MESSAGE` state after 60 seconds.
- No routing decisions, route discovery, Bluetooth architecture, LoRa backbone, or Android UI were changed.
- Builds validated: `pio run -e nodeA1` PASS; `pio run -e nodeA1_lora` PASS.

Latest implementation instructions:
- Follow `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`.
- Work on one workbook task at a time.
- Keep ESP32, Raspberry Pi, Android, and docs responsibilities separated.
- Do not add hardware-dependent logic unless the workbook step explicitly allows it.
- Follow hybrid AI workflow Codex conservation rules.
- Next incomplete activity for this thread: inspect workbook for next approved task after STEP042.

Expected outputs:
- Corrupted compact packets log `[LORA_DROP_CORRUPT] reason=<reason> payload=<short payload>`.
- No malformed packet creates or updates a neighbor.
- Known-good compact HELLO and message relay still work for gatewayA/gatewayB/nodeA1/nodeA2.
- Local LoRa MANET routing and validated `nodeA1 <-> nodeA2` Chat delivery remain non-regressed.
- STEP046C hardware validation evidence: attached screenshots confirm failed Bridge ACK cards show a user-friendly failed state without raw ACK JSON. Delivered, Pending, and Unknown Bridge ACK states remain user-friendly.

Latest build/test result:
- STEP 038 is treated as Stable STEP038 baseline firmware, not final firmware.
- STEP040B tested branch/HEAD recorded as `step-002-003-esp32-simulation` @ `8e018c0`.
- STEP041 tested on same branch/HEAD with serial bridge integration.
- Gateway-to-gateway encrypted internet tunnel is operational through Tailscale TCP relay.
- ESP32-to-Raspberry Pi USB serial bridge is operational with `[GW_JSON]` protocol.
- Gateway Bluetooth cleanup passed: gateway builds boot Bluetooth DISABLED; normal node builds keep Bluetooth enabled.
- STEP042A A-side physical discovery PASS: Android shows `nodeA1 ONLINE`, `nodeA2 ONLINE`, and `gatewayA ONLINE`.
- STEP042B 2-node physical messaging PASS: Android Chat delivery validated both directions between `nodeA1` and `nodeA2`.
- STEP044 physical validation PASS after corrective commit `9dba0ef`: corrupt drops logged, valid HELLO parses retained, no ghost neighbors, node table stable at 4 nodes.
- STEP045A physical validation PASS: Android discovery now includes `gatewayB ONLINE` via mesh-wide HELLO propagation.
- STEP045B physical validation PASS: Android real-network cleanup accepted on hardware.
- RPi gateway simulation tests last known passing through failover/recovery baseline.
- Full 6-node discovery is not complete: `nodeA3` is not deployed.

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
- Gateway ACK tracking.
- Heartbeat monitoring.
- Peer online detection.
- Automatic reconnect.
- Gateway relay mode.
- LoRa backup backhaul mode.

Store-and-forward requirements (STEP042D-A) are now defined in `docs/codex-task-logs/STEP042D_A_STORE_AND_FORWARD_REQUIREMENTS_DEFINITION.md`; implementation pending.

Recommended next model/tool: continue with STEP042D-B Queue Engine after workbook/user approval.
