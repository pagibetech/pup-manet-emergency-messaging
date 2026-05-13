# STEP 026 - Router Link Option A Simulation

Date: 2026-05-13

## Workbook Step

Detailed Steps row `5.1` - Router-to-Router Link.

## Scope

This is Option A only: a simulation-first router-to-router gateway link verification. It does not configure real WiFi routers yet.

## Files Changed

- `rpi-gateway/gateway_sim/models.py`
- `rpi-gateway/gateway_sim/simulator.py`
- `rpi-gateway/gateway_sim/dashboard.py`
- `rpi-gateway/run_simulation.py`
- `rpi-gateway/tests/test_gateway_simulator.py`
- `rpi-gateway/README.md`
- `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

## Implementation

- Added a `RouterLink` model for the simulated Router A to Router B path.
- Added gateway ping simulation with `PING_OK` and `PING_FAIL` events.
- Added `python3 run_simulation.py --router-link-demo`.
- Added router-link dashboard status.
- Added tests for ping success, ping failure, and router-link demo behavior.

## Validation

From `rpi-gateway/`:

```sh
python3 run_simulation.py --router-link-demo
python3 -m unittest discover -s tests
python3 -m compileall gateway_sim run_simulation.py
```

Results:

- Router-link demo showed initial ping success.
- Remote delivery used `GATEWAY_WIFI_ROUTER`.
- Link-down ping failed.
- Link-down remote delivery failed.
- Recovered ping succeeded.
- Unit tests passed: 11 tests.
- Python compile check passed.

## Notes

- Real router configuration is Option B and remains a separate hardware verification step.
- Real Raspberry Pi gateway ping evidence has not been claimed yet.
