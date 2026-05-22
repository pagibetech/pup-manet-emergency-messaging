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

## STEP 034 Physical Validation Fix (2026-05-22)

### Problem
Physical test showed `hopPath: ANDROID_APP>NODE_B` on the packet received by Node B, but Node B received it over Bluetooth (`[BT_RX]`) instead of LoRa (`[LORA_RX]`). This proved the Android app was opening the Bluetooth socket directly to the destination node's ESP32 (Node B) rather than the intended local bridge node (Node A). Node A stayed idle because the packet never reached it.

### Root Cause
`createBluetoothProtocolPacket()` set `hopPath = listOf("ANDROID_APP", destinationNode)`. When the app connected to Node B's Bluetooth service directly, the `destinationNode` was `NODE_B` and `hopPath` became `ANDROID_APP>NODE_B`, so the ESP32 firmware treated it as a local-only packet (`shouldForwardToLoRa` was false because `destinationNode == SIM_NODE_ID`). The physical Bluetooth transport target and the packet's logical destination were collapsed into a single field.

### Fix
Android app (`MainActivity.kt`):
- Added an optional `localBridgeNode: String? = null` parameter to `createBluetoothProtocolPacket()`.
- When `localBridgeNode` is provided, `hopPath` is built as `ANDROID_APP>localBridgeNode` while `destinationNode` remains the remote target.
- Updated both live-bridge call sites (main chat composer `onSend` and Simulation tab `onSendLoRaMessage`) to pass `localBridgeNode = localEsp32NodeId(connectedDevice)` alongside `destinationNode = peerNodeForConnectedEsp32(connectedDevice)`.

Result for Phone A connected to `PUP-MANET-NODE_A`:
- `destinationNode = NODE_B`
- `localBridgeNode = NODE_A`
- `hopPath = ANDROID_APP>NODE_A`
- ESP32 (Node A) sees `destinationNode != SIM_NODE_ID`, so `shouldForwardToLoRa = true`, triggers `[LORA_TX]`, and Node B receives `[LORA_RX]`.

Simulation fallback, BT-MANET-1.0 JSON format, and ESP32 firmware behavior are unchanged.

### Expected Flow After Fix
```
Phone A → BT SPP → Node A → [BT_RX] → LoRa forward → [LORA_TX]
                                            ↓
Phone B ← BT SPP ← Node B ← [BT_TX] ← [LORA_RX]
```

### Files Changed
- `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`
  - `createBluetoothProtocolPacket()` signature and body
  - Main chat composer `onSend` live-bridge block
  - Simulation tab `onSendLoRaMessage` live-bridge block

## STEP 035 Outcome Addendum (2026-05-22)

The physical validation that followed the STEP 034 routing/debug fixes is now recorded as STEP 035 - Real End-to-End LoRa Message Delivery.

Confirmed flow:

```text
Phone A -> Bluetooth SPP -> NODE_A ESP32 -> LoRa RF -> NODE_B ESP32 -> Bluetooth SPP -> Phone B
```

Evidence summary:
- Phone A connected to `PUP-MANET-NODE_A` with MAC shown.
- Phone B connected to `PUP-MANET-NODE_B` with MAC shown.
- NODE_A log shows `BT_RX` and LoRa forwarding.
- NODE_B log shows `LORA_RX` and `BT_TX`.
- Phone B displays `Incoming: Emergency message from Phone A`.
- Status includes `RECEIVED_OVER_LORA`.
- ACK path returns `FORWARDED_OVER_LORA`.

Next incomplete step:
- STEP 036 - Real Chat UI Integration.
