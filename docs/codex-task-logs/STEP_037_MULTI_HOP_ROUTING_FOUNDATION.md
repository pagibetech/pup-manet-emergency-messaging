# STEP 037 - Multi-Hop Routing Foundation

Date: 2026-05-22

## Result

Completed / PASS.

## Scope

Add the foundation for multi-hop routing (`hopCount`, `ttl`, `previousHop`) without breaking the current 2-node phone-to-phone working path.

## Changes

### ESP32 firmware (`src/main.cpp`)

1. **Extended protocol packet fields**
   - Added `hopCount`, `ttl`, `previousHop` to [`ProtocolPacket`](src/main.cpp:91).
   - Updated [`serializeProtocolPacket()`](src/main.cpp:378) and [`parseProtocolPacket()`](src/main.cpp:306) to handle new fields.
   - Updated [`emptyProtocolPacket()`](src/main.cpp:290), [`printProtocolPacketFields()`](src/main.cpp:825), and [`createProtocolPacket()`](src/main.cpp:740) defaults.
   - Updated protocol spec field list in [`printProtocolSpec()`](src/main.cpp:977).

2. **Extended LoRa relay frame**
   - [`serializeLoRaRelayPacket()`](src/main.cpp:625) now emits `packetId|sourceNode|destinationNode|payload|timestamp|ttl|hopCount|previousHop|hopPath`.
   - [`parseLoRaRelayPacket()`](src/main.cpp:635) parses the expanded fields, defaulting `ttl` to `DEFAULT_TTL` when omitted for backward compatibility.

3. **Multi-hop routing engine (`routeLoRaProtocolPacket()`)**
   - Duplicate suppression via `messageId` with [`hasSeenMessage()`](src/main.cpp:395) / [`rememberMessage()`](src/main.cpp:414):
     - Duplicate → logs `[DUPLICATE_DROP] packet_id=...` and returns.
   - Destination-local check via [`protocolPacketTargetsLocalNode()`](src/main.cpp:595):
     - If local → logs `[ROUTE_DECISION] deliver_local` and forwards to Bluetooth.
   - Relay path:
     - Decrements `ttl`, increments `hopCount`, sets `previousHop = SIM_NODE_ID`, appends to `hopPath`.
     - If `ttl <= 0` → logs `[TTL_DROP] packet_id=... ttl_exhausted_at=...` and returns.
     - Otherwise serializes a new relay frame and calls [`sendLoRaLine()`](src/main.cpp:551):
       - On success logs `[LORA_RELAY] packet_id=... relayed_by=... new_path=...`.

4. **Bluetooth MESSAGE forwarding integration**
   - When Android sends a `MESSAGE` packet that should go to LoRa, [`processIncomingBluetoothProtocolPacket()`](src/main.cpp:874) builds a relay packet with:
     - `hopCount = 0`
     - `ttl = DEFAULT_TTL` (5)
     - `previousHop = SIM_NODE_ID`
     - `hopPath = SIM_NODE_ID > destinationNode`
   - This ensures every outbound LoRa relay carries a fresh TTL budget.

5. **Simulation support for relay packets over Serial**
   - [`processSerialLine()`](src/main.cpp:1193) now detects lines starting with `BT1|` and routes them through [`processIncomingLoRaRelayPacket()`](src/main.cpp:697), allowing serial-injected relay tests without a second radio.

6. **Routing logs introduced**
   - `[ROUTE_DECISION] deliver_local ...`
   - `[ROUTE_DECISION] relay ...`
   - `[LORA_RELAY] ...`
   - `[DUPLICATE_DROP] ...`
   - `[TTL_DROP] ...`

## Acceptance Test

### A. Existing 2-node path still passes
- Phone A Chat → Bluetooth SPP → NODE_A → LoRa → NODE_B → Bluetooth SPP → Phone B Chat.
- Verified via `node_a_lora` / `node_b_lora` builds.

### B. Simulated multi-hop logic test passes
- Paste a `BT1|` relay packet targeting `NODE_C` into `node_b` serial:
  ```
  BT1|msg-001|NODE_A|NODE_C|Hello|12345|5|0||NODE_A
  ```
  - `NODE_B` logs `[ROUTE_DECISION] relay` and `[LORA_RELAY]` because `ttl > 0` and destination is not local.
  - `NODE_B` does **not** deliver locally.

### C. Duplicate packet test
- Paste the same `BT1|` relay packet twice:
  - First pass routes.
  - Second pass logs `[DUPLICATE_DROP] packet_id=...`.

### D. TTL test
- Paste a `BT1|` relay packet with `ttl=0` or `ttl=1` (relayed once so it becomes 0):
  - Logs `[TTL_DROP] packet_id=... ttl_exhausted_at=...`.

## Files Changed

- `src/main.cpp`
  - Extended `ProtocolPacket` and parser/serializer.
  - Extended LoRa relay frame format.
  - Added `routeLoRaProtocolPacket()`, `deliverToBluetooth()`.
  - Wired relay logic into `processIncomingLoRaRelayPacket()` and `processIncomingLoRaProtocolPacket()`.
  - Wired `ttl`/`hopCount` initialization into Bluetooth `MESSAGE` LoRa forwarding.
  - Added serial-line detection for `BT1|` relay packets.

## Next Step

STEP 038 — Third Node Hardware Integration or Advanced Routing Table.
