# ESP32 Multi-Node Simulation

This PlatformIO project contains the ESP32 simulation node for the PUP MANET Emergency Messaging System. It uses the Arduino framework and Serial input/output to simulate MANET packet handling between `NODE_A`, `NODE_B`, and `NODE_C`.

No LoRa, Bluetooth, WiFi, GSM, Android, or real hardware communication logic is included in this step.

Step 017 adds a firmware-side parser for the future Android-to-ESP32 Bluetooth packet protocol. The parser is tested through Serial Monitor only; it does not enable Bluetooth or LoRa hardware communication yet.

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
```

Build a specific node:

```sh
pio run -e node_a
pio run -e node_b
pio run -e node_c
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
```

Expected parser behavior:

- `PARSE_HELLO` validates a future Android HELLO packet.
- `PARSE_MESSAGE` validates a future Android MESSAGE packet intended for ESP32-to-LoRa forwarding.
- `PARSE_STATUS` validates a future Android STATUS request.
- `PARSE_BAD_PACKET` rejects an invalid protocol version, packet type, and checksum placeholder.
- `PRINT_PROTOCOL` prints the current protocol version, supported packet types, required fields, checksum placeholder, and a sample MESSAGE packet.

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

## Current Limitations

- Bluetooth is not enabled yet.
- LoRa packet sending is not enabled yet.
- Parser tests use Serial Monitor only.
- Parsed `MESSAGE` packets are not forwarded to LoRa in this step.
- The checksum is a placeholder only; no CRC is implemented yet.
