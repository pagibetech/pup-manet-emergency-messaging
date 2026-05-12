# STEP 021 - Android to ESP32 Live Packet Test

## Workbook Context

- Latest completed step before this task: `STEP 020 - Android Bluetooth Socket Layer`
- Current step: `STEP 021 - Android to ESP32 Live Packet Test`
- Next step after this task: `STEP 022 - ESP32 to ESP32 LoRa Live Test`

## What Was Implemented

- Added a guided live packet test to the Android Real Bluetooth Socket panel.
- Added pass/fail counters for live Android-to-ESP32 packet exchange.
- Added live test status and error reporting.
- Added response waiting for ESP32 Bluetooth socket replies.
- Added a `Run Test` action that sends:
  - `HELLO`, expecting `ACK`
  - `STATUS`, expecting `STATUS`
  - simulation-safe `MESSAGE` with `MODE=AUTO`, expecting `ACK`
- Kept test packets newline-delimited and aligned with `BT-MANET-1.0`.

## User Workflow

1. Upload Step 018 ESP32 firmware to an ESP32.
2. Pair Android with the ESP32 Bluetooth service in Android settings.
3. Open the Android app.
4. Go to the Sim tab.
5. Confirm Bluetooth permissions are ready.
6. Press `Load Paired`.
7. Select the ESP32 service, for example `PUP-MANET-NODE_A`.
8. Press `Connect ESP32`.
9. Press `Run Test`.
10. Confirm the passed count reaches 3 and failed count remains 0.

## Scope Preserved

- No ESP32-to-ESP32 LoRa live messaging was implemented.
- No chat message delivery over Bluetooth was enabled.
- No Raspberry Pi files were modified.
- No ESP32 firmware files were modified in this step.
- Simulation remains the default app behavior.

## Files Updated

- `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`
- `android-chat-app/README.md`
- `docs/codex-task-logs/STEP_021_ANDROID_TO_ESP32_LIVE_PACKET_TEST.md`

## Validation

Local Gradle build:

```text
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

Result:

- Android app builds successfully.
- Sim tab Real Bluetooth Socket panel shows `Run Test`.
- Live packet test remains disabled until a Bluetooth socket is connected.

## Next Step

Continue with `STEP 022 - ESP32 to ESP32 LoRa Live Test`. Do not implement Raspberry Pi gateway integration until the workbook reaches that step.
