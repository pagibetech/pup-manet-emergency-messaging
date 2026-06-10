# ANDROID STEP 013 - Real ESP32 Transport Preparation

## What Was Implemented

- Added a simulation-only hardware mode setting in the Android app.
- Added an ESP32 transport preparation panel in the Route tab.
- Added placeholder ESP32 bridge configuration fields:
  - ESP32 device name
  - Connection type: Bluetooth or WiFi
  - Packet format version
  - Connection status
  - Last handshake time
- Added a simulated HELLO / ESP32_ACK handshake workflow.
- Added an outgoing MANET packet preview using the existing packet abstraction.
- Added a hardware readiness checklist for future ESP32 integration.

## Packet Preview Fields

The preview displays:

- packetId
- source
- destination
- transport
- payload
- hopPath
- status

## Simulation-Only Behavior

- Hardware mode remains disabled.
- Bluetooth is the primary planned Android-to-ESP32 connection path.
- WiFi remains a future optional placeholder only.
- The field architecture is Android Phone to ESP32 over Bluetooth, then ESP32 to other ESP32 nodes over LoRa.
- The HELLO / ESP32_ACK handshake is simulated locally.
- No Android Bluetooth permissions were added.
- No BLE, Classic Bluetooth, WiFi backend, ESP32, Raspberry Pi, or LoRa hardware logic was added.

## Files Updated

- `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`
- `android-chat-app/README.md`
- `docs/codex-task-logs/ANDROID_STEP_013_ESP32_TRANSPORT_PREPARATION.md`

## Validation Notes

- The app remains simulation-only.
- Existing packet abstraction, queue engine, routing engine, transport placeholders, Bluetooth placeholder, validation checklist, event log, and message sending workflow are preserved.
- Hardware readiness remains a preparation checklist until a later workbook step enables real hardware integration.

## Next Step

Continue with the next workbook-defined Android task. Real ESP32 Bluetooth, WiFi, or LoRa transport should only be added when the workbook explicitly requests hardware integration.
