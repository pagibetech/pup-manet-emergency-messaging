# STEP042D-A - Store-and-Forward Requirements Definition

Date: 2026-06-09

Status: COMPLETE / Planning Only

## Scope

STEP042D-A defines store-and-forward queue requirements for gateway-buffered cross-network message delivery. No ESP32, Android, or Raspberry Pi source code was modified. No store-and-forward implementation was written.

This document is the design authority for the STEP042D-B Queue Engine, STEP042D-C Replay Engine, and STEP042D-D Validation sub-tasks.

## 1. Store-and-Forward Objective

Store-and-forward enables the Raspberry Pi gateway to buffer cross-network MANET messages when the Tailscale VPN internet backhaul is unavailable, and automatically replay them in FIFO order when the backhaul recovers.

**Context from validated architecture:**

- **Priority 1**: Local LoRa MANET (direct node-to-node, no store-and-forward needed).
- **Priority 2**: Tailscale VPN gateway tunnel (primary cross-network path, store-and-forward scope).
- **Priority 3**: Long-range LoRa gateway backup (already local, no store-and-forward needed).
- **Priority 4**: GSM/cellular optional fallback (future).

**Trigger**: Cross-network message arrives at the Raspberry Pi gateway via ESP32 USB serial (`[GW_JSON]`) and the Tailscale TCP relay to the peer gateway is unavailable.

**Goal**: Buffer the message, retry delivery when the link recovers, and notify the sender of the outcome via the existing two-tier ACK model.

**Non-goals**:

- Store-and-forward is not needed for local LoRa MANET messages (they use direct node relays).
- Store-and-forward does not buffer messages on ESP32 nodes (they have constrained RAM and no persistent storage).
- Store-and-forward does not create a new routing layer; it buffers at the gateway boundary only.
- Store-and-forward does not replace the existing TCP relay; it wraps around it.

## 2. Queue Owner Recommendation

**Recommended owner**: Raspberry Pi gateway service (`rpi-gateway/gateway_service.py`).

**Rationale**:

- The RPi gateway already receives messages from the ESP32 via USB serial (`[GW_JSON]` protocol).
- The RPi gateway already owns the Tailscale TCP relay client/server socket.
- The RPi gateway has ample RAM and optional disk for persistent storage.
- Keeping the queue on the gateway avoids adding constraints to ESP32 nodes (which have ~320KB free RAM and no filesystem suitable for persistent queues).
- The gateway is the only component that knows whether the VPN backhaul is available.

**Not recommended**:

- **ESP32 node**: RAM is too constrained for a cross-network message queue; node failure would lose all buffered messages; the node does not control VPN availability.
- **Android phone**: Cannot listen for VPN recovery events reliably; would require a service that drains battery.

## 3. Queue Storage Location

**Primary**: In-memory Python `collections.deque` (or `list`) on the Raspberry Pi gateway, with a configurable maximum depth.

**Optional persistent layer**:

- JSON-Lines append-only file (`store_forward_queue.jsonl`) on the Raspberry Pi SD card.
- Each entry is one JSON line: `{"messageId":"...","srcNode":"...","destNode":"...","originalPacket":"...","receivedAt":<unix_ts>,"expiresAt":<unix_ts>,"gatewayJson":"..."}`.
- On gateway startup, the persistent file is replayed into the in-memory queue.
- On successful forward, the entry is removed from the file (or a reaped-flag file is maintained).
- `sync`/`fsync` is called after each append to protect against sudden power loss.

**Constraints**:

- Maximum queue depth: 256 messages (hard limit, configurable).
- Maximum persistent file size: 8 MB before rotation.
- Messages beyond the depth limit are dropped with a `[STORE_FORWARD_DROP] reason=QUEUE_FULL` log and a diagnostic-only ACK to the sender.

## 4. Message Types to Queue

**Queued**: Any cross-network BT-MANET message that arrives at the gateway via `[GW_JSON]` serial when the Tailscale TCP relay to the peer gateway is unavailable.

**Specifically queued message types**:

| Message Type | Rationale |
|---|---|
| `MESSAGE` | User text/data; the primary store-and-forward payload. |
| `DELIVERY` ACK | Destination delivery acknowledgment crossing networks; must be relayed even if backhaul was briefly down. |
| `SEEN` ACK | App-level read receipt; lower priority than DELIVERY but still a crossing-network message. |
| `FORWARD` / `HOP` ACK (diagnostic) | Optional queue; can be dropped first if queue pressure occurs. |

**Not queued**:

| Message Type | Rationale |
|---|---|
| `HELLO` | Presence packets are time-sensitive; stale HELLO is useless after the interval. Drop immediately. |
| `STATUS`, `NODE_LIST`, `NEIGHBORS` | Diagnostic serial commands; do not cross networks. |
| Corrupt/invalid packets | Rejected before queue insertion by existing STEP044 validation. |
| Duplicate packets | Rejected by the existing duplicate cache before queue insertion. |
| Local MANET intra-network messages | These stay within the local LoRa mesh; the gateway should not intercept them for store-and-forward. |

## 5. ACK Interaction Rules

Store-and-forward must integrate with the existing STEP046B/STEP046C two-tier ACK model without breaking it.

**Gateway ACK to sender on queue acceptance**:

When the gateway accepts a message into the store-and-forward queue, it should send a diagnostic `FORWARD` ACK back to the source node via the ESP32 serial bridge:

```
[GW_JSON]{"packetType":"ACK","packetId":"ACK-<originalPacketId>-<gatewayId>-<timestamp>","sourceNode":"<gatewayId>","destinationNode":"<originalSourceNode>","payload":{"ackVersion":1,"ackType":"FORWARD","ackFor":"<originalPacketId>","ackStatus":"BUFFERED","originNode":"<originalSourceNode>","finalDestinationNode":"<originalDestNode>","ackSource":"<gatewayId>","reason":"QUEUED_FOR_STORE_FORWARD","route":[...]},"hopCount":0,"ttl":6,"previousHop":"<gatewayId>","timestamp":<ts>,"status":"ACK","checksum":"CHECKSUM_PLACEHOLDER"}
```

This `FORWARD` ACK with `ackStatus=BUFFERED` is a diagnostic-only ACK per the STEP046B two-tier model. It must **not** cause the Android Chat UI to display `Delivered`. The Android should treat `BUFFERED` as a diagnostic status that does not change the user-facing Bridge ACK from `Pending`.

**Destination delivery ACK during queue hold**:

If a `DELIVERY` ACK from the destination arrives at the gateway while the original message is still queued, the gateway should:

1. Forward the delivery ACK to the sender immediately (this is a cross-network ACK).
2. If the backhaul recovers, check whether the message has already been acknowledged as delivered before replaying it.

**Replay ACK on forward success**:

When a queued message is successfully replayed and the peer gateway acknowledges receipt, the local gateway forwards the final destination ACK back to the sender normally. No special replay ACK is needed beyond the normal flow.

**Android UX rule**:

- `BUFFERED` status from a gateway `FORWARD` ACK may appear in Android diagnostics but must never cause the user-facing Bridge ACK to show `Delivered`.
- The user-facing Bridge ACK must remain `Pending` until either a final destination `DELIVERY` ACK arrives or the 12-second timeout expires per STEP046B.

## 6. Replay Rules

**Order**: Strict FIFO (first queued, first replayed).

**Trigger**: The Tailscale TCP relay to the peer gateway transitions from unavailable to available.

**Replay flow**:

1. Gateway detects VPN link recovery (via TCP socket reconnect or explicit `[PEER_STATUS] Connected`).
2. Gateway logs `[STORE_FORWARD_REPLAY_BEGIN] depth=<N>`.
3. For each entry in the queue (oldest first):
   a. Validate the message is not expired (see Section 7).
   b. Check the message has not already been acknowledged as delivered (see Section 8).
   c. Re-send the `[GW_JSON]` message to the peer gateway via the TCP relay.
   d. Wait for the peer gateway to ACK the TCP relay (confirm forwarding, not delivery).
   e. On ACK received: remove from queue, log `[STORE_FORWARD_REPLAY_OK] messageId=<id>`.
   f. On retryable failure (TCP timeout, write error): keep in queue, increment retry count, log `[STORE_FORWARD_REPLAY_RETRY] messageId=<id> retry=<N>`.
   g. On permanent failure (peer rejects malformed packet): drop from queue, log `[STORE_FORWARD_DROP] messageId=<id> reason=PEER_REJECT`.
4. Gateway logs `[STORE_FORWARD_REPLAY_END] replayed=<M> remaining=<K>`.

**Replay flow for CONCURRENT replays**:

- Only one replay pass may be active at a time.
- If the VPN link flaps during replay, the pass completes the current message, then stops.
- A new replay pass begins on the next link-up event, with remaining messages still in FIFO order.

**Replay throttling**:

- Maximum replay rate: 10 messages per second (configurable).
- Minimum inter-message delay: 100 ms (configurable).
- This prevents flooding the TCP relay on recovery.

## 7. Timeout / Expiration Rules

**Message TTL**: Each queued message carries an expiration timestamp.

**Default TTL**: 300 seconds (5 minutes) from `receivedAt`. Configurable.

**On expiration**:

- The message is dropped from the queue.
- A diagnostic `FORWARD` ACK with `ackStatus=EXPIRED` is sent to the sender:
  ```
  ackStatus: "EXPIRED"
  reason: "STORE_FORWARD_TTL_EXPIRED"
  ```
- The gateway logs `[STORE_FORWARD_EXPIRED] messageId=<id> age=<seconds>`.

**Android UX on expiration**:

- The `FORWARD` ACK with `ackStatus=EXPIRED` is a diagnostic-only ACK.
- Android must not show `Delivered` or `Failed` on expiration alone.
- The existing 12-second Bridge ACK timeout in STEP046B will already have shown `Unknown` or `Delivery ACK pending` by the time the 300-second queue TTL expires. The expiration ACK serves as final confirmation that the message will not be retried.
- If Android receives an `EXPIRED` ACK after the 12-second window, it may update the diagnostic panel to show `Expired from store-and-forward queue` but must not change the user-facing Bridge ACK display from its already-resolved state.

## 8. Duplicate Prevention Rules

Store-and-forward must prevent enqueuing and replaying duplicate messages.

**At queue insertion**:

- Before enqueuing, check the `messageId` (original packet ID) against all currently queued message IDs.
- If a message with the same `messageId` is already in the queue, drop the new arrival.
- Log `[STORE_FORWARD_DUPLICATE_DROP] messageId=<id> reason=ALREADY_QUEUED`.

**At replay time**:

- Before replaying, check whether a `DELIVERY` or `SEEN` ACK for this `messageId` has already been relayed back to the sender.
- If the message has been acknowledged, do not replay it.
- Log `[STORE_FORWARD_SKIP_ACKED] messageId=<id>` and remove from queue.

**LoRa-layer duplicate protection remains separate**:

- The existing ESP32 duplicate cache (compact `BT1` packet ID tracking) prevents duplicate LoRa delivery to the local Android phone.
- The gateway's store-and-forward queue duplicate prevention is an additional layer for cross-network messages only.
- A message queued on Gateway A and replayed to Gateway B must not be treated as duplicate by Gateway B's ESP32 LoRa delivery logic (because the `packetId` is the same, and the ESP32 duplicate cache may have evicted it after TTL). The ESP32 duplicate cache TTL should be considered — if the queue holds messages for longer than the ESP32's duplicate cache window, the replayed packet will be accepted and re-delivered. This is acceptable because the Android phone's duplicate message ID tracking will suppress duplicate displays.

## 9. Failure Scenarios

| Scenario | Gateway Behavior | Sender UX | Recovery |
|---|---|---|---|
| **VPN link down at message arrival** | Enqueue with `BUFFERED` status. | `Pending` (Bridge ACK). | Auto-replay on link-up. |
| **VPN link down for full TTL (300s)** | Expire from queue. Send `EXPIRED` ACK. | `Unknown` (Bridge ACK, 12s timeout already resolved). | None. Sender must resend manually. |
| **Gateway process crash with in-memory queue** | All buffered messages lost. No ACK sent. | `Unknown` (Bridge ACK timeout). | If persistent layer exists, replay on restart. Otherwise sender must resend. |
| **Gateway crash with persistent file** | On restart, reload `store_forward_queue.jsonl` into memory. Resume normal queue lifecycle. | `Unknown` during outage. Messages delivered after recovery. | Automatic reload + replay. |
| **Queue full (depth >= 256)** | Drop new message. Send `FORWARD` ACK with `ackStatus=DROPPED`, `reason=QUEUE_FULL`. | `Unknown` (Bridge ACK timeout). Diagnostic panel may show `Dropped from store-and-forward queue (queue full)`. | Sender must resend manually. |
| **Peer gateway rejects replayed packet** | Drop from queue. Log reason. | `Unknown` (Bridge ACK timeout). | Sender must resend manually. |
| **Serial bridge to ESP32 fails during queue hold** | Queue remains. Messages cannot be forwarded or ACKed until serial recovers. | No change to UX (already `Pending`/`Unknown`). | On serial recovery, queue resume is determined by VPN link status. |
| **Duplicate message arrives while original is queued** | Drop duplicate. Log `DUPLICATE_DROP`. | No change. | No action needed. |
| **Delivery ACK for queued message arrives before replay** | Forward ACK to sender. When replay occurs, skip the message (already ACKed). | `Delivered` (Bridge ACK) upon receiving the late delivery ACK. | Message removed from queue without replay. |
| **VPN link flaps rapidly (link up/down within <5s)** | Do not start replay until link is stable for `REPLAY_STABILITY_SECONDS` (default 5s). | `Pending`. | Replay begins only after stable link for 5 seconds. |
| **Partial replay (some messages fail, some succeed)** | Successful messages are removed. Failed messages stay in queue for next replay pass. Log `STORE_FORWARD_REPLAY_PARTIAL`. | Mixed: delivered messages get `Delivered`, others stay `Pending`/`Unknown`. | Next replay pass retries remaining messages. |
| **Persistent file corruption (partial write)** | On startup, skip corrupted lines. Log `[STORE_FORWARD_LOAD_SKIP] line=<N> reason=CORRUPT`. | Some messages lost (same as crash without persistence). | Sender must resend lost messages. |

## 10. Acceptance Criteria

These criteria apply to the STEP042D-B (Queue Engine), STEP042D-C (Replay Engine), and STEP042D-D (Validation) sub-tasks collectively.

### 10A. Queue Engine (STEP042D-B)

1. Gateway service detects VPN link down before attempting cross-network forward.
2. Cross-network `MESSAGE`, `DELIVERY` ACK, and `SEEN` ACK packets are enqueued when VPN is down.
3. `HELLO` and diagnostic packets are not enqueued.
4. Duplicate messages (same `messageId` already in queue) are dropped.
5. Queue enforces maximum depth (256) and drops excess with a diagnostic ACK.
6. Queue logs `[STORE_FORWARD_BUFFERED]` on enqueue, `[STORE_FORWARD_DROP]` on drop.
7. Optional persistent JSON-Lines file is append-only and fsync'd.
8. Persistent file is loaded on gateway startup, corrupted lines are skipped.
9. Gateway sends diagnostic `FORWARD` ACK with `ackStatus=BUFFERED` on enqueue.
10. Existing LoRa routing, Bluetooth architecture, and STEP044 validation are non-regressed.

### 10B. Replay Engine (STEP042D-C)

1. Replay triggers only after VPN link has been stable for `REPLAY_STABILITY_SECONDS` (default 5s).
2. Messages are replayed in strict FIFO order.
3. Expired messages (age > TTL 300s) are skipped and dropped.
4. Messages already ACKed as `DELIVERED` or `SEEN` are skipped and dropped.
5. Replay respects throttling (max 10 msg/s, min 100ms inter-message delay).
6. Successful forward removes message from queue.
7. Retryable failure keeps message in queue for next replay pass (max retries 3 per message).
8. Permanent failure (peer reject, max retries exceeded) drops message with `[STORE_FORWARD_DROP]`.
9. Replay logs `[STORE_FORWARD_REPLAY_BEGIN]`, `[STORE_FORWARD_REPLAY_OK]`, `[STORE_FORWARD_REPLAY_RETRY]`, and `[STORE_FORWARD_REPLAY_END]`.
10. Flapping VPN link does not trigger concurrent replay passes; only one replay pass runs at a time.
11. Android user-facing Bridge ACK display is non-regressed (STEP046C sanitization preserved).
12. Existing LoRa routing, Bluetooth architecture, STEP044 validation, STEP042C delivery tracking, and STEP046A production-safe routing are non-regressed.

### 10C. Validation (STEP042D-D)

1. Queue enqueue and drop are verified in gateway simulation tests.
2. Replay FIFO order is verified in simulation.
3. Expiration behavior (TTL timeout) is verified in simulation.
4. Duplicate prevention (enqueue and replay) is verified in simulation.
5. Queue-full boundary (depth 256) is verified.
6. Persistent file write/read/recovery is verified in simulation.
7. No regression in existing gateway simulation tests: failover, recovery, store-forward demo, router-link demo, latency demo.
8. Tailscale TCP relay continues to pass its basic JSON relay test.
9. ESP32 serial bridge (`[GW_JSON]` protocol) is non-regressed.
10. Android build passes.
11. ESP32 firmware builds pass for `gatewayA_lora`, `gatewayB_lora`, `nodeA1_lora`, and `nodeA2_lora`.

## 11. Risks and Regression Controls

### Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Queue memory exhaustion under sustained VPN outage | Medium | High | Hard depth limit (256); drop oldest or reject new with clear diagnostic ACK. |
| Replayed messages arrive at destination after user has already assumed failure | High | Medium | Android UI treats late delivery as normal; `UNKNOWN -> DELIVERED` transition is allowed by STEP042C state machine. |
| Persistent file corruption on sudden power loss | Low (RPi has stable power) | Medium | Append-only JSON-Lines; skip corrupted lines on load; fsync each write. |
| ESP32 duplicate cache rejects replayed packets | Medium | Low | ESP32 duplicate cache TTL is shorter than queue TTL (typically); if it happens, Android phone dedup catches it. |
| Gateway replays flood TCP relay on link recovery | Medium | Medium | Throttling (10 msg/s) prevents flood. |
| Conflicting ACK states (BUFFERED then EXPIRED then DELIVERED out of order) | Low | Medium | State precedence rules in STEP042C apply: SEEN > DELIVERED > MESSAGE > UNKNOWN > FAILED. Late delivery ACK can still promote to DELIVERED. |
| Android interprets BUFFERED ACK as delivery | Low | High | STEP046C UI sanitization rules prevent raw ACK display; Android ACK parser must recognize `ackStatus=BUFFERED` as diagnostic only. |

### Regression Controls

The following must remain non-regressed after STEP042D implementation:

1. **STEP044** strict compact `BT1` LoRa validation.
2. **STEP045A** mesh-wide HELLO presence propagation.
3. **STEP045B** live discovered-node destination selection in Android.
4. **STEP046A** production-safe routing (TEST_FORCE_GATEWAY_ROUTE=0).
5. **STEP046B** destination-node delivery ACK authority; FORWARD/HOP ACK diagnostics only.
6. **STEP046C** user-facing Bridge ACK sanitization (no raw JSON).
7. **STEP047** Bluetooth phone-to-node access boundary; LoRa MANET backbone.
8. **STEP042A** node discovery and reachability.
9. **STEP042B** destination messaging (nodeA1 <-> nodeA2).
10. **STEP042C** delivery tracking state machine (`MESSAGE -> DELIVERED -> SEEN`).
11. **STEP041** ESP32-to-Raspberry Pi USB serial bridge (`[GW_JSON]` protocol).
12. **STEP040B** Tailscale TCP relay between gateways.

## 12. Proposed Split

STEP042D is split into three sub-tasks to isolate queue storage from replay logic and to require separate validation before declaring completion.

### STEP042D-B: Queue Engine

**Scope**: Implement the store-and-forward queue data structure, enqueue/dequeue logic, persistent storage layer, queue depth limits, duplicate prevention at insertion, and the `BUFFERED` `FORWARD` ACK generation on the Raspberry Pi gateway service.

**Files affected**: `rpi-gateway/gateway_service.py` (new queue module, optional `store_forward_queue.py`).

**Key deliverables**:
- `StoreForwardQueue` class with enqueue, dequeue, peek, depth, drop methods.
- Enqueue logic: VPN-down gate, message-type filter, duplicate check, depth check.
- `BUFFERED` FORWARD ACK generation and serial write to ESP32.
- Optional `StoreForwardPersistentQueue` subclass with JSON-Lines file backup.
- Startup reload from persistent file.
- Unit tests for enqueue, drop, duplicate prevention, depth limit.

### STEP042D-C: Replay Engine

**Scope**: Implement the replay loop, VPN stability gating, FIFO ordered replay, expiration checking, ACK correlation skip, retry logic, replay throttling, replay logging, and integration with the TCP relay.

**Files affected**: `rpi-gateway/gateway_service.py` (replay loop, link monitoring changes).

**Key deliverables**:
- Replay trigger on VPN link-up after stability gating.
- FIFO replay loop calling TCP relay for each queued message.
- Expiration check before replay.
- ACK correlation skip (do not replay already-ACKed messages).
- Retry logic (max 3 retries per message, retryable vs permanent failure).
- Replay throttling (max 10 msg/s, min 100ms inter-message).
- `[STORE_FORWARD_REPLAY_*]` logging.
- Integration with existing TCP relay send path.

### STEP042D-D: Validation

**Scope**: Run the full store-and-forward lifecycle through simulation, verify all acceptance criteria, run existing regression tests, and confirm no regressions in Android or ESP32 builds.

**Files affected**: `rpi-gateway/tests/` (new and existing tests), `rpi-gateway/gateway_sim/` (if simulation needs updates).

**Key deliverables**:
- Queue engine simulation tests: enqueue, drop, duplicate, depth limit, expiration.
- Replay engine simulation tests: FIFO order, stability gating, throttling, retry, skip-ACKed.
- Persistent storage recovery test (write, crash, reload).
- Full regression: all existing gateway simulation tests pass.
- Tailscale TCP relay basic test pass.
- ESP32 firmware builds pass (gatewayA_lora, gatewayB_lora, nodeA1_lora, nodeA2_lora).
- Android build passes (`:app:assembleDebug`).
- `git diff --check` pass.

### Task Dependency Chain

```
STEP042D-A (this document) ── DONE ──> STEP042D-B (Queue Engine)
                                      ─> STEP042D-C (Replay Engine, depends on B)
                                      ─> STEP042D-D (Validation, depends on B + C)
```

## 13. Preserved Behaviors

The following existing behaviors are explicitly preserved and must not be altered by STEP042D implementation:

- **Routing core**: TTL, hopCount, duplicate suppression, LoRa forwarding, route decisions, compact BT1 relay.
- **Bluetooth architecture**: Phone-to-node access only; no node-to-node Bluetooth transport.
- **LoRa backbone**: Compact `BT1` format remains the MANET transport.
- **Gateway ESP32 Bluetooth disable**: `gatewayA_lora` and `gatewayB_lora` boot with Bluetooth DISABLED.
- **Serial bridge**: `[GW_JSON]` prefix protocol between ESP32 and RPi.
- **TCP relay**: Tailscale VPN tunnel between Raspberry Pi gateways on port 5050.
- **Bridge ACK model**: Two-tier (DELIVERY authoritative, FORWARD/HOP diagnostic).
- **Android ACK sanitization**: No raw JSON in user-facing Bridge ACK.
- **Delivery tracking**: STEP042C state machine (`MESSAGE -> DELIVERED -> SEEN`).
- **Node discovery**: STEP042A/STEP045A mesh-wide HELLO propagation.

## Next Steps

1. Proceed to STEP042D-B Queue Engine implementation only after these requirements are accepted.
2. Implement queue data structure, enqueue logic, and persistent storage.
3. Implement STEP042D-C Replay Engine after queue engine is validated.
4. Run STEP042D-D validation after both engine sub-tasks are complete.
5. Do not modify ESP32 firmware, Android app, or existing TCP relay logic during STEP042D unless a sub-task explicitly requires a gateway service change.
