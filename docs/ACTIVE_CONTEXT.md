# Active Context

Last updated: 2026-05-22

Project: PUP MANET Emergency Messaging Prototype

Repository: https://github.com/pagibetech/pup-manet-emergency-messaging

Local path: `/Users/macbookm1max321tb/A_Design/A_Coding/ADM_Manet`

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed: `f7e2501 STEP 037 — Multi-Hop Routing Foundation`

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

Tested branch/HEAD: `step-002-003-esp32-simulation` @ `f7e2501`.

Recommended next task: start STEP 038 - Real Multi-Hop Relay Validation.

Recommended model/tool: local hardware bench + Android/PlatformIO tools; Codex only for focused validation or difficult blockers.

Escalation guidance: keep STEP 038 scoped to real multi-hop relay validation unless the workbook explicitly authorizes broader routing work.
