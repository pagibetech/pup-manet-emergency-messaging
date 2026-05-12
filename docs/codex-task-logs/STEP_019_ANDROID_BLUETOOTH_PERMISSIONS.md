# STEP 019 - Android Bluetooth Permissions

## Workbook Context

- Latest completed step before this task: `STEP 018 - ESP32 Bluetooth Service`
- Current step: `STEP 019 - Android Bluetooth Permissions`
- Next step after this task: `STEP 020 - Android Bluetooth Socket Layer`

## What Was Implemented

- Added Android Bluetooth permission declarations to `AndroidManifest.xml`.
- Added Android 12+ permissions:
  - `BLUETOOTH_SCAN`
  - `BLUETOOTH_CONNECT`
- Added Android 11 and older compatibility permissions:
  - `BLUETOOTH`
  - `BLUETOOTH_ADMIN`
  - `ACCESS_FINE_LOCATION`
- Added a Bluetooth permission readiness model in the Android app.
- Added a runtime permission request action in the existing Sim tab Bluetooth panel.
- Updated the Bluetooth readiness checklist so:
  - ESP32 firmware parser is ready.
  - ESP32 Bluetooth service is ready.
  - Android Bluetooth permissions are ready.
  - Android Bluetooth socket/service remains pending.

## Scope Preserved

- No Android Bluetooth socket connection was implemented.
- No real Bluetooth scan logic was implemented.
- No BLE or Classic Bluetooth communication code was added.
- No ESP32 firmware files were modified in this step.
- No Raspberry Pi files were modified.
- Simulation behavior remains the default app behavior.

## Files Updated

- `android-chat-app/app/src/main/AndroidManifest.xml`
- `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`
- `android-chat-app/README.md`
- `docs/codex-task-logs/STEP_019_ANDROID_BLUETOOTH_PERMISSIONS.md`

## Validation

Initial local Gradle build:

```text
./gradlew :app:assembleDebug
```

Result:

- Blocked because the shell could not locate a Java runtime.

Successful local Gradle build using the Android Studio bundled JBR:

```text
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

Result:

- Android app builds successfully.
- Sim tab shows Bluetooth permission readiness.
- Permission request button is available when runtime permissions are not granted.
- App still does not open a Bluetooth socket.

## Next Step

Continue with `STEP 020 - Android Bluetooth Socket Layer`. Do not implement LoRa live messaging until the workbook reaches the ESP32 LoRa/live packet steps.
