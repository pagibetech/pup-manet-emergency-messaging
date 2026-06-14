#!/usr/bin/env python3
"""
PUP MANET — Gateway Integration Test Harness (STEP048 Testability)

Simulates an ESP32 gateway node's serial output to exercise the full
gateway service pipeline locally, without physical hardware.

Usage:
    python3 gateway_test_harness.py [--step-through]

Modes:
    --step-through   Pause between test phases for inspection
    (default)        Run all phases automatically

Test phases:
    1. Baseline: normal HELLO packets, no degradation
    2. Degradation: inject [DEGRADATION] events, verify route state transitions
    3. Recovery: inject [RECOVERY] events, verify return to PRIMARY_LORA
    4. Store-Forward: queue packets during simulated peer disconnect
    5. Relay Ack: verify relay tracking pipeline
"""

import importlib.util
import json
import os
import sys
import time

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
GATEWAY_SERVICE_PATH = os.path.join(
    PROJECT_ROOT,
    "esp32-node-platformio",
    "raspberry-pi-gateway",
    "gateway_service.py",
)
RPI_GATEWAY_DIR = os.path.join(PROJECT_ROOT, "rpi-gateway")
sys.path.insert(0, RPI_GATEWAY_DIR)

from route_state_machine import (
    STATE_PRIMARY_LORA, STATE_DEGRADED,
    STATE_FAILOVER_ACTIVE, STATE_RECOVERING,
)

spec = importlib.util.spec_from_file_location(
    "gateway_service_harness", GATEWAY_SERVICE_PATH,
)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
GatewayRelay = module.GatewayRelay


def make_gw_json(payload: dict) -> str:
    return f"[GW_JSON] {json.dumps(payload, separators=(',', ':'))}"


def make_hello_line(node_id: str, gateway_id: str) -> str:
    ts = int(time.time() * 1000)
    return (
        f"BT1|HELLO-{node_id}-{ts}|{node_id}|BROADCAST|"
        f"{{\"gatewayId\":\"{gateway_id}\"}}|{node_id}|1|0|{node_id}|5|0|{ts}"
    )


def make_degradation_line(node_id: str, rssi: int = -82) -> str:
    return f"[DEGRADATION] node={node_id} rssi={rssi} state=DEGRADED"


def make_recovery_line(node_id: str, rssi: int = -55) -> str:
    return f"[RECOVERY] node={node_id} rssi={rssi}"


def make_message_gw_json(msg_id: str, src: str, dest: str, text: str) -> dict:
    return {
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
        "timestamp": int(time.time()),
        "status": "MESSAGE",
        "checksum": "CHECKSUM_PLACEHOLDER",
    }


def step_through_mode():
    return "--step-through" in sys.argv


def wait_if(msg: str = ""):
    if step_through_mode():
        input(f"\n  [STEP] {msg} — press Enter to continue...")


def report(phase: str, result: str, detail: str = ""):
    status = "PASS" if "PASS" in result else "FAIL"
    print(f"  [{status}] {phase}: {result}")
    if detail:
        print(f"        {detail}")


def main():
    print("=" * 60)
    print("PUP MANET Gateway Integration Test Harness")
    print("=" * 60)

    relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})

    # ---- Phase 1: Baseline ----
    print("\n--- Phase 1: Baseline ---")
    wait_if("Starting baseline phase")

    # Ingest a HELLO from nodeA1
    hello = make_hello_line("nodeA1", "A")
    relay._health_monitor.process_line(hello) if relay._health_monitor else None
    with relay._nodes_lock:
        relay._local_nodes["nodeA1"] = {"gateway": "A", "last_seen": time.time()}

    state = relay.get_route_state()
    expected = STATE_PRIMARY_LORA
    report("1.1 Initial state", "PASS" if state == expected else "FAIL",
           f"state={state} expected={expected}")

    relay._tick_route_state_once()
    state = relay.get_route_state()
    report("1.2 After tick", "PASS" if state == STATE_PRIMARY_LORA else "FAIL",
           f"state={state}")

    # ---- Phase 2: Degradation ----
    print("\n--- Phase 2: Degradation ---")
    wait_if("Injecting degradation event")

    # Process degradation event through the health monitor
    deg_line = make_degradation_line("nodeA1", -82)
    health = module.parse_health_event(deg_line)
    if health and relay._health_monitor:
        relay._health_monitor._apply_event(health)
        report("2.1 Degradation parsed", "PASS",
               f"event={health['event']} node={health['nodeId']} rssi={health['rssi']}")

    relay._tick_route_state_once()
    state = relay.get_route_state()
    report("2.2 Route state after degrade", "PASS" if state == STATE_DEGRADED else "FAIL",
           f"state={state} expected={STATE_DEGRADED}")
    wait_if("Degradation detected, advancing time for failover")

    # Advance failover timer
    relay._route_state_machine._degraded_since = time.time() - relay._route_state_machine.failover_seconds - 1
    relay._tick_route_state_once()
    state = relay.get_route_state()
    report("2.3 Failover active", "PASS" if state == STATE_FAILOVER_ACTIVE else "FAIL",
           f"state={state} expected={STATE_FAILOVER_ACTIVE}")

    # ---- Phase 3: Recovery ----
    print("\n--- Phase 3: Recovery ---")
    wait_if("Injecting recovery event")

    rec_line = make_recovery_line("nodeA1", -55)
    health = module.parse_health_event(rec_line)
    if health and relay._health_monitor:
        relay._health_monitor._apply_event(health)
        report("3.1 Recovery parsed", "PASS",
               f"event={health['event']} node={health['nodeId']} rssi={health['rssi']}")

    relay._tick_route_state_once()
    state = relay.get_route_state()
    report("3.2 Route state after recover", "PASS" if state == STATE_RECOVERING else "FAIL",
           f"state={state} expected={STATE_RECOVERING}")
    wait_if("Recovery detected, advancing time for return to primary")

    relay._route_state_machine._recovered_since = time.time() - relay._route_state_machine.recovery_seconds - 1
    relay._tick_route_state_once()
    state = relay.get_route_state()
    report("3.3 Return to primary", "PASS" if state == STATE_PRIMARY_LORA else "FAIL",
           f"state={state} expected={STATE_PRIMARY_LORA}")

    # ---- Phase 4: Store-Forward ----
    print("\n--- Phase 4: Store-Forward Queue ---")
    wait_if("Testing store-forward queue")

    relay._route_state_machine.force_state(STATE_FAILOVER_ACTIVE)
    msg = make_message_gw_json("MSG-SF-001", "nodeA1", "nodeB1", "store-forward test")
    result = relay.send(msg)
    depth = relay._store_forward_queue.depth
    report("4.1 Queue in failover", "PASS" if result and depth == 1 else "FAIL",
           f"queued={'YES' if result else 'NO'} depth={depth}")

    msg2 = make_message_gw_json("MSG-SF-002", "nodeA1", "nodeB1", "second msg")
    relay.send(msg2)
    report("4.2 Second message queued", "PASS" if relay._store_forward_queue.depth == 2 else "FAIL",
           f"depth={relay._store_forward_queue.depth}")

    # Filter non-queueable
    status_pkt = {"packetType": "STATUS", "packetId": "STATUS-001",
                  "sourceNode": "nodeA1", "destinationNode": "ESP32_BRIDGE"}
    relay.send(status_pkt)
    report("4.3 STATUS not queued", "PASS" if relay._store_forward_queue.depth == 2 else "FAIL",
           f"depth stayed at {relay._store_forward_queue.depth}")

    # ---- Phase 5: Relay ACK Tracking ----
    print("\n--- Phase 5: Relay ACK Tracking ---")
    wait_if("Testing relay ACK tracking")

    relay._route_state_machine.force_state(STATE_PRIMARY_LORA)

    class FakeSock:
        def __init__(self):
            self.sent = []
        def sendall(self, data):
            self.sent.append(data)

    fake = FakeSock()
    relay.conn = fake
    relay._connected = True
    relay._route_state_machine.force_state(STATE_FAILOVER_ACTIVE)

    relay.send(make_message_gw_json("MSG-TRACK-001", "nodeA1", "nodeB1", "track me"))
    tracking = relay.get_relay_tracking()
    report("5.1 Relay tracked", "PASS" if tracking["pending"] == 1 else "FAIL",
           f"pending={tracking['pending']} total={tracking['total_tracked']}")

    relay._process_peer_ack({"type": "ack", "ref_type": "MESSAGE",
                             "ref_packet_id": "MSG-TRACK-001"})
    tracking = relay.get_relay_tracking()
    report("5.2 Relay acknowledged", "PASS" if tracking["acknowledged"] == 1 else "FAIL",
           f"acked={tracking['acknowledged']} pending={tracking['pending']}")

    # ---- Summary ----
    print("\n" + "=" * 60)
    print("HARNESS COMPLETE")
    relay_status = relay.get_gateway_relay_status()
    peer_status = relay.get_peer_status()
    print(f"  Route state:      {relay.get_route_state()}")
    print(f"  Relay stats:      relayed={relay_status['stats']['relayed']} "
          f"failed={relay_status['stats']['failed']}")
    print(f"  Relay tracking:   pending={tracking['pending']} "
          f"acknowledged={tracking['acknowledged']}")
    print(f"  Store-forward:    depth={relay._store_forward_queue.depth}")
    print(f"  Peer connected:   {relay.is_connected()}")
    print("=" * 60)

    relay.stop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
