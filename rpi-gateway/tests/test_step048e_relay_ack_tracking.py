#!/usr/bin/env python3
"""Integration tests for STEP048E Gateway Relay ACK Tracking."""

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

from route_state_machine import STATE_FAILOVER_ACTIVE, STATE_PRIMARY_LORA


def load_gateway_service_module():
    spec = importlib.util.spec_from_file_location(
        "gateway_service_step048e", GATEWAY_SERVICE_PATH,
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


class TestStep048ERelayTrackingInit(unittest.TestCase):
    def test_relay_tracking_starts_empty(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        status = relay.get_relay_tracking()
        self.assertEqual(status["pending"], 0)
        self.assertEqual(status["acknowledged"], 0)
        self.assertEqual(status["total_tracked"], 0)
        self.assertEqual(len(status["entries"]), 0)

    def test_relay_tracking_is_copy_safe(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        relay._relay_track("PKT-001", "nodeA1", "nodeB1")
        snap = relay.get_relay_tracking()
        snap["entries"]["PKT-001"]["status"] = "CORRUPTED"
        self.assertEqual(
            relay._relay_tracking["PKT-001"]["status"], "pending",
            "returned snapshot must not mutate internal tracking",
        )


class TestStep048ERelayTrackAndAck(unittest.TestCase):
    def setUp(self):
        self.relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})

    def test_relay_track_records_pending_entry(self):
        self.relay._relay_track("PKT-001", "nodeA1", "nodeB1")

        self.assertIn("PKT-001", self.relay._relay_tracking)
        entry = self.relay._relay_tracking["PKT-001"]
        self.assertEqual(entry["status"], "pending")
        self.assertEqual(entry["src_node"], "nodeA1")
        self.assertEqual(entry["dest_node"], "nodeB1")
        self.assertGreater(entry["sent_at"], 0)

    def test_relay_ack_marks_entry_acknowledged(self):
        self.relay._relay_track("PKT-001", "nodeA1", "nodeB1")
        self.relay._relay_ack("PKT-001")

        entry = self.relay._relay_tracking["PKT-001"]
        self.assertEqual(entry["status"], "acknowledged")
        self.assertGreater(entry["acked_at"], 0)

    def test_relay_ack_unknown_packet_is_noop(self):
        self.relay._relay_ack("PKT-NEVER_TRACKED")
        self.assertEqual(len(self.relay._relay_tracking), 0)

    def test_multiple_relay_packets_tracked_independently(self):
        self.relay._relay_track("PKT-A", "nodeA1", "nodeB1")
        self.relay._relay_track("PKT-B", "nodeA2", "nodeB2")
        self.relay._relay_ack("PKT-A")

        self.assertEqual(self.relay._relay_tracking["PKT-A"]["status"], "acknowledged")
        self.assertEqual(self.relay._relay_tracking["PKT-B"]["status"], "pending")
        self.assertEqual(len(self.relay._relay_tracking), 2)

    def test_double_ack_is_idempotent(self):
        self.relay._relay_track("PKT-001", "nodeA1", "nodeB1")
        self.relay._relay_ack("PKT-001")
        acked_at_first = self.relay._relay_tracking["PKT-001"]["acked_at"]
        time.sleep(0.001)
        self.relay._relay_ack("PKT-001")
        self.assertEqual(
            self.relay._relay_tracking["PKT-001"]["acked_at"], acked_at_first,
            "duplicate ACK must not overwrite first acked_at",
        )

    def test_relay_tracking_status_counts(self):
        self.relay._relay_track("PKT-001", "src", "dst")
        self.relay._relay_track("PKT-002", "src", "dst")
        self.relay._relay_track("PKT-003", "src", "dst")
        self.relay._relay_ack("PKT-001")

        status = self.relay.get_relay_tracking()
        self.assertEqual(status["pending"], 2)
        self.assertEqual(status["acknowledged"], 1)
        self.assertEqual(status["total_tracked"], 3)

    def test_relay_track_max_entries_enforced(self):
        for i in range(120):
            self.relay._relay_track(f"PKT-{i:04d}", "src", "dst")
        self.assertLessEqual(
            len(self.relay._relay_tracking), 100,
            "relay tracking must be bounded to prevent memory growth",
        )


class TestStep048ERelayAckFromPeer(unittest.TestCase):
    def setUp(self):
        self.relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})

    def test_process_peer_ack_with_ref_packet_id(self):
        self.relay._relay_track("PKT-001", "nodeA1", "nodeB1")

        ack_msg = {
            "type": "ack",
            "ref_type": "MESSAGE",
            "ref_packet_id": "PKT-001",
            "timestamp": time.time(),
        }
        self.relay._process_peer_ack(ack_msg)

        self.assertEqual(
            self.relay._relay_tracking["PKT-001"]["status"], "acknowledged",
        )

    def test_process_peer_ack_without_ref_packet_id_no_error(self):
        self.relay._relay_track("PKT-001", "nodeA1", "nodeB1")

        ack_msg = {"type": "ack", "ref_type": "heartbeat", "timestamp": time.time()}
        self.relay._process_peer_ack(ack_msg)

        self.assertEqual(
            self.relay._relay_tracking["PKT-001"]["status"], "pending",
        )

    def test_process_peer_ack_non_dict_no_error(self):
        self.relay._relay_track("PKT-001", "nodeA1", "nodeB1")
        self.relay._process_peer_ack(None)
        self.relay._process_peer_ack("not a dict")
        self.assertEqual(
            self.relay._relay_tracking["PKT-001"]["status"], "pending",
        )


class TestStep048ERelaySendIntegration(unittest.TestCase):
    def setUp(self):
        self.relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})

    def test_gateway_relay_tracks_packet(self):
        fake = FakeSocket()
        self.relay.conn = fake
        self.relay._connected = True
        self.relay._route_state_machine.force_state(STATE_FAILOVER_ACTIVE)

        self.relay.send(make_message_packet("MSG-TRACKED"))
        self.assertIn("MSG-TRACKED", self.relay._relay_tracking)
        self.assertEqual(
            self.relay._relay_tracking["MSG-TRACKED"]["status"], "pending",
        )

    def test_primary_lora_send_does_not_relay_track(self):
        fake = FakeSocket()
        self.relay.conn = fake
        self.relay._connected = True
        self.relay._route_state_machine.force_state(STATE_PRIMARY_LORA)

        self.relay.send(make_message_packet("MSG-DIRECT"))
        self.assertNotIn("MSG-DIRECT", self.relay._relay_tracking)


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
