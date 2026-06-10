# ANDROID STEP 008 - Adaptive Routing Decision Engine

## What Was Created

- Added a dedicated `AdaptiveRoutingEngine` inside the Android app.
- Added routing abstraction models:
  - `RouteCandidate`
  - `RouteScore`
  - `RoutingDecision`
- Updated the existing route decision flow so packet generation and message simulation use the adaptive routing engine output.
- Added weighted route scoring for:
  - Hop count
  - RSSI
  - SNR
  - Battery level
  - Transport availability
  - Gateway availability
  - Satellite availability
  - Node health
- Added a routing decision panel showing:
  - Preferred route
  - Selected route
  - Route score
  - Failover reason
  - Available route candidates
  - Candidate visualization
- Preserved packet abstraction, transport bridge, Bluetooth placeholder pairing, packet log, and existing simulation controls.

## Candidate Routes

The routing engine evaluates simulated candidates for:

- LoRa direct
- Multi-hop LoRa
- WiFi relay
- GSM fallback
- Satellite fallback

Each candidate records availability, score, path, and reason text. If the preferred route becomes unavailable, the engine selects the strongest available candidate and explains the failover reason in the UI.

## Simulation Behavior

- Route decisions recalculate when network toggles, selected network mode, local node, target node, or Bluetooth-linked LoRa simulation state changes.
- Packet generation uses the selected route from the adaptive routing engine.
- Packet logs continue to display the chosen transport route and delivery status.
- No real transport, radio, or ESP32 communication is performed.

## Boundaries

- No real Bluetooth communication was added.
- No BLE or Classic Bluetooth APIs were added.
- No WiFi backend communication was added.
- No ESP32 packet sending was added.
- No LoRa hardware integration was added.
- No Raspberry Pi or backend code was added.
- ESP32 and Raspberry Pi project folders were not modified.

## Validation Notes

- Existing packet abstraction, transport bridge, Bluetooth placeholder pairing, packet log, route visualization, and message send flow were preserved.
- Command attempted: `./gradlew :app:assembleDebug`
- Result: build could not start because the shell environment could not locate a Java runtime.
- Build must be validated in Android Studio or a configured Android Gradle environment with Java available.

## Next Step

Continue with the next workbook-defined Android task. Real BLE, WiFi, ESP32, or LoRa transport should only be added when the workbook explicitly requests hardware integration.
