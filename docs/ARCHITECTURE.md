# Architecture

Last updated: 2026-06-06

Purpose: PUP MANET emergency messaging prototype with Android phones connected to ESP32 nodes, LoRa node-to-node transport, Raspberry Pi 3B local gateways, primary internet/Tailscale gateway backhaul, long-range LoRa gateway backup backhaul, and validated ESP32-to-Raspberry Pi USB serial bridge.

Boundaries:
- `esp32-node-platformio/`: ESP32 PlatformIO firmware, Bluetooth SPP service for normal MANET nodes, gateway Bluetooth-disabled LoRa builds, packet parser, `[GW_JSON]` serial bridge parsing, STEP042A HELLO/node table discovery export fix, and controlled SX1278 LoRa live-test environments.
- `android-chat-app/`: Kotlin Jetpack Compose Android app, simulation-first UI, Bluetooth permission/readiness flow, Classic Bluetooth SPP socket layer, and Step 023 Android-to-LoRa demo controls.
- `raspberry-pi-gateway/`: Python gateway TCP relay service with Tailscale VPN backhaul, USB serial bridge to ESP32 via `[GW_JSON]` protocol, LoRa SPI abstraction baseline, failover, buffering, recovery.
- `docs/`: workbook, operational memory, diagrams, test procedures, and Codex task logs.

Validated local physical path:

`Android Phone A -> Bluetooth SPP -> ESP32 NODE_A -> SX1278 LoRa -> ESP32 NODE_B -> Bluetooth SPP -> Android Phone B`

Reverse direction is validated through STEP 037.

Validated gateway serial bridge path (STEP041):

`ESP32 Gateway Node -> USB Serial [GW_JSON] -> Raspberry Pi Gateway Service -> Tailscale VPN TCP -> Peer Gateway Service -> USB Serial [GW_JSON] -> ESP32 Gateway Node -> SX1278 LoRa`

Final STEP039 gateway/backhaul design:

`Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B`

Confirmed STEP040B gateway relay validation:
- Gateway A: `pup-gateway-a` / `100.123.79.41`.
- Gateway B: `pup-gateway-b` / `100.79.214.18`.
- TCP relay port: `5050`.
- Tailscale ping between gateways: PASS.
- TCP relay test over Tailscale: PASS.
- Python gateway TCP relay successfully transferred JSON messages from Gateway A to Gateway B through Tailscale VPN.
- ACK and heartbeat behavior worked.

Confirmed STEP041 serial bridge validation:
- Gateway service opened `/dev/ttyUSB0` on both Raspberry Pis.
- Gateway B sent `STEP041-FINAL-001` over TCP to Gateway A.
- Gateway A logged `[TCP_RX]` and wrote to ESP32 via `[SERIAL_TX]`.
- ESP32 logged `[GATEWAY_SERIAL_PARSE]` with `valid=true`.
- ESP32 executed `[ROUTE_DECISION]` followed by `[LORA_TX]`.
- Full bidirectional serial bridge validated.

STEP041 bug fix: `gateway_service.py` ignores non-`[GW_JSON]` serial lines, preventing ESP32 boot/debug logs from being parsed as JSON.

Gateway ESP32 Bluetooth policy:
- Gateway ESP32 devices are connected to Raspberry Pi gateways by USB serial.
- Gateway ESP32 builds `gatewayA_lora` and `gatewayB_lora` boot with Bluetooth DISABLED.
- Gateway ESP32 devices must not appear in Android Bluetooth scans or accept Android pairing.
- Normal MANET node ESP32 devices keep Bluetooth enabled for Android phones.
- Validation: Android scan shows only `PUP-MANET-nodeA1` and `PUP-MANET-nodeA2`, not gateway ESP32 devices.

STEP042A discovery export fix:
- Root cause: compact LoRa relay HELLO packets such as `HELLO-nodeA1` and `HELLO-nodeA2` were parsed as `MESSAGE` unconditionally, then routed, relayed, or duplicate-dropped before node table insertion.
- Fix in `esp32-node-platformio/src/main.cpp`: infer compact relay HELLO packets from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId` payload, learn HELLO packets before route/duplicate handling, store `hopCount` in node table entries, and log `[NEIGHBOR_ADD]`, `[NEIGHBOR_UPDATE]`, and `[NODE_LIST_EXPORT]`.
- NODE_LIST generation now removes expired nodes before export and logs each exported row.
- Build passed for `nodeA1_lora`, `nodeA2_lora`, `gatewayA_lora`, and `gatewayB_lora`.
- Physical A-side discovery validation is PASS for `nodeA1`, `nodeA2`, and `gatewayA`.

STEP042A/STEP042B physical validation checkpoint:
- Flashed/running firmware: `gatewayA_lora` restored as `gatewayA`, `gatewayB_lora` restored as `gatewayB`, `nodeA1_lora` as `nodeA1`, and `nodeA2_lora` as `nodeA2`.
- Android discovery shows `nodeA1 ONLINE`, `nodeA2 ONLINE`, and `gatewayA ONLINE`.
- `gatewayB` boots and broadcasts `HELLO-gatewayB`, but does not yet appear in Android discovery.
- STEP042B bidirectional 2-node Android LoRa messaging is PASS for `nodeA1 -> nodeA2` and `nodeA2 -> nodeA1`.
- Phone A sent `hello from A`; Phone B received it via LoRa on path `nodeA1 -> nodeA2`.
- Phone B sent `hello from b`; Phone A received it via LoRa on path `nodeA2 -> nodeA1`.
- This is a 2-node validation only; `nodeA3` has not been flashed or deployed.
- Temporary Android validation mapping: `peerNodeForConnectedEsp32()` was changed from `"nodeA2" -> "nodeA3"` to `"nodeA2" -> "nodeA1"` because `nodeA3` is not deployed.

STEP043 LoRa HELLO packet format:
- RF receive is confirmed: Gateway B receives Gateway A compact LoRa HELLO payloads.
- Protocol decision: keep compact `BT1` LoRa relay packets as the final current firmware protocol because nodeA1/nodeA2 routing already works with compact packets.
- Initial JSON-oriented STEP043 fix failed physical validation; the actual gateway path still transmitted `BT1|HELLO-...|gatewayA|BROADCAST|{"gatewayId":"A"}|...`.
- Corrected receiver-side fix: compact `BT1` RX now logs `[LORA_RELAY_PARSE] valid`, infers HELLO from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId`, learns the peer through `learnNodeFromHelloPacket()` before duplicate/drop routing, and returns immediately for HELLO.
- Expected discovery evidence: `[LORA_RX] payload=BT1|HELLO-...`, `[LORA_RELAY_PARSE] valid`, `[DISCOVERY] HELLO from gatewayA gateway=A`, and `[NEIGHBOR_ADD]` / `[NEIGHBOR_UPDATE] node=gatewayA` on Gateway B; equivalent logs for `gatewayB` on Gateway A.
- Local builds pass for `gatewayA_lora`, `gatewayB_lora`, `nodeA1_lora`, and `nodeA2_lora`.
- Physical validation pending: `STATUS` and `NEIGHBORS` should list both gateways.

Current Android/topology limitations:
- Android Nodes/Route/Topology UI still partly uses simulated Node Alpha/Bravo/Charlie/Delta labels.
- Route tab can show `No route` even while real LoRa messages are delivered.
- Discovered node list works, but send destination is still not fully driven by live discovered nodes.
- GatewayB repeated reset/garbage serial output needs investigation.

Architecture change:
- The previous `WiFi Router A <-> WiFi Router B simulated satellite link` is removed.
- Internet backhaul is now the primary gateway transport.
- Tailscale VPN is the primary encrypted tunnel.
- Ethernet/WiFi internet connectivity is preferred.
- LoRa-to-LoRa gateway backhaul becomes the backup path if internet is unavailable.
- WiFi routers are local internet access/router/AP devices only, not the inter-network backhaul.
- GSM/cellular can remain an optional future fallback.
- The objective is RPi3B-to-RPi3B communication over the internet.
- ESP32-to-RPi USB serial bridge uses `[GW_JSON]` prefix protocol for JSON line framing.

Architecture priority order:
- Priority 1: Local LoRa MANET.
- Priority 2: Tailscale VPN gateway tunnel.
- Priority 3: Long-range LoRa gateway backup.
- Priority 4: GSM/cellular optional fallback.

Clarified requirements (2026-06-02):
- Default node IDs: nodeA1, nodeA2, nodeA3, nodeB1, nodeB2, nodeB3.
- Android shows only online/reachable nodes; nodes remain visible even without Android phones attached.
- One Android phone maximum per ESP32 node.
- Node list grouped by mesh/gateway in Android UI.
- Presence protocol: HELLO interval = 10 sec; offline timeout = 30 sec.
- STEP042 split into: STEP042A Node Discovery and Reachability; STEP042B Destination Messaging; STEP042C Delivery Tracking; STEP042D Store-and-Forward.
- Delivery tracking statuses: MESSAGE (sent), DELIVERED (received at destination node), SEEN (read by recipient Android user).

Future gateway code should support:
- Store-and-forward queue.
- Gateway ACK tracking.
- Heartbeat monitoring.
- Peer online detection.
- Automatic reconnect.
- Gateway relay mode.
- LoRa backup backhaul mode.

Firmware status:
- STEP038 ESP32 firmware is not final.
- It is now considered Stable STEP038 baseline firmware with STEP041 serial bridge additions.
- Existing routing logic remains valid: TTL, hopCount, duplicate suppression, Bluetooth bridge, LoRa forwarding, gateway Bluetooth-disable behavior, and STEP042A HELLO/node table discovery export fix.
- ESP32 firmware supports both NODE mode and GATEWAY mode via `[GW_JSON]` prefix parsing in `processSerialLine()`.

Simulation-first rule:
- Prefer simulation and abstraction layers before hardware-specific behavior.
- Do not introduce new real ESP32, Raspberry Pi, or Android logic unless the workbook step explicitly allows it.

Current milestone:
- STEP043 Fix LoRa HELLO Packet Format - corrected compact `BT1` receiver fix built / physical validation pending.
- STEP042A Node Discovery and Reachability - PASS for A-side `nodeA1`/`nodeA2`/`gatewayA` discovery.
- STEP042B Destination Messaging - PASS for bidirectional 2-node Android LoRa messaging on `nodeA1 <-> nodeA2`.
- Next validation activity: physically validate corrected compact HELLO reciprocal gateway discovery, then return to gatewayB Android discovery and live discovered-node destination selection.
- Do not start STEP042C Delivery Tracking or STEP042D Store-and-Forward yet.
