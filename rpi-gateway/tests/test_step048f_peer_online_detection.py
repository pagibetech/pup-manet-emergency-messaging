#!/usr/bin/env python3
"""Integration tests for STEP048F Peer Online Detection Hardening."""

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


def load_gateway_service_module():
    spec = importlib.util.spec_from_file_location(
        "gateway_service_step048f", GATEWAY_SERVICE_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gateway_service = load_gateway_service_module()
GatewayRelay = gateway_service.GatewayRelay


class TestStep048FPeerOnlineInit(unittest.TestCase):
    def test_peer_last_seen_starts_none(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        self.assertIsNone(relay._peer_last_seen)

    def test_peer_online_starts_false(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        self.assertFalse(relay.is_peer_online())

    def test_peer_lost_count_starts_zero(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        self.assertEqual(relay._peer_lost_count, 0)


class TestStep048FPeerOnlineDetection(unittest.TestCase):
    def setUp(self):
        self.relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})

    def test_peer_online_when_last_seen_recent(self):
        self.relay._touch_peer_seen()
        self.assertTrue(self.relay.is_peer_online())

    def test_peer_offline_when_last_seen_exceeds_timeout(self):
        self.relay._touch_peer_seen()
        self.relay._peer_last_seen -= self.relay.peer_online_timeout_sec + 1
        self.assertFalse(self.relay.is_peer_online())

    def test_peer_online_at_exactly_timeout_boundary(self):
        self.relay._touch_peer_seen()
        self.relay._peer_last_seen -= self.relay.peer_online_timeout_sec
        self.assertFalse(self.relay.is_peer_online())

    def test_peer_lost_count_increments_on_offline_transition(self):
        self.relay._touch_peer_seen()
        self.assertTrue(self.relay.is_peer_online())  # establish online state
        self.relay._peer_last_seen -= self.relay.peer_online_timeout_sec + 1
        self.relay.is_peer_online()
        self.assertEqual(self.relay._peer_lost_count, 1)
        self.relay.is_peer_online()
        self.assertEqual(self.relay._peer_lost_count, 1,
                         "lost count must not increment repeatedly while still offline")

    def test_peer_lost_count_resets_on_reconnect(self):
        self.relay._touch_peer_seen()
        self.assertTrue(self.relay.is_peer_online())  # establish online
        self.relay._peer_last_seen -= self.relay.peer_online_timeout_sec + 1
        self.relay.is_peer_online()
        self.assertEqual(self.relay._peer_lost_count, 1)
        self.relay._touch_peer_seen()
        self.assertTrue(self.relay.is_peer_online())
        self.relay._peer_last_seen -= self.relay.peer_online_timeout_sec + 1
        self.relay.is_peer_online()
        self.assertEqual(self.relay._peer_lost_count, 1,
                         "lost count resets on reconnect, so second loss is count=1")

    def test_handle_peer_link_up_touches_peer_seen(self):
        self.relay._handle_peer_link_up()
        self.assertIsNotNone(self.relay._peer_last_seen)
        self.assertTrue(self.relay.is_peer_online())

    def test_handle_peer_link_down_invalidates_online(self):
        self.relay._touch_peer_seen()
        self.relay._handle_peer_link_down()
        self.assertTrue(self.relay._peer_last_seen is None or not self.relay.is_peer_online())

    def test_get_peer_status_returns_snapshot(self):
        self.relay._touch_peer_seen()
        status = self.relay.get_peer_status()
        self.assertTrue(status["online"])
        self.assertIsNotNone(status["last_seen"])
        self.assertEqual(status["lost_count"], 0)
        self.assertEqual(status["gateway_id"], "A")

    def test_peer_online_threshold_configurable(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False,
                              "peer_online_timeout_sec": 5})
        self.assertEqual(relay.peer_online_timeout_sec, 5)


if __name__ == "__main__":
    unittest.main()
