#!/usr/bin/env python3
"""
PUP MANET — ESP32 Serial Simulator (STEP048 Testability)

Generates realistic ESP32 gateway node serial output to exercise
the full gateway service pipeline. Feeds data via stdin pipe or
writes to a file that can be tailed into gateway_service.py.

Usage:
    # Pipe directly into gateway service (requires serial_enabled=False):
    python3 serial_simulator.py --duration 120 | python3 gateway_service.py config.json

    # Write to file for later replay:
    python3 serial_simulator.py --duration 300 --output sim_output.jsonl

    # With degradation cycle:
    python3 serial_simulator.py --degrade-cycle --duration 180

Scenarios generated:
    - HELLO packets from nodeA1, nodeA2, gatewayB at realistic intervals
    - Periodic [DEGRADATION] / [RECOVERY] events
    - [GW_JSON] message packets
    - NODE_LIST exports
"""

import argparse
import json
import random
import sys
import time


def format_ts(multiplier: float = 1000.0) -> int:
    return int(time.time() * multiplier)


def make_hello_line(node_id: str, gateway_id: str) -> str:
    ts = format_ts()
    return (
        f"BT1|HELLO-{node_id}-{ts}|{node_id}|BROADCAST|"
        f"{{\"gatewayId\":\"{gateway_id}\"}}|{node_id}|1|0|{node_id}|5|0|{ts}"
    )


def make_gw_json(payload: dict) -> str:
    return f"[GW_JSON] {json.dumps(payload, separators=(',', ':'))}"


def make_message_gw_json(msg_id: str, src: str, dest: str, text: str) -> str:
    pkt = {
        "protocolVersion": "BT-MANET-1.0",
        "packetType": "MESSAGE",
        "packetId": msg_id,
        "sourceNode": src,
        "destinationNode": dest,
        "payload": f"MODE=LORA;TEXT={text};messageId={msg_id}",
        "hopPath": f"{src}>{dest}",
        "hopCount": 1,
        "ttl": 6,
        "previousHop": src,
        "retryCount": 0,
        "timestamp": format_ts(1.0),
        "status": "MESSAGE",
        "checksum": "CHECKSUM_PLACEHOLDER",
    }
    return make_gw_json(pkt)


def make_degradation(node_id: str, rssi: int = -82) -> str:
    return f"[DEGRADATION] node={node_id} rssi={rssi} state=DEGRADED"


def make_recovery(node_id: str, rssi: int = -55) -> str:
    return f"[RECOVERY] node={node_id} rssi={rssi}"


def make_node_list_export(nodes: list[tuple[str, str, str]]) -> str:
    parts = ["type=NODE_LIST", f"count={len(nodes)}"]
    for nid, gw, state in nodes:
        parts.append(f"{nid},{gw},{state}")
    return make_gw_json({
        "protocolVersion": "BT-MANET-1.0",
        "packetType": "STATUS",
        "packetId": f"GW-NODELIST-{format_ts()}",
        "sourceNode": "GATEWAY",
        "destinationNode": "ESP32_BRIDGE",
        "payload": ";".join(parts),
        "hopPath": "GATEWAY>ESP32_BRIDGE",
        "hopCount": 0,
        "ttl": 5,
        "previousHop": "",
        "retryCount": 0,
        "timestamp": format_ts(1.0),
        "status": "ONLINE",
        "checksum": "checksum pending / simulated",
    })


def main():
    parser = argparse.ArgumentParser(description="ESP32 Serial Simulator")
    parser.add_argument("--duration", type=int, default=60,
                        help="Simulation duration in seconds")
    parser.add_argument("--output", type=str, default=None,
                        help="Output file (default: stdout)")
    parser.add_argument("--degrade-cycle", action="store_true",
                        help="Include degradation/recovery cycles")
    parser.add_argument("--hello-interval", type=float, default=10.0,
                        help="HELLO interval in seconds")
    parser.add_argument("--msg-count", type=int, default=5,
                        help="Number of test messages to generate")
    args = parser.parse_args()

    out = open(args.output, "w") if args.output else sys.stdout

    deadline = time.time() + args.duration
    hello_seq = 0
    msg_seq = 0
    degrade_phase = 0  # 0=healthy, 1=degrading, 2=degraded, 3=recovering
    degrade_switch_at = time.time() + 30 if args.degrade_cycle else float("inf")
    degraded_node = "nodeA1"

    while time.time() < deadline:
        now = time.time()

        # Degradation cycle
        if args.degrade_cycle and now >= degrade_switch_at:
            degrade_phase = (degrade_phase + 1) % 4
            degrade_switch_at = now + random.uniform(25, 40)
            if degrade_phase == 1:
                out.write(make_degradation(degraded_node, -82) + "\n")
                out.write(make_degradation(degraded_node, -85) + "\n")
            elif degrade_phase == 2:
                out.write(make_degradation(degraded_node, -88) + "\n")
            elif degrade_phase == 3:
                out.write(make_recovery(degraded_node, -55) + "\n")
            elif degrade_phase == 0:
                out.write(make_recovery(degraded_node, -50) + "\n")

        # Periodic HELLO from local nodes
        if hello_seq % max(1, int(args.hello_interval)) == 0:
            out.write(make_hello_line("nodeA1", "A") + "\n")
            if hello_seq % 2 == 0:
                out.write(make_hello_line("nodeA2", "A") + "\n")

        # Occasional test message
        if msg_seq < args.msg_count and random.random() < 0.1:
            msg_seq += 1
            out.write(make_message_gw_json(
                f"SIM-MSG-{msg_seq:03d}", "nodeA1", "nodeB1",
                f"simulated message {msg_seq}"
            ) + "\n")

        # Periodic NODE_LIST export
        if hello_seq % 20 == 0:
            out.write(make_node_list_export([
                ("nodeA1", "A", "ONLINE"),
                ("nodeA2", "A", "ONLINE"),
                ("gatewayA", "A", "ONLINE"),
            ]) + "\n")

        hello_seq += 1
        out.flush()
        time.sleep(1)

    if args.output:
        out.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
