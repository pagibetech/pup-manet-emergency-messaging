# STEP044 - Strict Compact BT1 LoRa Packet Validation

Date: 2026-06-06

Branch: `step-002-003-esp32-simulation`

Status: Fix built / physical corrupt-packet validation pending

## Context

Physical gateway-to-gateway LoRa validation now passes:

- Gateway A and Gateway B discover each other.
- `TEST_FINAL_001` from `gatewayB` to `gatewayA` was received.

Corrupted LoRa packets can still sometimes be parsed as valid compact packets and create bad neighbors such as `gateway=UNKNOWN` or corrupted node names.

## Protocol Decision

Keep compact `BT1` LoRa relay packets.

Do not convert LoRa packets to JSON.

## Fix

Updated `esp32-node-platformio/src/main.cpp`:

- Added strict compact `BT1` validation before node learning or routing.
- Added `[LORA_DROP_CORRUPT] reason=<reason> payload=<short payload>`.
- Prevented malformed HELLO packets from reaching `learnNodeFromHelloPacket()`.
- Removed `UNKNOWN` gateway fallback for HELLO learning.

## Validation Rules

Compact packets are rejected when:

- Packet does not start with `BT1|`.
- Required field count is not exactly 10.
- `packetId` is blank.
- `sourceNode` is blank.
- `destinationNode` is blank.
- `sourceNode` contains invalid characters.
- `destinationNode` contains invalid characters.
- HELLO payload does not contain `gatewayId` `A` or `B`.
- `ttl` cannot be parsed as a number.
- `hopCount` cannot be parsed as a number.
- `ttl` is below `0` or above `DEFAULT_TTL`.
- `hopCount` is below `0` or above `DEFAULT_TTL`.

## Build Validation

Commands:

```sh
pio run -e gatewayA_lora -e gatewayB_lora
pio run -e nodeA1_lora -e nodeA2_lora
```

Results:

- `gatewayA_lora`: SUCCESS
- `gatewayB_lora`: SUCCESS
- `nodeA1_lora`: SUCCESS
- `nodeA2_lora`: SUCCESS

## Physical Validation Pending

Expected:

- Corrupted compact packets log `[LORA_DROP_CORRUPT] reason=<reason> payload=<short payload>`.
- Corrupted packets do not create or update neighbors.
- Known-good gatewayA/gatewayB/nodeA1/nodeA2 discovery and routing remain working.
