# Hermes Transition Handoff

Date: 2026-06-10

Transitioning from: ChatGPT (OpenCode CLI)
Transitioning to:   Hermes

---

## 1. READ THIS FIRST

Hermes, before doing anything else:

1. Read `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`
2. Read `docs/ACTIVE_CONTEXT.md`
3. Read `docs/AI_SESSION_HANDOFF.md`
4. Read `docs/PROJECT_STATUS.md`
5. Read `docs/ARCHITECTURE.md`
6. Read this file (`docs/codex-task-logs/HERMES_TRANSITION_HANDOFF.md`)
7. Run `cd rpi-gateway && python -m unittest discover -s tests -p "test_*.py"` -- should show 187 PASS
8. Do NOT implement anything until you confirm current status and next step

---

## 2. Project Identity

**Project**: PUP MANET Emergency Messaging Prototype
**Repository**: `https://github.com/pagibetech/pup-manet-emergency-messaging`
**Local path**: `/Users/macbookm1max321tb/A_Design/A_Coding/ADM_Manet`
**Branch**: `step-002-003-esp32-simulation`
**Latest commit**: `20d337c` -- `feat: STEP048B Gateway Route State Machine`
**Workbook**: `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

---

## 3. Completed Milestones (chronological order)

| Step | Name | Phase | Status |
|---|---|---|---|
| STEP 023 | End-to-End Android-LoRa-Android Messaging | Integration | PASS |
| STEP 024 | Raspberry Pi Gateway Simulation Baseline | Gateway | PASS |
| STEP 035 | Real End-to-End LoRa Message Delivery | Integration | PASS |
| STEP 036 | Real Chat UI Integration | Android | PASS |
| STEP 037 | Multi-Hop Routing Foundation | Routing | PASS |
| STEP 038 | Stable Baseline Firmware | Firmware | PASS |
| STEP040B | Python Gateway TCP Relay Service (Tailscale) | Gateway | PASS |
| STEP041 | ESP32-to-RPi Serial Bridge Integration | Gateway | PASS |
| STEP042A | Node Discovery and Reachability | Discovery | PASS |
| STEP042B | Destination Messaging | Messaging | PASS |
| STEP042C | Delivery Tracking | Tracking | PASS |
| STEP042D | Store-and-Forward (A/B/C/D) | Gateway | PASS |
| STEP043 | Fix LoRa HELLO Packet Format | Discovery | PASS |
| STEP044 | Strict Compact BT1 Packet Validation | LoRa | PASS |
| STEP045A | Mesh-Wide Presence Propagation | Discovery | PASS |
| STEP045B | Android Real-Network Cleanup | Android | PASS |
| STEP046A | Controlled Multi-Hop Lab Mode | Routing | PASS |
| STEP046B | Bridge ACK Reliability Improvement | Reliability | PASS |
| STEP046B-A | Bridge ACK Requirements Definition | Reliability | PASS |
| STEP046C | Bridge ACK UI Sanitization | Reliability | PASS |
| STEP047 | Bluetooth Transport Layer | Bluetooth | PASS |
| STEP048A | Gateway Degradation Awareness | Gateway | PASS |
| STEP048B | Gateway Route State Machine | Gateway | PASS |

---

## 4. Current Test Counts

| Suite | Tests |
|---|---|
| Queue engine (STEP042D-B) | 42 |
| Replay engine (STEP042D-C) | 19 |
| Gateway simulation | 21 |
| Validation (STEP042D-D) | 23 |
| Health event parser (STEP048A) | 23 |
| Node health monitor (STEP048A) | 25 |
| Route state machine (STEP048B) | 34 |
| **Total** | **187 PASS, 0 failures** |

Run command: `cd rpi-gateway && python -m unittest discover -s tests -p "test_*.py"`

---

## 5. Current Architecture

```
Phone A -> Bluetooth SPP -> ESP32 NODE_A -> LoRa -> ESP32 NODE_B -> Bluetooth SPP -> Phone B

Gateway Cross-Network:
ESP32 Gateway -> USB Serial [GW_JSON] -> Raspberry Pi Gateway Service
  -> Tailscale VPN TCP -> Peer Raspberry Pi -> ESP32 -> LoRa
```

**Architecture priority**: (1) Local LoRa MANET, (2) Tailscale VPN gateway tunnel, (3) Long-range LoRa gateway backup, (4) GSM/cellular fallback.

### Gateway modules (rpi-gateway/)

| Module | Purpose | Tests |
|---|---|---|
| `store_forward_queue.py` | In-memory deque, 256 depth, type-gated, duplicate-safe | 42 |
| `replay_engine.py` | ReplayScheduler: FIFO, stability-gated, throttled, retry-limited | 19 |
| `health_event_parser.py` | Regex parser for `[DEGRADATION]`/`[RECOVERY]` serial lines | 23 |
| `node_health_monitor.py` | Thread-safe per-node health state tracking | 25 |
| `route_state_machine.py` | 4-state FSM (PRIMARY_LORA→DEGRADED→FAILOVER_ACTIVE→RECOVERING) | 34 |
| `gateway_sim/` | Gateway simulation baseline (failover, recovery, store-forward) | 21 |
| `tests/` | All test files | -- |

### Integration status

| Module | Imported by gateway_service.py? | When wired |
|---|---|---|
| `NodeHealthMonitor` | YES (STEP048A) | STEP048A |
| `parse_health_event` | YES (STEP048A) | STEP048A |
| `StoreForwardQueue` | NO | STEP048C |
| `ReplayScheduler` | NO | STEP048C |
| `RouteStateMachine` | NO | STEP048C |

---

## 6. Preserved Rules for Hermes

When implementing any task:

- **Preserve MANET routing behavior**: TTL, hopCount, duplicate suppression, LoRa forwarding, compact BT1 relay
- **Preserve LoRa forwarding behavior**: SX1278 Ra-02 at 433 MHz, compact BT1 format, STEP044 strict validation
- **Preserve Bluetooth phone-to-node access**: Bluetooth is ONLY for Android→local ESP32; NOT a node-to-node transport
- **Preserve Bridge ACK behavior**: Two-tier ACK model; DELIVERY authoritative, FORWARD/HOP diagnostic; STEP046C UI sanitization
- **Preserve compact BT1 validation**: STEP044 strict parsing; reject corrupt packets before neighbor learning
- **Preserve STEP042 state machine**: MESSAGE → DELIVERED → SEEN transitions
- **Do NOT redesign architecture** unless explicitly approved
- **Follow workbook** as primary source of truth
- **Work on one task at a time**
- **Keep ESP32, Android, and RPi boundaries separated**
- **Never use `git add .`**; stage only explicit approved files

---

## 7. Current Blockers and Deferred Tests

| Item | Status |
|---|---|
| Physical distance/RSSI degradation testing | DEFERRED -- no physical space for attenuation |
| Phone/Bluetooth physical tests | DEFERRED -- hardware not available for this session |
| nodeA3 deployment | NOT DONE -- 2-node validation only |
| 6-node topology (nodeB1-B3) | NOT DONE |
| GatewayB serial reset issue | OPEN -- needs investigation |
| Store-and-forward persistent file recovery on physical RPi | TESTED IN SIM ONLY |

---

## 8. Hardware Status

| Hardware | Availability |
|---|---|
| nodeA1 and nodeA2 (ESP32 + LoRa) | Connected via USB to Mac |
| gatewayA (ESP32 + LoRa) | Flashed, USB serial to RPi-A |
| gatewayB (ESP32 + LoRa) | Flashed, USB serial to RPi-B |
| Raspberry Pi gateway A | Accessible via SSH |
| Raspberry Pi gateway B | Accessible via SSH |
| Tailscale VPN tunnel | Configured and validated |
| Android phones (2) | Available for physical testing |
| nodeA3, nodeB1-B3 | Not deployed |

---

## 9. Next Recommended Activity

**STEP048C: Store-and-Forward Gateway Integration**

### What STEP048C does
Wires 3 existing standalone modules into `gateway_service.py`:
- `StoreForwardQueue` → enqueue messages when VPN is down
- `ReplayScheduler` → replay queued messages when VPN recovers
- `RouteStateMachine` → background tick evaluating node health → route state

### What exists (ready to wire)
- `StoreForwardQueue` in `rpi-gateway/store_forward_queue.py` (42 tests, type-gated, duplicate-safe)
- `ReplayScheduler` in `rpi-gateway/replay_engine.py` (19 tests, FIFO, stability-gated, throttled)
- `RouteStateMachine` in `rpi-gateway/route_state_machine.py` (34 tests, 4-state FSM)
- `NodeHealthMonitor` already wired into `gateway_service.py` (STEP048A)

### What changes
- `gateway_service.py` only: ~60 lines added across `__init__()`, `send()`, connection hooks, background tick thread
- New integration tests: `rpi-gateway/tests/test_step048c_integration.py` (~15 tests)
- Zero module internals change

### Design review
See `docs/codex-task-logs/STEP048B_ROUTE_STATE_MACHINE_SPEC.md` for the previous design. STEP048C design review is in this conversation context.

### Hermes instructions for STEP048C
1. Study `STEP048C design review` in this document
2. Study `gateway_service.py` (especially `send()`, `_client_loop()`, `_server_accept_loop()`, `start()`, `stop()`)
3. Study `store_forward_queue.py`, `replay_engine.py`, `route_state_machine.py` APIs
4. Implement wiring in `gateway_service.py` only
5. Create integration tests
6. Run 187 + new tests -- expect 200+ total PASS
7. Do NOT modify ESP32 firmware, Android app, or existing standalone modules

### What NOT to do yet
- Do NOT implement STEP048D Gateway Relay Mode
- Do NOT change routing behavior
- Do NOT change LoRa/Bluetooth/ACK behavior
- Do NOT implement physical test campaigns (8.2, 8.3, 8.4, 8.5)

---

## 10. Git Rules for Hermes

```
NEVER:  git add .
ALWAYS: git add <explicit file list>
BEFORE COMMIT: show git status, git diff --stat, staged files
COMMIT: descriptive message with step reference
PUSH: after successful validation only
```

---

## 11. Key File Locations

| What | Path |
|---|---|
| Workbook | `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx` |
| Active context | `docs/ACTIVE_CONTEXT.md` |
| Session handoff | `docs/AI_SESSION_HANDOFF.md` |
| Project status | `docs/PROJECT_STATUS.md` |
| Architecture | `docs/ARCHITECTURE.md` |
| AGENTS rules | `AGENTS.md` |
| Gateway modules | `rpi-gateway/*.py` |
| Gateway tests | `rpi-gateway/tests/test_*.py` |
| ESP32 firmware | `esp32-node-platformio/src/main.cpp` |
| Gateway service | `esp32-node-platformio/raspberry-pi-gateway/gateway_service.py` |
| Android app | `android-chat-app/` |
