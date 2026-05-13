# STEP 029 - Store And Forward

Date: 2026-05-13

## Workbook Step

Detailed Steps row `6.2` - Store and Forward.

## Scope

This step adds simulation-safe message buffering during gateway/router-link outage and ordered flush after recovery.

## Files Changed

- `rpi-gateway/gateway_sim/models.py`
- `rpi-gateway/gateway_sim/simulator.py`
- `rpi-gateway/gateway_sim/dashboard.py`
- `rpi-gateway/run_simulation.py`
- `rpi-gateway/tests/test_gateway_simulator.py`
- `rpi-gateway/README.md`
- `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

## Implementation

- Added `store_forward_queue`.
- Added `store_forward_depth` to snapshots and dashboard output.
- Router-link outage now buffers gateway packets as `BUFFERED_FOR_FORWARD`.
- Router-link recovery flushes buffered packets in original order.
- Added `python3 run_simulation.py --store-forward-demo`.

## Validation

From `rpi-gateway/`:

```sh
python3 run_simulation.py --store-forward-demo
python3 -m unittest discover -s tests
python3 -m compileall gateway_sim run_simulation.py
```

Results:

- Store-forward demo buffered 2 messages.
- Buffered messages flushed in original order.
- `order_preserved` was `true`.
- Store-forward queue depth returned to `0`.
- Unit tests passed: 19 tests.
- Python compile check passed.

## Notes

- Physical router outage and live RPI buffering tests remain later hardware verification.
- No Android or ESP32 code was changed.
