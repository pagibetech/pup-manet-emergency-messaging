#!/usr/bin/env python3
"""Unit tests for health_event_parser.py (STEP048A)."""

import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from health_event_parser import parse_health_event


class TestParseDegradation(unittest.TestCase):
    def test_valid_degradation(self):
        event = parse_health_event("[DEGRADATION] node=nodeA2 rssi=-82 state=DEGRADED")
        self.assertIsNotNone(event)
        self.assertEqual(event["event"], "degradation")
        self.assertEqual(event["nodeId"], "nodeA2")
        self.assertEqual(event["rssi"], -82)

    def test_valid_degradation_with_rssi_positive(self):
        event = parse_health_event("[DEGRADATION] node=nodeB1 rssi=-78 state=DEGRADED")
        self.assertIsNotNone(event)
        self.assertEqual(event["nodeId"], "nodeB1")
        self.assertEqual(event["rssi"], -78)

    def test_degradation_with_trailing_text(self):
        event = parse_health_event("[DEGRADATION] node=nodeA1 rssi=-90 state=DEGRADED extra")
        self.assertIsNotNone(event)
        self.assertEqual(event["nodeId"], "nodeA1")
        self.assertEqual(event["rssi"], -90)

    def test_degradation_with_gateway_id(self):
        event = parse_health_event("[DEGRADATION] node=gatewayA rssi=-85 state=DEGRADED")
        self.assertIsNotNone(event)
        self.assertEqual(event["nodeId"], "gatewayA")
        self.assertEqual(event["rssi"], -85)

    def test_degradation_malformed_no_rssi_returns_none(self):
        event = parse_health_event("[DEGRADATION] garbage")
        self.assertIsNone(event)

    def test_degradation_non_numeric_rssi_returns_none(self):
        event = parse_health_event("[DEGRADATION] node=nodeA1 rssi=abc")
        self.assertIsNone(event)

    def test_degradation_empty_node_id_returns_none(self):
        event = parse_health_event("[DEGRADATION] node= rssi=-80")
        self.assertIsNone(event)


class TestParseRecovery(unittest.TestCase):
    def test_valid_recovery(self):
        event = parse_health_event("[RECOVERY] node=nodeA2 rssi=-55")
        self.assertIsNotNone(event)
        self.assertEqual(event["event"], "recovery")
        self.assertEqual(event["nodeId"], "nodeA2")
        self.assertEqual(event["rssi"], -55)

    def test_recovery_with_trailing(self):
        event = parse_health_event("[RECOVERY] node=nodeA2 rssi=-55 trailing")
        self.assertIsNotNone(event)
        self.assertEqual(event["nodeId"], "nodeA2")

    def test_recovery_malformed_returns_none(self):
        event = parse_health_event("[RECOVERY] incomplete")
        self.assertIsNone(event)

    def test_recovery_non_numeric_rssi_returns_none(self):
        event = parse_health_event("[RECOVERY] node=nodeA1 rssi=xyz")
        self.assertIsNone(event)

    def test_recovery_empty_node_returns_none(self):
        event = parse_health_event("[RECOVERY] node= rssi=-55")
        self.assertIsNone(event)


class TestParseNonHealthLines(unittest.TestCase):
    def test_lora_rx_returns_none(self):
        self.assertIsNone(parse_health_event("[LORA_RX] rssi=-45 payload=hello"))

    def test_gw_json_returns_none(self):
        self.assertIsNone(parse_health_event('[GW_JSON] {"type":"test"}'))

    def test_empty_string_returns_none(self):
        self.assertIsNone(parse_health_event(""))

    def test_none_input_returns_none(self):
        self.assertIsNone(parse_health_event(None))

    def test_garbage_returns_none(self):
        self.assertIsNone(parse_health_event("garbage text here"))

    def test_hello_log_returns_none(self):
        self.assertIsNone(parse_health_event("[HELLO] discovered nodeA1"))

    def test_whitespace_only_returns_none(self):
        self.assertIsNone(parse_health_event("   "))


class TestParseEdgeCases(unittest.TestCase):
    def test_partial_prefix_no_match(self):
        self.assertIsNone(parse_health_event("[DEGRADATION"))

    def test_case_sensitive_prefix(self):
        self.assertIsNone(parse_health_event("[degradation] node=nodeA1 rssi=-80"))

    def test_degradation_preserves_negative_rssi(self):
        event = parse_health_event("[DEGRADATION] node=nodeA1 rssi=-100 state=DEGRADED")
        self.assertEqual(event["rssi"], -100)

    def test_degradation_with_node_id_containing_hyphen(self):
        event = parse_health_event("[DEGRADATION] node=node-A1 rssi=-80 state=DEGRADED")
        self.assertIsNotNone(event)
        self.assertEqual(event["nodeId"], "node-A1")


if __name__ == "__main__":
    unittest.main()
