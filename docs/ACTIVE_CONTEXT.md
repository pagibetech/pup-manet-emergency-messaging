# Active Context

Last updated: 2026-05-22

Project: PUP MANET Emergency Messaging Prototype

Repository: https://github.com/pagibetech/pup-manet-emergency-messaging

Local path: `/Users/macbookm1max321tb/A_Design/A_Coding/ADM_Manet`

Current branch: `step-002-003-esp32-simulation`

Local HEAD observed: `147431c fix(firmware): clarify ACK status for LoRa-not-ready case`

Workbook path: `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

Workbook latest referenced commit: `147431c`

Latest completed workbook step: STEP 035 - Real End-to-End LoRa Message Delivery

Current milestone: STEP 036 - Real Chat UI Integration

Current feature: Integrate the confirmed real LoRa RF path into the chat UI workflow

Active implementation files: none for this continuity commit. Existing Android/ESP32 working tree changes remain unstaged.

Unresolved issue: STEP 036 is pending; preserve simulation fallback while integrating the real chat UI path.

Current testing state: STEP 035 physical RF end-to-end validation passed.

Confirmed working flow: `Phone A -> Bluetooth SPP -> NODE_A ESP32 -> LoRa RF -> NODE_B ESP32 -> Bluetooth SPP -> Phone B`.

Evidence summary: Phone A connected to `PUP-MANET-NODE_A` with MAC shown; Phone B connected to `PUP-MANET-NODE_B` with MAC shown; NODE_A log shows `BT_RX` and LoRa forwarding; NODE_B log shows `LORA_RX` and `BT_TX`; Phone B displays `Incoming: Emergency message from Phone A`; status includes `RECEIVED_OVER_LORA`; ACK path returns `FORWARDED_OVER_LORA`.

Tested branch/HEAD: `step-002-003-esp32-simulation` @ `147431c4da544c6f0b0cc972f83bcc124ac2a069`. Existing uncommitted Android/ESP32 validation fixes were present and are intentionally not staged in this docs-only commit.

Recommended next task: start STEP 036 - Real Chat UI Integration.

Recommended model/tool: Kimi/Android Studio for implementation; Codex only for focused validation or difficult blockers.

Escalation guidance: keep STEP 036 scoped to chat UI integration unless the workbook explicitly authorizes ESP32/RPi changes.
