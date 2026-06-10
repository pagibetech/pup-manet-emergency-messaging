# ESP32 Simulation Node

This PlatformIO project contains the Step 002 ESP32 simulation node for the PUP MANET Emergency Messaging System. It uses the Arduino framework and Serial input/output to simulate network traffic.

No LoRa, Bluetooth, WiFi, GSM, or real hardware communication logic is included in this step.

## Simulation Mode

The node has configurable build-time IDs in `platformio.ini`:

```ini
build_flags =
  -DSIM_NODE_ID=\"NODE_A\"
  -DDEFAULT_DEST_ID=\"NODE_B\"
```

Each message uses this JSON format:

```json
{
  "msg_id": "NODE_A-12345-1",
  "src": "NODE_A",
  "dest": "NODE_B",
  "hop": 0,
  "payload": "Emergency test message",
  "timestamp": "123"
}
```

The timestamp is a simulation timestamp based on seconds since the node booted.

## Store and Forward Behavior

- Messages addressed to this node are logged as delivered.
- Messages addressed to another node are forwarded.
- Forwarded messages have `hop` increased by `1`.
- Duplicate `msg_id` values are ignored using an in-memory duplicate cache.
- Serial output represents the simulated network transmit path.

## How to Test with Serial Monitor

Build and upload the project:

```sh
pio run
pio run --target upload
```

Open the Serial Monitor:

```sh
pio device monitor -b 115200
```

Send a plain text message to the default destination:

```text
Emergency test message
```

Send a message to a specific destination:

```text
send NODE_C Medical supplies needed
```

Paste a JSON message to simulate receiving from another node:

```json
{"msg_id":"NODE_X-1","src":"NODE_X","dest":"NODE_A","hop":0,"payload":"Hello NODE_A","timestamp":"1"}
```

Expected logs include:

- `[RECEIVED]` when the node accepts a new message.
- `[FORWARDED]` when the node forwards a message for another destination.
- `[DELIVERED]` when the node receives a message addressed to its own node ID.
- `[DUPLICATE]` when a repeated `msg_id` is ignored.

