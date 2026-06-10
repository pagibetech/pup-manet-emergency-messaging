# ANDROID STEP 015 - ESP32 Bluetooth Packet Protocol Specification

## What Was Implemented

- Added a Bluetooth packet protocol model for future Android-to-ESP32 communication.
- Added supported packet types:
  - HELLO
  - ACK
  - MESSAGE
  - ROUTE_DISCOVERY
  - ROUTE_REPLY
  - STATUS
  - ERROR
- Added a protocol preview panel in the Route tab.
- Added sample outgoing packet preview:
  - Android sends MESSAGE to ESP32.
  - ESP32 forwards the message to LoRa in a future hardware step.
- Added sample incoming packet preview:
  - ESP32 sends RECEIVED/ACK back to Android.
- Added JSON-like packet serialization placeholder.
- Added JSON-like packet deserialization placeholder.
- Added checksum placeholder text: `checksum pending / simulated`.
- Added protocol validation status:
  - Version valid
  - Required fields present
  - Packet type supported
  - Checksum placeholder valid

## Protocol Packet Fields

- protocolVersion
- packetType
- packetId
- sourceNode
- destinationNode
- payload
- hopPath
- retryCount
- timestamp
- status
- checksumPlaceholder

## Simulation-Only Behavior

- Protocol packets are local data models only.
- Serialization and deserialization are placeholders for future Bluetooth transport.
- No real checksum or CRC is implemented.
- No Android Bluetooth permissions were added.
- No BLE APIs were added.
- No Classic Bluetooth APIs were added.
- No socket code was added.
- No ESP32 code was modified.
- No Raspberry Pi code was modified.

## Files Updated

- `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`
- `android-chat-app/README.md`
- `docs/codex-task-logs/ANDROID_STEP_015_BLUETOOTH_PACKET_PROTOCOL.md`

## Validation Notes

- Existing simulation messaging remains unchanged.
- Bluetooth remains the official Android-to-ESP32 path.
- Android scope remains Bluetooth-first for the future ESP32 bridge.
- The protocol preview prepares the Android side for a future workbook step that will add real Bluetooth communication.

## Next Step

Continue with the next workbook-defined Android task. Real Bluetooth permissions, Bluetooth APIs, socket communication, ESP32 firmware changes, or Raspberry Pi changes should only be added when explicitly requested by the workbook.
