import argparse
import json

from gateway_sim.dashboard import run_dashboard
from gateway_sim.lora_spi import SimulatedLoRaSpiRadio
from gateway_sim.simulator import GatewaySimulator


def main() -> None:
    parser = argparse.ArgumentParser(description="PUP MANET Raspberry Pi gateway simulator")
    parser.add_argument("--dashboard", action="store_true", help="run the local dashboard service")
    parser.add_argument("--lora-spi-demo", action="store_true", help="run the simulated LoRa SPI heartbeat intake")
    parser.add_argument("--host", default="127.0.0.1", help="dashboard host")
    parser.add_argument("--port", type=int, default=8080, help="dashboard port")
    args = parser.parse_args()

    if args.dashboard:
        run_dashboard(host=args.host, port=args.port)
        return

    simulator = GatewaySimulator()
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
