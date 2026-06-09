# STEP042C Delivery Tracking Implementation Review

Date: 2026-06-09

Status: COMPLETE / PASS — DEFECTS FOUND AND FIXED, ACK CORRELATION HARDENED

## Files Reviewed

- `esp32-node-platformio/src/main.cpp`

## Build Verification

```bash
pio run -e nodeA1       # SUCCESS
pio run -e nodeA1_lora  # SUCCESS
```

## 1. Current Correlation Logic Found

### ACK Receipt Handling (`routeLoRaProtocolPacket`, line ~1613)
Original code called:
```cpp
updateDeliveryState(packet.packetId, DELIVERY_STATE_DELIVERED);
```
where `packet.packetId` is the **ACK's own generated ID** (e.g. `ACK-BT-MSG-001-nodeA1-12345`).

### `updateDeliveryState` (`line ~220`)
Original implementation:
1. Attempted exact match via `findDeliveryTrackingIndex(packetId)`.
2. On failure, performed a **fallback substring search**:
   ```cpp
   for (size_t i = 0; i < deliveryTrackingCount; ++i) {
     if (packetId.indexOf(deliveryTrackingTable[i].packetId) >= 0) {
       idx = static_cast<int>(i);
       break;
     }
   }
   ```

### Delivery Tracking Initialization (`line ~2100`)
Correctly initialized with the **original MESSAGE `packetId`** when a Bluetooth MESSAGE was forwarded to LoRa:
```cpp
initDeliveryTracking(result.packet.packetId, result.packet.destinationNode);
```

## 2. Is Implementation Correct? (Original)

**NO**

## 3. Risks Discovered

| Risk | Severity | Description |
|------|----------|-------------|
| Wrong correlation key for ACK | **Critical** | `routeLoRaProtocolPacket` passed the ACK's own `packetId` instead of the original message ID (`ackFor`). |
| Non-deterministic fallback match | **Critical** | The `indexOf` fallback could match the **wrong message** when multiple messages are in flight if one `packetId` is a substring of another (e.g. `MSG-1` vs `MSG-10`). The first table entry whose ID is a substring of the ACK ID wins, which is order-dependent and therefore non-deterministic. |
| DELIVERED/SEEN misassignment | **Critical** | Combined, the two issues above allow `DELIVERED` (and potentially `SEEN`) to be assigned to the wrong message. |
| `DELIVERY_SEEN` ambiguity | Minor | The serial `DELIVERY_SEEN <packetId>` command accepts any string. If a user accidentally passes an ACK ID, the old `indexOf` fallback could match unpredictably. |

### Verified Safe Behaviors

- **Routing**: No routing decisions were changed. The only added logic inside `routeLoRaProtocolPacket` updates state metadata and returns early only along existing paths.
- **UNKNOWN timeout**: `processDeliveryTimeouts` correctly transitions only `MESSAGE` → `UNKNOWN` after 60s. `DELIVERED` and `SEEN` are not affected.
- **Bluetooth forwarding**: Unchanged.
- **BT1 validation**: Unchanged.
- **Bridge ACK behavior**: `sendDeliveryAckForMessage` and `createDeliveryAckPacket` were untouched.

## 4. Required Code Changes

### Change A — Use `ackFor` for ACK correlation
In `routeLoRaProtocolPacket`, extract the original message ID from the ACK payload instead of using the ACK's own `packetId`:

```cpp
// ---- STEP042C: update delivery state on ACK receipt ----
if (packet.packetType == "ACK") {
  const String ackFor = extractJsonString(packet.payload, "ackFor");
  if (ackFor.length() > 0) {
    updateDeliveryState(ackFor, DELIVERY_STATE_DELIVERED);
  }
}
```

### Change B — Remove non-deterministic `indexOf` fallback
In `updateDeliveryState`, remove the substring fallback loop. Only exact matches are allowed:

```cpp
void updateDeliveryState(const String &packetId, const String &newState) {
  int idx = findDeliveryTrackingIndex(packetId);
  if (idx < 0) return;
  deliveryTrackingTable[idx].state = newState;
  deliveryTrackingTable[idx].lastUpdatedMs = millis();
  Serial.print("[DELIVERY_TRACK] update packetId=");
  Serial.print(deliveryTrackingTable[idx].packetId);
  Serial.print(" newState=");
  Serial.println(newState);
}
```

## 5. Exact Files Modified

- `esp32-node-platformio/src/main.cpp`

## 6. Build Commands to Verify

```bash
# Simulation build (no hardware)
pio run -e nodeA1

# LoRa-enabled build
pio run -e nodeA1_lora
```

Both builds passed after the fixes.

## Final PASS Validation

- ACK handling now uses `ackFor` for exact correlation.
- No substring matching remains (`indexOf` fallback removed).
- `DELIVERY_SEEN` uses exact `packetId` matching.
- `MESSAGE -> DELIVERED -> SEEN` state machine validated.
- `UNKNOWN` timeout only affects `MESSAGE` state.
- Builds passed for `nodeA1` and `nodeA1_lora`.

## Summary

The STEP042C implementation correctly initialized tracking with the original message ID and preserved all routing/timeout behaviors, but the **ACK-to-message correlation was broken**. It used the ACK's own ID and a dangerous substring fallback that could update the wrong in-flight message. The two minimal fixes above restore deterministic, exact correlation using the `ackFor` field and remove the `indexOf` fallback entirely. Final build and validation PASS.
