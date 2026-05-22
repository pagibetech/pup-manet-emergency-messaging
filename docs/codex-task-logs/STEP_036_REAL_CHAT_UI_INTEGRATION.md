# STEP 036 - Real Chat UI Integration

Date: 2026-05-22

## Result

Completed / PASS.

## Scope

Integrate real received LoRa messages into the Chat tab conversation UI without breaking existing behavior.

## Changes

### Android app (`android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`)

1. **Validation fix — Chat Send always uses real Bluetooth SPP when connected**
   - **Problem**: Initial implementation used `val isLiveBridgeMode = realBluetoothSocketState.connected && decision.route == RouteLabel.Lora`. The simulation routing engine (`decideRoute()`) could return WiFi/GSM failover even when a real Bluetooth socket was connected, because it checks `networkState.loraAvailable` which is toggled by the simulation loop. This caused Chat Send to show "WiFi failover" cards and never reach the ESP32.
   - **Fix**: Changed condition to `if (realBluetoothSocketState.connected)`. When the real Bluetooth socket is connected, Chat Send **unconditionally** uses the live SPP transport, completely bypassing the simulation routing engine. The simulation fallback (`else` branch) only runs when no real socket is connected.
   - **Result**: Chat Send now produces `[BT_RX]` / `[LORA_TX]` on the connected NODE, matching the Sim tab "Send Msg" behavior.

2. **Auto-receive loop for real Bluetooth SPP**
   - Added `LaunchedEffect(realBluetoothSocketState.connected)` that polls `androidBluetoothSocketClient.readAvailableLine()` every 300 ms while connected.
   - When a line contains `"packetType":"MESSAGE"`, it is deserialized, deduplicated via `autoReceivedPacketIds`, and inserted into the `messages` list as a `ChatMessage`.
   - This makes incoming LoRa messages appear in the Chat tab automatically without requiring the user to press Check In.

3. **Chat message UI indicator**
   - `note` field shows: `Received via LoRa | Source node: <sourceNode> | Time: <HH:mm:ss>`.
   - `deliveryProgress` field shows: `Received via LoRa`.
   - `route` is `RouteLabel.Lora`.
   - `status` is `MessageStatus.Delivered`.

4. **Logging**
   - `eventLog` receives `[CHAT_RX_LORA] packetId=... src=... text=...`.
   - `eventLog` receives `[ANDROID_RX] packetId=... src=... text=...`.

5. **Manual Check In preserved**
   - The existing `onReadIncomingPacket` callback (Check In button) still works and shares the same deduplication set so auto-received messages are not duplicated if the user also presses Check In.

6. **Simulation fallback preserved**
   - When `realBluetoothSocketState.connected == false`, the `LaunchedEffect` exits immediately.
   - Existing `SimulationTransport` and queue/retry/delivery loop remain unchanged.

## Acceptance Test

- Phone A sends "STEP036 CHAT TEST FROM PHONE A" via Chat composer.
- Node A logs `[BT_RX]` and `[LORA_TX]`.
- Node B logs `[LORA_RX]` and `[BT_TX]`.
- Phone B Chat tab automatically displays the message.
- Sim tab still shows diagnostics and the Incoming status.

## Files Changed

- `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`
  - Added `autoReceivedPacketIds` state.
  - Added `LaunchedEffect` auto-receive coroutine.
  - Updated manual `onReadIncomingPacket` to use deduplication and new UI note format.
  - Fixed Chat Send to bypass simulation routing when real Bluetooth socket is connected.

## Next Step

Proceed to STEP 037 - Real Chat UI Polish or continue with additional hardware integration steps as planned.
