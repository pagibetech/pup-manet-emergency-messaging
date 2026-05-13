# STEP 030 - Recovery/Reversion

Date: 2026-05-13

## Workbook Step

Detailed Steps row `6.3` - Recovery.

## Scope

This step adds simulation-safe recovery/reversion behavior after a degraded LoRa path becomes stable again.

## Files Changed

- `rpi-gateway/gateway_sim/simulator.py`
- `rpi-gateway/run_simulation.py`
- `rpi-gateway/tests/test_gateway_simulator.py`
- `rpi-gateway/README.md`
- `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

## Implementation

- Added `python3 run_simulation.py --recovery-demo`.
- Gateway remains on `GATEWAY_WIFI_ROUTER` while the route is `RECOVERING`.
- Gateway waits 10 stable seconds before restoring `PRIMARY_LORA`.
- After recovery, local traffic returns to `LOCAL_LORA`.
- Recovery switch completes immediately after the 10-second stable window, within the 5-second acceptance limit.

## Validation

From `rpi-gateway/`:

```sh
python3 run_simulation.py --recovery-demo
python3 -m unittest discover -s tests
python3 -m compileall gateway_sim run_simulation.py
```

Results:

- During failover, route was `GATEWAY_WIFI_ROUTER`.
- Before stable 10 seconds completed, state was `RECOVERING`.
- After stable 10 seconds, state was `PRIMARY_LORA`.
- `completed_within_5s` was `true`.
- Route after recovery was `LOCAL_LORA`.
- Unit tests passed: 21 tests.
- Python compile check passed.

## Notes

- Physical RF recovery and live RPI/LoRa timing tests remain later hardware verification.
- No Android or ESP32 code was changed.
