# Architecture

Last updated: 2026-05-23

Purpose: PUP MANET emergency messaging prototype with Android phones connected to ESP32 nodes, LoRa node-to-node transport, Raspberry Pi 3B local gateways, and RPi3B-to-RPi3B internet backhaul through Tailscale VPN.

Boundaries:
- `esp32-node-platformio/`: ESP32 PlatformIO firmware, Bluetooth SPP service, packet parser, and controlled SX1278 LoRa live-test environments.
- `android-chat-app/`: Kotlin Jetpack Compose Android app, simulation-first UI, Bluetooth permission/readiness flow, Classic Bluetooth SPP socket layer, and Step 023 Android-to-LoRa demo controls.
- `rpi-gateway/`: Python simulation-first Raspberry Pi gateway model, LoRa SPI abstraction baseline, failover, buffering, recovery, and future Tailscale VPN gateway backhaul logic.
- `docs/`: workbook, operational memory, diagrams, test procedures, and Codex task logs.

Validated local physical path:

`Android Phone A -> Bluetooth SPP -> ESP32 NODE_A -> SX1278 LoRa -> ESP32 NODE_B -> Bluetooth SPP -> Android Phone B`

Reverse direction is validated through STEP 037.

Current gateway/backhaul design direction:

`RPi3B Gateway A <-> Tailscale VPN <-> RPi3B Gateway B`

Architecture change:
- The previous WiFi router-to-router simulated satellite link is removed.
- Gateway A and Gateway B connect directly to the internet through LAN or WiFi.
- Tailscale provides encrypted private networking between the two RPi3B gateways.
- WiFi routers are local internet access/router/AP devices only, not the inter-network backhaul.
- GSM/cellular can remain an optional fallback/backhaul.
- The objective is RPi3B-to-RPi3B communication over the internet.

Future gateway code should support:
- Tailscale peer IP configuration.
- Message relay API/socket between gateways.
- Store-and-forward queue.
- Link status monitoring.
- Optional GSM fallback.

Simulation-first rule:
- Prefer simulation and abstraction layers before hardware-specific behavior.
- Do not introduce new real ESP32, Raspberry Pi, or Android logic unless the workbook step explicitly allows it.

Current next milestone:
- STEP 038 - Real Multi-Hop Relay Validation remains current unless separately validated.
- STEP 039 - Raspberry Pi Tailscale Gateway Backhaul Architecture is the next design milestone.
- Do not mark Tailscale implementation complete until RPi3B-to-RPi3B internet/VPN communication is validated.
