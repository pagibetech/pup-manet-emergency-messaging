# Active Context

Last updated: 2026-05-24

Project: PUP MANET Emergency Messaging Prototype

Repository: https://github.com/pagibetech/pup-manet-emergency-messaging

Local path: `/Users/macbookm1max321tb/A_Design/A_Coding/ADM_Manet`

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed: `8e018c0 feat(gateway): add TCP relay service foundation`

Workbook path: `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

Workbook latest referenced commit: `8e018c0`

Latest completed workbook step: STEP040B - Python Gateway TCP Relay Service / Tailscale TCP relay PASS

Current milestone: STEP041 - ESP32-to-Raspberry Pi Serial Bridge Integration

Current feature: USB serial bridge between ESP32 gateway node and Raspberry Pi gateway service for BT-MANET/LORA protocol JSON lines

Active implementation files: none for this continuity commit. Source code is not being modified.

Unresolved issue: ESP32-to-Raspberry Pi serial integration is not complete yet; LoRa gateway backup and optional GSM fallback remain future work.

Current testing state: STEP040B PASS. `pup-gateway-a` (`100.123.79.41`) reached `pup-gateway-b` (`100.79.214.18`) over Tailscale TCP port `5050`; JSON relay, ACK, and heartbeat worked.

Confirmed working flow: `Phone A Chat -> NODE_A -> LoRa -> NODE_B -> Phone B Chat`, plus reverse `Phone B Chat -> NODE_B -> LoRa -> NODE_A -> Phone A Chat`.

Evidence summary: logs show `[BT_RX]`, `[LORA_TX]`, `[LORA_RX]`, `[ROUTE_DECISION] deliver_local`, and `[BT_TX]`; new multi-hop fields `hopCount`, `ttl`, and `previousHop` are active; no regression from STEP 035 / STEP 036.

Current design direction: `Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B`.

Architecture update: replace `WiFi Router A <-> WiFi Router B simulated satellite link` with `Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B`. Internet backhaul is now the primary gateway transport; Tailscale VPN is the primary encrypted tunnel; Ethernet/WiFi internet connectivity is preferred. LoRa-to-LoRa gateway backhaul is the backup path if internet is unavailable. GSM/cellular remains optional future fallback.

Architecture priority order: Priority 1 Local LoRa MANET; Priority 2 Tailscale VPN gateway tunnel; Priority 3 Long-range LoRa gateway backup; Priority 4 GSM/cellular optional fallback.

Existing ESP32 routing logic remains valid: TTL, hopCount, duplicate suppression, Bluetooth bridge, and LoRa forwarding. Future ESP32 firmware will support NODE mode and GATEWAY mode.

STEP040B evidence summary: Gateway B listened on `0.0.0.0:5050`; Gateway A connected to `100.79.214.18:5050`; Gateway A sent `{"type":"test","message":"HELLO_FROM_GATEWAY_A"}`; Gateway B logged `[TCP_RX]` from `100.123.79.41:<port>`; ACK returned; heartbeat packets worked.

STEP041 goal: connect ESP32 gateway node to Raspberry Pi via USB serial and pass BT-MANET/LORA protocol JSON lines between ESP32 and Raspberry Pi gateway service. Do not mark ESP32 serial integration complete yet.

Future gateway code must support store-and-forward queue, gateway ACK tracking, heartbeat monitoring, peer online detection, automatic reconnect, gateway relay mode, and LoRa backup backhaul mode.

Recommended model/tool: local ESP32/RPi USB serial hardware tools for STEP041; Codex only for focused validation or difficult blockers.

Escalation guidance: do not mark STEP041 complete until ESP32 serial bridge integration is validated.
