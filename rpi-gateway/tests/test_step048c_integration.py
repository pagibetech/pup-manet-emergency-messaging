#!/usr/bin/env python3
"""Integration tests for STEP048C Store-and-Forward Gateway Integration."""

import importlib.util
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

from route_state_machine import STATE_FAILOVER_ACTIVE, STATE_PRIMARY_LORA
from replay_engine import ReplayResult


def load_gateway_service_module():
    spec = importlib.util.spec_from_file_location(
        "gateway_service_step048c", GATEWAY_SERVICE_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gateway_service = load_gateway_service_module()
GatewayRelay = gateway_service.GatewayRelay


class FakeSocket:
    def __init__(self):
        self.sent = []

    def sendall(self, payload: bytes) -> None:
        self.sent.append(payload)


class FakeReplayScheduler:
    def __init__(self):
        self.link_up_count = 0
        self.link_down_count = 0
        self.stop_count = 0

    def on_link_up(self):
        self.link_up_count += 1

    def on_link_down(self):
        self.link_down_count += 1

    def stop(self):
        self.stop_count += 1


class TestStep048CComponentWiring(unittest.TestCase):
    def test_gateway_initializes_store_forward_route_state_and_replay(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})

        self.assertIsNotNone(relay._store_forward_queue)
        self.assertIsNotNone(relay._replay_scheduler)
        self.assertIsNotNone(relay._route_state_machine)
        self.assertEqual(relay.get_route_state(), STATE_PRIMARY_LORA)
        self.assertEqual(relay._store_forward_queue.depth, 0)


class TestStep048CLocalNodeRouteTick(unittest.TestCase):
    def test_active_local_node_ids_exclude_expired_entries(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        now = time.time()
        with relay._nodes_lock:
            relay._local_nodes["nodeA1"] = {"gateway": "A", "last_seen": now}
            relay._local_nodes["nodeA2"] = {"gateway": "A", "last_seen": now - 100}

        self.assertEqual(relay._get_active_local_node_ids(), ["nodeA1"])

    def test_route_state_tick_consumes_local_node_health(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        with relay._nodes_lock:
            relay._local_nodes["nodeA1"] = {"gateway": "A", "last_seen": time.time()}

        relay._health_monitor._apply_event({
            "event": "degradation",
            "nodeId": "nodeA1",
            "rssi": -90,
        })
        relay._tick_route_state_once()

        self.assertEqual(relay.get_route_state(), "DEGRADED")


class TestStep048CSendQueueBehavior(unittest.TestCase):
    def setUp(self):
        self.relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})

    def test_connected_send_uses_existing_tcp_path_without_queueing(self):
        fake = FakeSocket()
        self.relay.conn = fake
        self.relay._connected = True

        result = self.relay.send(make_message_packet("MSG-DIRECT"))

        self.assertTrue(result)
        self.assertEqual(self.relay._store_forward_queue.depth, 0)
        self.assertEqual(len(fake.sent), 1)
        self.assertIn(b"MSG-DIRECT", fake.sent[0])

    def test_disconnected_primary_lora_does_not_queue_packet(self):
        self.relay._route_state_machine.force_state(STATE_PRIMARY_LORA)

        result = self.relay.send(make_message_packet("MSG-PRIMARY-DROP"))

        self.assertFalse(result)
        self.assertEqual(self.relay._store_forward_queue.depth, 0)

    def test_disconnected_failover_active_queues_message_packet(self):
        self.relay._route_state_machine.force_state(STATE_FAILOVER_ACTIVE)

        result = self.relay.send(make_message_packet("MSG-QUEUED"))

        self.assertTrue(result)
        self.assertEqual(self.relay._store_forward_queue.depth, 1)
        self.assertEqual(self.relay._store_forward_queue.peek().message_id, "MSG-QUEUED")

    def test_disconnected_failover_active_does_not_queue_status_packet(self):
        self.relay._route_state_machine.force_state(STATE_FAILOVER_ACTIVE)

        result = self.relay.send({
            "protocolVersion": "BT-MANET-1.0",
            "packetType": "STATUS",
            "packetId": "STATUS-001",
            "sourceNode": "nodeA1",
            "destinationNode": "ESP32_BRIDGE",
        })

        self.assertFalse(result)
        self.assertEqual(self.relay._store_forward_queue.depth, 0)


class TestStep048CReplayHooks(unittest.TestCase):
    def test_peer_link_up_and_down_notify_replay_scheduler(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        fake_replay = FakeReplayScheduler()
        relay._replay_scheduler = fake_replay

        relay._handle_peer_link_up()
        relay._handle_peer_link_down()
        relay.stop()

        self.assertEqual(fake_replay.link_up_count, 1)
        self.assertEqual(fake_replay.link_down_count, 1)
        self.assertEqual(fake_replay.stop_count, 1)

    def test_replay_send_callback_returns_success_for_tcp_send_success(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        relay.conn = FakeSocket()
        relay._connected = True

        result = relay._send_replay_packet(make_message_packet("MSG-REPLAY"))

        self.assertEqual(result, ReplayResult.SUCCESS)
        self.assertEqual(relay._store_forward_queue.depth, 0)

    def test_replay_send_callback_returns_retryable_for_tcp_send_failure(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        relay.conn = None
        relay._connected = False

        result = relay._send_replay_packet(make_message_packet("MSG-RETRY"))

        self.assertEqual(result, ReplayResult.RETRYABLE)


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
