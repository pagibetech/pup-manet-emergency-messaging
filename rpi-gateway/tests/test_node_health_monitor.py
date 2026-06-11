#!/usr/bin/env python3
"""Unit tests for node_health_monitor.py (STEP048A)."""

import os
import sys
import time
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from node_health_monitor import NodeHealthMonitor, NodeHealthRecord


class TestNodeHealthMonitorDegradation(unittest.TestCase):
    def setUp(self):
        self.monitor = NodeHealthMonitor(gateway_id="A")

    def test_degradation_makes_node_degraded(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-82 state=DEGRADED")
        self.assertTrue(self.monitor.is_degraded("nodeA2"))

    def test_degradation_sets_rssi(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-82 state=DEGRADED")
        record = self.monitor.get_record("nodeA2")
        self.assertEqual(record.last_rssi, -82)

    def test_degradation_sets_timestamp(self):
        before = time.time()
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-82 state=DEGRADED")
        record = self.monitor.get_record("nodeA2")
        self.assertGreaterEqual(record.degraded_at, before)

    def test_degradation_increments_event_count(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-82 state=DEGRADED")
        record = self.monitor.get_record("nodeA2")
        self.assertEqual(record.event_count, 1)

    def test_unknown_node_is_not_degraded(self):
        self.assertFalse(self.monitor.is_degraded("nonexistent"))

    def test_unknown_node_record_is_none(self):
        self.assertIsNone(self.monitor.get_record("nonexistent"))


class TestNodeHealthMonitorRecovery(unittest.TestCase):
    def setUp(self):
        self.monitor = NodeHealthMonitor(gateway_id="A")

    def test_recovery_clears_degraded(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-82 state=DEGRADED")
        self.assertTrue(self.monitor.is_degraded("nodeA2"))
        self.monitor.process_line("[RECOVERY] node=nodeA2 rssi=-55")
        self.assertFalse(self.monitor.is_degraded("nodeA2"))

    def test_recovery_sets_recovered_at(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-82 state=DEGRADED")
        self.monitor.process_line("[RECOVERY] node=nodeA2 rssi=-55")
        record = self.monitor.get_record("nodeA2")
        self.assertGreater(record.recovered_at, 0)

    def test_recovery_updates_rssi(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-82 state=DEGRADED")
        self.monitor.process_line("[RECOVERY] node=nodeA2 rssi=-55")
        record = self.monitor.get_record("nodeA2")
        self.assertEqual(record.last_rssi, -55)

    def test_recovery_without_prior_degradation_is_noop(self):
        self.monitor.process_line("[RECOVERY] node=nodeA2 rssi=-55")
        self.assertFalse(self.monitor.is_degraded("nodeA2"))
        record = self.monitor.get_record("nodeA2")
        self.assertEqual(record.last_rssi, -55)
        self.assertEqual(record.event_count, 0)


class TestNodeHealthMonitorCycles(unittest.TestCase):
    def setUp(self):
        self.monitor = NodeHealthMonitor(gateway_id="A")

    def test_multiple_degrade_recover_cycles(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-82 state=DEGRADED")
        self.monitor.process_line("[RECOVERY] node=nodeA2 rssi=-55")
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-84 state=DEGRADED")
        self.monitor.process_line("[RECOVERY] node=nodeA2 rssi=-50")

        record = self.monitor.get_record("nodeA2")
        self.assertEqual(record.event_count, 2)
        self.assertFalse(record.degraded)
        self.assertEqual(record.last_rssi, -50)

    def test_duplicate_degradation_is_idempotent(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-82 state=DEGRADED")
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-83 state=DEGRADED")
        record = self.monitor.get_record("nodeA2")
        self.assertEqual(record.event_count, 1)
        self.assertEqual(record.last_rssi, -83)

    def test_duplicate_recovery_is_idempotent(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-82 state=DEGRADED")
        self.monitor.process_line("[RECOVERY] node=nodeA2 rssi=-55")
        self.monitor.process_line("[RECOVERY] node=nodeA2 rssi=-50")
        self.assertFalse(self.monitor.is_degraded("nodeA2"))


class TestNodeHealthMonitorSnapshot(unittest.TestCase):
    def setUp(self):
        self.monitor = NodeHealthMonitor(gateway_id="B")

    def test_empty_snapshot(self):
        snap = self.monitor.get_snapshot()
        self.assertEqual(snap["gateway_id"], "B")
        self.assertEqual(snap["degraded_count"], 0)
        self.assertEqual(snap["healthy_count"], 0)
        self.assertEqual(snap["total_count"], 0)

    def test_snapshot_with_mixed_nodes(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA1 rssi=-82 state=DEGRADED")
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-84 state=DEGRADED")
        self.monitor.process_line("[RECOVERY] node=nodeA2 rssi=-50")

        snap = self.monitor.get_snapshot()
        self.assertEqual(snap["degraded_count"], 1)
        self.assertEqual(snap["healthy_count"], 1)
        self.assertEqual(snap["total_count"], 2)
        self.assertIn("nodeA1", snap["nodes"])
        self.assertIn("nodeA2", snap["nodes"])
        self.assertTrue(snap["nodes"]["nodeA1"]["degraded"])
        self.assertFalse(snap["nodes"]["nodeA2"]["degraded"])

    def test_snapshot_timestamp_is_recent(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA1 rssi=-82 state=DEGRADED")
        snap = self.monitor.get_snapshot()
        self.assertAlmostEqual(snap["timestamp"], time.time(), delta=2.0)


class TestNodeHealthMonitorLists(unittest.TestCase):
    def setUp(self):
        self.monitor = NodeHealthMonitor(gateway_id="A")

    def test_degraded_nodes_list(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA1 rssi=-82 state=DEGRADED")
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-84 state=DEGRADED")
        self.assertEqual(self.monitor.get_degraded_nodes(), ["nodeA1", "nodeA2"])

    def test_healthy_nodes_list(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA1 rssi=-82 state=DEGRADED")
        self.monitor.process_line("[RECOVERY] node=nodeA1 rssi=-55")
        self.assertEqual(self.monitor.get_healthy_nodes(), ["nodeA1"])

    def test_mixed_lists(self):
        self.monitor.process_line("[DEGRADATION] node=nodeA1 rssi=-82 state=DEGRADED")
        self.monitor.process_line("[DEGRADATION] node=nodeA2 rssi=-84 state=DEGRADED")
        self.monitor.process_line("[RECOVERY] node=nodeA2 rssi=-50")
        self.assertEqual(self.monitor.get_degraded_nodes(), ["nodeA1"])
        self.assertEqual(self.monitor.get_healthy_nodes(), ["nodeA2"])


class TestNodeHealthMonitorNonHealthLines(unittest.TestCase):
    def setUp(self):
        self.monitor = NodeHealthMonitor(gateway_id="A")

    def test_lora_rx_line_is_ignored(self):
        result = self.monitor.process_line("[LORA_RX] rssi=-45 payload=hello")
        self.assertIsNone(result)

    def test_gw_json_line_is_ignored(self):
        result = self.monitor.process_line('[GW_JSON] {"type":"test"}')
        self.assertIsNone(result)

    def test_empty_line_is_ignored(self):
        result = self.monitor.process_line("")
        self.assertIsNone(result)

    def test_garbage_line_is_ignored(self):
        result = self.monitor.process_line("random text")
        self.assertIsNone(result)

    def test_node_state_unchanged_by_non_health_lines(self):
        self.monitor.process_line("[LORA_RX] rssi=-45 payload=hello")
        self.assertFalse(self.monitor.is_degraded("nodeA1"))


class TestNodeHealthMonitorThreadSafety(unittest.TestCase):
    def test_concurrent_updates(self):
        import threading
        monitor = NodeHealthMonitor(gateway_id="A")
        errors = []

        def worker(node_id, rssi):
            try:
                for _ in range(50):
                    monitor.process_line(
                        f"[DEGRADATION] node={node_id} rssi={rssi} state=DEGRADED"
                    )
                    monitor.process_line(
                        f"[RECOVERY] node={node_id} rssi=-50"
                    )
            except Exception as e:
                errors.append(e)

        threads = []
        for i in range(4):
            t = threading.Thread(target=worker, args=(f"node{i}", -(80 + i)))
            threads.append(t)
            t.start()

        for t in threads:
            t.join(timeout=5)

        self.assertEqual(len(errors), 0, f"thread safety errors: {errors}")
        snap = monitor.get_snapshot()
        self.assertEqual(snap["total_count"], 4)


if __name__ == "__main__":
    unittest.main()
