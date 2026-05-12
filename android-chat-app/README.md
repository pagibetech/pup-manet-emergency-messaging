# PUP MANET Messenger Android App

This folder contains the Android Step 001 app shell for the PUP MANET Emergency Messaging System.

The app is Kotlin-based, uses Jetpack Compose, and is simulation-first. It includes a placeholder Bluetooth pairing flow, Android Bluetooth permission declarations/readiness controls, and a Classic Bluetooth SPP socket layer for paired ESP32 devices. It does not implement BLE, GSM sending, WiFi backend communication, real LoRa, or end-to-end Android-to-LoRa messaging yet.

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
- Bluetooth architecture layer with simulated lifecycle states, packet bridge counters, reconnect timing, and diagnostics.
- Bluetooth permission readiness panel and runtime permission request action for the future Android-to-ESP32 Bluetooth transport.
- Real Bluetooth socket panel for loading paired devices, opening an ESP32 SPP socket, sending a `BT-MANET-1.0` `HELLO`, and closing the socket.
- ESP32 Bluetooth packet protocol preview with supported packet types, serialization placeholder, checksum placeholder, and validation status.
- Bluetooth readiness and hardware test plan panels for ESP32 firmware, protocol test cases, architecture, and next implementation stages.
- LoRa/MANET packet abstraction for generated chat messages.
- ESP32 communication bridge abstraction with simulation and placeholder transports.
- Adaptive routing decision engine with weighted candidate scoring and failover reasons.
- Realistic MANET state simulation with fluctuating signal, intermittent links, node mobility, and outages.
- Tabbed UI with Chat, Nodes, Route, Sim, and Logs sections.
- Simulation speed control with Slow, Normal, and Fast modes.
- Live simulation state indicators for Stable, Congested, Recovering, and Partitioned conditions.
- Network event log for dynamic link, node, route, and recovery events.
- Routing decision panel showing preferred route, selected route, score, failover reason, and route candidates.
- Transport bridge status panel with active implementation, connection state, last sent packet, and last received packet.
- Transport selection dropdown for Simulation, Bluetooth Placeholder, and WiFi Placeholder.
- ESP32 transport preparation panel with hardware mode disabled, bridge configuration fields, simulated handshake, packet preview, and hardware readiness checklist.
- Compact field-use message cards with technical packet details moved to Logs.
- Packet log panel showing recent generated packets, route, and delivery status.
- Queue statistics panel showing queued/active, delivered, failed, and retry totals.
- Validation checklist in Logs with basic validation and reset controls.
- Local simulation controls for LoRa, WiFi, GSM, and simulated satellite link availability.
- Simulated metrics for RSSI, SNR, hop count, gateway proximity, and simulated satellite link status.
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
- Each node has local placeholder RSSI, SNR, hop count, gateway proximity, and route availability values.
- Messages use simulated store-and-forward routing through intermediate nodes.
- Adaptive failover priority is LoRa, then WiFi, then GSM, then Simulated Satellite.
- If the preferred route is unavailable across the path, the app automatically tries the next available route.
- Messages can show `Queued`, `Routing`, `Relaying`, `Retrying`, `Delivered`, or `Failed`.
- Route labels include `LoRa`, `WiFi`, `GSM`, `Simulated Satellite`, or `No route`.
- Message cards show route type, delivery status, path, hop count, RSSI, SNR, and failover/failure reason when relevant.
- Message cards use different visual styles for queued, routing, relaying, retrying, delivered, and failed states.
- Messages progress through queued, routing, relaying, retrying, delivered, and failed states using local simulated delays based on routing, relay path, congestion, and retry timeout.
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
- This flow can request Android Bluetooth permissions as of Step 019, but it still does not use BLE, Classic Bluetooth sockets, or hardware communication APIs.

## Bluetooth Architecture Layer

Android Step 014 prepares the production Bluetooth architecture without enabling hardware communication.

The planned future stack is:

```text
Android Bluetooth API
-> BluetoothTransportManager
-> BluetoothPacketBridge
-> MANET routing engine
```

The app currently includes these platform-free architecture types:

- `BluetoothTransportManager`
- `BluetoothDeviceState`
- `BluetoothPacketBridge`
- `BluetoothConnectionSession`

The simulated lifecycle supports `Idle`, `Scanning`, `Pairing`, `Connecting`, `Connected`, `Disconnected`, `Failed`, and `Retrying`. The Sim tab can scan for fake ESP32 devices, pair, connect, exchange simulated `HELLO` / `ESP32_ACK` packets, disconnect, and simulate a timeout followed by automatic reconnect attempts.

Bluetooth diagnostics show the current paired ESP32, signal placeholder, packet counters, last reconnect attempt, connection uptime, retry counter, reconnect countdown, and timeout status. These diagnostics remain local Compose state only.

Android Bluetooth permissions are declared and can be requested as of Step 019. Classic Bluetooth socket connection support is available as of Step 020. BLE APIs, ESP32 firmware logic, LoRa forwarding, and Raspberry Pi logic are not included in the Android app.

## Android Bluetooth Permissions

Android Step 019 adds permission preparation for the official Android-to-ESP32 Bluetooth path.

Manifest permissions:

- Android 12 and newer:
  - `BLUETOOTH_SCAN`
  - `BLUETOOTH_CONNECT`
- Android 11 and older:
  - `BLUETOOTH`
  - `BLUETOOTH_ADMIN`
  - `ACCESS_FINE_LOCATION`

The Sim tab Bluetooth panel now shows permission readiness and a **Request Permissions** action. This step prepares permission access only. It does not scan for real ESP32 devices, open a Bluetooth socket, send protocol packets over Bluetooth, or connect to hardware.

## Android Bluetooth Socket Layer

Android Step 020 adds a narrow Classic Bluetooth socket layer for ESP32 SPP communication.

Workflow:

1. Pair the ESP32 from Android system Bluetooth settings first.
2. Open the app and go to the Sim tab.
3. Confirm Bluetooth permissions are ready.
4. Press **Load Paired**.
5. Select the paired ESP32 service, such as `PUP-MANET-NODE_A`.
6. Press **Connect ESP32**.
7. Press **Send HELLO** to send one newline-delimited `BT-MANET-1.0` packet.
8. Press **Close Socket** when done.

This step only opens/closes the socket and sends the protocol `HELLO`. It does not implement message chat delivery over Bluetooth, real scanning/discovery, LoRa forwarding, or Android-to-ESP32 live packet test automation.

## ESP32 Bluetooth Packet Protocol

Android Step 015 defines the packet format that future Bluetooth communication will use between Android and ESP32.

Supported packet types:

- `HELLO`
- `ACK`
- `MESSAGE`
- `ROUTE_DISCOVERY`
- `ROUTE_REPLY`
- `STATUS`
- `ERROR`

The protocol packet model includes:

- Protocol version
- Packet type
- Packet ID
- Source node
- Destination node
- Payload
- Hop path
- Retry count
- Timestamp
- Status
- Checksum placeholder

The Route tab shows a protocol preview panel with:

- Compact sample outgoing packet: Android sends `MESSAGE` to ESP32, then ESP32 forwards to LoRa.
- Compact sample incoming packet: ESP32 sends `RECEIVED/ACK` back to Android.
- Compact JSON-like serialization preview.
- String-to-packet deserialization placeholder.
- Validation status for protocol version, required fields, supported packet type, and checksum placeholder.

The checksum remains `checksum pending / simulated`; no CRC is implemented yet. Step 020 can send a `HELLO` packet over a Bluetooth socket, while chat `MESSAGE` sending remains simulation-only until a later workbook step.

## Bluetooth Readiness and Hardware Test Plan

Android Step 016 keeps the Route tab compact and moves detailed protocol readiness information into the Logs tab.

The Logs tab now includes:

- Bluetooth readiness checklist
- ESP32 firmware requirements
- Protocol test cases
- Detailed outgoing and incoming protocol packet data
- Readable serialization output
- Next-stage implementation plan

Hardware architecture note:

```text
Android Phone <-> Bluetooth <-> ESP32 <-> LoRa <-> ESP32 <-> Bluetooth <-> Android Phone
```

Readiness checks include Android Bluetooth architecture, packet protocol definition, ESP32 firmware packet parser, ESP32 Bluetooth service, SX1278 LoRa wiring, LoRa send/receive testing, Android Bluetooth permissions, and Android Bluetooth socket/service implementation. Android Bluetooth permissions are ready as of Step 019; the initial socket layer is ready as of Step 020.

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
- RSSI and SNR
- Gateway and satellite status
- Delivery status

The packet remains local to the app. The Logs tab lists the latest generated packets with packet ID, route, source, destination, path, RSSI, SNR, hop count, and status.

## Message Queue Engine

Android Step 011 adds a local multi-message queue and delivery state engine.

- Messages enter the queue before routing begins.
- Routing and relay delays are simulated separately.
- Congestion and partitioned network states can increase delivery delay.
- Random packet drops can trigger retry behavior.
- If a route becomes unavailable mid-send, the queue engine reroutes using the adaptive routing engine.
- Retry attempts use the next best available route when possible.
- The retry limit is configured in code as `MAX_RETRY_COUNT`.
- Packets track queued, relay, and delivery timestamps.
- The Logs tab includes queue statistics for queued/active, delivered, failed, and retry totals.

## Simulation Validation Checklist

Android Step 012 adds an in-app validation section in the Logs tab.

The checklist includes:

- App opens on Chat tab
- Message send works
- Message queue states work
- Routing decision updates
- Failover works
- Packet log updates
- Event log updates
- Transport bridge status updates
- Bluetooth placeholder pairing works
- No battery level appears
- Simulation speed control works
- Network toggles work

Each item can show `Pass`, `Fail`, or `Not tested`. **Run Basic** checks what can be verified from current app state. Manual-only checks remain `Not tested` until the user performs the relevant action. **Reset** clears all checklist items back to `Not tested`.

## ESP32 Communication Bridge Interface

Android Step 007 adds a transport bridge abstraction for future ESP32 communication.

The current app includes these local transport implementations:

- `SimulationTransport`
- `BluetoothTransportPlaceholder`
- `WiFiTransportPlaceholder`

Each implementation follows the same interface shape:

- `connect()`
- `disconnect()`
- `sendPacket(packet)`
- `receivePacket()`
- `getTransportStatus()`

`SimulationTransport` is the default active transport. Placeholder transports update local status only and do not perform real BLE, Classic Bluetooth, WiFi, ESP32, or network communication. Packet sending is routed through the active transport abstraction before the existing local message simulation continues.

## ESP32 Transport Preparation

Android Step 013 adds a real-hardware preparation panel while keeping hardware mode disabled.

- Hardware mode shows `Simulation Mode` as active and `Hardware Disabled` as unavailable for now.
- ESP32 bridge configuration includes device name, connection type, packet format version, connection status, and last handshake time.
- Bluetooth is the primary planned Android-to-ESP32 connection path.
- WiFi remains a future optional placeholder only.
- The intended field architecture is Android Phone to ESP32 over Bluetooth, then ESP32 to other ESP32 nodes over LoRa.
- The simulated handshake flow sends a local `HELLO`, waits briefly, then shows a simulated `ESP32_ACK`.
- The outgoing MANET packet preview shows packet ID, source, destination, transport, payload, hop path, and status.
- The hardware readiness checklist tracks Android readiness, ESP32 firmware readiness, Bluetooth pairing readiness, LoRa wiring, and packet format matching.
- This step does not add Android Bluetooth permissions, BLE, Classic Bluetooth, WiFi backend, ESP32 firmware, Raspberry Pi code, or physical hardware communication.

## Adaptive Routing Decision Engine

Android Step 008 adds a dedicated local routing engine for explainable route selection.

The engine evaluates simulated route candidates such as:

- LoRa direct
- Multi-hop LoRa
- WiFi relay
- GSM fallback
- Satellite fallback

Candidate scoring considers:

- Hop count
- RSSI
- SNR
- Transport availability
- Gateway availability
- Satellite availability
- Node health

The app displays the preferred route, selected route, route score, failover reason, available candidates, and candidate details. If a transport is toggled down or a path becomes unavailable, the engine immediately recalculates and updates the visible routing decision. Generated packet logs continue to reflect the selected route.

## Realistic MANET State Simulation

Android Step 009 adds dynamic local network behavior to exercise routing and failover decisions.

The simulation can now change over time:

- RSSI and SNR fluctuate per node.
- LoRa, WiFi, GSM, and simulated satellite links can fail or recover.
- Node ordering can shift to simulate mobility and path rediscovery.
- Nodes can enter weak, critical, or offline states.

Simulation speed can be set to Slow, Normal, or Fast. The UI shows a simulation condition of Stable, Congested, Recovering, or Partitioned. The network event log records changes such as degraded relays, recovered links, node health changes, route rediscovery, and failover-related events.

## Tabbed Interface

The app opens on the Chat tab by default.

- Chat: Adamson University header, compact current route summary, message list, and bottom message composer.
- Nodes: network mode selection, current local node, target destination node, selected node information, and topology summary.
- Route: adaptive routing decision details, route candidates, candidate visualization, and transport bridge.
- Sim: Bluetooth pairing placeholder, simulation speed, network state, and LoRa/WiFi/GSM/Satellite toggles.
- Logs: current status summary, metrics panel, queue statistics, validation checklist, packet log, and network event log.

The battery signal was removed from the Android UI and route scoring model. Routing now uses RSSI, SNR, hop count, node health, gateway availability, satellite availability, and transport availability.

## Current Limitations

- Messages are stored only in local Compose state.
- Route decisions and metrics are simulated only.
- Adaptive routing scores are local simulation values only.
- Dynamic MANET behavior is generated locally and is not based on physical radio measurements.
- Bluetooth pairing is simulated only and uses fake ESP32 node names.
- LoRa/MANET packets are local data models only and are not sent to an ESP32.
- Hardware mode is visible but disabled; ESP32 handshake and readiness checks are placeholders only.
- Transport bridge implementations are local simulation/placeholder classes only.
- LoRa, WiFi, GSM, and simulated satellite statuses are controlled by local toggles only.
- No messages leave the app.
- No ESP32, Raspberry Pi, backend, or hardware integration is included.

## Next Step

Continue with the next workbook-defined Android task only. Keep the app simulation-first until the workbook explicitly requests real Bluetooth, WiFi, GSM, LoRa, or backend integration.
