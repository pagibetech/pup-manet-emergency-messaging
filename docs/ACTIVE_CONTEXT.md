# Active Context

Last updated: 2026-06-06

Project: PUP MANET Emergency Messaging Prototype

Repository: https://github.com/pagibetech/pup-manet-emergency-messaging

Local path: `/Users/macbookm1max321tb/A_Design/A_Coding/ADM_Manet`

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed before STEP043 fix: `e8cf02d STEP042A Gateway node advertisement and serial bridge`

Workbook path: `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

Workbook latest referenced commit before STEP043 fix: `e8cf02d`

Latest completed workbook step: STEP042B - Destination Messaging physical validation, limited to bidirectional 2-node Android LoRa messaging.

Current milestone: STEP043 - Fix LoRa HELLO Packet Format. Initial JSON-oriented fix failed physical validation; corrected compact `BT1` receiver-side fix is built and physical validation is pending.

Current feature: gateway HELLO discovery over LoRa using compact `BT1` relay packets for firmware consistency with the validated nodeA1/nodeA2 route.

Active implementation files: `esp32-node-platformio/src/main.cpp` contains the STEP042A discovery export fix and preserves gateway Bluetooth-disable behavior. `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt` has a temporary validation mapping change in `peerNodeForConnectedEsp32()` from `"nodeA2" -> "nodeA3"` to `"nodeA2" -> "nodeA1"` because `nodeA3` has not been flashed or deployed.

Unresolved issue: corrected STEP043 firmware must be flashed and physically validated so compact `BT1` HELLO RX logs `[LORA_RELAY_PARSE] valid`, `[DISCOVERY] HELLO from <gateway>`, and `[NEIGHBOR_ADD]` / `[NEIGHBOR_UPDATE]`.

Current testing state: `gatewayA_lora` restored and running as `gatewayA`; `gatewayB_lora` restored and running as `gatewayB`; `nodeA1_lora` flashed/running as `nodeA1`; `nodeA2_lora` flashed/running as `nodeA2`. Android discovery now shows `nodeA1 ONLINE`, `nodeA2 ONLINE`, and `gatewayA ONLINE`. STEP042A is physically validated for this A-side discovery scope.

STEP042B physical validation: PASS for bidirectional 2-node Android-to-Android LoRa bridge after the temporary Android mapping fix. Phone A sent `hello from A`; Phone B received `hello from A` via LoRa on path `nodeA1 -> nodeA2`. Phone B sent `hello from b`; Phone A received `hello from b` via LoRa on path `nodeA2 -> nodeA1`.

STEP043 correction result: physical validation after flashing `gatewayA_lora` and `gatewayB_lora` showed Gateway A still transmitting compact HELLO frames: `BT1|HELLO-gatewayA-...|gatewayA|BROADCAST|{"gatewayId":"A"}|...`. The project protocol decision is to keep compact `BT1` LoRa relay packets because nodeA1/nodeA2 routing already works with compact packets.

STEP043 corrected fix: `esp32-node-platformio/src/main.cpp` keeps `sendHelloBroadcast()` and all LoRa relay TX on compact `BT1` format. The receiver-side compact parser now logs `[LORA_RELAY_PARSE] valid`, infers `HELLO` from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId`, learns the node through `learnNodeFromHelloPacket()` before duplicate/drop routing, and returns immediately for HELLO so duplicate handling does not block discovery. Discovery logging includes `[HELLO] discovered <nodeId>` and `[DISCOVERY] HELLO from <nodeId> gateway=<gatewayId>`.

STEP043 corrected local validation: PlatformIO builds PASS for `gatewayA_lora`, `gatewayB_lora`, `nodeA1_lora`, and `nodeA2_lora`.

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
1. Completed for A-side two-node discovery.
2. Flashed/running: `nodeA1_lora`, `nodeA2_lora`, `gatewayA_lora`, `gatewayB_lora`.
3. Android discovered nodes: `nodeA1 ONLINE`, `nodeA2 ONLINE`, and `gatewayA ONLINE`.
4. Remaining discovery gap: `gatewayB` broadcasts `HELLO-gatewayB` but is not visible in Android discovery.

Open issues:
- This is a 2-node validation only; `nodeA3` has not been flashed or validated.
- `gatewayB` is flashed and broadcasting but not yet visible in Android discovery.
- Android Nodes/Route/Topology UI still partly uses simulated Node Alpha/Bravo/Charlie/Delta labels.
- Route tab can show `No route` even while real LoRa messages are delivered.
- Discovered node list works, but send destination is still not fully driven by live discovered nodes.
- GatewayB repeated reset/garbage serial output needs investigation.

Future gateway code must support store-and-forward queue, gateway ACK tracking, heartbeat monitoring, peer online detection, automatic reconnect, gateway relay mode, and LoRa backup backhaul mode.

Recommended model/tool: proceed with local ESP32/Raspberry Pi hardware validation for corrected STEP043 compact HELLO discovery.

Escalation guidance: do not start STEP042C/D. Do not replace the temporary Android destination mapping until STEP043 gateway HELLO discovery is physically accepted.

Clarified requirements (2026-06-02):
- Default node IDs: nodeA1, nodeA2, nodeA3, nodeB1, nodeB2, nodeB3.
- Android shows only online/reachable nodes; nodes remain visible even without Android phones attached.
- One Android phone maximum per node.
- Node list grouped by mesh/gateway in Android UI.
- Presence protocol: HELLO interval = 10 sec; offline timeout = 30 sec.
- STEP042 split into: STEP042A Node Discovery and Reachability; STEP042B Destination Messaging; STEP042C Delivery Tracking; STEP042D Store-and-Forward.
- Delivery tracking statuses: MESSAGE (sent), DELIVERED (received at destination node), SEEN (read by recipient Android user).

STEP043 corrected physical validation procedure:
1. Flash `gatewayA_lora` and `gatewayB_lora`.
2. Monitor both serial consoles at 115200 baud.
3. Confirm LoRa TX HELLO remains compact: `BT1|HELLO-gatewayA-...|gatewayA|BROADCAST|{"gatewayId":"A"}|...`.
4. Confirm the receiver logs `[LORA_RX] payload=BT1|HELLO-...`.
5. Confirm the receiver logs `[LORA_RELAY_PARSE] valid`.
6. Confirm Gateway B logs `[DISCOVERY] HELLO from gatewayA gateway=A` and `[NEIGHBOR_ADD] node=gatewayA` or `[NEIGHBOR_UPDATE] node=gatewayA`.
7. Confirm Gateway A logs `[DISCOVERY] HELLO from gatewayB gateway=B` and `[NEIGHBOR_ADD] node=gatewayB` or `[NEIGHBOR_UPDATE] node=gatewayB`.
8. Run `STATUS` and `NEIGHBORS` on both ESP32s.
9. Expected: neighbor table lists both gateways.

Next validation activity: physically validate corrected STEP043 compact HELLO discovery, then return to gatewayB Android discovery and live discovered-node destination selection.
