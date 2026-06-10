# ANDROID STEP 001 - Android App Shell

## What Was Created

- Created an Android project in `android-chat-app/`.
- Added Kotlin and Jetpack Compose Gradle configuration.
- Added a Gradle wrapper for Android Studio and terminal builds.
- Added the `PUP MANET Messenger` app module.
- Added a Compose main screen with:
  - Centered Adamson University logo and title header
  - Local message list
  - Text input
  - Send button
  - Network selection for Auto, LoRa, WiFi, and GSM
  - Status panel for selected network and simulated link states
- Added local-only message simulation. Sent messages appear in the local chat list with the selected route label.
- Updated the top layout to use status bar padding so the header does not overlap Android system UI.
- Added an initial future metrics placeholder for RSSI, SNR, hop count, battery level, gateway proximity, and simulated satellite link status. This is expanded by Android Step 002.
- Updated `android-chat-app/README.md`.

No Bluetooth, GSM sending, WiFi backend, real LoRa, ESP32 integration, Raspberry Pi integration, or Android-to-hardware messaging was added.

## How to Open in Android Studio

1. Open Android Studio.
2. Select **Open**.
3. Choose the `android-chat-app/` folder.
4. Wait for Gradle sync to complete.

## How to Run Using Emulator or Phone

1. Start an Android emulator or connect a phone with USB debugging enabled.
2. Select the `app` run configuration.
3. Press **Run** in Android Studio.

Terminal build, when Gradle is available:

```sh
./gradlew :app:assembleDebug
```

## Current Limitations

- Simulation mode only.
- Chat messages are kept in memory only and reset when the app restarts.
- Network selection only changes the displayed route label.
- RSSI, SNR, hop count, battery level, gateway proximity, and simulated satellite link status are placeholder future metrics only.
- LoRa, WiFi, and GSM statuses are simulated.
- Bluetooth is shown as not connected.
- No real Bluetooth, WiFi, GSM, LoRa, ESP32, Raspberry Pi, or backend implementation exists yet.

## Next Step

Continue with the next Android workbook task. Keep simulation mode stable before adding any real communication transport.
