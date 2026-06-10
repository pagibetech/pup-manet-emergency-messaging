# ANDROID STEP 009 - Realistic MANET Network State Simulation

## What Was Created

- Added dynamic MANET simulation behavior inside the Android app.
- Added simulation speed controls:
  - Slow
  - Normal
  - Fast
- Added simulation state indicators:
  - Stable
  - Congested
  - Recovering
  - Partitioned
- Added a live network event log panel.
- Added fluctuating RSSI and SNR values per simulated node.
- Added gradual simulated battery drain.
- Added critical battery behavior that can disable a node.
- Added intermittent link failure and recovery for LoRa, WiFi, GSM, and simulated satellite.
- Added node mobility behavior that can reorder simulated paths and trigger route rediscovery.
- Updated routing inputs so the adaptive routing engine reacts to dynamic node and link state.
- Preserved packet abstraction, transport bridge, Bluetooth placeholder pairing, packet log, routing decision panel, and message send flow.

## Dynamic Simulation Behavior

- RSSI values change over time and can naturally create weak or critical signal states.
- Battery levels slowly drain and affect node health and route scoring.
- Nodes with critical battery can become unavailable.
- Individual node route links can fail or recover.
- Global LoRa, WiFi, GSM, and simulated satellite availability can fail or recover.
- Simulated mobility can change node ordering, which changes hop paths and candidate route availability.
- The event log records changes such as:
  - Node health changes
  - Signal degradation
  - RSSI recovery
  - Relay degradation
  - Link restoration
  - Route rediscovery

## UI Behavior

- The Simulation Controls panel now includes speed selection and the current network state indicator.
- The Topology panel updates from the dynamic node list.
- The Routing Decision panel recalculates from the latest simulated node and link state.
- The Packet Log continues to show packets generated from the currently selected route.
- Existing packet previews and message cards remain unchanged.

## Boundaries

- No real Bluetooth communication was added.
- No BLE or Classic Bluetooth APIs were added.
- No WiFi backend communication was added.
- No ESP32 packet sending was added.
- No LoRa hardware integration was added.
- No Raspberry Pi or backend code was added.
- ESP32 and Raspberry Pi project folders were not modified.

## Validation Notes

- Existing adaptive routing, transport bridge, packet abstraction, Bluetooth placeholder pairing, packet log, topology, and message send flow were preserved.
- Command attempted: `./gradlew :app:assembleDebug`
- Result: build could not start because the shell environment could not locate a Java runtime.
- Build and live UI behavior must be validated in Android Studio or a configured Android Gradle environment with Java available.

## Next Step

Continue with the next workbook-defined Android task. Real BLE, WiFi, ESP32, LoRa, or Raspberry Pi integration should only be added when the workbook explicitly requests hardware behavior.
