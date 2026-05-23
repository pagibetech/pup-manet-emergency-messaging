# Active Context

Last updated: 2026-05-23

Project: PUP MANET Emergency Messaging Prototype

Repository: https://github.com/pagibetech/pup-manet-emergency-messaging

Local path: `/Users/macbookm1max321tb/A_Design/A_Coding/ADM_Manet`

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed: `102ffe9 fix(step038): enforce TTL drop before relay delivery`

Workbook path: `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

Workbook latest referenced commit: `102ffe9`

Latest completed workbook step: STEP 038 - Real Multi-Hop Relay Validation / Stable STEP038 baseline firmware

Current milestone: STEP 039 - Raspberry Pi Tailscale Gateway Backhaul Architecture

Current feature: Finalized primary Tailscale VPN internet backhaul with long-range LoRa gateway backup

Active implementation files: none for this continuity commit. Source code is not being modified.

Unresolved issue: Tailscale implementation is not validated; future gateway mode enhancements remain planned.

Current testing state: STEP035-038 preserved as PASS. STEP038 ESP32 firmware is stable baseline firmware, not final firmware.

Confirmed working flow: `Phone A Chat -> NODE_A -> LoRa -> NODE_B -> Phone B Chat`, plus reverse `Phone B Chat -> NODE_B -> LoRa -> NODE_A -> Phone A Chat`.

Evidence summary: logs show `[BT_RX]`, `[LORA_TX]`, `[LORA_RX]`, `[ROUTE_DECISION] deliver_local`, and `[BT_TX]`; new multi-hop fields `hopCount`, `ttl`, and `previousHop` are active; no regression from STEP 035 / STEP 036.

Current design direction: `Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B`.

Architecture update: replace `WiFi Router A <-> WiFi Router B simulated satellite link` with `Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B`. Internet backhaul is now the primary gateway transport; Tailscale VPN is the primary encrypted tunnel; Ethernet/WiFi internet connectivity is preferred. LoRa-to-LoRa gateway backhaul is the backup path if internet is unavailable. GSM/cellular remains optional future fallback.

Architecture priority order: Priority 1 Local LoRa MANET; Priority 2 Tailscale VPN gateway tunnel; Priority 3 Long-range LoRa gateway backup; Priority 4 GSM/cellular optional fallback.

Existing ESP32 routing logic remains valid: TTL, hopCount, duplicate suppression, Bluetooth bridge, and LoRa forwarding. Future ESP32 firmware will support NODE mode and GATEWAY mode.

Future gateway code must support store-and-forward queue, gateway ACK tracking, heartbeat monitoring, peer online detection, automatic reconnect, gateway relay mode, and LoRa backup backhaul mode.

Recommended model/tool: local RPi/Tailscale hardware tools for future validation; Codex only for focused validation or difficult blockers.

Escalation guidance: do not mark Tailscale implementation complete until Raspberry Pi 3B gateway-to-gateway communication over internet/VPN is validated.
