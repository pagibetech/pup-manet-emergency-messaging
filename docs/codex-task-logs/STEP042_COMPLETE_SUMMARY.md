# STEP042 -- Complete Summary

Date: 2026-06-10

Status: **COMPLETE / PASS**

## 1. STEP042 Objectives

STEP042 is the fourth major milestone in the PUP MANET Implementation Workbook. It covers the foundational MANET communication layer: node discovery, destination messaging, delivery tracking, and store-and-forward message buffering.

Split into four sub-tasks:
- **STEP042A** Node Discovery and Reachability
- **STEP042B** Destination Messaging
- **STEP042C** Delivery Tracking
- **STEP042D** Store-and-Forward (4 sub-sub-tasks: A/B/C/D)

---

## 2. STEP042A -- Node Discovery and Reachability

### Status: COMPLETE / PASS

### Root cause
Compact LoRa relay HELLO packets such as `HELLO-nodeA1` and `HELLO-nodeA2` were parsed as `MESSAGE` unconditionally. They were routed, relayed, and sometimes duplicate-dropped before node table insertion, so `NODE_LIST` exported only the local boot entry and Android showed `count=1`.

### Fix
In `esp32-node-platformio/src/main.cpp`:
- Infer compact relay HELLO packets from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId` payload
- Learn HELLO packets before route/duplicate handling
- Add `hopCount` to node table entries
- Add `[NEIGHBOR_ADD]`, `[NEIGHBOR_UPDATE]`, `[NODE_LIST_EXPORT]` logs
- Remove expired nodes before NODE_LIST export

### Physical validation
Flashed/running firmware: `nodeA1`, `nodeA2`, `gatewayA`, `gatewayB`. Android Nodes screen shows four ONLINE entries grouped by gateway. One Android phone maximum per ESP32 node. Nodes remain visible even without attached phones.

### Files
`esp32-node-platformio/src/main.cpp`

---

## 3. STEP042B -- Destination Messaging

### Status: COMPLETE / PASS

### Implementation
Bidirectional 2-node Android-to-Android LoRa bridge messaging.

### Validated paths
- `Phone A -> nodeA1 -> LoRa -> nodeA2 -> Phone B`
- `Phone B -> nodeA2 -> LoRa -> nodeA1 -> Phone A`

### Physical validation
Phone A sent `hello from A`; Phone B received via LoRa on path `nodeA1 -> nodeA2`. Phone B sent `hello from b`; Phone A received via LoRa on path `nodeA2 -> nodeA1`.

### Temporary fix
`peerNodeForConnectedEsp32()` in `MainActivity.kt` was mapped from `"nodeA2" -> "nodeA3"` to `"nodeA2" -> "nodeA1"` because `nodeA3` has not been deployed. Later steps (STEP045B) replaced the hardcoded mapping with live discovered-node destination selection.

### Files
`esp32-node-platformio/src/main.cpp`, `android-chat-app/.../MainActivity.kt`

---

## 4. STEP042C -- Delivery Tracking

### Status: COMPLETE / PASS

### Implementation
ESP32 delivery state machine with exact `ackFor` correlation:
- `DeliveryEntry` tracking table (up to 16 in-flight messages)
- States: `MESSAGE`, `DELIVERED`, `SEEN`, `FAILED`, `UNKNOWN`
- Allowed transitions: `MESSAGE -> DELIVERED -> SEEN`, `MESSAGE -> UNKNOWN`, `MESSAGE -> FAILED`, `UNKNOWN -> DELIVERED`, `UNKNOWN -> SEEN`
- State precedence: `SEEN > DELIVERED > MESSAGE > UNKNOWN > FAILED`
- `UNKNOWN` timeout (60s) only transitions `MESSAGE` state
- No substring `indexOf` fallback for ACK correlation
- Serial commands: `DELIVERY_STATUS`, `DELIVERY_SEEN <packetId>`

### Integration
Works with STEP046B Bridge ACK two-tier model: destination-node `DELIVERY` ACK drives `DELIVERED` state; `FORWARD`/`HOP` ACKs are diagnostic only. STEP046C UI sanitization hides raw ACK JSON from end users.

### Assessment
ESP32-only change. No routing decisions, route discovery, Bluetooth architecture, LoRa backbone, or Android UI changed. Builds validated for `nodeA1` and `nodeA1_lora`.

### Files
`esp32-node-platformio/src/main.cpp`, `docs/codex-task-logs/STEP042C_DELIVERY_TRACKING_DESIGN.md`

---

## 5. STEP042D -- Store-and-Forward

### Status: COMPLETE / PASS (A/B/C/D)

### STEP042D-A: Requirements Definition (Planning Only)
- Queue owner: Raspberry Pi gateway service
- Storage: in-memory deque (256 max) + optional persistent JSON-Lines file
- Message types: `MESSAGE`, `DELIVERY` ACK, `SEEN` ACK
- ACK interaction: diagnostic `FORWARD/BUFFERED` on enqueue; never `DELIVERY` tier
- Replay: strict FIFO, stability-gated (5s), throttled (10 msg/s), max 3 retries
- TTL: 300s default (configurable retry window, not disaster retention)
- Duplicate prevention at enqueue and replay
- 12 failure scenarios defined with handling, UX, and recovery
- Proposed split: B (Queue Engine), C (Replay Engine), D (Validation)

### STEP042D-B: Queue Engine
**Files**: `rpi-gateway/store_forward_queue.py`, `rpi-gateway/tests/test_store_forward_queue.py`

- `StoreForwardQueue`: in-memory deque with type-gated enqueue, duplicate detection, depth limit (256)
- `StoreForwardPersistentQueue`: JSON-Lines file backing with fsync, startup reload, rotation at 8MB
- `QueuedMessage`: dataclass with messageId, receivedAt, expiresAt, retryCount
- Ack builders: `build_buffered_ack()`, `build_dropped_ack()`, `build_expired_ack()` -- all `FORWARD` tier
- 42 unit tests

### STEP042D-C: Replay Engine
**Files**: `rpi-gateway/replay_engine.py`, `rpi-gateway/tests/test_replay_engine.py`

- `ReplayScheduler`: thread-safe coordinator with stability gating, FIFO replay, throttling, TTL verification, retry limits (3 max), concurrent prevention
- `ReplayEngine`: extends ReplayScheduler with `ack_callback` for diagnostic ACK emission on drop/expire/retry-exceeded
- `ReplayResult` enum: SUCCESS, RETRYABLE, PERMANENT, EXPIRED, RETRY_EXCEEDED
- Replay injects packets into the normal TCP transport path; does not mutate delivery state directly
- 19 unit tests

### STEP042D-D: Validation
**Files**: `rpi-gateway/tests/test_step042d_validation.py`

8 scenarios validated:

| # | Scenario | Result |
|---|---|---|
| S1 | VPN down -> MESSAGE queued -> replayed -> normal delivery path | PASS |
| S2 | VPN down -> DELIVERY ACK queued -> replayed -> sender gets Delivered | PASS |
| S3 | VPN down -> SEEN ACK queued -> replayed -> sender gets Seen | PASS |
| S4 | TTL expiration -> FORWARD/EXPIRED diagnostic -> no DELIVERY tier | PASS |
| S5 | Duplicate enqueue -> rejected | PASS |
| S6 | Link flap -> stability gate prevents replay storm | PASS |
| S7 | mark_acked -> replay skipped for acknowledged messages | PASS |
| S8 | STEP042C MESSAGE->DELIVERED->SEEN state machine preserved | PASS |

23 validation tests.

**No ESP32 firmware changes. No Android changes. Raspberry Pi gateway code only.**

---

## 6. Final Architecture

```
Phone A -> Bluetooth SPP -> ESP32 NODE_A -> LoRa -> ESP32 NODE_B -> Bluetooth SPP -> Phone B

Gateway Cross-Network Path:
ESP32 Gateway -> USB Serial [GW_JSON] -> RPi Gateway Service -> Tailscale VPN TCP -> Peer RPi -> ESP32 -> LoRa

Store-and-Forward (at RPi Gateway boundary):
[GW_JSON] message -> StoreForwardQueue.enqueue() [if VPN down]
  -> ReplayScheduler.on_link_up() [on VPN recovery]
    -> send_callback() -> TCP relay -> peer gateway -> ESP32 -> LoRa -> destination
```

### Architecture priority order
1. Local LoRa MANET
2. Tailscale VPN gateway tunnel (store-and-forward scope)
3. Long-range LoRa gateway backup
4. GSM/cellular optional fallback

---

## 7. Validation Results

| Suite | Tests | Result |
|---|---|---|
| Queue Engine (STEP042D-B) | 42 | PASS |
| Replay Engine (STEP042D-C) | 19 | PASS |
| Gateway Simulation (existing) | 21 | PASS |
| Validation (STEP042D-D) | 23 | PASS |
| **Total** | **105** | **PASS** |
| **Regressions** | **0** | **--** |

---

## 8. Preserved Behaviors

All prior steps remain unchanged:

- **Routing core**: TTL, hopCount, duplicate suppression, LoRa forwarding, compact BT1 relay
- **STEP044**: Strict compact BT1 LoRa packet validation
- **STEP045A**: Mesh-wide HELLO presence propagation
- **STEP045B**: Live discovered-node destination selection (Android)
- **STEP046A**: Production-safe routing (`TEST_FORCE_GATEWAY_ROUTE=0`)
- **STEP046B**: Two-tier Bridge ACK model (DELIVERY authoritative, FORWARD/HOP diagnostic)
- **STEP046C**: Android UI sanitization (no raw ACK JSON to end users)
- **STEP047**: Bluetooth phone-to-node access boundary; LoRa MANET backbone
- **STEP041**: ESP32-to-RPi USB serial bridge (`[GW_JSON]` protocol)
- **STEP040B**: Tailscale VPN TCP relay between gateways
- `gatewayA_lora` and `gatewayB_lora` boot with Bluetooth DISABLED
- No node-to-node Bluetooth transport; LoRa remains the MANET backbone

---

## 9. Known Limitations

- 2-node validation only: `nodeA3` has not been flashed or deployed
- Full 6-node topology (nodeA1-A3, nodeB1-B3) has not been physically validated
- GatewayB repeated reset/garbage serial output needs investigation
- Store-and-forward persistent file recovery is tested in simulation only; not on physical RPi with SD card
- Store-and-forward does not maintain per-destination queues (single global queue targets one peer gateway)
- No automatic retransmission of original messages; sender must resend manually after queue expiration
- The 300s TTL is a retry window, not a disaster-message shelf life; longer outages require TTL adjustment

---

## 10. Future Enhancements

- Full 6-node topology deployment and validation
- GatewayB serial stability investigation
- Multi-gateway store-and-forward (per-destination queues for 3+ gateways)
- Persistent queue crash-recovery validation on physical RPi hardware
- Automatic MESSAGE retransmission after queue expiration
- Long-range LoRa gateway backup backhaul integration
- GSM/cellular optional fallback
- Gateway ACK tracking, heartbeat monitoring, peer online detection, automatic reconnect

---

## 11. Final Status

**STEP042 -- COMPLETE / PASS**

All four sub-tasks (A, B, C, D) are implemented, physically validated where applicable, and tested. 105/105 automated tests pass with zero regressions across queue engine, replay engine, gateway simulation, and validation suites.

The PUP MANET project now supports:
- Mesh-wide node discovery with group-aware Android display
- Bidirectional Android-to-Android LoRa messaging
- End-to-end delivery tracking (`MESSAGE -> DELIVERED -> SEEN`)
- Store-and-forward message buffering at the gateway boundary with VPN recovery replay

Next workbook task: continue from the next approved step.
