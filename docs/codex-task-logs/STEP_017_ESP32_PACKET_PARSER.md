# STEP 017 - ESP32 Firmware Packet Parser

## What Was Implemented

- Added ESP32 firmware constants for the `BT-MANET-1.0` protocol.
- Added supported packet types:
  - HELLO
  - ACK
  - MESSAGE
  - ROUTE_DISCOVERY
  - ROUTE_REPLY
  - STATUS
  - ERROR
- Added `ProtocolPacket` model fields:
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
  - checksum
- Added packet parser validation for:
  - Required fields
  - Protocol version
  - Supported packet type
  - Checksum placeholder
- Added serializer for converting a parsed packet back to a protocol string.
- Added Serial Monitor parser test commands.

## Serial Monitor Commands

```text
PARSE_HELLO
PARSE_MESSAGE
PARSE_STATUS
PARSE_BAD_PACKET
PRINT_PROTOCOL
```

## Expected Behavior

- `PARSE_HELLO` should parse and validate a future Android HELLO packet.
- `PARSE_MESSAGE` should parse and validate a future Android MESSAGE packet.
- `PARSE_STATUS` should parse and validate a future Android STATUS packet.
- `PARSE_BAD_PACKET` should reject a bad packet and print an error reason.
- `PRINT_PROTOCOL` should print the protocol version, supported packet types, required fields, checksum placeholder, and a sample message packet.

## Simulation-Only Scope

- No Bluetooth was added.
- No real LoRa packet sending was added.
- No Raspberry Pi code was modified.
- Existing ESP32 Serial Monitor MANET simulation commands remain available.

## Files Updated

- `esp32-node-platformio/src/main.cpp`
- `esp32-node-platformio/README.md`
- `docs/codex-task-logs/STEP_017_ESP32_PACKET_PARSER.md`

## Next Step

Continue with the next workbook-defined task. The parser is ready for a future ESP32 Bluetooth service step, but no real Android-to-ESP32 Bluetooth communication is active yet.
