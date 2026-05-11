# PUP MANET Messenger Android App

This folder contains the Android Step 001 app shell for the PUP MANET Emergency Messaging System.

The app is Kotlin-based, uses Jetpack Compose, and is simulation-only. It includes a placeholder Bluetooth pairing flow, but does not implement Bluetooth permissions, BLE, Classic Bluetooth, GSM sending, WiFi backend communication, real LoRa, or Android-to-ESP32 hardware messaging yet.

## Project Details

- App name: PUP MANET Messenger
- Language: Kotlin
- UI: Jetpack Compose with Material 3
- Minimum SDK: 26
- Package: `ph.edu.pup.manetmessenger`

## Current Screens and Components

- Main chat screen with a local message list, text input, and send button.
- Centered Adamson University header using `app/src/main/res/drawable/adamson_logo.png`.
- Network selection with Auto, LoRa, WiFi, and GSM options.
- Multi-node MANET selectors for current local node and target destination node.
- Simulated MANET node profiles for Node Alpha, Node Bravo, Node Charlie, Node Delta, and Gateway Node.
- Status panel showing the current selected network and simulated communication states.
- Bluetooth status panel showing simulated availability, connected ESP32 placeholder node, and pairing status.
- Simulated Bluetooth pairing controls for scanning, selecting, pairing, and disconnecting fake ESP32 nodes.
- LoRa/MANET packet abstraction for generated chat messages.
- Compact packet preview inside each message card.
- Packet log panel showing recent generated packets, route, and delivery status.
- Local simulation controls for LoRa, WiFi, GSM, and simulated satellite link availability.
- Simulated metrics for RSSI, SNR, hop count, battery level, gateway proximity, and simulated satellite link status.
- Scrollable Network, Status, Simulation Controls, Metrics, and Messages content.
- Fixed bottom message composer so text input and Send stay visible on small screens.
- Local message simulation that adds typed messages to the chat list with the selected route label.
- Per-message route and status labels.

## Header Logo

The current header uses `app/src/main/res/drawable/adamson_logo.png`. To replace the logo, keep the same file name and place the updated PNG at that path, then rebuild the app.

## How to Open in Android Studio

1. Open Android Studio.
2. Choose **Open**.
3. Select the `android-chat-app/` folder.
4. Let Gradle sync finish.

## How to Run

Use an Android emulator or a physical phone with USB debugging enabled.

From Android Studio:

1. Select the `app` run configuration.
2. Choose an emulator or connected phone.
3. Press **Run**.

From a terminal with Gradle available:

```sh
./gradlew :app:assembleDebug
```

## Local Simulation Engine

The app includes a local-only simulation engine for route and failover testing.

- Simulated nodes: Node Alpha, Node Bravo, Node Charlie, Node Delta, and Gateway Node.
- Each node has local placeholder battery, RSSI, SNR, hop count, gateway proximity, and route availability values.
- Messages use simulated store-and-forward routing through intermediate nodes.
- Adaptive failover priority is LoRa, then WiFi, then GSM, then Simulated Satellite.
- If the preferred route is unavailable across the path, the app automatically tries the next available route.
- Messages can show `Queued`, `Relayed`, `Delivered`, or `Failed`.
- Route labels include `LoRa`, `WiFi`, `GSM`, `Simulated Satellite`, or `No route`.
- Message cards show route type, full node path, hop count, RSSI, SNR, battery, gateway proximity, and satellite status.
- Message cards use different visual styles for Queued, Relayed, Delivered, and Failed.
- Messages briefly progress through Queued, Relayed, and Delivered using local simulated delays based on hop count, route type, and RSSI quality.
- Route quality is displayed as Excellent, Good, Weak, or Critical.
- Topology overview shows connected nodes, offline nodes, gateway node, current selected path, and node health.
- The message input clears after a local send is queued.
- Toggle controls can mark LoRa, WiFi, GSM, and the simulated satellite link available or unavailable.
- The message composer is kept in the bottom bar while the rest of the simulation panels scroll.

## Bluetooth Placeholder Simulation

Android Step 005 adds a local-only Bluetooth placeholder flow for future ESP32 pairing.

- Bluetooth status can display `Available`, `Disabled`, `Not supported`, or `Simulated`; the current implementation stays in `Simulated` mode.
- Pairing status can show `Not paired`, `Scanning`, `Paired`, or `Connection failed`.
- The fake ESP32 node list contains `ESP32-MANET-01`, `ESP32-MANET-02`, and `ESP32-MANET-03`.
- Press **Scan** to reveal the simulated ESP32 nodes.
- Select a fake node and press **Pair** to mark it as the connected ESP32 node.
- Press **Disconnect** to clear the simulated connection.
- When paired, LoRa is shown as `Bluetooth-linked to ESP32`, and route notes state that LoRa transport is simulated through the paired ESP32.
- This flow does not request Android Bluetooth permissions and does not use BLE or Classic Bluetooth APIs.

## LoRa/MANET Packet Abstraction

Android Step 006 prepares messages for future ESP32 transport by converting each typed chat message into a structured local packet before it appears in the chat list.

Each generated packet includes:

- Packet ID
- Source node ID
- Destination node ID
- Selected transport
- Payload text
- Unix timestamp
- Hop path and hop count
- RSSI, SNR, and battery
- Gateway and satellite status
- Delivery status

The packet remains local to the app. Message cards show a compact packet preview, and the packet log panel lists the latest generated packets with packet ID, route, and status.

## Current Limitations

- Messages are stored only in local Compose state.
- Route decisions and metrics are simulated only.
- Bluetooth pairing is simulated only and uses fake ESP32 node names.
- LoRa/MANET packets are local data models only and are not sent to an ESP32.
- LoRa, WiFi, GSM, and simulated satellite statuses are controlled by local toggles only.
- No messages leave the app.
- No ESP32, Raspberry Pi, backend, or hardware integration is included.

## Next Step

Continue with the next workbook-defined Android task only. Keep the app simulation-first until the workbook explicitly requests real Bluetooth, WiFi, GSM, LoRa, or backend integration.
