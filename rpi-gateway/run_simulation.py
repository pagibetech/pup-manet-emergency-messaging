import argparse
import json

from gateway_sim.dashboard import run_dashboard
from gateway_sim.simulator import GatewaySimulator


def main() -> None:
    parser = argparse.ArgumentParser(description="PUP MANET Raspberry Pi gateway simulator")
    parser.add_argument("--dashboard", action="store_true", help="run the local dashboard service")
    parser.add_argument("--host", default="127.0.0.1", help="dashboard host")
    parser.add_argument("--port", type=int, default=8080, help="dashboard port")
    args = parser.parse_args()

    if args.dashboard:
        run_dashboard(host=args.host, port=args.port)
        return

    simulator = GatewaySimulator()
    result = simulator.run_demo()
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
