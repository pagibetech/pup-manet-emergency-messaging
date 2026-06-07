# STEP046A - Controlled Multi-Hop Lab Mode

Date: 2026-06-07

Branch: `step-002-003-esp32-simulation`

Starting HEAD: `8dc04be Mark STEP045B Android real-network cleanup pass`

## Objective

Add an ESP32 firmware lab mode that can force controlled multi-hop forwarding on a short-range indoor bench where all SX1278 radios can otherwise hear each other directly.

## Scope

- ESP32 firmware only.
- Android unchanged.
- Raspberry Pi gateway service unchanged.
- Compact `BT1` LoRa relay format preserved.
- STEP044 strict corrupt-packet validation preserved.

## Files Changed

- `esp32-node-platformio/src/main.cpp`

## Implementation Summary

- Added compile-time flag `TEST_FORCE_GATEWAY_ROUTE`, defaulting to `0`.
- Production behavior remains unchanged when `TEST_FORCE_GATEWAY_ROUTE == 0`.
- When enabled, only `nodeA1 -> nodeA2` and `nodeA2 -> nodeA1` `MESSAGE` traffic is forced through gateway paths.
- Forced paths:
  - `nodeA1 -> gatewayA -> gatewayB -> nodeA2`
  - `nodeA2 -> gatewayB -> gatewayA -> nodeA1`
- Direct overheard forced-route packets are ignored before duplicate-cache insertion, allowing the valid gateway-relayed copy to deliver.
- Added logs:
  - `[FORCED_ROUTE]`
  - `[FORWARD]`
- Added boot/status visibility for `test_force_gateway_route`.

## Preserved Protections

- TTL
- hopCount
- duplicate suppression
- relay behavior
- loop prevention through expected previous-hop gating
- STEP044 compact `BT1` packet validation

## Validation

Initial implementation commands run:

```bash
pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora
git diff --check
```

Result:

- `gatewayA_lora`: SUCCESS
- `gatewayB_lora`: SUCCESS
- `nodeA1_lora`: SUCCESS
- `nodeA2_lora`: SUCCESS
- `git diff --check`: PASS

## Physical Validation Status

PASS / COMPLETE.

Manual test firmware was flashed with `TEST_FORCE_GATEWAY_ROUTE=1`.

Validated results:

- A -> B forced route delivered: `nodeA1 -> gatewayA -> gatewayB -> nodeA2`.
- B -> A forced route delivered: `nodeA2 -> gatewayB -> gatewayA -> nodeA1`.
- GatewayA-off test: node count became 3, `gatewayA` disappeared from NODE_LIST, and `nodeA1`/`nodeA2` still delivered messages directly.

GatewayA-off interpretation:

- This proves node discovery expiry.
- This proves direct fallback/survivability for nearby nodeA1/nodeA2 radios.
- This does not prove full alternate gateway reroute.

Production restore:

- Source default restored to `#define TEST_FORCE_GATEWAY_ROUTE 0`.
- Production-mode build validation passed for `gatewayA_lora`, `gatewayB_lora`, `nodeA1_lora`, and `nodeA2_lora`.

## Expected Lab Logs

Forward path:

```text
[FORCED_ROUTE]
source=nodeA1
dest=nodeA2
via=gatewayA

[FORWARD]
from=gatewayA
to=gatewayB

[FORWARD]
from=gatewayB
to=nodeA2
```

Reverse path:

```text
[FORCED_ROUTE]
source=nodeA2
dest=nodeA1
via=gatewayB

[FORWARD]
from=gatewayB
to=gatewayA

[FORWARD]
from=gatewayA
to=nodeA1
```

## Next Step

Continue from the next workbook-approved task after STEP046A. If adaptive alternate gateway reroute is desired, define it explicitly as a new step.
