# STEP 023 - End-to-End Android to LoRa to Android Messaging

## Workbook Context

- Latest completed step before this task: `STEP 022 - ESP32 to ESP32 LoRa Live Test`
- Current step: `STEP 023 - End-to-End Android to LoRa to Android Messaging`
- Next step after this task: continue only with the next workbook step.

## Expected Output

Create a narrow hardware test path:

```text
Android Phone A <-> Bluetooth <-> NODE_A <-> LoRa <-> NODE_B <-> Bluetooth <-> Android Phone B
```

## What Was Implemented

- Added an Android **Send LoRa** action in the real Bluetooth socket panel.
- Added an Android **Check In** action so the receiving phone can read one incoming bridged LoRa packet on demand.
- Added Android destination selection for the peer ESP32 node:
  - Connected to `PUP-MANET-NODE_A` sends to `NODE_B`.
  - Connected to `PUP-MANET-NODE_B` sends to `NODE_A`.
- Updated ESP32 Bluetooth `MESSAGE` handling:
  - `MODE=LORA` or `MODE=AUTO` can forward to LoRa when the destination is another node.
  - Bluetooth protocol JSON is converted to a compact `BT1|...` LoRa relay frame before transmit to avoid SX1278 packet truncation.
  - ACK payload reports `FORWARDED_OVER_LORA` when transmit succeeds.
- Updated ESP32 LoRa receive handling:
  - Existing Step 022 simulation JSON packets remain supported.
  - `BT-MANET-1.0` protocol JSON packets are validated.
  - Compact `BT1|...` relay frames are rebuilt as `BT-MANET-1.0` packets.
  - Packets targeting the local node are written to the connected Android Bluetooth SPP client.
- Updated Android and ESP32 documentation with the physical test procedure.

## Scope Preserved

- USB Serial remains debug/test only and is not part of Android scope.
- Android real Bluetooth permissions were not expanded beyond Step 019.
- Android socket connection behavior remains limited to the Step 020 SPP path.
- Continuous Android background Bluetooth receive was not implemented.
- Raspberry Pi gateway logic was not implemented.
- WiFi/GSM production transport logic was not implemented.
- Multi-hop routing beyond the current ESP32 simulation behavior was not expanded.

## Files Updated

- `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`
- `android-chat-app/README.md`
- `esp32-node-platformio/src/main.cpp`
- `esp32-node-platformio/README.md`
- `docs/codex-task-logs/STEP_023_END_TO_END_ANDROID_LORA_ANDROID_MESSAGING.md`

## Physical Test Procedure

1. Upload `node_a_lora` to the ESP32 paired with Android Phone A.
2. Upload `node_b_lora` to the ESP32 paired with Android Phone B.
3. Pair Phone A with `PUP-MANET-NODE_A` in Android Bluetooth settings.
4. Pair Phone B with `PUP-MANET-NODE_B` in Android Bluetooth settings.
5. Open the app on both phones and go to the Sim tab.
6. On both phones, press **Load Paired**, select the matching ESP32, then press **Connect ESP32**.
7. On Phone A, press **Send LoRa**.
8. On Phone B, press **Check In**.

Expected result:

- Phone A receives an ACK containing `FORWARDED_OVER_LORA`.
- NODE_A logs `[LORA_TX]` with a compact `BT1|...` relay frame.
- NODE_B logs `[LORA_RX]`, `[LORA_PROTOCOL_RX]`, and `[BT_TX_FROM_LORA]`.
- Phone B shows the incoming protocol packet in Last received.

## Validation

Android Gradle build:

```text
./gradlew :app:assembleDebug
```

Result:

- `BUILD SUCCESSFUL`

ESP32 PlatformIO builds:

```text
/Users/macbookm1max321tb/.platformio/penv/bin/pio run -e node_a -e node_a_lora -e node_b_lora
```

Result:

- `node_a` build succeeded.
- `node_a_lora` build succeeded.
- `node_b_lora` build succeeded.

Physical end-to-end verification still requires two Android phones paired to the two ESP32 LoRa nodes.
