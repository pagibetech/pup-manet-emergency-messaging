# Active Context

Last updated: 2026-06-04

Project: PUP MANET Emergency Messaging Prototype

Repository: https://github.com/pagibetech/pup-manet-emergency-messaging

Local path: `/Users/macbookm1max321tb/A_Design/A_Coding/ADM_Manet`

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed: `8e018c0 feat(gateway): add TCP relay service foundation`

Workbook path: `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

Workbook latest referenced commit: `8e018c0`

Latest completed workbook step: STEP041 - ESP32 ↔ Raspberry Pi Serial Bridge Integration; gateway Bluetooth cleanup validated

Current milestone: STEP042A Node Discovery and Reachability - discovery export fix built; physical acceptance pending.

Current feature: NODE_LIST export for online/reachable LoRa nodes using HELLO-derived node table entries.

Active implementation files: `esp32-node-platformio/src/main.cpp` contains the STEP042A discovery export fix and preserves gateway Bluetooth-disable behavior. No Android, Raspberry Pi gateway service, workbook, or MD implementation changes are part of STEP042A firmware acceptance.

Unresolved issue: STEP042A is not physically accepted until `nodeA1_lora` and `nodeA2_lora` are flashed and Android NODE_LIST shows `count>=2`.

Current testing state: Gateway Bluetooth cleanup passed; `gatewayA_lora` and `gatewayB_lora` boot with Bluetooth DISABLED. Android Bluetooth scan now shows only `PUP-MANET-nodeA1` and `PUP-MANET-nodeA2`, not `PUP-MANET-NODE_A` or `PUP-MANET-NODE_B`. STEP042A firmware fix builds passed for `nodeA1_lora`, `nodeA2_lora`, `gatewayA_lora`, and `gatewayB_lora`; physical Android acceptance is pending.

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
1. Flash `nodeA1_lora` and `nodeA2_lora`.
2. Power both nodes.
3. Wait 60 seconds.
4. Connect Phone A to `PUP-MANET-nodeA1`.
5. Connect Phone B to `PUP-MANET-nodeA2`.
6. Press Refresh Nodes.
7. Expected Android NODE_LIST `count >= 2`.
8. Expected discovered nodes: `nodeA1,A,ONLINE` and `nodeA2,A,ONLINE`.

Future gateway code must support store-and-forward queue, gateway ACK tracking, heartbeat monitoring, peer online detection, automatic reconnect, gateway relay mode, and LoRa backup backhaul mode.

Recommended model/tool: proceed with local ESP32/Android hardware validation for STEP042A. Use Codex only for focused validation or difficult blocker analysis.

Escalation guidance: do not mark STEP042A complete until Android shows NODE_LIST `count>=2` with `nodeA1,A,ONLINE` and `nodeA2,A,ONLINE`. Do not start STEP042B messaging yet.

Clarified requirements (2026-06-02):
- Default node IDs: nodeA1, nodeA2, nodeA3, nodeB1, nodeB2, nodeB3.
- Android shows only online/reachable nodes; nodes remain visible even without Android phones attached.
- One Android phone maximum per node.
- Node list grouped by mesh/gateway in Android UI.
- Presence protocol: HELLO interval = 10 sec; offline timeout = 30 sec.
- STEP042 split into: STEP042A Node Discovery and Reachability; STEP042B Destination Messaging; STEP042C Delivery Tracking; STEP042D Store-and-Forward.
- Delivery tracking statuses: MESSAGE (sent), DELIVERED (received at destination node), SEEN (read by recipient Android user).

Do not start STEP042B-D until STEP042A physical acceptance is confirmed.
