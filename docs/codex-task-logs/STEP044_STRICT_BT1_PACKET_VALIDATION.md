# STEP044 - Strict Compact BT1 LoRa Packet Validation

Date: 2026-06-06

Branch: `step-002-003-esp32-simulation`

Status: COMPLETE / Physical Validation PASS

## Context

Physical gateway-to-gateway LoRa validation now passes:

- Gateway A and Gateway B discover each other.
- `TEST_FINAL_001` from `gatewayB` to `gatewayA` was received.

Corrupted LoRa packets can still sometimes be parsed as valid compact packets and create bad neighbors such as `gateway=UNKNOWN` or corrupted node names.

First physical STEP044 validation failed after commit `93dd506`:

- Corrupt packet `BT1|HELLO-nodeA1-40000zno4eA1|...` was accepted as valid.
- The node table gained `[NEIGHBOR_ADD] node=BROADCAST gateway=UNKNOWN`.
- Corrupt source name `gatlwayB` was accepted and added as `[NEIGHBOR_ADD] node=gatlwayB gateway=B`.

Corrective firmware commit `9dba0ef` was flashed and physically validated under RF stress with four active LoRa nodes:

- `gatewayA`
- `gatewayB`
- `nodeA1`
- `nodeA2`

## Protocol Decision

Keep compact `BT1` LoRa relay packets.

Do not convert LoRa packets to JSON.

## Fix

Updated `esp32-node-platformio/src/main.cpp`:

- Added strict compact `BT1` validation before node learning or routing.
- Added `[LORA_DROP_CORRUPT] reason=<reason> payload=<short payload>`.
- Prevented malformed HELLO packets from reaching `learnNodeFromHelloPacket()`.
- Removed `UNKNOWN` gateway fallback for HELLO learning.
- Added HELLO semantic validation: known source format only, source cannot be `BROADCAST`, packet ID must exactly match `HELLO-<sourceNode>-<numeric timestamp>`, and gateway payload must be strict `{"gatewayId":"A"}` or `{"gatewayId":"B"}` JSON.
- Added defensive duplicate HELLO semantic guards inside `learnNodeFromHelloPacket()` before neighbor-table insertion.

## Validation Rules

Compact packets are rejected when:

- Packet does not start with `BT1|`.
- Required field count is not exactly 10.
- `packetId` is blank.
- `sourceNode` is blank.
- `destinationNode` is blank.
- `sourceNode` contains invalid characters.
- `destinationNode` contains invalid characters.
- HELLO `sourceNode` equals `BROADCAST`.
- HELLO `sourceNode` is not a known firmware node/gateway format: `nodeA1`-`nodeA3`, `nodeB1`-`nodeB3`, `gatewayA`, or `gatewayB`.
- HELLO `packetId` does not exactly match `HELLO-<sourceNode>-<numeric timestamp>`.
- HELLO payload is not strict gateway JSON.
- HELLO payload does not contain `gatewayId` `A` or `B`.
- `ttl` cannot be parsed as a number.
- `hopCount` cannot be parsed as a number.
- `ttl` is below `0` or above `DEFAULT_TTL`.
- `hopCount` is below `0` or above `DEFAULT_TTL`.

## Build Validation

Commands:

```sh
pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora
```

Results:

- `gatewayA_lora`: SUCCESS
- `gatewayB_lora`: SUCCESS
- `nodeA1_lora`: SUCCESS
- `nodeA2_lora`: SUCCESS

## Physical Validation Result

Result: PASS / COMPLETE.

Observed corrupt packet rejection logs:

- `[LORA_DROP_CORRUPT] reason=bad_prefix`
- `[LORA_DROP_CORRUPT] reason=bad_field_count`
- `[LORA_DROP_CORRUPT] reason=malformed_gatewayId_json`

Observed valid packet parse logs:

- `[LORA_RELAY_PARSE] valid packet_id=HELLO-gatewayB...`
- `[LORA_RELAY_PARSE] valid packet_id=HELLO-nodeA1...`
- `[LORA_RELAY_PARSE] valid packet_id=HELLO-nodeA2...`

No ghost neighbors were observed:

- No `gateway=UNKNOWN`.
- No `node=BROADCAST`.
- No malformed node names such as `gatlwayB`.

Node table remained stable at 4 nodes:

- `gatewayA`
- `gatewayB`
- `nodeA1`
- `nodeA2`
