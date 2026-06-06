# STEP043 - LoRa HELLO Packet Format Fix

Date: 2026-06-05 / corrected 2026-06-06

Branch: `step-002-003-esp32-simulation`

Status: Initial JSON-oriented fix physically failed; corrected compact `BT1` receiver-side fix built / physical validation pending

## Initial Finding

Gateway A transmitted a LoRa HELLO as compact relay text:

`BT1|HELLO-gatewayA-17840000|gatewayA|BROADCAST|{"gatewayId":"A"}`

Gateway B received the RF payload but rejected it with:

`[LORA_ERROR] Expected simulation JSON packet from LoRa peer.`

This confirmed the radios communicate, but discovery failed because compact gateway HELLO advertisements were not being accepted by the receiver path under validation.

## Trace

- `GatewayRelay._advertise_nodes_loop()` advertises node lists over the TCP/Tailscale peer link.
- ESP32 gateway serial input accepts `[GW_JSON] <json>` and raw BT-MANET protocol JSON.
- ESP32 `sendHelloBroadcast()` was the immediate LoRa HELLO sender and previously serialized HELLO packets as `BT1|...`.
- ESP32 LoRa RX accepts full protocol JSON when the payload starts with `{` and includes `"protocolVersion"`.
- `parseProtocolPacket()` requires `protocolVersion`, `packetType`, `packetId`, `sourceNode`, `destinationNode`, `payload`, `hopPath`, `retryCount`, `timestamp`, `status`, and `checksum`; `hopCount`, `ttl`, and `previousHop` are parsed for routing.

## Initial Fix Attempt

Updated `esp32-node-platformio/src/main.cpp` on 2026-06-05:

- Periodic LoRa HELLO broadcasts now use full BT-MANET JSON via `serializeProtocolPacket(packet)`.
- Relayed HELLO packets remain BT-MANET JSON instead of being converted to compact `BT1|...`.
- Non-HELLO message relay continues to use the existing compact relay format.
- Discovery logging now includes `[HELLO] discovered <nodeId>`.

## Physical Validation Failure

Physical validation after flashing `gatewayA_lora` and `gatewayB_lora` showed Gateway A still transmitting compact HELLO frames:

`[LORA_TX] BT1|HELLO-gatewayA-...|gatewayA|BROADCAST|{"gatewayId":"A"}|...`

The final current firmware protocol decision is to keep compact `BT1` relay packets because nodeA1/nodeA2 routing is already validated with compact packets.

## Corrected Fix

Updated `esp32-node-platformio/src/main.cpp` on 2026-06-06:

- `sendHelloBroadcast()` remains compact `BT1`.
- All LoRa relay TX paths remain compact `BT1`.
- Compact HELLO RX logs `[LORA_RELAY_PARSE] valid`.
- `parseLoRaRelayPacket()` infers HELLO from `HELLO-*` packet IDs or `BROADCAST` + `gatewayId`.
- `processIncomingLoRaRelayPacket()` learns HELLO before duplicate/drop routing and returns immediately for HELLO.
- Discovery logs include `[HELLO] discovered <nodeId>` and `[DISCOVERY] HELLO from <nodeId> gateway=<gatewayId>`.

## Validation

Build commands:

```sh
pio run -e gatewayA_lora -e gatewayB_lora
pio run -e nodeA1_lora -e nodeA2_lora
```

Corrected 2026-06-06 results:

- `gatewayA_lora`: SUCCESS
- `gatewayB_lora`: SUCCESS
- `nodeA1_lora`: SUCCESS
- `nodeA2_lora`: SUCCESS

## Physical Validation Pending

Expected physical validation output:

- Gateway B log shows `[LORA_RX] payload=BT1|HELLO-...`.
- Gateway B log shows `[LORA_RELAY_PARSE] valid`.
- Gateway B log shows `[DISCOVERY] HELLO from gatewayA gateway=A`.
- Gateway B log shows `[NEIGHBOR_ADD] node=gatewayA` or `[NEIGHBOR_UPDATE] node=gatewayA`.
- Gateway A log shows the equivalent sequence for `gatewayB`.
- `STATUS` and `NEIGHBORS` on both ESP32 gateways list both gateway nodes.
