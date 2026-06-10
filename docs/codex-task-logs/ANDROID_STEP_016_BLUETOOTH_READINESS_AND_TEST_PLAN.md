# ANDROID STEP 016 - Bluetooth Readiness, Protocol Cleanup, and Hardware Test Plan

## What Was Implemented

- Cleaned up the Route tab Bluetooth packet protocol preview.
- Made outgoing MESSAGE and incoming ACK packet previews compact.
- Made the Route tab serialization preview shorter and easier to scan.
- Added detailed Bluetooth readiness and protocol test planning content to the Logs tab.
- Added hardware architecture note:

```text
Android Phone <-> Bluetooth <-> ESP32 <-> LoRa <-> ESP32 <-> Bluetooth <-> Android Phone
```

## Bluetooth Readiness Checklist

- Android Bluetooth architecture ready
- Packet protocol defined
- ESP32 firmware packet parser pending
- ESP32 Bluetooth service pending
- SX1278 LoRa wiring pending
- LoRa send/receive test pending
- Android real Bluetooth permission pending
- Android real Bluetooth socket/service pending

## ESP32 Firmware Requirements

- ESP32 must expose Bluetooth connection
- ESP32 must accept HELLO
- ESP32 must return ESP32_ACK
- ESP32 must accept MESSAGE packet
- ESP32 must forward MESSAGE over LoRa
- ESP32 must report STATUS

## Protocol Test Cases

- HELLO handshake test
- MESSAGE packet send test
- ACK receive test
- Invalid packet test
- Route discovery packet test
- Status packet test

## Next-Stage Implementation Plan

- Step 017: ESP32 firmware packet parser
- Step 018: ESP32 Bluetooth service
- Step 019: Android real Bluetooth permissions
- Step 020: Android Bluetooth connection implementation
- Step 021: Android-to-ESP32 live packet test
- Step 022: ESP32-to-ESP32 LoRa packet test

## Simulation-Only Behavior

- No Android Bluetooth permissions were added.
- No BLE APIs were added.
- No Classic Bluetooth APIs were added.
- No socket code was added.
- No ESP32 code was modified.
- No Raspberry Pi code was modified.
- Existing message queue, routing engine, transport bridge, validation checklist, Bluetooth simulated lifecycle, and protocol model were preserved.

## Files Updated

- `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`
- `android-chat-app/README.md`
- `docs/codex-task-logs/ANDROID_STEP_016_BLUETOOTH_READINESS_AND_TEST_PLAN.md`

## Validation Notes

- Route tab now keeps the protocol preview compact.
- Logs tab contains detailed protocol readiness, firmware requirements, test cases, and next-stage implementation plan.
- Real Bluetooth and hardware communication remain disabled until future workbook steps explicitly request them.
