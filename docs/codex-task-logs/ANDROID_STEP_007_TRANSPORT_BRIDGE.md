# ANDROID STEP 007 - ESP32 Communication Bridge Interface

## What Was Created

- Added a `ManetTransportInterface` abstraction inside the Android app.
- Added local transport implementations:
  - `SimulationTransport`
  - `BluetoothTransportPlaceholder`
  - `WiFiTransportPlaceholder`
  - `UsbSerialTransportPlaceholder`
- Added interface operations for:
  - `connect()`
  - `disconnect()`
  - `sendPacket(packet)`
  - `receivePacket()`
  - `getTransportStatus()`
- Routed generated LoRa/MANET packets through the active transport interface before they enter the local message simulation flow.
- Added a compact transport bridge panel showing:
  - Active transport implementation
  - Connection state
  - Last packet sent
  - Last packet received
- Added transport selection dropdown options:
  - Simulation
  - Bluetooth Placeholder
  - WiFi Placeholder
  - USB Serial Placeholder
- Updated `android-chat-app/README.md` with Step 007 architecture and limitations.

## Simulation Behavior

- `SimulationTransport` is the default active implementation.
- Changing transport creates a new local placeholder transport and updates the status panel.
- Placeholder transports only update local state and status.
- Sending a message still generates a LoRa/MANET packet, routes it through the active transport interface, updates the packet log, and preserves the existing queued, relayed, and delivered simulation flow.
- Simulated packet delivery calls the active transport receive path to update last received packet status.

## Boundaries

- No real Bluetooth communication was added.
- No Bluetooth permissions were added.
- No BLE or Classic Bluetooth APIs were added.
- No WiFi backend communication was added.
- No USB serial communication was added.
- No ESP32 serial/BLE packet sending was added.
- No LoRa hardware integration was added.
- ESP32 and Raspberry Pi project folders were not modified.

## Validation Notes

- Existing routing, failover, Bluetooth placeholder pairing, packet abstraction, packet log, and message queue visualization were preserved.
- Command attempted: `./gradlew :app:assembleDebug`
- Result: build could not start because the shell environment could not locate a Java runtime.
- Build must be validated in Android Studio or a configured Android Gradle environment with Java available.

## Next Step

Continue with the next workbook-defined Android task. Real ESP32 communication should only be added when the workbook explicitly requests hardware transport integration.
