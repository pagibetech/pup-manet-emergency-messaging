# Active Context

Last updated: 2026-05-23

Project: PUP MANET Emergency Messaging Prototype

Repository: https://github.com/pagibetech/pup-manet-emergency-messaging

Local path: `/Users/macbookm1max321tb/A_Design/A_Coding/ADM_Manet`

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed: `6929119 STEP 038 — Real Multi-Hop Relay Validation`

Workbook path: `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

Workbook latest referenced commit: `f7e2501`

Latest completed workbook step: STEP 037 - Multi-Hop Routing Foundation

Current milestone: STEP 038 - Real Multi-Hop Relay Validation

Current feature: Validate real multi-hop relay behavior beyond the confirmed 2-node Chat LoRa path

Active implementation files: none for this continuity commit. Source code is not being modified.

Unresolved issue: STEP 038 pending: real multi-hop relay validation.

Current testing state: STEP 037 PASS. Multi-hop routing foundation validated without breaking real 2-node LoRa Chat delivery.

Confirmed working flow: `Phone A Chat -> NODE_A -> LoRa -> NODE_B -> Phone B Chat`, plus reverse `Phone B Chat -> NODE_B -> LoRa -> NODE_A -> Phone A Chat`.

Evidence summary: logs show `[BT_RX]`, `[LORA_TX]`, `[LORA_RX]`, `[ROUTE_DECISION] deliver_local`, and `[BT_TX]`; new multi-hop fields `hopCount`, `ttl`, and `previousHop` are active; no regression from STEP 035 / STEP 036.

Current design direction: `RPi3B Gateway A <-> Tailscale VPN <-> RPi3B Gateway B`.

Architecture update: the old WiFi router-to-router simulated satellite link is removed. Each Raspberry Pi 3B gateway connects directly to the internet through LAN or WiFi, and the two gateways communicate over Tailscale VPN. WiFi routers are local internet access/router/AP devices only, not the inter-network backhaul. GSM/cellular remains optional fallback/backhaul.

Future gateway code must support Tailscale peer IP configuration, a message relay API/socket between gateways, store-and-forward queue, link status monitoring, and optional GSM fallback.

Recommended next task: continue STEP 038 - Real Multi-Hop Relay Validation; prepare STEP 039 - Raspberry Pi Tailscale Gateway Backhaul Architecture.

Recommended model/tool: local hardware bench + RPi/Tailscale tools when validating; Codex only for focused validation or difficult blockers.

Escalation guidance: do not mark Tailscale implementation complete until RPi3B-to-RPi3B communication over internet/VPN is validated.
