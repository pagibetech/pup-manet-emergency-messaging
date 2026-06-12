# Architecture

Last updated: 2026-06-10

Purpose: PUP MANET emergency messaging prototype with Android phones connected to local ESP32 nodes over Bluetooth SPP, LoRa node-to-node MANET transport, Raspberry Pi 3B local gateways, primary internet/Tailscale gateway backhaul, long-range LoRa gateway backup backhaul, and validated ESP32-to-Raspberry Pi USB serial bridge.

Boundaries:
- `esp32-node-platformio/`: ESP32 PlatformIO firmware, Bluetooth SPP phone-to-node access service for normal MANET nodes, gateway Bluetooth-disabled LoRa builds, packet parser, [GW_JSON] serial bridge parsing, STEP042A HELLO/node table discovery export fix, STEP042C delivery tracking with exact ackFor correlation, STEP045A mesh-wide HELLO propagation, STEP046A controlled multi-hop lab mode, STEP047 Bluetooth access-layer diagnostics, and controlled SX1278 LoRa live-test environments.
- `android-chat-app/`: Kotlin Jetpack Compose Android app, simulation-first UI, Bluetooth permission/readiness flow, Classic Bluetooth SPP phone-to-local-node socket layer, and Step 023 Android-to-LoRa demo controls.
- `rpi-gateway/`: Python modules (store_forward_queue, replay_engine, health_event_parser, node_health_monitor, route_state_machine), gateway simulation (failover, recovery, store-forward), and tests.
- `esp32-node-platformio/raspberry-pi-gateway/`: Live gateway service (gateway_service.py) with Tailscale VPN TCP relay, USB serial bridge to ESP32 via [GW_JSON] protocol, STEP048A health event detection.
- `docs/`: workbook, operational memory, diagrams, test procedures, Codex task logs, Hermes handoff.

Validated local physical path:

`Android Phone A -> Bluetooth SPP -> ESP32 NODE_A -> SX1278 LoRa -> ESP32 NODE_B -> Bluetooth SPP -> Android Phone B`

Reverse direction is validated through STEP 037.

Validated gateway serial bridge path (STEP041):

`ESP32 Gateway Node -> USB Serial [GW_JSON] -> Raspberry Pi Gateway Service -> Tailscale VPN TCP -> Peer Gateway Service -> USB Serial [GW_JSON] -> ESP32 Gateway Node -> SX1278 LoRa`

Final gateway/backhaul design:

`Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B`

Architecture priority order:
- Priority 1: Local LoRa MANET
- Priority 2: Tailscale VPN gateway tunnel (store-and-forward scope)
- Priority 3: Long-range LoRa gateway backup
- Priority 4: GSM/cellular optional fallback

Gateway module inventory (rpi-gateway/):

| Module | Purpose | Status |
|---|---|---|
| store_forward_queue.py | In-memory deque queue engine (256 depth, type-gated, duplicate-safe) | IMPLEMENTED, 42 tests |
| replay_engine.py | ReplayScheduler (FIFO, stability-gated, throttled, retry-limited) | IMPLEMENTED, 19 tests |
| health_event_parser.py | Regex parser for [DEGRADATION]/[RECOVERY] serial lines | IMPLEMENTED, 23 tests |
| node_health_monitor.py | Thread-safe per-node health state tracking | IMPLEMENTED, 25 tests |
| route_state_machine.py | 4-state FSM (PRIMARY_LORA->DEGRADED->FAILOVER_ACTIVE->RECOVERING) | IMPLEMENTED, 34 tests |
| gateway_sim/ | Gateway simulation baseline | IMPLEMENTED, 21 tests |

Integration status with gateway_service.py:

| Module | Wired? | When |
|---|---|---|
| NodeHealthMonitor + parse_health_event | YES | STEP048A |
| StoreForwardQueue | NO | STEP048C (pending) |
| ReplayScheduler | NO | STEP048C (pending) |
| RouteStateMachine | NO | STEP048C (pending) |

Current topology limitations:
- nodeA3 has not been flashed or deployed
- GatewayB repeated reset/garbage serial output needs investigation
- Full alternate gateway reroute is not yet implemented or validated

Preserved behaviors (must not be altered by any future step):
- Routing core: TTL, hopCount, duplicate suppression, LoRa forwarding, compact BT1 relay
- STEP044: Strict compact BT1 LoRa validation
- STEP045A: Mesh-wide HELLO presence propagation
- STEP046B: Two-tier Bridge ACK model (DELIVERY authoritative, FORWARD/HOP diagnostic)
- STEP046C: Android UI sanitization (no raw ACK JSON)
- STEP047: Bluetooth phone-to-node access boundary; LoRa MANET backbone
- STEP042C: Delivery tracking state machine (MESSAGE -> DELIVERED -> SEEN)
- STEP041: ESP32-to-RPi USB serial bridge ([GW_JSON] protocol)
- STEP040B: Tailscale VPN TCP relay between gateways
- gatewayA_lora and gatewayB_lora boot with Bluetooth DISABLED

Current milestone:
- STEP048B Gateway Route State Machine - IMPLEMENTED / PASS
- STEP048A Gateway Degradation Awareness - IMPLEMENTED / PASS
- STEP042 is closed
- Next: STEP048C Store-and-Forward Gateway Integration (design reviewed)

Hermes: read `docs/codex-task-logs/HERMES_TRANSITION_HANDOFF.md` for full context.
