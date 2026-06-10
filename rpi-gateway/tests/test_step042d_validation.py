#!/usr/bin/env python3
"""
STEP042D-D Store-and-Forward Validation Tests

End-to-end validation scenarios using both Queue Engine and Replay Engine.
Each scenario traces messages through the full store-and-forward lifecycle.

Scenarios tested:
  S1  VPN down -> MESSAGE queued -> VPN restored -> replayed -> delivery path
  S2  VPN down -> DELIVERY ACK queued -> VPN restored -> replayed -> Delivered
  S3  VPN down -> SEEN ACK queued -> VPN restored -> replayed -> Seen
  S4  TTL expiration -> packet dropped -> FORWARD/EXPIRED diagnostic only
  S5  Duplicate enqueue -> duplicate rejected
  S6  Link flap -> stability gate prevents replay storm
  S7  MESSAGE already acknowledged -> queue.mark_acked() -> replay skipped
  S8  STEP042C state machine preserved: MESSAGE -> DELIVERED -> SEEN
"""

import os
import sys
import threading
import time
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from store_forward_queue import (
    DEFAULT_TTL_SECONDS,
    StoreForwardQueue,
    build_buffered_ack,
    build_expired_ack,
    build_dropped_ack,
)
from replay_engine import (
    ReplayEngine,
    ReplayResult,
    ReplayScheduler,
)


SHORT_GATE = 0.2
SHORT_DELAY = 0.02
NOW = time.time

STEP042C_STATES = {"MESSAGE", "DELIVERED", "SEEN", "UNKNOWN", "FAILED"}
STEP042C_TRANSITIONS = {
    "MESSAGE": {"DELIVERED", "UNKNOWN", "FAILED"},
    "DELIVERED": {"SEEN"},
    "UNKNOWN": {"DELIVERED", "SEEN"},
    "FAILED": {"MESSAGE"},
    "SEEN": set(),
}


def make_message(msg_id, src="nodeA1", dest="nodeB1", text="hello"):
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


def make_delivery_ack(ack_for, src="nodeB1", dest="nodeA1", ack_id=None):
    ts = int(time.time() * 1000)
    pid = ack_id or f"ACK-{ack_for}-{src}-{ts}"
    return {
        "protocolVersion": "BT-MANET-1.0",
        "packetType": "ACK",
        "packetId": pid,
        "sourceNode": src,
        "destinationNode": dest,
        "payload": {
            "ackVersion": 1,
            "ackType": "DELIVERY",
            "ackFor": ack_for,
            "ackStatus": "DELIVERED",
            "originNode": dest,
            "finalDestinationNode": src,
            "ackSource": src,
            "reason": "",
            "route": [dest, src],
        },
        "hopPath": f"{src}>{dest}",
        "hopCount": 1,
        "ttl": 6,
        "previousHop": src,
        "retryCount": 0,
        "timestamp": ts,
        "status": "ACK",
        "checksum": "CHECKSUM_PLACEHOLDER",
    }


def make_seen_ack(ack_for, src="nodeB1", dest="nodeA1"):
    pkt = make_delivery_ack(ack_for, src, dest,
                            ack_id=f"ACK-SEEN-{ack_for}-{src}-{int(time.time()*1000)}")
    pkt["payload"]["ackType"] = "SEEN"
    pkt["payload"]["ackStatus"] = "SEEN"
    return pkt


class DeliveryTracker:
    """Lightweight STEP042C delivery tracking state machine for validation."""

    def __init__(self):
        self.states: dict[str, str] = {}
        self.valid_transitions = STEP042C_TRANSITIONS

    def create_message(self, msg_id: str) -> str:
        self.states[msg_id] = "MESSAGE"
        return "MESSAGE"

    def deliver(self, msg_id: str) -> bool:
        current = self.states.get(msg_id)
        if current is None:
            self.states[msg_id] = "DELIVERED"
            return True
        if "DELIVERED" in self.valid_transitions.get(current, set()):
            self.states[msg_id] = "DELIVERED"
            return True
        return False

    def seen(self, msg_id: str) -> bool:
        current = self.states.get(msg_id)
        if current and "SEEN" in self.valid_transitions.get(current, set()):
            self.states[msg_id] = "SEEN"
            return True
        return False

    def unknown(self, msg_id: str) -> bool:
        current = self.states.get(msg_id)
        if current is None:
            self.states[msg_id] = "UNKNOWN"
            return True
        if "UNKNOWN" in self.valid_transitions.get(current, set()):
            self.states[msg_id] = "UNKNOWN"
            return True
        return False

    def transition_allowed(self, msg_id: str, new_state: str) -> bool:
        current = self.states.get(msg_id)
        if current is None:
            return True
        return new_state in self.valid_transitions.get(current, set())


class S1_VpnDownMessageQueuedReplayed(unittest.TestCase):
    """Scenario 1: VPN down -> MESSAGE queued -> VPN restored -> replayed."""

    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")
        self.sent_packets = []
        self.acked_by_sender = []

        def send_cb(packet):
            self.sent_packets.append(packet)
            return ReplayResult.SUCCESS

        self.engine = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=SHORT_DELAY,
        )

    def tearDown(self):
        self.engine.stop()

    def test_message_queued_and_replayed(self):
        msg = make_message("MSG-S1-001", "nodeA1", "nodeB1")
        entry = self.queue.enqueue(msg, now=NOW())
        self.assertIsNotNone(entry)
        self.assertEqual(self.queue.depth, 1)

        ack = build_buffered_ack(msg, "GWA")
        self.acked_by_sender.append(ack)
        self.assertEqual(ack["payload"]["ackStatus"], "BUFFERED")
        self.assertEqual(ack["payload"]["ackType"], "FORWARD")

        self.engine.on_link_up()

        deadline = time.time() + 5.0
        while time.time() < deadline:
            if len(self.sent_packets) >= 1:
                break
            time.sleep(0.02)

        self.assertGreaterEqual(len(self.sent_packets), 1)
        self.assertEqual(self.sent_packets[0]["packetType"], "MESSAGE")
        self.assertEqual(self.sent_packets[0]["packetId"], "MSG-S1-001")
        self.assertEqual(self.queue.depth, 0)
        self.assertEqual(self.engine.stats.succeeded, 1)


class S2_DeliveryAckQueuedReplayed(unittest.TestCase):
    """Scenario 2: VPN down -> DELIVERY ACK queued -> VPN restored -> replayed."""

    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")
        self.sent_packets = []
        self.tracker = DeliveryTracker()

        def send_cb(packet):
            self.sent_packets.append(packet)
            return ReplayResult.SUCCESS

        self.engine = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=SHORT_DELAY,
        )

    def tearDown(self):
        self.engine.stop()

    def test_delivery_ack_queued_and_replayed(self):
        self.tracker.create_message("MSG-S2-001")
        ack = make_delivery_ack("MSG-S2-001", "nodeB1", "nodeA1")

        entry = self.queue.enqueue(ack, now=NOW())
        self.assertIsNotNone(entry)
        self.assertEqual(self.queue.depth, 1)

        self.engine.on_link_up()

        deadline = time.time() + 5.0
        while time.time() < deadline:
            if len(self.sent_packets) >= 1:
                break
            time.sleep(0.02)

        self.assertGreaterEqual(len(self.sent_packets), 1)
        replayed = self.sent_packets[0]
        self.assertEqual(replayed["packetType"], "ACK")
        self.assertEqual(replayed["payload"]["ackType"], "DELIVERY")
        self.assertEqual(replayed["payload"]["ackFor"], "MSG-S2-001")
        self.assertEqual(self.queue.depth, 0)

        self.assertTrue(self.tracker.deliver("MSG-S2-001"))
        self.assertEqual(self.tracker.states["MSG-S2-001"], "DELIVERED")


class S3_SeenAckQueuedReplayed(unittest.TestCase):
    """Scenario 3: VPN down -> SEEN ACK queued -> VPN restored -> replayed."""

    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")
        self.sent_packets = []
        self.tracker = DeliveryTracker()

        def send_cb(packet):
            self.sent_packets.append(packet)
            return ReplayResult.SUCCESS

        self.engine = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=SHORT_DELAY,
        )

    def tearDown(self):
        self.engine.stop()

    def test_seen_ack_queued_and_replayed(self):
        self.tracker.create_message("MSG-S3-001")
        self.tracker.deliver("MSG-S3-001")
        seen = make_seen_ack("MSG-S3-001", "nodeB1", "nodeA1")

        entry = self.queue.enqueue(seen, now=NOW())
        self.assertIsNotNone(entry)
        self.assertEqual(self.queue.depth, 1)

        self.engine.on_link_up()

        deadline = time.time() + 5.0
        while time.time() < deadline:
            if len(self.sent_packets) >= 1:
                break
            time.sleep(0.02)

        self.assertGreaterEqual(len(self.sent_packets), 1)
        replayed = self.sent_packets[0]
        self.assertEqual(replayed["packetType"], "ACK")
        self.assertEqual(replayed["payload"]["ackType"], "SEEN")
        self.assertEqual(replayed["payload"]["ackFor"], "MSG-S3-001")
        self.assertEqual(self.queue.depth, 0)

        self.assertTrue(self.tracker.seen("MSG-S3-001"))
        self.assertEqual(self.tracker.states["MSG-S3-001"], "SEEN")


class S4_TTLExpirationDiagnosticOnly(unittest.TestCase):
    """Scenario 4: TTL expiration -> FORWARD/EXPIRED diagnostic -> no Delivered."""

    def setUp(self):
        self.queue = StoreForwardQueue(ttl_seconds=1, gateway_id="A")
        self.tracker = DeliveryTracker()
        self.acks_emitted = []

        def ack_cb(packet):
            self.acks_emitted.append(packet)

        self.engine = ReplayEngine(
            queue=self.queue,
            send_callback=lambda p: ReplayResult.SUCCESS,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=SHORT_DELAY,
            ack_callback=ack_cb,
        )

    def tearDown(self):
        self.engine.stop()

    def test_ttl_expired_is_diagnostic_only(self):
        self.tracker.create_message("MSG-S4-001")
        msg = make_message("MSG-S4-001")

        self.queue.enqueue(msg, now=NOW() - 300)
        self.engine.on_link_up()

        deadline = time.time() + 5.0
        while time.time() < deadline:
            if self.engine.stats.skipped_expired >= 1:
                break
            time.sleep(0.02)

        self.assertEqual(self.engine.stats.skipped_expired, 1,
                         f"Expected 1 skipped_expired, got "
                         f"{self.engine.stats.skipped_expired}")
        self.assertEqual(self.queue.depth, 0)
        self.assertGreater(len(self.acks_emitted), 0,
                           "Expected at least 1 expired ACK")

        expired_ack = self.acks_emitted[0]
        self.assertEqual(expired_ack["payload"]["ackType"], "FORWARD",
                         "EXPIRED must be diagnostic FORWARD, not DELIVERY")
        self.assertEqual(expired_ack["payload"]["ackStatus"], "EXPIRED")
        self.assertNotEqual(expired_ack["payload"]["ackStatus"], "DELIVERED")

        for ack in self.acks_emitted:
            self.assertNotEqual(ack["payload"].get("ackType"), "DELIVERY",
                                "EXPIRED ACK must never use DELIVERY tier")


class S5_DuplicateEnqueueRejected(unittest.TestCase):
    """Scenario 5: Duplicate enqueue -> duplicate rejected."""

    def test_duplicate_rejected(self):
        queue = StoreForwardQueue(gateway_id="A")
        msg = make_message("MSG-S5-001")

        e1 = queue.enqueue(msg, now=NOW())
        self.assertIsNotNone(e1)
        self.assertEqual(queue.depth, 1)

        e2 = queue.enqueue(msg, now=NOW())
        self.assertIsNone(e2)
        self.assertEqual(queue.depth, 1)
        self.assertEqual(queue.stats["dropped_duplicate"], 1)

    def test_different_ids_both_accepted(self):
        queue = StoreForwardQueue(gateway_id="A")

        e1 = queue.enqueue(make_message("MSG-S5-A"), now=NOW())
        self.assertIsNotNone(e1)

        e2 = queue.enqueue(make_message("MSG-S5-B"), now=NOW())
        self.assertIsNotNone(e2)

        self.assertEqual(queue.depth, 2)

    def test_message_and_its_ack_are_not_duplicates(self):
        queue = StoreForwardQueue(gateway_id="A")

        e1 = queue.enqueue(make_message("MSG-S5-001"), now=NOW())
        self.assertIsNotNone(e1)

        ack = make_delivery_ack("MSG-S5-001")
        e2 = queue.enqueue(ack, now=NOW())
        self.assertIsNotNone(e2)

        self.assertEqual(queue.depth, 2)


class S6_LinkFlapStabilityGate(unittest.TestCase):
    """Scenario 6: Link flap -> stability gate prevents replay storm."""

    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")

        self.count = 0
        self.lock = threading.Lock()

        def send_cb(packet):
            with self.lock:
                self.count += 1
            return ReplayResult.SUCCESS

        self.scheduler = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=SHORT_DELAY,
        )

    def tearDown(self):
        self.scheduler.stop()

    def test_flapping_link_respects_stability_gate(self):
        self.queue.enqueue(make_message("MSG-S6-001"), now=NOW())
        self.scheduler.on_link_up()
        time.sleep(0.05)
        self.scheduler.on_link_down()
        time.sleep(0.4)
        self.scheduler.on_link_up()

        deadline = time.time() + 5.0
        while time.time() < deadline:
            with self.lock:
                if self.count >= 1:
                    break
            time.sleep(0.05)

        self.assertGreaterEqual(self.count, 1,
                                "message should eventually replay after gate")

    def test_no_replay_during_rapid_flap(self):
        self.queue.enqueue(make_message("MSG-S6-002"), now=NOW())
        self.scheduler.on_link_up()
        time.sleep(0.02)
        self.scheduler.on_link_down()
        time.sleep(0.02)
        self.scheduler.on_link_up()
        time.sleep(0.02)
        self.scheduler.on_link_down()

        self.assertEqual(self.queue.depth, 1)

        time.sleep(0.3)
        self.assertEqual(self.queue.depth, 1,
                         "message should still be queued, not replayed")


class S7_MessageAlreadyAcknowledged(unittest.TestCase):
    """Scenario 7: mark_acked -> replay skipped."""

    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")

        self.sent = []
        self.acks_emitted = []

        def send_cb(packet):
            self.sent.append(packet.get("packetId", ""))
            return ReplayResult.SUCCESS

        def ack_cb(packet):
            self.acks_emitted.append(packet)

        self.engine = ReplayEngine(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=SHORT_DELAY,
            ack_callback=ack_cb,
        )

    def tearDown(self):
        self.engine.stop()

    def test_mark_acked_prevents_replay(self):
        self.queue.enqueue(make_message("MSG-S7-001"), now=NOW())
        self.queue.enqueue(make_message("MSG-S7-002"), now=NOW())
        self.assertEqual(self.queue.depth, 2)

        self.assertTrue(self.queue.mark_acked("MSG-S7-001"))
        self.assertEqual(self.queue.depth, 1)
        self.assertFalse(self.queue.is_duplicate("MSG-S7-001"))

        self.engine.on_link_up()

        deadline = time.time() + 5.0
        while time.time() < deadline:
            if self.engine.stats.passes_completed >= 1:
                break
            time.sleep(0.02)

        self.assertNotIn("MSG-S7-001", self.sent)
        self.assertIn("MSG-S7-002", self.sent)
        self.assertEqual(self.engine.stats.succeeded, 1)

    def test_mark_acked_before_replay_triggers_delivery_flow(self):
        self.queue.enqueue(make_message("MSG-S7-003"), now=NOW())
        self.assertTrue(self.queue.mark_acked("MSG-S7-003"))
        self.assertEqual(self.queue.depth, 0)

        self.engine.on_link_up()
        deadline = time.time() + 5.0
        while time.time() < deadline:
            if self.engine.stats.passes_completed >= 1:
                break
            time.sleep(0.02)

        self.assertEqual(len(self.sent), 0)


class S8_Step042CStateMachine(unittest.TestCase):
    """Scenario 8: STEP042C state machine MESSAGE -> DELIVERED -> SEEN preserved."""

    def setUp(self):
        self.tracker = DeliveryTracker()

    def test_message_created(self):
        state = self.tracker.create_message("MSG-S8-001")
        self.assertEqual(state, "MESSAGE")

    def test_message_to_delivered(self):
        self.tracker.create_message("MSG-S8-001")
        self.assertTrue(self.tracker.deliver("MSG-S8-001"))
        self.assertEqual(self.tracker.states["MSG-S8-001"], "DELIVERED")

    def test_delivered_to_seen(self):
        self.tracker.create_message("MSG-S8-001")
        self.tracker.deliver("MSG-S8-001")
        self.assertTrue(self.tracker.seen("MSG-S8-001"))
        self.assertEqual(self.tracker.states["MSG-S8-001"], "SEEN")

    def test_seen_is_terminal(self):
        self.tracker.create_message("MSG-S8-001")
        self.tracker.deliver("MSG-S8-001")
        self.tracker.seen("MSG-S8-001")
        self.assertFalse(self.tracker.deliver("MSG-S8-001"))
        self.assertFalse(self.tracker.seen("MSG-S8-001"))
        self.assertFalse(self.tracker.unknown("MSG-S8-001"))

    def test_message_to_unknown(self):
        self.tracker.create_message("MSG-S8-001")
        self.assertTrue(self.tracker.unknown("MSG-S8-001"))
        self.assertEqual(self.tracker.states["MSG-S8-001"], "UNKNOWN")

    def test_unknown_to_delivered(self):
        self.tracker.create_message("MSG-S8-001")
        self.tracker.unknown("MSG-S8-001")
        self.assertTrue(self.tracker.deliver("MSG-S8-001"))
        self.assertEqual(self.tracker.states["MSG-S8-001"], "DELIVERED")

    def test_unknown_to_seen(self):
        self.tracker.create_message("MSG-S8-001")
        self.tracker.unknown("MSG-S8-001")
        self.assertTrue(self.tracker.seen("MSG-S8-001"))
        self.assertEqual(self.tracker.states["MSG-S8-001"], "SEEN")

    def test_delivered_cannot_return_to_message(self):
        self.tracker.create_message("MSG-S8-001")
        self.tracker.deliver("MSG-S8-001")
        self.assertFalse(self.tracker.transition_allowed("MSG-S8-001", "MESSAGE"))

    def test_seen_cannot_return_to_delivered(self):
        self.tracker.create_message("MSG-S8-001")
        self.tracker.deliver("MSG-S8-001")
        self.tracker.seen("MSG-S8-001")
        self.assertFalse(self.tracker.transition_allowed("MSG-S8-001", "DELIVERED"))

    def test_failed_can_retry_message(self):
        self.tracker.create_message("MSG-S8-001")
        self.tracker.states["MSG-S8-001"] = "FAILED"
        self.assertTrue(self.tracker.transition_allowed("MSG-S8-001", "MESSAGE"))


class S_RegressionChecks(unittest.TestCase):
    """Regression: verify test file inventory and importability."""

    def test_all_modules_importable(self):
        from store_forward_queue import (StoreForwardQueue,
            StoreForwardPersistentQueue, QueuedMessage, build_buffered_ack,
            build_dropped_ack, build_expired_ack)
        from replay_engine import (ReplayScheduler, ReplayEngine,
            ReplayResult, ReplayStats, evaluate_send_result)
        self.assertTrue(True)

    def test_test_file_count(self):
        import glob
        tests_dir = os.path.join(os.path.dirname(__file__))
        test_files = glob.glob(os.path.join(tests_dir, "test_*.py"))
        self.assertGreaterEqual(len(test_files), 4,
            f"Expected >=4 test files, found {len(test_files)}")


if __name__ == "__main__":
    unittest.main()
