# STEP 002 - ESP32 Simulation Node

## What Was Implemented

- Created a PlatformIO ESP32 Arduino project in `esp32-node-platformio/`.
- Added `esp32-node-platformio/platformio.ini` with configurable simulation node IDs.
- Added `esp32-node-platformio/src/main.cpp`.
- Implemented Serial-only simulation mode messaging.
- Implemented configurable node ID and default destination ID.
- Implemented message ID generation for locally entered messages.
- Implemented store-and-forward behavior.
- Implemented duplicate message prevention with an in-memory `msg_id` cache.
- Implemented Serial logs for received, forwarded, delivered, duplicate, and invalid messages.
- Updated `esp32-node-platformio/README.md` with simulation testing instructions.

No LoRa, Bluetooth, WiFi, GSM, or real hardware communication logic was added.

## Message Format

Messages are represented as JSON:

```json
{
  "msg_id": "string",
  "src": "node_id",
  "dest": "node_id",
  "hop": 0,
  "payload": "text",
  "timestamp": "unix"
}
```

In this simulation step, `timestamp` is represented as seconds since node boot because no network clock or hardware time source is used.

## Simulation Behavior

- Plain Serial text creates a new outbound message to `DEFAULT_DEST_ID`.
- `send <DEST_NODE_ID> <message>` creates a new outbound message to a specific destination.
- JSON pasted into Serial Monitor is treated as an incoming simulated network message.
- If `dest` matches `SIM_NODE_ID`, the message is delivered.
- If `dest` does not match `SIM_NODE_ID`, the node increments `hop` and forwards the JSON to Serial.
- Repeated `msg_id` values are ignored to prevent duplicate forwarding.

## Next Steps

- Run `pio run` in `esp32-node-platformio/` when PlatformIO and ESP32 packages are available.
- Continue to the next workbook-defined step only.
- Keep simulation mode working before any future hardware interface is added.
- Do not add LoRa, Bluetooth, WiFi, or GSM code until a later workbook step explicitly requests it.

