# ANDROID STEP 002 - Local Simulation Engine

## What Was Created

- Added a local simulation engine inside `android-chat-app/`.
- Added simulated network availability state for:
  - LoRa
  - WiFi
  - GSM
  - Simulated satellite link
- Added UI controls to toggle each simulated route up or down.
- Updated the status panel to show simulated up/down state for LoRa, WiFi, GSM, and satellite link.
- Reworked the UI layout so Network, Status, Simulation Controls, Metrics, and Messages live in a scrollable content area.
- Moved the message text input and Send button into a fixed bottom composer so sending remains available on small screens.
- Made the simulation controls more compact for older and low-end phones.
- Added simulated metrics:
  - RSSI
  - SNR
  - Hop count
  - Battery level
  - Gateway proximity
  - Simulated satellite status
- Added route decision logic:
  - Auto mode scores available routes using RSSI, SNR, hop count, and battery level, then selects the strongest available route.
  - Manual LoRa, WiFi, and GSM modes use the selected route when available.
  - Manual unavailable routes create `Pending` or `Failed` messages instead of silently rerouting.
- Added message statuses:
  - Sent
  - Delivered
  - Pending
  - Failed
- Added visible per-message route labels:
  - LoRa
  - WiFi
  - GSM
  - Simulated Satellite
  - No route
- Updated `android-chat-app/README.md`.

No Bluetooth, real LoRa, WiFi backend, GSM sending, ESP32 integration, Raspberry Pi integration, or backend implementation was added.

## How to Test

1. Open `android-chat-app/` in Android Studio.
2. Run the `app` configuration on an emulator or connected phone.
3. Select Auto, LoRa, WiFi, or GSM.
4. Toggle LoRa, WiFi, GSM, or satellite link availability.
5. Type a message and press Send.
6. Confirm the message appears in the local chat list with route, status, and simulated metrics.

## Current Limitations

- Simulation mode only.
- Metrics are deterministic local placeholders.
- Messages are kept in memory only.
- No real transport is used.
- No Android-to-ESP32, Raspberry Pi, Bluetooth, WiFi backend, GSM, or LoRa implementation exists yet.

## Next Step

Continue with the next workbook-defined Android task. Keep this local simulation engine stable before adding real communication transports.
