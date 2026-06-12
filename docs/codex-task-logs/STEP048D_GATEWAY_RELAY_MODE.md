# STEP048D -- Gateway Relay Mode

Date: 2026-06-12

Status: **IMPLEMENTED / PASS**

---

## 1. Objective

Add explicit gateway relay execution behavior to the live Raspberry Pi gateway service after STEP048C wired route-state, store-forward queue, and replay scheduling.

STEP048D implements the connected failover path:

```text
RouteStateMachine FAILOVER_ACTIVE / RECOVERING
  + peer TCP/Tailscale link connected
  + eligible MESSAGE / ACK packet
  -> gateway relay over existing TCP link
```

If relay transmission fails, the packet falls back to the STEP048C store-and-forward queue.

---

## 2. Files Changed

| File | Change |
|---|---|
| `esp32-node-platformio/raspberry-pi-gateway/gateway_service.py` | Added explicit STEP048D gateway relay mode helpers, diagnostics, relay status snapshot, and failover/recovery relay path in `send()`. |
| `rpi-gateway/tests/test_step048d_gateway_relay_mode.py` | Added integration tests for connected failover relay, recovering relay, primary direct-send preservation, non-relay packet filtering, relay-failure store-forward fallback, and copy-safe diagnostics. |

---

## 3. Preserved Behavior

No changes were made to:

- ESP32 firmware
- Android app
- MANET routing core
- LoRa forwarding behavior
- Bluetooth phone-to-node access behavior
- Bridge ACK delivery semantics
- Compact BT1 validation
- STEP042C delivery tracking
- STEP042D queue/replay module internals
- STEP048B route-state machine internals

Gateway relay mode does **not** mutate BT-MANET payloads. It preserves the packet format sent to the peer gateway and uses service-local diagnostics only.

---

## 4. Implementation Summary

### Gateway relay candidate policy

Eligible gateway relay candidates are packet dictionaries with:

```text
packetType == MESSAGE
packetType == ACK
```

Non-relay packets such as `STATUS`, `HELLO`, `nodes`, `heartbeat`, and gateway diagnostics continue through their existing paths and are not counted as gateway-relayed failover traffic.

### Gateway relay state policy

Gateway relay mode is active only when:

```text
peer TCP link is connected
AND route_state is FAILOVER_ACTIVE or RECOVERING
AND packet is a relay candidate
```

### Failure handling

If connected relay send fails:

```text
relay failure
  -> increment failed relay diagnostic counter
  -> attempt StoreForwardQueue enqueue
```

This avoids packet loss when the peer connection object exists but the actual send fails.

### Diagnostics

Added service-local diagnostics:

```python
get_gateway_relay_status()
```

Returns:

```text
gateway_id
route_state
peer_connected
stats: relayed, skipped, failed, last_relay_at
```

---

## 5. Validation

Specific STEP048D test command:

```bash
cd rpi-gateway
python3 tests/test_step048d_gateway_relay_mode.py -v
```

Result:

```text
Ran 6 tests

OK
```

Full gateway suite:

```bash
cd rpi-gateway
python3 -m unittest discover -s tests -p "test_*.py"
```

Result:

```text
Ran 203 tests in 6.035s

OK
```

Regression result:

```text
197 previous tests + 6 new STEP048D tests = 203 PASS
0 failures
0 errors
```

---

## 6. Known Notes

- Validation used `python3` because this macOS environment does not expose `python`.
- Physical failover/degradation tests remain deferred.
- STEP048D is still gateway-service execution logic only; it does not implement LoRa backup backhaul mode.

---

## 7. Next Recommended Step

Next gateway work should be one of the following, after review/commit of STEP048D:

1. Gateway ACK tracking / peer delivery diagnostics.
2. Peer online/reconnect hardening.
3. LoRa backup backhaul design step.
4. Physical degradation/failover test campaign when hardware conditions allow.
