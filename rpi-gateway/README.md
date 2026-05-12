# Raspberry Pi Gateway

This folder contains the simulation-first Raspberry Pi gateway workspace for the PUP MANET Emergency Messaging System.

Step 024 starts the gateway layer without requiring Raspberry Pi hardware yet. It models:

- Six MANET nodes:
  - `A1`, `A2`, `A3`
  - `B1`, `B2`, `B3`
- Two Raspberry Pi gateways:
  - `GWA` for `NET_A`
  - `GWB` for `NET_B`
- A WiFi-router simulated satellite path between gateways.
- Local LoRa delivery inside one network.
- Gateway forwarding for remote-network destinations.
- Message buffering and ACK completion.
- Failover after 10 seconds when a local LoRa route is unavailable.
- Recovery after 10 stable seconds when LoRa becomes available again.

Real Raspberry Pi SPI wiring, SX1278 gateway hardware access, router configuration, and production services are reserved for later workbook steps.

## Run The Simulation

From this folder:

```sh
python3 run_simulation.py
```

Expected result:

- JSON status is printed.
- `delivered_count` is greater than `0`.
- Recent events include local LoRa delivery, gateway forwarding, failover, ACK, and recovery.

## Run Tests

From this folder:

```sh
python3 -m unittest discover -s tests
```

The tests verify:

- Six-node, two-gateway topology.
- Local LoRa delivery.
- Cross-network gateway routing.
- 10-second failover trigger.
- 10-second recovery rule.
- Failed delivery when the simulated gateway link is offline.

## Dashboard

The dashboard can run with Flask if dependencies are installed:

```sh
python3 -m pip install -r requirements.txt
python3 run_simulation.py --dashboard
```

Then open:

```text
http://127.0.0.1:8080
```

If Flask is not installed, the command falls back to a small standard-library HTTP dashboard so the simulator can still run on a clean Mac or Raspberry Pi Python install.

Useful endpoints:

```text
GET  /api/status
POST /api/demo
```
