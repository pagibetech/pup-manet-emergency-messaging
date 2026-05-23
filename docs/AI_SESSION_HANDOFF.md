# AI Session Handoff

Last updated: 2026-05-23

Current milestone: STEP 039 - Raspberry Pi Tailscale Gateway Backhaul Architecture.

Latest completed milestone: STEP 038 - Real Multi-Hop Relay Validation / Stable STEP038 baseline firmware.

Unfinished task: implement and validate the finalized STEP039 gateway/backhaul architecture only when a later workbook task explicitly allows it.

Pending validations:
- Real multi-hop relay behavior beyond the confirmed 2-node Chat LoRa path.
- Route logs show relay decisions, ttl handling, hopCount updates, and previousHop tracking.
- Existing 2-node Chat LoRa path remains non-regressed.
- Raspberry Pi 3B-to-Raspberry Pi 3B internet/Tailscale VPN backhaul is not yet validated.
- LoRa-to-LoRa gateway backup backhaul is not yet validated.

Blockers:
- No blocker for continuity update.
- Tailscale implementation must not be marked complete until Gateway A and Gateway B communicate over internet/VPN.

Architecture change:
- Old backhaul removed: `WiFi Router A <-> WiFi Router B simulated satellite link`.
- Finalized STEP039 backhaul direction: `Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B`.
- Each MANET still has 3 ESP32 LoRa nodes paired to Android phones.
- Each local MANET has one Raspberry Pi 3B gateway with LoRa module.
- Internet backhaul is the primary gateway transport.
- Tailscale VPN is the primary encrypted tunnel.
- Ethernet/WiFi internet connectivity is preferred.
- LoRa-to-LoRa gateway backhaul is the backup path if internet is unavailable.
- WiFi routers are local internet access/router/AP devices only.
- GSM/cellular remains optional future fallback.

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
- STEP 038 is treated as Stable STEP038 baseline firmware, not final firmware.
- Tested branch/HEAD recorded as `step-002-003-esp32-simulation` @ `102ffe9`.
- RPi gateway simulation tests last known passing through failover/recovery baseline.
- No build, test, or simulation run was performed during this continuity documentation update.

Architecture priority order:
- Priority 1: Local LoRa MANET.
- Priority 2: Tailscale VPN gateway tunnel.
- Priority 3: Long-range LoRa gateway backup.
- Priority 4: GSM/cellular optional fallback.

ESP32 firmware status:
- STEP038 ESP32 firmware is not final.
- It is now considered Stable STEP038 baseline firmware.
- Existing TTL, hopCount, duplicate suppression, Bluetooth bridge, and LoRa forwarding logic remains valid.
- Future ESP32 firmware will support NODE mode and GATEWAY mode.

Future gateway-code requirements:
- Store-and-forward queue.
- Gateway ACK tracking.
- Heartbeat monitoring.
- Peer online detection.
- Automatic reconnect.
- Gateway relay mode.
- LoRa backup backhaul mode.

Recommended next model/tool: local RPi/Tailscale hardware tools for future validation; use Codex only for focused validation or difficult blocker analysis.
