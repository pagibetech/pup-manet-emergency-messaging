# ANDROID STEP 005 - Bluetooth Placeholder and Simulated Pairing Flow

## What Was Created

- Added a simulation-only Bluetooth status and pairing state model inside the Android app.
- Added fake ESP32 node discovery for:
  - `ESP32-MANET-01`
  - `ESP32-MANET-02`
  - `ESP32-MANET-03`
- Added a compact Bluetooth pairing panel with:
  - Bluetooth status
  - Connected ESP32 node
  - Pairing status
  - Scan control
  - Pair selected node control
  - Disconnect control
- Updated the general status panel to show the simulated Bluetooth state and connected ESP32 placeholder.
- Updated simulated routing so a paired ESP32 placeholder enables LoRa as `Bluetooth-linked to ESP32`.
- Added route notes explaining that LoRa transport is simulated through the paired ESP32.
- Updated `android-chat-app/README.md` with Step 005 usage and limitations.

## Simulation Behavior

- The Bluetooth state defaults to `Simulated`.
- Pressing **Scan** reveals the fake ESP32 node list and sets pairing status to `Scanning`.
- Selecting a fake ESP32 node chooses the target placeholder device.
- Pressing **Pair** marks the selected node as connected and sets pairing status to `Paired`.
- Pressing **Disconnect** clears the connected node and returns pairing status to `Not paired`.
- When paired, the app treats LoRa as available for local routing simulation and displays the LoRa transport as Bluetooth-linked to the paired ESP32.

## Boundaries

- No Android Bluetooth permissions were added.
- No BLE or Classic Bluetooth APIs were added.
- No real ESP32 communication was added.
- No LoRa, WiFi backend, GSM sending, Raspberry Pi, or backend logic was added.
- ESP32 and Raspberry Pi project folders were not modified.

## Validation Notes

- The Step 004 route visualization, message queue states, simulated delays, and message send flow remain in the Android app.
- Command attempted: `./gradlew :app:assembleDebug`
- Result: build could not start because the shell environment could not locate a Java runtime.
- Local Android Studio build validation still requires a Java runtime and Android SDK/Gradle environment on the workstation.

## Next Step

Continue with the next workbook-defined Android task. Real Bluetooth permissions and transport code should only be added when the workbook explicitly requests hardware integration.
