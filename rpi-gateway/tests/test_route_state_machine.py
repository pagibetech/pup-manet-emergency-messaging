#!/usr/bin/env python3
"""Unit tests for route_state_machine.py (STEP048B)."""

import os
import sys
import time
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from route_state_machine import (
    RouteStateMachine,
    STATE_PRIMARY_LORA,
    STATE_DEGRADED,
    STATE_FAILOVER_ACTIVE,
    STATE_RECOVERING,
)


class MockHealthMonitor:
    """Controllable NodeHealthMonitor stand-in for tests."""

    def __init__(self):
        self._degraded: set[str] = set()

    def set_degraded(self, *node_ids: str) -> None:
        self._degraded.update(node_ids)

    def set_healthy(self, *node_ids: str) -> None:
        self._degraded.difference_update(node_ids)

    def clear_all(self) -> None:
        self._degraded.clear()

    def is_degraded(self, node_id: str) -> bool:
        return node_id in self._degraded


class TestInitialState(unittest.TestCase):
    def test_initial_state_is_primary_lora(self):
        health = MockHealthMonitor()
        rsm = RouteStateMachine(health, gateway_id="A")
        self.assertEqual(rsm.state, STATE_PRIMARY_LORA)

    def test_transition_count_starts_at_zero(self):
        health = MockHealthMonitor()
        rsm = RouteStateMachine(health, gateway_id="A")
        self.assertEqual(rsm.transition_count, 0)


class TestNoNodesSeen(unittest.TestCase):
    def setUp(self):
        self.health = MockHealthMonitor()
        self.rsm = RouteStateMachine(self.health, gateway_id="A")

    def test_no_local_nodes_stays_primary(self):
        state = self.rsm.tick(local_node_ids=[])
        self.assertEqual(state, STATE_PRIMARY_LORA)

    def test_no_local_nodes_multiple_ticks_stays_primary(self):
        for _ in range(10):
            self.rsm.tick(local_node_ids=[])
        self.assertEqual(self.rsm.state, STATE_PRIMARY_LORA)

    def test_none_local_nodes_stays_primary(self):
        self.rsm.tick(local_node_ids=None)
        self.assertEqual(self.rsm.state, STATE_PRIMARY_LORA)


class TestPrimaryLoraToDegraded(unittest.TestCase):
    def setUp(self):
        self.health = MockHealthMonitor()
        self.rsm = RouteStateMachine(self.health, gateway_id="A")

    def test_local_node_degraded_transitions_to_degraded(self):
        self.health.set_degraded("nodeA1")
        state = self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(state, STATE_DEGRADED)

    def test_first_local_node_degraded_in_mixed_list(self):
        self.health.set_degraded("nodeA1")
        state = self.rsm.tick(local_node_ids=["nodeA1", "nodeA2"])
        self.assertEqual(state, STATE_DEGRADED)

    def test_degraded_node_not_in_local_list_no_transition(self):
        self.health.set_degraded("nodeA3")
        state = self.rsm.tick(local_node_ids=["nodeA1", "nodeA2"])
        self.assertEqual(state, STATE_PRIMARY_LORA)


class TestQuickRecovery(unittest.TestCase):
    def setUp(self):
        self.health = MockHealthMonitor()
        self.clock = Clock(0.0)
        self.rsm = RouteStateMachine(
            self.health, gateway_id="A",
            failover_seconds=10.0, _time_func=self.clock,
        )

    def test_degrade_recover_within_failover_window_stays_primary(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_DEGRADED)

        self.clock.advance(3.0)
        self.health.set_healthy("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_PRIMARY_LORA)

    def test_degrade_recover_at_boundary_stays_primary(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_DEGRADED)

        self.clock.advance(9.9)
        self.health.set_healthy("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_PRIMARY_LORA)


class TestDegradedToFailover(unittest.TestCase):
    def setUp(self):
        self.health = MockHealthMonitor()
        self.clock = Clock(0.0)
        self.rsm = RouteStateMachine(
            self.health, gateway_id="A",
            failover_seconds=10.0, _time_func=self.clock,
        )

    def test_degraded_for_exactly_failover_seconds(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_DEGRADED)

        self.clock.advance(10.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_FAILOVER_ACTIVE)

    def test_degraded_for_more_than_failover_seconds(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.clock.advance(15.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_FAILOVER_ACTIVE)

    def test_degraded_stays_degraded_within_failover_window(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.clock.advance(5.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_DEGRADED)


class TestFailoverActive(unittest.TestCase):
    def setUp(self):
        self.health = MockHealthMonitor()
        self.clock = Clock(100.0)
        self.rsm = RouteStateMachine(
            self.health, gateway_id="A",
            failover_seconds=10.0, _time_func=self.clock,
        )

    def test_failover_active_stays_while_degraded(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.clock.advance(10.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_FAILOVER_ACTIVE)

        self.clock.advance(5.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_FAILOVER_ACTIVE)

    def test_failover_to_recovering_when_all_healthy(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.clock.advance(15.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_FAILOVER_ACTIVE)

        self.health.set_healthy("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_RECOVERING)


class TestRecovering(unittest.TestCase):
    def setUp(self):
        self.health = MockHealthMonitor()
        self.clock = Clock(200.0)
        self.rsm = RouteStateMachine(
            self.health, gateway_id="A",
            failover_seconds=10.0, recovery_seconds=10.0,
            _time_func=self.clock,
        )

    def _enter_recovering(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.clock.advance(10.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.health.set_healthy("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_RECOVERING)

    def test_recovering_to_primary_after_recovery_seconds(self):
        self._enter_recovering()
        self.clock.advance(10.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_PRIMARY_LORA)

    def test_recovering_stays_recovering_within_window(self):
        self._enter_recovering()
        self.clock.advance(5.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_RECOVERING)

    def test_recovering_back_to_failover_on_degradation(self):
        self._enter_recovering()
        self.clock.advance(3.0)
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_FAILOVER_ACTIVE)

    def test_full_cycle_primary_to_primary(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_DEGRADED)
        self.clock.advance(10.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_FAILOVER_ACTIVE)
        self.health.set_healthy("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_RECOVERING)
        self.clock.advance(10.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_PRIMARY_LORA)


class TestRemoteNodeIsolation(unittest.TestCase):
    def setUp(self):
        self.health = MockHealthMonitor()
        self.rsm = RouteStateMachine(self.health, gateway_id="A")

    def test_remote_node_degraded_no_effect(self):
        self.health.set_degraded("nodeB1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_PRIMARY_LORA)

    def test_local_degraded_remote_healthy(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1", "nodeB1"])
        self.assertEqual(self.rsm.state, STATE_DEGRADED)

    def test_remote_degraded_local_healthy(self):
        self.health.set_degraded("nodeB1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_PRIMARY_LORA)


class TestFlappingProtection(unittest.TestCase):
    def setUp(self):
        self.health = MockHealthMonitor()
        self.clock = Clock(300.0)
        self.rsm = RouteStateMachine(
            self.health, gateway_id="A",
            failover_seconds=10.0, recovery_seconds=10.0,
            _time_func=self.clock,
        )

    def test_rapid_degrade_recover_does_not_escalate(self):
        for i in range(5):
            self.health.set_degraded("nodeA1")
            self.rsm.tick(local_node_ids=["nodeA1"])
            self.clock.advance(2.0)
            self.health.set_healthy("nodeA1")
            self.rsm.tick(local_node_ids=["nodeA1"])
            self.clock.advance(2.0)

        self.assertIn(self.rsm.state, (STATE_PRIMARY_LORA, STATE_DEGRADED))
        self.assertNotEqual(self.rsm.state, STATE_FAILOVER_ACTIVE)

    def test_recovery_flap_resets_window(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.clock.advance(10.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.health.set_healthy("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_RECOVERING)

        self.clock.advance(8.0)
        self.health.set_degraded("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_FAILOVER_ACTIVE)

        self.health.set_healthy("nodeA1")
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_RECOVERING)

        self.clock.advance(10.0)
        self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(self.rsm.state, STATE_PRIMARY_LORA)


class TestDeterminism(unittest.TestCase):
    def setUp(self):
        self.health = MockHealthMonitor()
        self.clock = Clock(400.0)
        self.rsm = RouteStateMachine(
            self.health, gateway_id="A",
            failover_seconds=10.0, _time_func=self.clock,
        )

    def test_repeated_ticks_same_input_produce_same_state(self):
        self.health.set_degraded("nodeA1")
        s1 = self.rsm.tick(local_node_ids=["nodeA1"])
        s2 = self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(s1, s2)

        self.health.set_healthy("nodeA1")
        s3 = self.rsm.tick(local_node_ids=["nodeA1"])
        self.clock.advance(0.1)
        s4 = self.rsm.tick(local_node_ids=["nodeA1"])
        self.assertEqual(s3, s4)

    def test_deterministic_full_cycle(self):
        self.health.set_degraded("nodeA1")
        self.assertEqual(self.rsm.tick(["nodeA1"]), STATE_DEGRADED)
        self.clock.advance(10.0)
        self.assertEqual(self.rsm.tick(["nodeA1"]), STATE_FAILOVER_ACTIVE)
        self.health.set_healthy("nodeA1")
        self.assertEqual(self.rsm.tick(["nodeA1"]), STATE_RECOVERING)
        self.clock.advance(10.0)
        self.assertEqual(self.rsm.tick(["nodeA1"]), STATE_PRIMARY_LORA)

    def test_transition_count_increments(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(["nodeA1"])
        self.assertEqual(self.rsm.transition_count, 1)
        self.clock.advance(10.0)
        self.rsm.tick(["nodeA1"])
        self.assertEqual(self.rsm.transition_count, 2)
        self.health.set_healthy("nodeA1")
        self.rsm.tick(["nodeA1"])
        self.assertEqual(self.rsm.transition_count, 3)
        self.clock.advance(10.0)
        self.rsm.tick(["nodeA1"])
        self.assertEqual(self.rsm.transition_count, 4)


class TestForceState(unittest.TestCase):
    def setUp(self):
        self.health = MockHealthMonitor()
        self.rsm = RouteStateMachine(self.health, gateway_id="A")

    def test_force_state_valid(self):
        self.assertTrue(self.rsm.force_state(STATE_FAILOVER_ACTIVE))
        self.assertEqual(self.rsm.state, STATE_FAILOVER_ACTIVE)

    def test_force_state_invalid_returns_false(self):
        self.assertFalse(self.rsm.force_state("INVALID"))
        self.assertEqual(self.rsm.state, STATE_PRIMARY_LORA)

    def test_force_same_state_no_transition_count(self):
        self.rsm.force_state(STATE_DEGRADED)
        count = self.rsm.transition_count
        self.rsm.force_state(STATE_DEGRADED)
        self.assertEqual(self.rsm.transition_count, count)


class TestGetInfo(unittest.TestCase):
    def setUp(self):
        self.health = MockHealthMonitor()
        self.rsm = RouteStateMachine(self.health, gateway_id="B",
                                     failover_seconds=15.0,
                                     recovery_seconds=20.0)

    def test_get_info_initial(self):
        info = self.rsm.get_info()
        self.assertEqual(info["state"], STATE_PRIMARY_LORA)
        self.assertEqual(info["gateway_id"], "B")
        self.assertEqual(info["transition_count"], 0)
        self.assertEqual(info["failover_seconds"], 15.0)
        self.assertEqual(info["recovery_seconds"], 20.0)

    def test_get_info_after_transitions(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(["nodeA1"])
        info = self.rsm.get_info()
        self.assertEqual(info["state"], STATE_DEGRADED)
        self.assertEqual(info["transition_count"], 1)


class TestTimeInState(unittest.TestCase):
    def setUp(self):
        self.health = MockHealthMonitor()
        self.clock = Clock(500.0)
        self.rsm = RouteStateMachine(
            self.health, gateway_id="A",
            _time_func=self.clock,
        )

    def test_time_in_primary_is_zero(self):
        self.assertEqual(self.rsm.time_in_state(), 0.0)

    def test_time_in_degraded(self):
        self.health.set_degraded("nodeA1")
        self.rsm.tick(["nodeA1"])
        self.clock.advance(3.0)
        self.assertAlmostEqual(self.rsm.time_in_state(), 3.0, delta=0.1)


class Clock:
    def __init__(self, start: float = 0.0):
        self._t = start

    def __call__(self) -> float:
        return self._t

    def advance(self, seconds: float) -> None:
        self._t += seconds


if __name__ == "__main__":
    unittest.main()
