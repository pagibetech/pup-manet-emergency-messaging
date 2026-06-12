# Project Status

Last updated: 2026-06-10

Overall state: Hermes transition checkpoint. STEP048B Gateway Route State Machine IMPLEMENTED / PASS. 187/187 tests pass. STEP048C Store-and-Forward Gateway Integration design reviewed, implementation pending.

Clarified requirements recorded 2026-06-02 (no source changes yet):
- Default node IDs: nodeA1, nodeA2, nodeA3, nodeB1, nodeB2, nodeB3
- Android shows only online/reachable nodes; nodes remain visible even without Android phones attached
- One Android phone maximum per node
- Node list grouped by mesh/gateway in Android UI
- Presence protocol: HELLO interval = 10 sec; offline timeout = 30 sec
- STEP042 split into: STEP042A Node Discovery and Reachability; STEP042B Destination Messaging; STEP042C Delivery Tracking; STEP042D Store-and-Forward
- Delivery tracking statuses: MESSAGE (sent), DELIVERED (received at destination node), SEEN (read by recipient Android user)

Current branch: `step-002-003-esp32-simulation`

Latest commit: `20d337c` feat: STEP048B Gateway Route State Machine

Workbook current milestone: STEP048B Gateway Route State Machine IMPLEMENTED / PASS.

Latest physically completed workbook step: STEP047 - Bluetooth Transport Layer.

Implementation note: ESP32-to-Raspberry Pi USB serial bridge is validated end-to-end. Gateway Bluetooth-disable cleanup is validated and must not be undone. Android live discovered-node destination selection is validated; nodeA3 has not been deployed.

Completed highlights:
- STEP042: Discovery (A), Messaging (B), Delivery Tracking (C), Store-and-Forward (D)
- STEP048A: Gateway Degradation Awareness (health_event_parser, node_health_monitor)
- STEP048B: Gateway Route State Machine (4-state FSM, 10s timers)
- ESP32 simulation and multi-node simulation baseline
- ESP32 packet parser and Bluetooth service
- Android app shell, simulation engine, routing visualization, packet abstraction, queue, transport bridge, and Bluetooth readiness layers
- Android Bluetooth socket layer and live packet test support
- Raspberry Pi gateway simulation baseline, LoRa SPI abstraction, router-link simulation, latency simulation, failover, store-and-forward, and recovery simulation
- Android build validation and network selection validation
- STEP 035 real RF flow passed
- STEP 036 real Chat UI integration passed
- STEP 037 multi-hop routing foundation passed
- STEP040B Python gateway TCP relay passed over Tailscale VPN
- STEP041 ESP32 to Raspberry Pi Serial Bridge Integration complete
- Gateway Bluetooth cleanup passed
- STEP043 PASS: compact gatewayA/gatewayB discovery
- STEP044 PASS: strict compact BT1 packet validation
- STEP045A PASS: mesh-wide HELLO presence propagation
- STEP045B PASS: Android real-network cleanup
- STEP046A PASS: controlled multi-hop lab mode
- STEP047 PASS: Bluetooth phone-to-node access
- STEP046B/C PASS: Bridge ACK reliability + UI sanitization

Current gateway/backhaul design:
- Raspberry Pi 3B Gateway A <-> Tailscale VPN Tunnel over Internet <-> Raspberry Pi 3B Gateway B
- Each MANET network has 3 ESP32 LoRa nodes paired to Android phones via Bluetooth
- Each local MANET has a Raspberry Pi 3B gateway with LoRa module
- Internet backhaul is the primary gateway transport; Tailscale VPN is the primary encrypted tunnel
- LoRa-to-LoRa gateway backhaul is the backup path if internet is unavailable
- GSM/cellular remains optional future fallback

Architecture priority order:
- Priority 1: Local LoRa MANET
- Priority 2: Tailscale VPN gateway tunnel
- Priority 3: Long-range LoRa gateway backup
- Priority 4: GSM/cellular optional fallback

Current test counts:
- Queue engine (STEP042D-B): 42 tests
- Replay engine (STEP042D-C): 19 tests
- Gateway simulation: 21 tests
- Validation (STEP042D-D): 23 tests
- Health parser (STEP048A): 23 tests
- Node health monitor (STEP048A): 25 tests
- Route state machine (STEP048B): 34 tests
- Total: 187 PASS, 0 regressions

Current blockers / open issues:
- Physical distance/RSSI degradation testing deferred (no physical space)
- Phone/Bluetooth physical tests deferred (hardware unavailable)
- nodeA3 not deployed; 6-node topology incomplete
- GatewayB repeated reset/garbage serial output needs investigation

Next task: STEP048C Store-and-Forward Gateway Integration (design reviewed, ready for implementation).

Hermes: read `docs/codex-task-logs/HERMES_TRANSITION_HANDOFF.md` for full context.
