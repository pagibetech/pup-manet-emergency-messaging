# STEP 028 - Failover State Machine

Date: 2026-05-13

## Workbook Step

Detailed Steps row `6.1` - Failover State Machine.

## Scope

This step adds simulation-safe failover state-machine behavior for RSSI degradation and ACK timeout triggers.

## Files Changed

- `rpi-gateway/gateway_sim/models.py`
- `rpi-gateway/gateway_sim/simulator.py`
- `rpi-gateway/run_simulation.py`
- `rpi-gateway/tests/test_gateway_simulator.py`
- `rpi-gateway/README.md`
- `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

## Implementation

- Added gateway failover state fields:
  - `last_rssi`
  - `degraded_since`
  - `failover_active_since`
  - `failover_reason`
- Added RSSI threshold detection below `-78 dBm`.
- Added 10-second transition from `DEGRADED` to `FAILOVER_ACTIVE`.
- Added ACK-timeout failover trigger.
- Added `python3 run_simulation.py --failover-demo`.
- Added failover route behavior through `GATEWAY_WIFI_ROUTER`.

## Validation

From `rpi-gateway/`:

```sh
python3 run_simulation.py --failover-demo
python3 -m unittest discover -s tests
python3 -m compileall gateway_sim run_simulation.py
```

Results:

- Failover demo used RSSI `-82 dBm`.
- State before 10 seconds: `DEGRADED`.
- State at 10 seconds: `FAILOVER_ACTIVE`.
- Message route after failover: `GATEWAY_WIFI_ROUTER`.
- Unit tests passed: 17 tests.
- Python compile check passed.

## Notes

- Physical RF attenuation and live RPI/LoRa failover testing remain later hardware verification.
- No Android or ESP32 code was changed.
