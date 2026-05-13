# STEP 027 - Router Link Latency Simulation

Date: 2026-05-13

## Workbook Step

Detailed Steps row `5.2` - Latency Simulation.

## Scope

This step adds simulation-safe router-link latency measurement for the 500-700 ms target range. It does not apply Linux `tc/netem` on the Raspberry Pis yet.

## Hardware Context Recorded

The user confirmed the real TP-Link router bridge works on the independent project network:

- Router A / `MGateway1`: `192.168.50.1`
- Router B / `MGateway2`: `192.168.50.102`
- RPI A: `192.168.50.41`
- RPI B: `192.168.50.42`

The user reported all four addresses are pingable when connected to either `MGateway1` or `MGateway2`.

## Files Changed

- `rpi-gateway/gateway_sim/models.py`
- `rpi-gateway/gateway_sim/simulator.py`
- `rpi-gateway/gateway_sim/dashboard.py`
- `rpi-gateway/run_simulation.py`
- `rpi-gateway/tests/test_gateway_simulator.py`
- `rpi-gateway/README.md`
- `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

## Implementation

- Added `last_ping_latency_ms` to the router-link state.
- Added `set_router_link_latency(...)`.
- Added latency values to `PING_OK` and `GATEWAY_FORWARD` events.
- Added `python3 run_simulation.py --latency-demo`.
- Added dashboard display for configured router-link latency.
- Added tests for configured latency and the 500-700 ms target.

## Validation

From `rpi-gateway/`:

```sh
python3 run_simulation.py --latency-demo
python3 -m unittest discover -s tests
python3 -m compileall gateway_sim run_simulation.py
```

Results:

- Latency demo measured `600 ms`.
- `within_500_700_ms_target` was `true`.
- Unit tests passed: 13 tests.
- Python compile check passed.

## Notes

- Real `tc/netem` latency injection can be tested later if needed.
- No Android or ESP32 code was changed.
