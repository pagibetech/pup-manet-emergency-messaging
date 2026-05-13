# STEP 025 - Raspberry Pi LoRa SPI Integration Baseline

Date: 2026-05-13

## Workbook Step

Detailed Steps row `4.2` - LoRa SPI Integration.

## Scope

This step adds a simulation-safe Raspberry Pi LoRa SPI hardware abstraction baseline. It does not claim physical Raspberry Pi/SX1278 driver bring-up yet.

## Files Changed

- `rpi-gateway/gateway_sim/lora_spi.py`
- `rpi-gateway/gateway_sim/models.py`
- `rpi-gateway/gateway_sim/simulator.py`
- `rpi-gateway/gateway_sim/dashboard.py`
- `rpi-gateway/gateway_sim/__init__.py`
- `rpi-gateway/run_simulation.py`
- `rpi-gateway/tests/test_gateway_simulator.py`
- `rpi-gateway/README.md`
- `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`

## Implementation

- Added `LoRaSpiConfig`, `LoRaFrame`, and `LoRaSpiRadio`.
- Added `SimulatedLoRaSpiRadio` for deterministic Mac/Raspberry Pi dry runs.
- Added compact heartbeat parsing for `HB1|<NODE>|<GATEWAY>|<UPTIME>` frames.
- Added gateway heartbeat intake and snapshot reporting.
- Added wrong-gateway heartbeat rejection.
- Added `python3 run_simulation.py --lora-spi-demo`.

## Validation

From `rpi-gateway/`:

```sh
python3 run_simulation.py --lora-spi-demo
python3 -m unittest discover -s tests
python3 -m compileall gateway_sim run_simulation.py
```

Results:

- LoRa SPI demo received 2 simulated heartbeats.
- Unit tests passed: 8 tests.
- Python compile check passed.

## Notes

- Real Raspberry Pi SPI/SX1278 driver calls remain future hardware bring-up work.
- Router configuration and GSM behavior remain future workbook scope.
