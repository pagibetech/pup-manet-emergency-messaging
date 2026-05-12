# ESP32 Multi-Node Simulation

This PlatformIO project contains the ESP32 node firmware for the PUP MANET Emergency Messaging System. It uses the Arduino framework, Serial input/output, an ESP32 Classic Bluetooth SPP service, and optional SX1278 LoRa live-test environments.

WiFi, GSM, Android-side socket code, Raspberry Pi gateway logic, and end-to-end Android-to-LoRa chat delivery are outside this firmware step.

Step 017 adds a firmware-side parser for the future Android-to-ESP32 Bluetooth packet protocol. The parser is tested through Serial Monitor only; it does not enable Bluetooth or LoRa hardware communication yet.

Step 018 enables the ESP32 Bluetooth service for the Android-to-ESP32 transport path. The service accepts newline-delimited `BT-MANET-1.0` JSON packets over Classic Bluetooth SPP, validates them with the Step 017 parser, and returns protocol `ACK`, `STATUS`, or `ERROR` packets. LoRa forwarding remains a simulation placeholder.

Step 022 adds controlled SX1278 LoRa live-test environments for ESP32-to-ESP32 packet exchange. Simulation environments remain available and unchanged.

## Build Environments

`platformio.ini` defines one environment per simulated node:

```ini
[env:node_a]
build_flags =
  -DSIM_NODE_ID=\"NODE_A\"
  -DDEFAULT_DEST_ID=\"NODE_B\"

[env:node_b]
build_flags =
  -DSIM_NODE_ID=\"NODE_B\"
  -DDEFAULT_DEST_ID=\"NODE_C\"

[env:node_c]
build_flags =
  -DSIM_NODE_ID=\"NODE_C\"
  -DDEFAULT_DEST_ID=\"NODE_A\"

[env:node_a_lora]
build_flags =
  -DSIM_NODE_ID=\"NODE_A\"
  -DDEFAULT_DEST_ID=\"NODE_B\"
  -DENABLE_LORA=1

[env:node_b_lora]
build_flags =
  -DSIM_NODE_ID=\"NODE_B\"
  -DDEFAULT_DEST_ID=\"NODE_A\"
  -DENABLE_LORA=1
```

Build a specific node:

```sh
pio run -e node_a
pio run -e node_b
pio run -e node_c
```

Build live LoRa test firmware:

```sh
pio run -e node_a_lora
pio run -e node_b_lora
```

Upload live LoRa test firmware to two ESP32 boards:

```sh
pio run -e node_a_lora --target upload
pio run -e node_b_lora --target upload
```

## Message Format

Messages use this JSON format:

```json
{
  "msg_id": "NODE_A-12345-1",
  "src": "NODE_A",
  "dest": "NODE_C",
  "hop": 0,
  "payload": "Emergency test message",
  "timestamp": "123"
}
```

The timestamp is a simulation timestamp based on seconds since the node booted.

## Bluetooth Packet Protocol Parser

The firmware includes a parser and serializer for the Android-defined `BT-MANET-1.0` protocol.

Supported packet types:

- `HELLO`
- `ACK`
- `MESSAGE`
- `ROUTE_DISCOVERY`
- `ROUTE_REPLY`
- `STATUS`
- `ERROR`

Protocol packet fields:

- `protocolVersion`
- `packetType`
- `packetId`
- `sourceNode`
- `destinationNode`
- `payload`
- `hopPath`
- `retryCount`
- `timestamp`
- `status`
- `checksum`

The checksum remains a placeholder and must equal:

```text
checksum pending / simulated
```

The parser validates:

- Required fields
- Protocol version
- Supported packet type
- Checksum placeholder

Valid packets are printed with parsed fields and a serialized round-trip output. Invalid packets print a validation failure and error reason.

## ESP32 Bluetooth Service

Each node starts a Classic Bluetooth SPP service at boot:

```text
PUP-MANET-NODE_A
PUP-MANET-NODE_B
PUP-MANET-NODE_C
```

Use the service for the official Android-to-ESP32 path:

```text
Android Phone <-> Bluetooth <-> ESP32 <-> LoRa placeholder
```

USB Serial remains a firmware debug/test path only. It is not part of Android scope.

Bluetooth input expects one newline-delimited `BT-MANET-1.0` JSON packet per line. Supported Bluetooth behavior in Step 018:

- `HELLO` returns an `ACK`.
- `STATUS` returns a `STATUS` response with node state, Bluetooth service state, client state, and supported manual modes.
- `MESSAGE` validates the packet and returns an `ACK` with `QUEUED_FOR_SIMULATION`; LoRa forwarding is not started in Step 018.
- Invalid packets return an `ERROR`.

Manual routing mode placeholders are recognized when the `MESSAGE` payload contains `MODE=AUTO`, `MODE=LORA`, `MODE=WIFI`, or `MODE=GSM`. Unsupported modes return an `ERROR`.

## SX1278 LoRa Live Test

Step 022 enables a narrow ESP32-to-ESP32 LoRa live test path through `node_a_lora` and `node_b_lora`.

Default SX1278 Ra-02 wiring:

| SX1278 Pin | ESP32 Pin |
| --- | --- |
| NSS / CS | GPIO 5 |
| SCK | GPIO 18 |
| MISO | GPIO 19 |
| MOSI | GPIO 23 |
| RST | GPIO 14 |
| DIO0 | GPIO 26 |
| 3.3V | 3.3V |
| GND | GND |

Default radio settings:

- Frequency: `433E6`
- Sync word: `0x12`
- TX power: `17`

Serial Monitor LoRa commands:

```text
LORA_STATUS
LORA_PING
LORA_SEND <DEST> <MESSAGE>
```

Two-node test:

1. Upload `node_a_lora` to ESP32 A.
2. Upload `node_b_lora` to ESP32 B.
3. Open Serial Monitor for both boards at `115200`.
4. On ESP32 A, run:

```text
LORA_STATUS
LORA_SEND NODE_B Hello from NODE_A
```

5. ESP32 B should print `[LORA_RX]`, then `[RECEIVED]`, `[DELIVERED]`, and `[DELIVERY]`.

This live test confirms ESP32-to-ESP32 LoRa packet exchange only. Android-to-LoRa chat delivery and multi-hop LoRa routing remain later workbook work.

## Neighbor Table

Each node starts with two simulated neighbors. A neighbor record includes:

- Neighbor node ID
- Simulated RSSI
- Last seen timestamp
- Online or offline state

Use `NEIGHBORS` in Serial Monitor to print the current table.

## Routing Behavior

- If `dest` matches this node ID, the node delivers the message.
- Otherwise, the node forwards to the online neighbor with the strongest simulated RSSI.
- Each forwarded packet increments `hop` by `1`.
- Packets are dropped if `hop` is already `5` or greater.
- Repeated `msg_id` values are dropped as duplicates.
- If the local node is offline or no online neighbor exists, the packet is dropped with a reason.

Serial output represents the simulated forwarding path. The forwarded JSON line can be copied into another node's Serial Monitor to continue the manual simulation.

## Serial Commands

```text
SEND <DEST> <MESSAGE>
STATUS
NEIGHBORS
OFFLINE
ONLINE
```

Additional simulation helpers are also supported:

```text
OFFLINE <NODE_ID>
ONLINE <NODE_ID>
```

These mark a simulated neighbor offline or online for routing tests.

Protocol parser test commands:

```text
PARSE_HELLO
PARSE_MESSAGE
PARSE_STATUS
PARSE_BAD_PACKET
PRINT_PROTOCOL
BT_STATUS
LORA_STATUS
LORA_PING
LORA_SEND NODE_B Hello from NODE_A
```

Expected parser behavior:

- `PARSE_HELLO` validates a future Android HELLO packet.
- `PARSE_MESSAGE` validates a future Android MESSAGE packet intended for ESP32-to-LoRa forwarding.
- `PARSE_STATUS` validates a future Android STATUS request.
- `PARSE_BAD_PACKET` rejects an invalid protocol version, packet type, and checksum placeholder.
- `PRINT_PROTOCOL` prints the current protocol version, supported packet types, required fields, checksum placeholder, and a sample MESSAGE packet.
- `BT_STATUS` prints the Classic Bluetooth SPP service name, service state, client state, protocol version, Android transport rule, LoRa placeholder, and manual mode placeholders.
- `LORA_STATUS` prints LoRa build state, radio readiness, frequency, sync word, pins, and counters.
- `LORA_PING` sends a JSON simulation packet to the default destination over LoRa when using a LoRa-enabled build.
- `LORA_SEND <DEST> <MESSAGE>` sends a JSON simulation packet to the requested destination over LoRa when using a LoRa-enabled build.

Commands are parsed before the plain-text fallback path. For example, typing `STATUS` prints node state and counters; it is not routed as a message payload.

`STATUS` prints:

- Current node ID
- Default destination
- Online or offline state
- Max hop count
- Duplicate cache count
- Message counter

Plain text that does not match a command is still accepted as a fallback and routed to `DEFAULT_DEST_ID`. Prefer `SEND <DEST> <MESSAGE>` for routing tests.

## How to Test with Serial Monitor

Build and upload a node:

```sh
pio run -e node_a
pio run -e node_a --target upload
```

Open the Serial Monitor:

```sh
pio device monitor -b 115200
```

Check current state:

```text
STATUS
NEIGHBORS
```

Send a message:

```text
SEND NODE_C Medical supplies needed
```

Paste a JSON message to simulate receiving from another node:

```json
{"msg_id":"NODE_X-1","src":"NODE_X","dest":"NODE_A","hop":0,"payload":"Hello NODE_A","timestamp":"1"}
```

Simulate failure and recovery:

```text
OFFLINE NODE_B
SEND NODE_C Route around unavailable neighbor
ONLINE NODE_B
```

Expected logs include:

- `[RECEIVED]` when the node accepts a new message.
- `[STATUS]` when the node prints current node ID, default destination, online/offline state, duplicate cache count, and message counter.
- `[NEIGHBORS]` when the node prints the simulated neighbor table.
- `[ROUTE]` when the node chooses the next hop.
- `[FORWARDED]` when the node forwards a message.
- `[DELIVERED]` and `[DELIVERY]` when the message reaches its destination.
- `[DROPPED]` when a packet is rejected because of duplicate ID, offline state, missing route, or max hop count.
- `[FALLBACK]` when non-command plain text is routed to the default destination.
- `[PROTOCOL_PARSE]`, `[PARSE_HELLO]`, `[PARSE_MESSAGE]`, `[PARSE_STATUS]`, or `[PARSE_BAD_PACKET]` when the protocol parser is tested.
- `[PROTOCOL]` when `PRINT_PROTOCOL` prints the firmware protocol specification.
- `[BT_SERVICE]` when the Bluetooth service starts or `BT_STATUS` is printed.
- `[BT_RX]` and `[BT_TX]` when packets are received from or returned to a Bluetooth client.
- `[LORA_SERVICE]` when the LoRa service starts or reports disabled/failure.
- `[LORA_STATUS]` when the LoRa status command is printed.
- `[LORA_TX]` when a LoRa packet is transmitted.
- `[LORA_RX]` when a LoRa packet is received, including RSSI and SNR.

## Current Limitations

- LoRa is enabled only in `node_a_lora` and `node_b_lora` builds.
- Android-to-LoRa chat delivery is not enabled in this firmware step.
- Parsed Bluetooth `MESSAGE` packets are acknowledged as queued for simulation only; they are not forwarded to real LoRa in Step 022.
- The checksum is a placeholder only; no CRC is implemented yet.
