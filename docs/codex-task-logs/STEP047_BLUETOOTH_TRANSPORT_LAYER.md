# STEP047 - Bluetooth Transport Layer

Date: 2026-06-07

Status: COMPLETE / Physical Validation PASS

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

## Physical Validation

- Bluetooth phone-to-node access verified.
- `NODE_LIST` verified.
- `PhoneA -> nodeA2` verified.
- `nodeA2 -> PhoneA` verified.
- Gateway disappearance detection verified.
- Gateway-loss messaging verified.

## Notes

STEP047 is closed. Next workbook-approved task is STEP046B Bridge ACK Reliability Improvement. STEP046B acceptance criteria remain to be confirmed before implementation; do not modify routing core.
