import argparse
import json

from gateway_sim.dashboard import run_dashboard
from gateway_sim.lora_spi import SimulatedLoRaSpiRadio
from gateway_sim.simulator import GatewaySimulator


def main() -> None:
    parser = argparse.ArgumentParser(description="PUP MANET Raspberry Pi gateway simulator")
    parser.add_argument("--dashboard", action="store_true", help="run the local dashboard service")
    parser.add_argument("--lora-spi-demo", action="store_true", help="run the simulated LoRa SPI heartbeat intake")
    parser.add_argument("--router-link-demo", action="store_true", help="run the simulated router-to-router gateway ping demo")
    parser.add_argument("--latency-demo", action="store_true", help="run the simulated 500-700 ms router latency demo")
    parser.add_argument("--failover-demo", action="store_true", help="run the RSSI-based 10-second failover state machine demo")
    parser.add_argument("--store-forward-demo", action="store_true", help="run the store-and-forward buffering demo")
    parser.add_argument("--recovery-demo", action="store_true", help="run the 10-second stable LoRa recovery demo")
    parser.add_argument("--latency-ms", type=int, default=600, help="latency to use with --latency-demo")
    parser.add_argument("--host", default="127.0.0.1", help="dashboard host")
    parser.add_argument("--port", type=int, default=8080, help="dashboard port")
    args = parser.parse_args()

    if args.dashboard:
        run_dashboard(host=args.host, port=args.port)
        return

    simulator = GatewaySimulator()
    if args.router_link_demo:
        result = simulator.run_router_link_demo()
        print(json.dumps(result, indent=2))
        return

    if args.latency_demo:
        result = simulator.run_latency_demo(latency_ms=args.latency_ms)
        print(json.dumps(result, indent=2))
        return

    if args.failover_demo:
        result = simulator.run_failover_demo()
        print(json.dumps(result, indent=2))
        return

    if args.store_forward_demo:
        result = simulator.run_store_forward_demo()
        print(json.dumps(result, indent=2))
        return

    if args.recovery_demo:
        result = simulator.run_recovery_demo()
        print(json.dumps(result, indent=2))
        return

    if args.lora_spi_demo:
        radio = SimulatedLoRaSpiRadio()
        radio.seed_heartbeat("A1", "GWA", now=0.0, rssi=-54.0, snr=9.8, uptime_s=12)
        radio.seed_heartbeat("A2", "GWA", now=0.2, rssi=-58.5, snr=8.9, uptime_s=15)
        received = simulator.poll_lora_radio(radio, now=1.0)
        result = simulator.snapshot_dict()
        result["lora_spi_demo"] = {"received_heartbeats": received}
        print(json.dumps(result, indent=2))
        return

    result = simulator.run_demo()
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
