# STEP 022 - ESP32 to ESP32 LoRa Live Test

## Workbook Context

- Latest completed step before this task: `STEP 021 - Android to ESP32 Live Packet Test`
- Current step: `STEP 022 - ESP32 to ESP32 LoRa Live Test`
- Next step after this task: `STEP 023 - End-to-End Android to LoRa to Android Messaging`

## What Was Implemented

- Added LoRa-enabled PlatformIO environments:
  - `node_a_lora`
  - `node_b_lora`
- Added optional SX1278 LoRa firmware path behind `ENABLE_LORA=1`.
- Preserved existing simulation builds:
  - `node_a`
  - `node_b`
  - `node_c`
- Added default SX1278 pin configuration:
  - SS / CS: GPIO 5
  - SCK: GPIO 18
  - MISO: GPIO 19
  - MOSI: GPIO 23
  - RST: GPIO 14
  - DIO0: GPIO 26
- Added default LoRa radio settings:
  - Frequency: `433E6`
  - Sync word: `0x12`
  - TX power: `17`
- Added Serial Monitor commands:
  - `LORA_STATUS`
  - `LORA_PING`
  - `LORA_SEND <DEST> <MESSAGE>`
- Added LoRa receive polling and logging:
  - `[LORA_SERVICE]`
  - `[LORA_STATUS]`
  - `[LORA_TX]`
  - `[LORA_RX]`

## User Workflow

1. Wire two ESP32 boards to SX1278 Ra-02 modules using the documented pin map.
2. Upload `node_a_lora` to ESP32 A.
3. Upload `node_b_lora` to ESP32 B.
4. Open Serial Monitor for both boards at `115200`.
5. On ESP32 A, run:

```text
LORA_STATUS
LORA_SEND NODE_B Hello from NODE_A
```

6. Confirm ESP32 B prints `[LORA_RX]`, `[RECEIVED]`, `[DELIVERED]`, and `[DELIVERY]`.

## Scope Preserved

- Android code was not modified.
- Raspberry Pi code was not modified.
- Android-to-LoRa chat delivery was not implemented.
- Multi-hop LoRa routing was not expanded beyond the existing simulation packet model.
- Simulation mode remains available by default.

## Files Updated

- `esp32-node-platformio/platformio.ini`
- `esp32-node-platformio/src/main.cpp`
- `esp32-node-platformio/README.md`
- `docs/codex-task-logs/STEP_022_ESP32_TO_ESP32_LORA_LIVE_TEST.md`

## Validation

Local PlatformIO simulation build:

```text
/Users/macbookm1max321tb/.platformio/penv/bin/pio run -e node_a
```

Result:

- `node_a` build succeeded.

Local PlatformIO LoRa builds:

```text
/Users/macbookm1max321tb/.platformio/penv/bin/pio run -e node_a_lora -e node_b_lora
```

Result:

- `node_a_lora` build succeeded.
- `node_b_lora` build succeeded.
- SX1278 LoRa library `LoRa@0.8.0` installed and compiled.
- Physical packet exchange still requires two ESP32 boards and two SX1278 modules.

## Next Step

Continue with `STEP 023 - End-to-End Android to LoRa to Android Messaging`. Do not implement Raspberry Pi gateway integration until the workbook reaches that step.
