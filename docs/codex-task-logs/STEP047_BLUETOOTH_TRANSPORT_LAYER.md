# STEP047 - Bluetooth Transport Layer

Date: 2026-06-07

Status: Implementation / build validation PASS; physical validation pending

## Scope

Bluetooth is Android Phone <-> local ESP32 node access only.

Bluetooth is not a node-to-node MANET transport. LoRa remains the MANET backbone, and Android phones remain client/controller devices connected to one local node.

## Changes

- Updated Android UI/diagnostic wording in `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt` so Bluetooth SPP is described as the phone-to-node access layer.
- Updated Android validation labels, readiness text, route notes, and fallback/lab strings to avoid implying Bluetooth carries MANET node-to-node traffic.
- Updated ESP32 Bluetooth status/error/startup diagnostics in `esp32-node-platformio/src/main.cpp`.
- Added `bluetoothRole=PHONE_NODE_ACCESS` to ESP32 status payloads.
- Preserved LoRa routing core, compact `BT1` validation, gateway Bluetooth-disable behavior, and STEP046A production default `TEST_FORCE_GATEWAY_ROUTE=0`.

## Validation

- Android build: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug` - PASS.
- ESP32 build: `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora` - PASS.

## Pending Physical Validation

1. Pair Android Phone A with a normal node ESP32 such as `PUP-MANET-nodeA1`.
2. Confirm Chat and NODE_LIST behavior remain unchanged.
3. Send `BT_STATUS` or inspect serial output on a node build.
4. Expected ESP32 status includes:
   - `access=Android phone <-> local ESP32 node`
   - `transport=Classic Bluetooth SPP phone-node access`
   - `manet_backbone=LoRa`
   - `node_to_node_bluetooth=DISABLED`
5. Confirm gateway builds still keep Bluetooth disabled.

## Notes

STEP047 does not start STEP046B ACK reliability, STEP042C delivery tracking, or STEP042D store-and-forward.
