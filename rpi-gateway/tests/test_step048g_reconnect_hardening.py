#!/usr/bin/env python3
"""Integration tests for STEP048G Gateway Reconnect Hardening."""

import importlib.util
import math
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
        "gateway_service_step048g", GATEWAY_SERVICE_PATH,
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


gateway_service = load_gateway_service_module()
GatewayRelay = gateway_service.GatewayRelay


class TestStep048GReconnectInit(unittest.TestCase):
    def test_reconnect_attempts_starts_at_zero(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        self.assertEqual(relay._reconnect_attempts, 0)

    def test_backoff_config_defaults_set(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        self.assertEqual(relay.reconnect_base_delay_sec, 5)
        self.assertEqual(relay.reconnect_max_delay_sec, 120)
        self.assertEqual(relay.reconnect_backoff_multiplier, 2.0)

    def test_backoff_config_custom(self):
        relay = GatewayRelay({
            "gateway_id": "A", "serial_enabled": False,
            "reconnect_base_delay_sec": 3,
            "reconnect_max_delay_sec": 60,
            "reconnect_backoff_multiplier": 1.5,
        })
        self.assertEqual(relay.reconnect_base_delay_sec, 3)
        self.assertEqual(relay.reconnect_max_delay_sec, 60)
        self.assertEqual(relay.reconnect_backoff_multiplier, 1.5)


class TestStep048GBackoffComputation(unittest.TestCase):
    def setUp(self):
        self.relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})

    def test_first_attempt_uses_base_delay(self):
        delay = self.relay._compute_reconnect_delay(0)
        self.assertAlmostEqual(delay, 5, delta=2)

    def test_second_attempt_doubles(self):
        delay = self.relay._compute_reconnect_delay(1)
        self.assertAlmostEqual(delay, 10, delta=2)

    def test_fourth_attempt_is_8x(self):
        delay = self.relay._compute_reconnect_delay(3)
        self.assertAlmostEqual(delay, 40, delta=6)

    def test_backoff_capped_at_max(self):
        for i in range(20):
            delay = self.relay._compute_reconnect_delay(i)
        self.assertLessEqual(delay, self.relay.reconnect_max_delay_sec * 1.11)

    def test_backoff_includes_jitter(self):
        delays = set()
        for _ in range(10):
            delays.add(round(self.relay._compute_reconnect_delay(1), 3))
        self.assertGreater(len(delays), 1,
                           "jitter should produce varying delays")

    def test_reconnect_after_success_resets_attempts(self):
        self.relay._reconnect_attempts = 5
        self.relay._reset_reconnect_attempts()
        self.assertEqual(self.relay._reconnect_attempts, 0)


class TestStep048GReconnectIntegration(unittest.TestCase):
    def test_handle_peer_link_up_resets_attempts(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        relay._reconnect_attempts = 7
        relay._handle_peer_link_up()
        self.assertEqual(relay._reconnect_attempts, 0)

    def test_reconnect_status_returns_current_state(self):
        relay = GatewayRelay({"gateway_id": "A", "serial_enabled": False})
        relay._reconnect_attempts = 3
        status = relay.get_reconnect_status()
        self.assertEqual(status["attempts"], 3)
        self.assertEqual(status["base_delay_sec"], 5)
        self.assertEqual(status["max_delay_sec"], 120)
        self.assertIn("next_delay_sec", status)


if __name__ == "__main__":
    unittest.main()
