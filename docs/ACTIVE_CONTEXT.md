# Active Context

Last updated: 2026-06-09

Project: PUP MANET Emergency Messaging Prototype

Repository: https://github.com/pagibetech/pup-manet-emergency-messaging

Local path: `/Users/macbookm1max321tb/A_Design/A_Coding/ADM_Manet`

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed before STEP043 fix: `e8cf02d STEP042A Gateway node advertisement and serial bridge`

Workbook path: `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

Workbook latest referenced commit before STEP043 fix: `e8cf02d`

Latest physically completed workbook step: STEP047 - Bluetooth Transport Layer. STEP042C is now COMPLETE / PASS after ACK correlation hardening.

Current milestone: STEP042C - Delivery Tracking COMPLETE / PASS after ACK correlation hardening.

Current feature: STEP047 is physically validated and closed. Bluetooth is Android phone-to-local ESP32 access only; Bluetooth is not a node-to-node MANET transport; LoRa remains the MANET backbone.

STEP046B-A planning status: Bridge ACK requirements defined in `docs/codex-task-logs/STEP046B_A_BRIDGE_ACK_REQUIREMENTS_DEFINITION.md`.

STEP046B-A Bridge ACK definition: Bridge ACK is the user-facing confirmation that a phone-originated message sent through the local ESP32 bridge was acknowledged by the final destination node. It is not merely Android Bluetooth write success, local ESP32 acceptance, gateway forwarding, or hop acceptance.

STEP046B-A ACK source decision: authoritative user-facing ACK source is the destination node. Forwarding gateway ACK and hop-by-hop ACK are diagnostics only and must not mark a Chat message as delivered.

STEP046B-A safest design: use a two-tier ACK model. `DELIVERY` ACK from the final destination node drives the primary UI. `FORWARD` and `HOP` ACKs may update diagnostics only. Android should match ACKs by `ackFor == original packetId`, wait up to 12 seconds for a matching delivery ACK, show `Pending` while waiting, show `Delivered to <nodeId>` only for a destination delivery ACK, and show `Delivery ACK pending` or `Unknown` on timeout instead of false failure or raw JSON.

STEP046B implementation status: Android and ESP32 ACK reliability changes are implemented and locally build-validated. Android now tracks pending Bridge ACKs by original packet ID, waits up to 12 seconds for a matching destination `DELIVERY` ACK, displays `Pending`, `Delivered`, or `Unknown`, and keeps `FORWARD`/`HOP` ACKs diagnostic-only. Raw ACK JSON is no longer the primary Chat Bridge ACK display.

STEP046B ESP32 status: destination nodes generate compact `BT1`/BT-MANET `ACK` packets with `ackType=DELIVERY`, `ackFor=<original packetId>`, `ackStatus=DELIVERED`, `ackSource=<destination node>`, and route metadata after local message delivery. Local bridge forwarding ACKs remain `FORWARD` diagnostics only. Routing decisions, route discovery, Bluetooth architecture, LoRa backbone behavior, and STEP044 strict validation are preserved.

STEP046B local validation: Android build PASS with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`. ESP32 build PASS with `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora`.

STEP046B hardware validation: delivery behavior PASS. `nodeA1 -> nodeA2` showed Delivered, `nodeA2 -> nodeA1` showed Delivered, timeout condition was detected, failed delivery was shown correctly, and failed messages were not incorrectly marked Delivered. Acceptance failure: the failed card still displayed raw Bridge ACK JSON, violating the requirement that end users see only user-friendly Bridge ACK states.

STEP046C status: COMPLETE / PASS. Android UI sanitizes Bridge ACK display at the message card/status-row boundary. Visible Bridge ACK states are `Delivered`, `Pending`, `Unknown`, or `Failed`; raw ACK JSON payloads are retained only for logs/debug and are not shown in user-facing Bridge ACK sections. Android build PASS with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`.

STEP046C physical validation: PASS per attached validation screenshots. Failed Bridge ACK card now shows a user-friendly Bridge ACK failed state and no raw Bridge ACK JSON is shown to the end user.

STEP042C delivery tracking: `esp32-node-platformio/src/main.cpp` now includes `DeliveryEntry` tracking table, `initDeliveryTracking()`, `updateDeliveryState()`, `processDeliveryTimeouts()`, and `printDeliveryTracking()`. ACK correlation uses exact `ackFor` match; no substring fallback remains. `DELIVERY_SEEN` serial command uses exact `packetId` matching. `UNKNOWN` timeout only affects `MESSAGE` state.

Active implementation files: `esp32-node-platformio/src/main.cpp` contains the STEP042A discovery export fix, STEP042C delivery tracking, STEP044 strict BT1 validation, STEP045A HELLO propagation, STEP046A lab-mode forced routing overlay, STEP047 Bluetooth access-layer status diagnostics, and STEP046B destination delivery ACK generation while preserving gateway Bluetooth-disable behavior. `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt` uses live discovered-node destination selection from STEP045B, STEP047 phone-to-node Bluetooth access-layer UI wording, STEP046B Bridge ACK correlation, and STEP046C user-facing Bridge ACK display sanitization.

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

STEP045B Android cleanup: `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt` now prioritizes real discovered nodes over simulation-era labels. Chat destination selection is driven by live `discoveredNodes`, excludes the local connected ESP32 node, and uses the selected discovered node instead of the temporary hardcoded `peerNodeForConnectedEsp32()` mapping. Fallback peer selection remains only when no live NODE_LIST exists and is labeled as fallback.

STEP045B UI cleanup: Android display labels no longer present `Node Alpha`, `Node Bravo`, `Node Charlie`, `Node Delta`, `Gateway Node`, `SimulationTransport`, `ESP32 Transport Preparation`, or simulation placeholder text as current real-network state. The Route tab shows live LoRa route information when real discovered nodes exist instead of misleading simulated `No route`.

STEP045B validation: local build PASS with `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`; physical hardware validation PASS.

STEP046A implementation: `esp32-node-platformio/src/main.cpp` adds `TEST_FORCE_GATEWAY_ROUTE`, defaulting to production-safe `0`. When enabled for lab builds, only `nodeA1 -> nodeA2` and `nodeA2 -> nodeA1` `MESSAGE` traffic is forced through `nodeA1 -> gatewayA -> gatewayB -> nodeA2` and reverse. Direct overheard forced-route packets are ignored before duplicate-cache insertion so the valid gateway-relayed copy can still be delivered. Existing compact `BT1`, TTL, hopCount, duplicate suppression, relay behavior, loop prevention, and STEP044 strict corrupt-packet validation remain preserved.

STEP046A physical validation: PASS / COMPLETE. Manual test firmware was flashed with `TEST_FORCE_GATEWAY_ROUTE=1`. A -> B forced route delivered on `nodeA1 -> gatewayA -> gatewayB -> nodeA2`. B -> A forced route delivered on `nodeA2 -> gatewayB -> gatewayA -> nodeA1`.

STEP046A gatewayA-off test: node count became 3, `gatewayA` disappeared from NODE_LIST, and `nodeA1`/`nodeA2` still delivered messages directly. This proves node discovery expiry and direct fallback/survivability, but not full alternate gateway reroute.

STEP046A production restore: source default is restored to `#define TEST_FORCE_GATEWAY_ROUTE 0`. Production-mode build validation PASS: `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora`. `git diff --check` PASS.

STEP047 architecture clarification: Bluetooth is Android Phone <-> local ESP32 node access only. Bluetooth is not a node-to-node MANET transport. LoRa remains the MANET backbone, and the phone is only a client/controller connected to one local node.

STEP047 implementation: Android UI/diagnostic wording now describes Bluetooth SPP as a phone-to-node access layer, not a MANET transport. ESP32 `BT_STATUS`, invalid-protocol error text, and Bluetooth service startup logs now report the same role and expose `bluetoothRole=PHONE_NODE_ACCESS` in status payloads.

STEP047 build validation: PASS. Android build command: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug`. ESP32 build command: `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora`.

STEP047 physical validation: PASS / COMPLETE. Bluetooth phone-to-node access verified; `NODE_LIST` verified; `PhoneA -> nodeA2` verified; `nodeA2 -> PhoneA` verified; gateway disappearance detection verified; gateway-loss messaging verified.

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
- GatewayB repeated reset/garbage serial output needs investigation.

Future gateway code must support store-and-forward queue, gateway ACK tracking, heartbeat monitoring, peer online detection, automatic reconnect, gateway relay mode, and LoRa backup backhaul mode.

Recommended model/tool: continue from the next workbook-approved task with Codex/local tools only after studying workbook and continuity files.

Escalation guidance: do not start STEP042C/D unless the workbook/user explicitly routes there next.

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

STEP042C validation:
- ACK handling now uses `ackFor` for exact correlation.
- No substring matching remains (`indexOf` fallback removed).
- `DELIVERY_SEEN` uses exact `packetId` matching.
- `MESSAGE -> DELIVERED -> SEEN` state transitions validated.
- `UNKNOWN` timeout only affects `MESSAGE` state.
- Builds passed for `nodeA1` and `nodeA1_lora`.

Next validation activity: continue only from the next workbook-approved task after STEP042C. Preserve routing core, compact `BT1` validation, mesh-wide discovery, live Android destination selection, STEP047 Bluetooth phone-node access boundary, and STEP042C delivery tracking.
