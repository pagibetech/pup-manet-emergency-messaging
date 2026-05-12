# STEP 018 - ESP32 Bluetooth Service

## Workbook Context

- Latest completed step before this task: `STEP 017 - ESP32 Firmware Packet Parser`
- Current step: `STEP 018 - ESP32 Bluetooth Service`
- Next step after this task: `STEP 019 - Android Bluetooth Permissions`

## What Was Implemented

- Enabled an ESP32 Classic Bluetooth SPP service at startup.
- Added per-node Bluetooth service names:
  - `PUP-MANET-NODE_A`
  - `PUP-MANET-NODE_B`
  - `PUP-MANET-NODE_C`
- Added Bluetooth receive buffering for newline-delimited `BT-MANET-1.0` JSON packets.
- Reused the Step 017 packet parser for Bluetooth input validation.
- Added Bluetooth protocol responses:
  - `ACK` for valid `HELLO` and accepted packet types.
  - `STATUS` response for valid `STATUS` requests.
  - `ACK` with `QUEUED_FOR_SIMULATION` for valid `MESSAGE` packets.
  - `ERROR` for invalid packets or unsupported manual mode placeholders.
- Added manual mode placeholders:
  - `AUTO`
  - `LORA`
  - `WIFI`
  - `GSM`
- Added Serial Monitor diagnostics:
  - `BT_STATUS`
  - `[BT_SERVICE]`
  - `[BT_RX]`
  - `[BT_TX]`

## Scope Preserved

- Android code was not modified.
- Android Bluetooth permissions were not implemented.
- Android Bluetooth socket code was not implemented.
- USB Serial remains firmware debug/test only and is not part of Android scope.
- LoRa forwarding remains a simulation placeholder.
- Raspberry Pi code was not modified.

## Files Updated

- `esp32-node-platformio/src/main.cpp`
- `esp32-node-platformio/README.md`
- `docs/codex-task-logs/STEP_018_ESP32_BLUETOOTH_SERVICE.md`

## Validation

Local PlatformIO build:

```text
/Users/macbookm1max321tb/.platformio/penv/bin/pio run
```

Result:

- `node_a` built successfully.

Additional environment build:

```text
/Users/macbookm1max321tb/.platformio/penv/bin/pio run -e node_b -e node_c
```

Result:

- `node_b` built successfully.
- `node_c` built successfully.

Runtime validation still requires ESP32 hardware:

- Firmware should start `PUP-MANET-<NODE_ID>` as the Bluetooth service name.
- `BT_STATUS` should print service name, service state, client state, protocol version, transport rule, LoRa placeholder, and manual mode placeholders.

## Next Step

Continue with `STEP 019 - Android Bluetooth Permissions`. Do not implement Android Bluetooth socket connection until `STEP 020`.
