# ANDROID UI TABS - Remove Battery

## What Was Changed

- Removed battery values from the Android app data path and UI.
- Removed battery from:
  - Selected Nodes panel
  - Metrics panel
  - Message cards
  - Packet model and packet preview
  - Routing score model
  - Route candidate evaluation
  - Realistic simulation event logic
- Removed battery as a route scoring factor.
- Reorganized the Android UI into tabs:
  - Messaging
  - Network
  - Routing
  - Simulation
  - Diagnostics

## Tab Layout

- Messaging is the default tab.
- Messaging contains the Adamson University header, current route summary, message list, packet previews, and the bottom message composer.
- Network contains network selection, local node selection, target destination node selection, selected node information, and topology summary.
- Routing contains the routing decision panel, route candidates, candidate visualization, and transport bridge panel.
- Simulation contains Bluetooth pairing simulation, simulation speed, network state, and LoRa/WiFi/GSM/Satellite toggles.
- Diagnostics contains current status summary, metrics panel, packet log, and network event log.

## Preserved Behavior

- Packet abstraction remains active.
- Transport bridge remains active.
- Adaptive routing engine remains active.
- Realistic MANET simulation remains active.
- Bluetooth placeholder pairing remains active.
- Event log remains active.
- Packet log remains active.
- Message sending remains active.
- Failover behavior remains active.

## Validation Notes

- Battery no longer appears in `MainActivity.kt`.
- Battery no longer affects route scoring or simulation events.
- Command attempted: `./gradlew :app:assembleDebug`
- Result: build could not start because the shell environment could not locate a Java runtime.
- Build and live UI validation must be completed in Android Studio or a configured Android Gradle environment with Java available.

## Next Step

Continue with the next workbook-defined Android task. Hardware behavior should only be added when the workbook explicitly requests it.
