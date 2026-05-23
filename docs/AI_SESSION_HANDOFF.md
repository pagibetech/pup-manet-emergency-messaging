# AI Session Handoff

Last updated: 2026-05-23

Current milestone: STEP 038 - Real Multi-Hop Relay Validation.

Latest completed milestone: STEP 037 - Multi-Hop Routing Foundation.

Unfinished task: validate a real multi-hop relay path using the new routing foundation; next design milestone is STEP 039 - Raspberry Pi Tailscale Gateway Backhaul Architecture.

Pending validations:
- Real multi-hop relay behavior beyond the confirmed 2-node Chat LoRa path.
- Route logs show relay decisions, ttl handling, hopCount updates, and previousHop tracking.
- Existing 2-node Chat LoRa path remains non-regressed.
- RPi3B-to-RPi3B internet/Tailscale VPN backhaul is not yet validated.

Blockers:
- No blocker for continuity update.
- STEP 038 physical relay validation remains pending.
- Tailscale implementation must not be marked complete until Gateway A and Gateway B communicate over internet/VPN.

Architecture change:
- Old backhaul removed: WiFi router-to-router simulated satellite link.
- New backhaul direction: `RPi3B Gateway A <-> Tailscale VPN <-> RPi3B Gateway B`.
- Each MANET still has 3 ESP32 LoRa nodes paired to Android phones.
- Each local MANET has one Raspberry Pi 3B gateway with LoRa module.
- Gateway A and Gateway B connect to internet over LAN or WiFi and communicate through Tailscale VPN.
- WiFi routers are local internet access/router/AP devices only.
- GSM/cellular remains optional fallback/backhaul.

Confirmed STEP 037 evidence:
- Existing 2-node path still works after multi-hop foundation: `Phone A Chat -> NODE_A -> LoRa -> NODE_B -> Phone B Chat`.
- Reverse path also works: `Phone B Chat -> NODE_B -> LoRa -> NODE_A -> Phone A Chat`.
- Logs show `[BT_RX]`, `[LORA_TX]`, `[LORA_RX]`, `[ROUTE_DECISION] deliver_local`, and `[BT_TX]`.
- New multi-hop fields confirmed active: `hopCount`, `ttl`, `previousHop`.
- No regression from STEP 035 / STEP 036.

Latest implementation instructions:
- Follow `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`.
- Work on one workbook task at a time.
- Keep ESP32, Raspberry Pi, Android, and docs responsibilities separated.
- Do not add hardware-dependent logic unless the workbook step explicitly allows it.
- Follow hybrid AI workflow Codex conservation rules.

Expected outputs:
- STEP 038 real multi-hop relay validation evidence.
- Route logs showing relay behavior and hop metadata.
- Confirmation that the 2-node Chat LoRa path still works.
- STEP 039 architecture output: Tailscale peer IP plan, gateway relay API/socket plan, store-and-forward queue plan, link status monitoring, optional GSM fallback.

Latest build/test result:
- STEP 037 Multi-Hop Routing Foundation passed.
- Tested branch/HEAD recorded as `step-002-003-esp32-simulation` @ `f7e2501`.
- RPi gateway simulation tests last known passing through failover/recovery baseline.
- No build, test, or simulation run was performed during this continuity documentation update.

Routing notes carried into STEP 038:
- Preserve STEP 035 / STEP 036 real Chat LoRa behavior.
- Watch `[ROUTE_DECISION] deliver_local` versus relay logs.
- Confirm `hopCount`, `ttl`, and `previousHop` are updated as expected.

Recommended next model/tool: local hardware bench + Android/PlatformIO tools for STEP 038; use Codex only for focused validation or difficult blocker analysis.

Future gateway-code requirements:
- Tailscale peer IP configuration.
- Message relay API/socket between gateways.
- Store-and-forward queue.
- Link status monitoring.
- Optional GSM fallback.
