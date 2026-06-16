# STEP048B -- Gateway Route State Machine Specification

Date: 2026-06-10

Status: **Design Only / No Implementation**

---

## 1. Route States

```
PRIMARY_LORA        The gateway's local LoRa MANET is healthy.
                    All messages route through local LoRa (Priority 1).

DEGRADED            At least one local MANET node is degraded.
                    Direct local LoRa is still attempted but may fail.
                    Route preference starts shifting toward gateway relay.

FAILOVER_ACTIVE     Sustained degradation. Local LoRa is no longer reliable.
                    Gateway forwards cross-network traffic through Tailscale VPN
                    (Priority 2). Store-and-forward may be active if VPN is also down.

RECOVERING          Node health has improved but the recovery window has not yet
                    completed. Traffic continues through the failover path until
                    the stable window proves recovery is sustained.
```

### State precedence

```
PRIMARY_LORA  (startup state, all local nodes healthy)
       ↓
DEGRADED      (≥1 local node degraded for < FAILOVER_SECONDS)
       ↓
FAILOVER_ACTIVE (≥1 local node degraded for ≥ FAILOVER_SECONDS)
       ↓
RECOVERING     (all local nodes recovered, waiting RECOVERY_SECONDS)
       ↓
PRIMARY_LORA   (RECOVERY_SECONDS elapsed with all nodes healthy)
```

---

## 2. Entry Criteria

| State | Entry Condition |
|---|---|
| PRIMARY_LORA | Initial state at gateway startup. Also the terminal recovery state. |
| DEGRADED | `NodeHealthMonitor.get_degraded_nodes()` returns ≥ 1 node that belongs to this gateway's local MANET. |
| FAILOVER_ACTIVE | `DEGRADED` has been true for `FAILOVER_SECONDS` (default: 10s). At least one local node still degraded. |
| RECOVERING | From `FAILOVER_ACTIVE` or `DEGRADED`: `NodeHealthMonitor.get_degraded_nodes()` returns zero local nodes. |

### How the gateway knows which nodes are "local"

The gateway already tracks local nodes in `GatewayRelay._local_nodes` (populated by `_ingest_local_node_from_packet`). For this state machine, "local" means: any node whose `last_seen` is within the expiry window (35s) in `_local_nodes`, AND whose `NodeHealthMonitor.is_degraded()` returns True.

---

## 3. Exit Criteria

| State | Exit Condition |
|---|---|
| PRIMARY_LORA | `NodeHealthMonitor` reports any local node as degraded. |
| DEGRADED | All local nodes recovered → RECOVERING. OR FAILOVER_SECONDS elapsed while still degraded → FAILOVER_ACTIVE. |
| FAILOVER_ACTIVE | All local nodes recovered → RECOVERING. |
| RECOVERING | `RECOVERY_SECONDS` (default: 10s) elapsed with zero local nodes degraded → PRIMARY_LORA. If any local node degrades again during the window → return to FAILOVER_ACTIVE. |

---

## 4. Timers

| Timer | Default | Purpose |
|---|---|---|
| `FAILOVER_SECONDS` | 10.0 | Minimum time in DEGRADED before escalating to FAILOVER_ACTIVE |
| `RECOVERY_SECONDS` | 10.0 | Minimum time with all nodes healthy before returning to PRIMARY_LORA |
| `HEALTH_CHECK_INTERVAL` | 1.0 | How frequently the state machine evaluates `NodeHealthMonitor` |

These match the simulation constants (`simulator.py:13-14`) and the ESP32 degradation constants (`main.cpp:161-162`).

### Timer behavior

**Degradation timer** (`degraded_since`):
- Started when first entering DEGRADED state
- If all nodes recover before FAILOVER_SECONDS, timer is reset (goes to RECOVERING)
- If FAILOVER_SECONDS elapses while still degraded, escalate to FAILOVER_ACTIVE

**Recovery timer** (`recovered_since`):
- Started when entering RECOVERING state
- If any node degrades again within RECOVERY_SECONDS, return to FAILOVER_ACTIVE
- If RECOVERY_SECONDS elapses with all nodes healthy, return to PRIMARY_LORA

---

## 5. State Transition Diagram

```
                     ┌──────────────────────────────────────────────┐
                     │                                              │
                     ▼                                              │
              ┌──────────────┐                                      │
     ┌───────▶│ PRIMARY_LORA │◀─────────┐                           │
     │        └──────┬───────┘          │                           │
     │               │ any local node   │ all local nodes           │
     │               │ degraded         │ healthy for               │
     │               ▼                  │ RECOVERY_SECONDS          │
     │        ┌──────────┐             │                           │
     │        │ DEGRADED ├─────────────┤                           │
     │        └─────┬────┘             │                           │
     │              │ all local nodes  │                           │
     │              │ healthy BEFORE   │                           │
     │              │ FAILOVER_SECONDS │                           │
     │              │                  │                           │
     │              │ ≥1 node still    │                           │
     │              │ degraded AFTER   │                           │
     │              │ FAILOVER_SECONDS │                           │
     │              ▼                  │                           │
     │        ┌────────────────┐      │                           │
     │        │ FAILOVER_ACTIVE├──────┘                           │
     │        └───────┬────────┘  all local nodes healthy         │
     │                │                                            │
     │                ▼                                            │
     │        ┌────────────┐                                      │
     │        │ RECOVERING ├──────────────────────────────────────┘
     │        └─────┬──────┘  any local node degrades again
     │              │          during RECOVERY_SECONDS
     │              │
     │              │ (returns to FAILOVER_ACTIVE)
     │              │
     └──────────────┘
```

### Transition matrix

| From → To | Trigger | Timer |
|---|---|---|
| PRIMARY_LORA → DEGRADED | Any local node degraded | -- |
| DEGRADED → RECOVERING | All local nodes healthy | Before FAILOVER_SECONDS |
| DEGRADED → FAILOVER_ACTIVE | Still degraded | After FAILOVER_SECONDS |
| FAILOVER_ACTIVE → RECOVERING | All local nodes healthy | -- |
| RECOVERING → PRIMARY_LORA | All local nodes healthy | After RECOVERY_SECONDS |
| RECOVERING → FAILOVER_ACTIVE | Any local node degrades again | During RECOVERY_SECONDS |

---

## 6. Interaction with NodeHealthMonitor

The route state machine is a **consumer** of `NodeHealthMonitor` data. It does not modify health state.

```python
# Pseudocode for the health evaluation loop
def _evaluate_route_state(self):
    local_nodes = self._get_local_node_ids()  # from GatewayRelay._local_nodes
    degraded = [n for n in local_nodes if self._health_monitor.is_degraded(n)]

    if not degraded:
        # All healthy
        if self._route_state in ("DEGRADED", "FAILOVER_ACTIVE"):
            self._transition_to(RECOVERING)
        elif self._route_state == "RECOVERING":
            if self._time_in_recovering() >= RECOVERY_SECONDS:
                self._transition_to(PRIMARY_LORA)
        # PRIMARY_LORA: no change
    else:
        # At least one node degraded
        if self._route_state == "PRIMARY_LORA":
            self._transition_to(DEGRADED)
        elif self._route_state == "DEGRADED":
            if self._time_in_degraded() >= FAILOVER_SECONDS:
                self._transition_to(FAILOVER_ACTIVE)
        elif self._route_state == "RECOVERING":
            self._transition_to(FAILOVER_ACTIVE)
        # FAILOVER_ACTIVE: remain
```

### Data flow

```
ESP32 -> [DEGRADATION] serial line
         -> gateway_service._serial_read_loop
           -> parse_health_event() -> NodeHealthMonitor._apply_event()
                                      -> record.degraded = True

RouteStateMachine._evaluate_route_state()  [runs every HEALTH_CHECK_INTERVAL]
  -> NodeHealthMonitor.is_degraded(node_id)  [reads, does not write]
  -> local_nodes = GatewayRelay._local_nodes [reads, does not write]
  -> transition to DEGRADED / FAILOVER_ACTIVE / RECOVERING / PRIMARY_LORA
```

---

## 7. Interaction with StoreForwardQueue

The route state machine does **not** directly interact with the `StoreForwardQueue`. That is STEP048C's scope.

However, the state machine provides the **decision signal** that STEP048C will consume:

| State | Queue behavior (STEP048C) |
|---|---|
| PRIMARY_LORA | Store-and-forward NOT needed (local LoRa works) |
| DEGRADED | Store-and-forward NOT triggered yet (still trying local) |
| FAILOVER_ACTIVE | Store-and-forward MAY be used if VPN is also down |
| RECOVERING | Store-and-forward continues if active; not newly triggered |

The `route_state` attribute is what STEP048C will read to decide whether to enqueue outgoing messages.

---

## 8. Interaction with ReplayEngine

The route state machine does **not** directly interact with the `ReplayEngine`. That is STEP048C's scope.

However, the state machine provides the **trigger signal** that STEP048C will consume:

| State transition | Replay behavior (STEP048C) |
|---|---|
| Any state → FAILOVER_ACTIVE | Do NOT trigger replay (degradation is not a link-up event) |
| FAILOVER_ACTIVE with Tailscale down | Queue messages (STEP048C) |
| Tailscale link UP while in FAILOVER_ACTIVE or RECOVERING | Trigger `ReplayEngine.on_link_up()` to flush queue |

---

## 9. Failure Scenarios

| Scenario | Behavior |
|---|---|
| No nodes ever seen by health monitor | State remains PRIMARY_LORA. No false degradation. |
| Node degrades then recovers within 3s | DEGRADED → RECOVERING (before failover timer expires). No escalation. |
| Node degrades for 15s then recovers | DEGRADED → FAILOVER_ACTIVE (after 10s) → RECOVERING → PRIMARY_LORA (after 10s). |
| Multiple nodes degrade at different times | State follows the most degraded node. DEGRADED when first node degrades; FAILOVER_ACTIVE 10s later if any still degraded. |
| Recovery window interrupted by new degradation | RECOVERING → FAILOVER_ACTIVE immediately. Recovery timer resets. |
| Gateway restarts while FAILOVER_ACTIVE | State starts at PRIMARY_LORA. Health monitor is empty (no ESP32 events yet). Will re-enter DEGRADED/FAILOVER_ACTIVE as events arrive. |
| Health monitor has data for remote nodes (from peer gateway advertisements) | Remote nodes are excluded from local degradation evaluation. Only `_local_nodes` are checked. |
| Node is in `_local_nodes` but never had a health event | `NodeHealthMonitor.is_degraded()` returns False for unknown nodes. Treated as healthy. |
| Gateway service has no serial connection (`serial_enabled=False`) | `_health_monitor` is still initialized. No health events arrive. All nodes remain "healthy" (default). State stays PRIMARY_LORA. |

---

## 10. Acceptance Criteria

| # | Criterion |
|---|---|
| AC1 | `route_state` starts as `"PRIMARY_LORA"` at gateway startup. |
| AC2 | When ≥1 local node is reported degraded, state transitions to `"DEGRADED"` within `HEALTH_CHECK_INTERVAL` (1s). |
| AC3 | After `FAILOVER_SECONDS` (10s) in `DEGRADED` with ≥1 node still degraded, state transitions to `"FAILOVER_ACTIVE"`. |
| AC4 | When all local nodes recover (all healthy), state transitions to `"RECOVERING"` from either `DEGRADED` or `FAILOVER_ACTIVE`. |
| AC5 | After `RECOVERY_SECONDS` (10s) in `RECOVERING` with all nodes healthy, state transitions to `"PRIMARY_LORA"`. |
| AC6 | If a node degrades during the `RECOVERING` window, state returns to `"FAILOVER_ACTIVE"` immediately. |
| AC7 | A node that recovers before `FAILOVER_SECONDS` elapses does NOT cause failover. |
| AC8 | Remote nodes (from peer gateway advertisements) do NOT affect route state. |
| AC9 | Nodes with no health events (unknown to `NodeHealthMonitor`) are treated as healthy. |
| AC10 | State transitions are logged with: `[ROUTE_STATE] <old_state> -> <new_state> reason=<trigger>`. |
| AC11 | `get_route_state()` is thread-safe (lock-protected). |
| AC12 | 153 existing tests continue to pass; new route state machine tests pass. |
| AC13 | No ESP32/Android/gateway routing changes. |

---

## 11. Regression Risks

| Risk | Severity | Mitigation |
|---|---|---|
| RouteStateMachine is a new background thread | Low | Thread-safe design with `threading.Lock`; does not block serial or TCP loops |
| State transitions are triggered by health events from a different thread | Low | `NodeHealthMonitor` is already thread-safe (STEP048A); state machine reads only |
| `_local_nodes` is accessed from state machine thread and `_serial_read_loop` | Low | Reuse existing `_nodes_lock` in `GatewayRelay` |
| Module is not importable on systems without `rpi-gateway/` | Low | Import guard (try/except) identical to STEP048A pattern |
| No existing code paths reference `route_state` | Low | State machine is additive; STEP048C will be the first consumer |

---

## 12. Validation Plan

### Unit tests (~25 tests)

| Category | Tests |
|---|---|
| State initialization | 1 test: starts at PRIMARY_LORA |
| Single-node degrade/recover | 5 tests: degrade → DEGRADED, degrade+10s → FAILOVER_ACTIVE, recover → RECOVERING, recover+10s → PRIMARY_LORA, quick recovery prevents failover |
| Multi-node scenarios | 4 tests: first degraded triggers DEGRADED, second degraded during DEGRADED keeps timer, all recovered → RECOVERING, mix of healthy/degraded |
| Recovery window interruption | 2 tests: new degradation during RECOVERING → FAILOVER_ACTIVE, full cycle with interruption |
| Remote node isolation | 2 tests: remote node degraded does not affect state, local/remote mix |
| Unknown node handling | 1 test: node without health events treated as healthy |
| Timer edge cases | 3 tests: exactly at FAILOVER_SECONDS boundary, exactly at RECOVERY_SECONDS boundary, state during timer tick |
| Thread safety | 2 tests: concurrent state reads/writes, rapid flip-flop cycles |
| Logging format | 2 tests: transition log format, non-transition (stable state) log silence |
| Regression | 3 tests: 153 existing tests still pass, no imports of ESP32/Android modules, no mutation of existing gateway state |

### Integration test

Simulate a full degradation-to-recovery cycle using mock `NodeHealthMonitor` events and verify:
1. t=0: PRIMARY_LORA
2. t=1: nodeA1 reported degraded → DEGRADED
3. t=11: still degraded → FAILOVER_ACTIVE
4. t=15: nodeA1 reported recovered → RECOVERING
5. t=25: stable for 10s → PRIMARY_LORA

### File plan

```
rpi-gateway/route_state_machine.py          [new, ~180 lines]
rpi-gateway/tests/test_route_state_machine.py [new, ~250 lines]
```

No changes to `gateway_service.py` in STEP048B. Integration with the gateway is deferred to STEP048C (store-and-forward integration) or a dedicated integration step.

---

## Summary

| Aspect | Value |
|---|---|
| States | 4 (PRIMARY_LORA, DEGRADED, FAILOVER_ACTIVE, RECOVERING) |
| Timers | 2 (FAILOVER_SECONDS=10s, RECOVERY_SECONDS=10s) |
| Health dependency | `NodeHealthMonitor.is_degraded()` (read-only) |
| Node source | `GatewayRelay._local_nodes` (read-only) |
| Thread model | Background evaluation thread, lock-protected state |
| ESP32 changes | 0 |
| Android changes | 0 |
| Gateway service changes | 0 (standalone module importable by gateway) |
| Tests | ~25 new + 153 existing regression |
