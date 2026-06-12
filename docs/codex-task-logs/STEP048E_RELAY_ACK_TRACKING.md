# STEP048E -- Gateway Relay ACK Tracking

Date: 2026-06-12

Status: **IMPLEMENTED / PASS**

---

## 1. Objective

Add gateway-to-gateway relay packet acknowledgment tracking so the source gateway knows whether a relayed packet was accepted and acknowledged by the peer gateway.

Before STEP048E, the gateway relayed packets during FAILOVER_ACTIVE/RECOVERING but had no visibility into whether the peer received and acknowledged them. STEP048E closes this observability gap.

---

## 2. Files Changed

| File | Change |
|---|---|
| `esp32-node-platformio/raspberry-pi-gateway/gateway_service.py` | Added `_relay_tracking` dict, `_relay_track()`, `_relay_ack()`, `_process_peer_ack()`, `get_relay_tracking()`; wired relay tracking into `_relay_gateway_packet()`; enhanced outbound peer ACK with `ref_packet_id`; wired incoming peer ACK processing into `_rx_loop`. |
| `rpi-gateway/tests/test_step048e_relay_ack_tracking.py` | 14 integration tests covering tracking initialization, relay record/pending, ACK correlation, idempotent ACK, independent concurrent tracking, max-entries enforcement, peer-ACK processing, relay-send integration, and diagnostic snapshot safety. |

---

## 3. Preserved Behavior

No changes to ESP32 firmware, Android app, MANET routing, LoRa forwarding, Bluetooth, Bridge ACK, compact BT1, or STEP042 semantics.

The enhanced `ref_packet_id` field in peer ACKs is additive and backwards-compatible: peer gateways without STEP048E simply ignore the field.

---

## 4. Implementation Summary

### Relay Tracking Table

Thread-safe bounded dict (max 100 entries) keyed by `packetId`:

```text
packetId -> {status: pending|acknowledged, src_node, dest_node, sent_at, acked_at}
```

Oldest entries are evicted when the table is full.

### Send-Side Tracking

`_relay_gateway_packet()` now calls `_relay_track()` before sending. The entry is created in `pending` state.

### Receive-Side ACK Processing

`_rx_loop` now intercepts incoming `type: ack` messages and calls `_process_peer_ack()`, which extracts `ref_packet_id` and marks the corresponding entry `acknowledged`.

The outbound peer ACK for non-heartbeat/non-ack messages is enhanced with `ref_packet_id` from the original message's `packetId` field.

### Diagnostics

`get_relay_tracking()` returns a snapshot:

```text
gateway_id, pending count, acknowledged count, total_tracked, max_entries, entries dict
```

Returned data is a deep copy — mutations to the returned dict do not affect internal tracking.

---

## 5. Validation

STEP048E test command:

```bash
cd rpi-gateway && python3 tests/test_step048e_relay_ack_tracking.py -v
```

Result:

```text
Ran 14 tests — OK
```

Full gateway suite:

```bash
cd rpi-gateway && python3 -m unittest discover -s tests -p "test_*.py"
```

Result:

```text
Ran 217 tests — OK
```

Regression:

```text
203 previous + 14 STEP048E = 217 PASS, 0 failures
```

---

## 6. Next Recommended Step

STEP048F: Peer online detection hardening. The current peer liveness model relies solely on TCP socket state and heartbeat send success. Adding explicit peer-last-seen timestamps and peer-online state tracking improves failover decision reliability before adding LoRa backup backhaul.
