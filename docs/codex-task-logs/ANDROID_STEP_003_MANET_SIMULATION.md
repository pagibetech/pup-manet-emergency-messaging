# ANDROID STEP 003 - Multi-node MANET Conversation Simulation

## What Was Created

- Added simulated MANET nodes:
  - Node Alpha
  - Node Bravo
  - Node Charlie
  - Node Delta
  - Gateway Node
- Added per-node simulation profiles with:
  - Node name
  - Battery percentage
  - RSSI
  - SNR
  - Hop count
  - Gateway proximity
  - LoRa, WiFi, GSM, and Satellite route availability
- Added node selector UI for:
  - Current local node
  - Target destination node
- Added a compact selected-node summary panel.
- Implemented simulated store-and-forward routing through intermediate nodes.
- Added full route path display per message, such as `Node Alpha -> Node Bravo -> Node Charlie -> Gateway Node`.
- Implemented adaptive failover priority:
  - LoRa first
  - WiFi second
  - GSM third
  - Simulated Satellite fourth
- Added message states:
  - Queued
  - Relayed
  - Delivered
  - Failed
- Added route detail display per message:
  - Route type used
  - Full node path
  - Hop count
  - RSSI
  - SNR
  - Battery percentage
  - Gateway proximity
  - Satellite status

## Preserved

- Adamson University branding.
- Existing Step 002 global simulation controls.
- Existing scrollable content layout with fixed bottom message composer.
- Existing local-only simulation behavior.

## Current Limitations

- Simulation mode only.
- Node profiles and metrics are deterministic placeholders.
- No real Bluetooth, LoRa, GSM, WiFi backend, ESP32, Raspberry Pi, or external backend code was added.
- Messages remain local in memory and reset when the app restarts.

## How to Test

1. Open `android-chat-app/` in Android Studio.
2. Run the app on an emulator or phone.
3. Select a local node and a target destination node.
4. Toggle LoRa, WiFi, GSM, or Satellite link availability.
5. Type a message and press Send.
6. Confirm the message card shows route type, node path, state, hop count, RSSI, SNR, battery, gateway proximity, and satellite status.

## Next Step

Continue with the next workbook-defined Android task. Keep the multi-node simulation stable before adding any real communication transport.

