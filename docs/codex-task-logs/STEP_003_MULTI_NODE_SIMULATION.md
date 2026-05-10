# STEP 003 - Multi-Node ESP32 Simulation

## What Was Implemented

- Updated the ESP32 PlatformIO simulation project for multi-node MANET routing.
- Added PlatformIO environments for `NODE_A`, `NODE_B`, and `NODE_C`.
- Added a simulated neighbor table with node ID, RSSI, last seen timestamp, and online state.
- Added routing logic that delivers local messages or forwards to the online neighbor with the strongest RSSI.
- Added max hop enforcement with `MAX_HOP_COUNT = 5`.
- Preserved duplicate suppression using processed `msg_id` values.
- Added simulated local and neighbor offline/online states.
- Added Serial commands: `SEND <DEST> <MESSAGE>`, `STATUS`, `NEIGHBORS`, `OFFLINE`, and `ONLINE`.
- Added optional helper commands: `OFFLINE <NODE_ID>` and `ONLINE <NODE_ID>` for neighbor failure testing.
- Added routing, forwarding path, delivery, and dropped-packet reason logs.
- Updated `esp32-node-platformio/README.md`.

No LoRa, Bluetooth, WiFi, GSM, Android, or real hardware communication logic was added.

## Routing Behavior

- If `dest` equals `SIM_NODE_ID`, the node logs delivery.
- If `dest` is another node, the node chooses the online neighbor with the best RSSI.
- Forwarding increments `hop`.
- Packets with `hop >= 5` are dropped.
- Packets already seen by `msg_id` are dropped as duplicates.
- Packets are dropped if the local node is offline or no online neighbor is available.

## Serial Command Summary

```text
SEND <DEST> <MESSAGE>
STATUS
NEIGHBORS
OFFLINE
ONLINE
OFFLINE <NODE_ID>
ONLINE <NODE_ID>
```

## Message Format

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

In this simulation, `timestamp` remains seconds since node boot.

## Validation Notes

- `pio run` should be run inside `esp32-node-platformio/` when PlatformIO is installed.
- The current local machine previously did not have `pio` installed, so build validation may require a PlatformIO-ready environment.

## Next Steps

- Continue with the next workbook-defined step only.
- Keep this Serial-only simulation stable before adding future packet abstractions.
- Do not add LoRa, Bluetooth, WiFi, GSM, or Android app logic until a later workbook step explicitly requires it.

