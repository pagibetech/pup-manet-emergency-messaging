# STEP048C -- Store-and-Forward Gateway Integration

Date: 2026-06-12

Status: **IMPLEMENTED / PASS**

---

## 1. Objective

Integrate the existing STEP042D store-and-forward modules and STEP048B route state machine into the live Raspberry Pi gateway service without changing ESP32 firmware, Android behavior, MANET routing, LoRa forwarding, Bluetooth transport, Bridge ACK semantics, compact BT1 validation, or standalone queue/replay/route module internals.

Integrated flow:

```text
GatewayRelay
  -> NodeHealthMonitor
  -> RouteStateMachine
  -> StoreForwardQueue
  -> ReplayScheduler
```

---

## 2. Files Changed

| File | Change |
|---|---|
| `esp32-node-platformio/raspberry-pi-gateway/gateway_service.py` | Added guarded imports and wiring for `StoreForwardQueue`, `ReplayScheduler`, and `RouteStateMachine`; added route-state tick helpers; added peer link-up/down replay hooks; added failover-state queueing path in `send()`; added replay send callback; added Python 3.9-compatible future annotations import. |
| `rpi-gateway/tests/test_step048c_integration.py` | Added integration tests covering component initialization, route-state local-node evaluation, connected direct send behavior, disconnected store-forward queueing, non-queueable packet filtering, replay link hooks, and replay send callback results. |

---

## 3. Preserved Behavior

No changes were made to:

- ESP32 firmware routing logic
- LoRa forwarding behavior
- Bluetooth phone-to-node access behavior
- Android application behavior
- Bridge ACK delivery semantics
- Compact BT1 validation
- STEP042 delivery tracking semantics
- STEP042 queue/replay module internals
- STEP048A health parser or node health monitor internals
- STEP048B route state machine internals

---

## 4. Implementation Summary

### Gateway service wiring

`GatewayRelay.__init__()` now initializes the following when imports are available:

- `_store_forward_queue`
- `_route_state_machine`
- `_replay_scheduler`

### Route state evaluation

Added helper methods:

- `_get_active_local_node_ids()`
- `_tick_route_state_once()`
- `_route_state_loop()`
- `get_route_state()`

The route state machine reads local nodes from `_local_nodes` and health state from `NodeHealthMonitor`.

### Store-and-forward behavior

`send()` preserves the existing direct TCP send path when a peer connection is active.

When no peer is connected:

- `PRIMARY_LORA`: packet is not queued; existing drop behavior is preserved.
- `FAILOVER_ACTIVE` / `RECOVERING`: eligible packets are queued through `StoreForwardQueue`.
- Non-queueable packets such as `STATUS`, `HELLO`, gateway node advertisements, and heartbeat-style packets remain filtered by existing queue rules.

### Replay behavior

Peer connection lifecycle hooks now notify the replay scheduler:

- `_handle_peer_link_up()` calls `ReplayScheduler.on_link_up()`.
- `_handle_peer_link_down()` calls `ReplayScheduler.on_link_down()`.
- `stop()` calls `ReplayScheduler.stop()`.

Replay uses `_send_replay_packet()` to send directly over the active TCP connection without recursively re-queueing failed replay packets.

---

## 5. Validation

Command run from `rpi-gateway/`:

```bash
python3 -m unittest discover -s tests -p "test_*.py"
```

Result:

```text
Ran 197 tests in 5.950s

OK
```

Regression result:

```text
187 existing tests + 10 new STEP048C tests = 197 PASS
0 failures
0 errors
```

---

## 6. Known Notes

- The local macOS environment has `python3` but not `python`; therefore validation used `python3`.
- Root-level unittest discovery with `-s rpi-gateway/tests` still has the existing import-path issue for `gateway_sim`. The validated command remains the handoff-equivalent command executed from `rpi-gateway/`.
- The two pre-existing untracked documentation files were left untouched unless separately staged/approved.

---

## 7. Next Recommended Step

Proceed to STEP048D only after review/commit of STEP048C:

```text
STEP048D -- Gateway Relay Mode / failover execution behavior
```

Do not begin STEP048D until STEP048C is accepted and committed.
