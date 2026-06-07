# STEP045A - Mesh-Wide Presence Propagation

Date: 2026-06-07

Branch: `step-002-003-esp32-simulation`

Status: COMPLETE / Physical Android Validation PASS

## Context

STEP044 strict compact `BT1` corrupt-packet validation is complete and must remain preserved.

After STEP044, gateway-to-gateway discovery worked, but Android discovery still missed `gatewayB` because gateway presence learned by one LoRa device was not propagated into the Android-connected node tables.

## Root Cause

Valid compact HELLO packets were learned locally, then `processIncomingLoRaRelayPacket()` returned immediately for HELLO packets.

That prevented learned `gatewayB` presence from propagating to `nodeA1` and `nodeA2`, so Android NODE_LIST export could not include `gatewayB` unless the Android-connected node directly heard it.

## Firmware Change

Updated `esp32-node-platformio/src/main.cpp`:

- Added a bounded HELLO relay cache.
- Added `forwardHelloPresencePacket()`.
- Forward valid HELLO presence only after strict STEP044 parsing and local learning.
- Preserve compact `BT1` LoRa relay format.
- Decrement `ttl` and increment `hopCount` before forwarding.
- Set `previousHop` to the forwarding node.
- Append the forwarding node to `hopPath`.
- Avoid loops using `previousHop`, source-node self checks, TTL/hop bounds, and the HELLO relay cache.
- Added `[HELLO_RELAY]` logs for forwarded, duplicate, and TTL-exhausted HELLO packets.

## Build Validation

Command:

```sh
pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora
```

Result: PASS.

- `gatewayA_lora`: SUCCESS
- `gatewayB_lora`: SUCCESS
- `nodeA1_lora`: SUCCESS
- `nodeA2_lora`: SUCCESS

## Physical Validation

Firmware was flashed to:

- `gatewayA`
- `gatewayB`
- `nodeA1`
- `nodeA2`

Android Nodes screen now shows:

Gateway A:

- `nodeA1 ONLINE`
- `gatewayA ONLINE`
- `nodeA2 ONLINE`

Gateway B:

- `gatewayB ONLINE`

Result: PASS. This confirms mesh-wide gatewayB discovery propagation is fixed.

## Preserved Behavior

- Compact `BT1` remains the LoRa firmware protocol.
- STEP044 strict corrupt-packet rejection remains in front of HELLO learning/relay.
- Malformed packets must still log `[LORA_DROP_CORRUPT]` and must not create `UNKNOWN`, `BROADCAST`, or malformed neighbors.
- Existing gatewayA/gatewayB/nodeA1/nodeA2 discovery and nodeA1 <-> nodeA2 routing remain the regression guard.

## Next Step

Recommended next task: STEP045B Android Real-Network Cleanup.

Do not start STEP042C Delivery Tracking or STEP042D Store-and-Forward yet.
