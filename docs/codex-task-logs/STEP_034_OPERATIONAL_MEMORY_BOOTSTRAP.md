# STEP 034 Prep - Operational Memory Bootstrap

Date: 2026-05-20

## Scope

Initialized minimal markdown operational memory files required by the hybrid low-cost AI workflow.

## Files Added

- `docs/HWorkflow_Dev_v3_1.txt`
- `docs/ACTIVE_CONTEXT.md`
- `docs/AI_SESSION_HANDOFF.md`
- `docs/ARCHITECTURE.md`
- `docs/CODEX_RULES.md`
- `docs/PROJECT_STATUS.md`
- `docs/AI_ROUTING_RULES.md`
- `docs/CODEX_LIMIT_STATUS.md`
- `docs/ROO_HANDOFF_PROMPT.md`

## Source State

- Workflow source inspected: `https://github.com/pagibetech/hybrid-ai-workflows`
- Source workflow file used: `HWorkflow_Dev_v3_1.txt`
- Requested workflow filename `HWorkflow_Dev_v3_Codex_Limit_Routing.txt` was not present on the inspected `main` branch.
- Workbook current milestone: Step 8.1 / STEP 034 - Baseline Test.
- Latest completed workbook step: Step 7.3 / STEP 033 - Android Network Selection Validation.

## Validation

Read-only inspections performed:

- `git ls-remote https://github.com/pagibetech/hybrid-ai-workflows.git`
- `rg --files /private/tmp/hybrid-ai-workflows-inspect`
- Workbook XML inspection for Active Context Snapshot, Progress Tracker, Detailed Steps, Build Validation Matrix, and Codex Task Log.
- `git branch --show-current`
- `git log -1 --oneline`
- `git status --short`

No Android, ESP32, Raspberry Pi, simulator, or workbook code/content was changed.

## Next Step

Run Step 8.1 physical baseline test and capture screenshots/logs plus GitHub repo, branch, and commit SHA evidence.

## STEP 034 Live Routing Debug Fix (2026-05-21)

### Problem
Android app routed MESSAGE packets directly over Bluetooth to the selected destination node instead of using the paired local ESP32 as a LoRa bridge. Evidence: ACK contained `forwarding=LOCAL_OR_SIMULATION_ONLY`; Node B received `[BT_RX]` directly; Node A received nothing.

### Root Causes
1. Android `createBluetoothProtocolPacket()` defaulted `destinationNode = "ESP32_BRIDGE"`, causing ESP32 firmware to skip LoRa forwarding (`destinationNode == "ESP32_BRIDGE"` is treated as local-only).
2. Android main chat `MessageComposer.onSend` used `activeTransport` (simulation placeholder) and never touched the real Bluetooth socket.
3. Android `peerNodeForConnectedEsp32()` had fragile string matching and no explicit local-node mapping.
4. Android `onReadIncomingPacket` only updated socket state, never added incoming LoRa-delivered messages to the chat list.
5. ESP32 ACK status for local-only case was inconsistent (`QUEUED_FOR_SIMULATION` in payload but also in status field, while `LORA_NOT_READY` was unexposed in status).

### Fixes Applied

**Android app** (`android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`):
- Added `localEsp32NodeId(connectedDevice)` to derive local node ID from paired Bluetooth device name (`PUP-MANET-NODE_A` -> `NODE_A`, `PUP-MANET-NODE_B` -> `NODE_B`).
- Rewrote `peerNodeForConnectedEsp32()` to use `localEsp32NodeId()` and return the opposite node (`NODE_A` <-> `NODE_B`).
- Integrated live bridge path into main `MessageComposer.onSend`: when `realBluetoothSocketState.connected && decision.route == RouteLabel.Lora`, create a `BluetoothProtocolPacket` with `MODE=LORA;TEXT=...` and correct remote `destinationNode`, serialize with `compactSerializedPacketText()`, and send via `androidBluetoothSocketClient.sendLineAndWaitForResponse()`.
- Added incoming LoRa message delivery to `onReadIncomingPacket`: deserialized `MESSAGE` packets with `RECEIVED_OVER_LORA` are now converted to `ChatMessage` and added to the `messages` list.
- Preserved simulation fallback: when no real Bluetooth socket is connected or route is not LoRa, existing `SimulationTransport` queue/retry/delivery loop runs unchanged.

**ESP32 firmware** (`esp32-node-platformio/src/main.cpp`):
- Refactored `processIncomingBluetoothProtocolPacket()` ACK status construction into explicit `ackStatus` variable so `LORA_NOT_READY` is correctly surfaced in the ACK status field (instead of being flattened to `QUEUED_FOR_SIMULATION`).
- No protocol format changes; BT-MANET-1.0 JSON and `BT1|...` LoRa relay frames remain identical.

### Verification Checklist
- [ ] Phone A paired to NODE_A (`PUP-MANET-NODE_A`) and Phone B paired to NODE_B (`PUP-MANET-NODE_B`).
- [ ] Phone A sends message via main chat composer with route set to LoRa.
- [ ] Node A serial shows `[BT_RX]` + `[LORA_TX]`.
- [ ] Node B serial shows `[LORA_RX]` + `[BT_TX]`.
- [ ] Phone B shows incoming message in chat list after pressing Check In.
- [ ] ACK from Node A contains `forwarding=FORWARDED_OVER_LORA`.
- [ ] Simulation mode still works when real Bluetooth socket is disconnected.

