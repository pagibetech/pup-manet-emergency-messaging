# STEP045B - Android Real-Network Cleanup

Date: 2026-06-07

Branch: `step-002-003-esp32-simulation`

Status: COMPLETE / Physical Android Hardware Validation PASS

## Context

STEP045A mesh-wide presence propagation is complete and physically validated. Android Nodes screen correctly shows:

- `nodeA1 ONLINE`
- `nodeA2 ONLINE`
- `gatewayA ONLINE`
- `gatewayB ONLINE`

STEP045B focuses on Android UI and destination-selection cleanup only.

## Scope

Updated Android only:

- `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`

No ESP32 firmware changes.

No Raspberry Pi gateway service changes.

No STEP042C Delivery Tracking work.

No STEP042D Store-and-Forward work.

## Changes

- Replaced simulation-era current-state display labels with real discovered node IDs from `discoveredNodes` when NODE_LIST exists.
- Added selected live destination state sourced from `discoveredNodes`.
- Excluded the local connected ESP32 node from selectable destinations.
- Updated Chat send to use the selected discovered node instead of the temporary hardcoded `peerNodeForConnectedEsp32()` mapping.
- Kept safe fallback destination only when `discoveredNodes` is empty and labeled it as fallback.
- Updated Route tab so real discovered nodes show live LoRa route state instead of misleading simulated `No route`.
- Removed or clearly marked simulation-only panels as fallback/lab state so they are not presented as current real-network status.
- Preserved Bluetooth connection and Refresh Nodes behavior.

## Build Validation

Initial Gradle run could not start because the shell did not have a Java runtime on PATH.

Successful command:

```sh
cd android-chat-app
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug
```

Result: BUILD SUCCESSFUL.

## Physical Validation

Result: PASS.

STEP045B passed on physical Android hardware.

Expected accepted behavior:

- Android Nodes uses real discovered node IDs.
- Chat destination selection is driven by live discovered nodes.
- Local connected node is not selectable as a destination.
- Route tab does not show misleading simulated `No route` while real discovered nodes exist.
- Refresh Nodes and Bluetooth SPP connection behavior remain working.

## Preserved Behavior

- STEP045A mesh-wide gatewayB discovery propagation remains valid.
- STEP044 strict compact `BT1` corrupt-packet validation remains preserved.
- Compact `BT1` firmware protocol remains unchanged.
- ESP32 firmware and Raspberry Pi gateway service were not modified.

## Next Step

Continue only from the next incomplete workbook task.

Do not start STEP042C Delivery Tracking or STEP042D Store-and-Forward unless explicitly routed by the workbook/user.
