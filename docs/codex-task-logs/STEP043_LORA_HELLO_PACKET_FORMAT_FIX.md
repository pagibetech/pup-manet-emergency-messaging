# STEP043 - LoRa HELLO Packet Format Fix

Date: 2026-06-05

Branch: `step-002-003-esp32-simulation`

Status: Fix built / physical validation pending

## Finding

Gateway A transmitted a LoRa HELLO as compact relay text:

`BT1|HELLO-gatewayA-17840000|gatewayA|BROADCAST|{"gatewayId":"A"}`

Gateway B received the RF payload but rejected it with:

`[LORA_ERROR] Expected simulation JSON packet from LoRa peer.`

This confirms the radios communicate, but discovery failed because gateway HELLO advertisements were not sent in the BT-MANET JSON format expected by the ESP32 LoRa protocol parser.

## Trace

- `GatewayRelay._advertise_nodes_loop()` advertises node lists over the TCP/Tailscale peer link.
- ESP32 gateway serial input accepts `[GW_JSON] <json>` and raw BT-MANET protocol JSON.
- ESP32 `sendHelloBroadcast()` was the immediate LoRa HELLO sender and previously serialized HELLO packets as `BT1|...`.
- ESP32 LoRa RX accepts full protocol JSON when the payload starts with `{` and includes `"protocolVersion"`.
- `parseProtocolPacket()` requires `protocolVersion`, `packetType`, `packetId`, `sourceNode`, `destinationNode`, `payload`, `hopPath`, `retryCount`, `timestamp`, `status`, and `checksum`; `hopCount`, `ttl`, and `previousHop` are parsed for routing.

## Fix

Updated `esp32-node-platformio/src/main.cpp`:

- Periodic LoRa HELLO broadcasts now use full BT-MANET JSON via `serializeProtocolPacket(packet)`.
- Relayed HELLO packets remain BT-MANET JSON instead of being converted to compact `BT1|...`.
- Non-HELLO message relay continues to use the existing compact relay format.
- Discovery logging now includes `[HELLO] discovered <nodeId>`.

## Validation

Build commands:

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

Expected physical validation output:

- Gateway B log shows `[HELLO] discovered gatewayA`.
- Gateway A log shows `[HELLO] discovered gatewayB`.
- `STATUS` and `NEIGHBORS` on both ESP32 gateways list both gateway nodes.
