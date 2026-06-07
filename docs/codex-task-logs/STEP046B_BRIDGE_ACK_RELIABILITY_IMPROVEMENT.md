# STEP046B - Bridge ACK Reliability Improvement

Date: 2026-06-07

Status: IMPLEMENTED / Local Build PASS / Physical Validation Pending

## Scope

STEP046B implements the approved STEP046B-A two-tier ACK model.

Bridge ACK authority remains the final destination node. Forwarding and hop ACKs are diagnostics only. Bluetooth remains Android phone-to-local ESP32 access only, and LoRa remains the MANET backbone.

## Changes

- Updated `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`.
- Added Bridge ACK parsing/correlation using `ackFor == original packetId`.
- Added a 12-second wait for matching destination `DELIVERY` ACKs on live Chat sends.
- Added user-facing message states `Pending`, `Delivered`, and `Unknown` for Bridge ACK handling.
- Replaced raw ACK JSON / false `Bridge ACK: No response` display with friendly Bridge ACK status text.
- Kept `FORWARD` and `HOP` ACKs diagnostic-only.
- Updated BT-MANET JSON serialization/parsing so ACK payload JSON is escaped and parsed reliably.
- Updated `esp32-node-platformio/src/main.cpp`.
- Added final destination-node `DELIVERY` ACK generation after local message delivery.
- Added compact `BT1` ACK inference for `ACK-...` packet IDs.
- Kept local bridge forwarding ACKs as `FORWARD` diagnostics only.

## Preserved Behavior

- No routing decisions changed.
- No route discovery changes.
- No Bluetooth architecture changes.
- No Raspberry Pi gateway service changes.
- No Android destination-selection architecture changes.
- Compact `BT1` LoRa packet format remains active.
- STEP044 strict corrupt-packet validation remains preserved.
- STEP045A mesh-wide discovery remains preserved.
- STEP045B live discovered-node destination selection remains preserved.
- STEP047 Bluetooth phone-node access boundary remains preserved.

## Validation

- Android build: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug` - PASS.
- ESP32 build: `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora` - PASS.

## Physical Validation Pending

Hardware validation should confirm:

- `nodeA1 -> nodeA2` Chat send shows `Bridge ACK: Pending`, then `Bridge ACK: Delivered to nodeA2`.
- `nodeA2 -> nodeA1` Chat send shows `Bridge ACK: Pending`, then `Bridge ACK: Delivered to nodeA1`.
- Forwarding ACKs do not mark messages delivered.
- Timeout without a final destination ACK shows `Bridge ACK: Unknown`, not `Failed`.
- Existing discovery and LoRa message delivery remain non-regressed.
