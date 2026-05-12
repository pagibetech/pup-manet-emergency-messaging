# STEP 020 - Android Bluetooth Socket Layer

## Workbook Context

- Latest completed step before this task: `STEP 019 - Android Bluetooth Permissions`
- Current step: `STEP 020 - Android Bluetooth Socket Layer`
- Next step after this task: `STEP 021 - Android to ESP32 Live Packet Test`

## What Was Implemented

- Added an Android Classic Bluetooth SPP socket client.
- Added paired-device loading from Android bonded Bluetooth devices.
- Added paired-device selection in the Sim tab.
- Added socket connect and disconnect actions.
- Added a `BT-MANET-1.0` `HELLO` send action over the socket.
- Added non-blocking read of any immediately available ESP32 response line.
- Added socket diagnostics:
  - Socket status
  - Selected device
  - Connected device
  - Last sent line
  - Last received line
  - Last error

## User Workflow

1. Pair ESP32 in Android system Bluetooth settings first.
2. Open the app.
3. Go to the Sim tab.
4. Confirm Bluetooth permissions are ready.
5. Press `Load Paired`.
6. Select the ESP32 service name, for example `PUP-MANET-NODE_A`.
7. Press `Connect ESP32`.
8. Press `Send HELLO`.
9. Press `Close Socket` when done.

## Scope Preserved

- No BLE implementation was added.
- No chat message delivery over Bluetooth was added.
- No LoRa forwarding was added.
- No ESP32 firmware files were modified in this step.
- No Raspberry Pi files were modified.
- Simulation remains the default app behavior.

## Files Updated

- `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`
- `android-chat-app/README.md`
- `docs/codex-task-logs/STEP_020_ANDROID_BLUETOOTH_SOCKET_LAYER.md`

## Validation

Local Gradle build:

```text
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

Result:

- Android app builds successfully.
- Sim tab shows the real Bluetooth socket panel.
- App can load already-paired Bluetooth devices when permission is granted.
- Socket connect requires a device paired in Android settings first.

## Next Step

Continue with `STEP 021 - Android to ESP32 Live Packet Test`. Do not implement ESP32-to-ESP32 LoRa live messaging until the workbook reaches the LoRa live packet step.
