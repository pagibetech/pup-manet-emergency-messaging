# Active Context

Last updated: 2026-06-02

Project: PUP MANET Emergency Messaging Prototype

Repository: https://github.com/pagibetech/pup-manet-emergency-messaging

Local path: `/Users/macbookm1max321tb/A_Design/A_Coding/ADM_Manet`

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed: `8e018c0 feat(gateway): add TCP relay service foundation`

Workbook path: `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

Workbook latest referenced commit: `8e018c0`

Latest completed workbook step: STEP041 - ESP32 ↔ Raspberry Pi Serial Bridge Integration

Current milestone: STEP041 marked COMPLETE/PASS. Next incomplete steps: 8.2 Degradation Test; STEP042A Node Discovery and Reachability.

Current feature: USB serial bridge between ESP32 gateway node and Raspberry Pi gateway service for BT-MANET/LORA protocol JSON lines — VALIDATED.

Active implementation files: `raspberry-pi-gateway/gateway_service.py` (serial bridge integration applied), `src/main.cpp` (ESP32 `[GW_JSON]` prefix parsing and `GATEWAY_SERIAL_PARSE` logging), `raspberry-pi-gateway/config.betg1.json`, `raspberry-pi-gateway/config.betg2.json`. Pre-STEP041 backup at `raspberry-pi-gateway/gateway_service.py.backup-step041`.

Unresolved issue: None. STEP041 serial bridge integration is complete and validated end-to-end.

Current testing state: STEP041 PASS. Gateway service opened `/dev/ttyUSB0` on both Raspberry Pis. Gateway B sent `STEP041-FINAL-001` over TCP to Gateway A; Gateway A logged `[TCP_RX]`, wrote packet to ESP32 via `[SERIAL_TX]`; ESP32 logged `[GATEWAY_SERIAL_PARSE]` with `valid=true`, executed `[ROUTE_DECISION]`, and triggered `[LORA_TX]`. Tailscale TCP relay remained working throughout. Full bidirectional path validated.

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

Future gateway code must support store-and-forward queue, gateway ACK tracking, heartbeat monitoring, peer online detection, automatic reconnect, gateway relay mode, and LoRa backup backhaul mode.

Recommended model/tool: proceed with 8.2 Degradation Test using local ESP32/RPi hardware. Use Codex only for focused validation or difficult blocker analysis.

Escalation guidance: STEP041 is complete and validated. 8.2 Degradation Test is the next incomplete hardware step. STEP042A-D are queued as the next design/implementation milestones after clarified requirements.

Clarified requirements (2026-06-02):
- Default node IDs: nodeA1, nodeA2, nodeA3, nodeB1, nodeB2, nodeB3.
- Android shows only online/reachable nodes; nodes remain visible even without Android phones attached.
- One Android phone maximum per node.
- Node list grouped by mesh/gateway in Android UI.
- Presence protocol: HELLO interval = 10 sec; offline timeout = 30 sec.
- STEP042 split into: STEP042A Node Discovery and Reachability; STEP042B Destination Messaging; STEP042C Delivery Tracking; STEP042D Store-and-Forward.
- Delivery tracking statuses: MESSAGE (sent), DELIVERED (received at destination node), SEEN (read by recipient Android user).

Do not modify firmware, gateway, or Android code for STEP042A-D until explicitly instructed by the workbook step.
