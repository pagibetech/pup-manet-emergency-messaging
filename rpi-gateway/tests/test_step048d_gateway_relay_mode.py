#!/usr/bin/env python3
"""Integration tests for STEP048D Gateway Relay Mode."""

import importlib.util
import json
import os
import sys
import time
import unittest

RPI_GATEWAY_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
PROJECT_ROOT = os.path.abspath(os.path.join(RPI_GATEWAY_DIR, ".."))
GATEWAY_SERVICE_PATH = os.path.join(
    PROJECT_ROOT,
    "esp32-node-platformio",
    "raspberry-pi-gateway",
    "gateway_service.py",
)

sys.path.insert(0, RPI_GATEWAY_DIR)

from route_state_machine import STATE_FAILOVER_ACTIVE, STATE_PRIMARY_LORA, STATE_RECOVERING


def load_gateway_service_module():
    spec = importlib.util.spec_from_file_location(
        "gateway_service_step048d", GATEWAY_SERVICE_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gateway_service = load_gateway_service_module()
GatewayRelay = gateway_service.GatewayRelay


class FakeSocket:
    def __init__(self, fail=False):
        self.fail = fail
        self.sent = []

    def sendall(self, payload: bytes) -> None:
        if self.fail:
            raise OSError("simulated send failure")
        self.sent.append(payload)


class TestStep048DGatewayRelayMode(unittest.TestCase):
    def setUp(self):
        self.relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})

    def test_failover_active_connected_message_uses_gateway_relay_mode(self):
        fake = FakeSocket()
        self.relay.conn = fake
        self.relay._connected = True
        self.relay._route_state_machine.force_state(STATE_FAILOVER_ACTIVE)

        result = self.relay.send(make_message_packet("MSG-RELAY"))

        self.assertTrue(result)
        self.assertEqual(self.relay._gateway_relay_stats["relayed"], 1)
        self.assertEqual(self.relay._store_forward_queue.depth, 0)
        self.assertEqual(len(fake.sent), 1)
        sent_payload = json.loads(fake.sent[0].decode("utf-8"))
        self.assertEqual(sent_payload["packetId"], "MSG-RELAY")
        self.assertNotIn("gatewayRelay", sent_payload,
                         "Gateway relay mode must not mutate BT-MANET payload format")

    def test_recovering_connected_message_still_uses_gateway_relay_mode(self):
        fake = FakeSocket()
        self.relay.conn = fake
        self.relay._connected = True
        self.relay._route_state_machine.force_state(STATE_RECOVERING)

        result = self.relay.send(make_message_packet("MSG-RECOVERING"))

        self.assertTrue(result)
        self.assertEqual(self.relay._gateway_relay_stats["relayed"], 1)
        self.assertEqual(len(fake.sent), 1)

    def test_primary_lora_connected_message_preserves_existing_direct_send(self):
        fake = FakeSocket()
        self.relay.conn = fake
        self.relay._connected = True
        self.relay._route_state_machine.force_state(STATE_PRIMARY_LORA)

        result = self.relay.send(make_message_packet("MSG-DIRECT"))

        self.assertTrue(result)
        self.assertEqual(self.relay._gateway_relay_stats["relayed"], 0)
        self.assertEqual(len(fake.sent), 1)

    def test_failover_active_connected_status_packet_is_not_gateway_relayed(self):
        fake = FakeSocket()
        self.relay.conn = fake
        self.relay._connected = True
        self.relay._route_state_machine.force_state(STATE_FAILOVER_ACTIVE)

        result = self.relay.send({
            "protocolVersion": "BT-MANET-1.0",
            "packetType": "STATUS",
            "packetId": "STATUS-STEP048D",
            "sourceNode": "nodeA1",
            "destinationNode": "ESP32_BRIDGE",
        })

        self.assertTrue(result)
        self.assertEqual(self.relay._gateway_relay_stats["relayed"], 0)
        self.assertEqual(len(fake.sent), 1)

    def test_gateway_relay_send_failure_falls_back_to_store_forward_queue(self):
        fake = FakeSocket(fail=True)
        self.relay.conn = fake
        self.relay._connected = True
        self.relay._route_state_machine.force_state(STATE_FAILOVER_ACTIVE)

        result = self.relay.send(make_message_packet("MSG-FALLBACK"))

        self.assertTrue(result)
        self.assertEqual(self.relay._gateway_relay_stats["failed"], 1)
        self.assertEqual(self.relay._store_forward_queue.depth, 1)
        self.assertEqual(self.relay._store_forward_queue.peek().message_id, "MSG-FALLBACK")

    def test_gateway_relay_status_snapshot_is_copy_safe(self):
        fake = FakeSocket()
        self.relay.conn = fake
        self.relay._connected = True
        self.relay._route_state_machine.force_state(STATE_FAILOVER_ACTIVE)
        self.relay.send(make_message_packet("MSG-SNAPSHOT"))

        snapshot = self.relay.get_gateway_relay_status()
        snapshot["stats"]["relayed"] = 999

        self.assertEqual(self.relay._gateway_relay_stats["relayed"], 1)
        self.assertEqual(snapshot["route_state"], STATE_FAILOVER_ACTIVE)
        self.assertTrue(snapshot["peer_connected"])


def make_message_packet(msg_id="MSG-001", src="nodeA1", dest="nodeB1", text="hello"):
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


if __name__ == "__main__":
    unittest.main()
