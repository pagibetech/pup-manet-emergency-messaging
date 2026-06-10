#!/usr/bin/env python3
"""
Unit tests for STEP042D-C Store-and-Forward Replay Engine.

Uses real time.time() with very short stability gates for reliable testing.
All operations complete well within the 5-second per-test deadline.
"""

import os
import sys
import threading
import time
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from store_forward_queue import StoreForwardQueue
from replay_engine import (
    ReplayEngine,
    ReplayResult,
    ReplayScheduler,
    evaluate_send_result,
)

SHORT_GATE = 0.1


def _now():
    return time.time()


def make_message_packet(msg_id="MSG-001", src="nodeA1", dest="nodeB1",
                        text="hello"):
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


class TestEvaluateSendResult(unittest.TestCase):
    def test_success_when_send_ok(self):
        self.assertEqual(evaluate_send_result(True), ReplayResult.SUCCESS)
        self.assertEqual(evaluate_send_result(True, True), ReplayResult.SUCCESS)

    def test_retryable_when_send_fails(self):
        self.assertEqual(evaluate_send_result(False), ReplayResult.RETRYABLE)


class TestReplaySchedulerStabilityGate(unittest.TestCase):
    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")
        self.sent = []

        def send_cb(packet):
            self.sent.append(packet)
            return ReplayResult.SUCCESS

        self.scheduler = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=0.01,
        )

    def tearDown(self):
        self.scheduler.stop()

    def test_replay_does_not_start_before_stability_gate(self):
        self.queue.enqueue(make_message_packet("MSG-001"), now=_now())
        self.scheduler.on_link_up()
        time.sleep(0.02)
        self.assertEqual(len(self.sent), 0)

    def test_replay_starts_after_stability_gate(self):
        self.queue.enqueue(make_message_packet("MSG-001"), now=_now())
        self.scheduler.on_link_up()
        self._wait_until(lambda: len(self.sent) >= 1)
        self.assertGreaterEqual(len(self.sent), 1)

    def test_empty_queue_starts_and_stops(self):
        self.scheduler.on_link_up()
        self._wait_until(lambda: self.scheduler.stats.passes_completed >= 1)
        self.assertEqual(len(self.sent), 0)
        self.assertGreaterEqual(self.scheduler.stats.passes_completed, 1)

    def _wait_until(self, condition, timeout=5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if condition():
                return True
            time.sleep(0.02)
        return False


class TestReplaySchedulerFIFO(unittest.TestCase):
    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")
        self.sent = []

        def send_cb(packet):
            self.sent.append(packet.get("packetId", ""))
            return ReplayResult.SUCCESS

        self.scheduler = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=0.02,
        )

    def tearDown(self):
        self.scheduler.stop()

    def test_replay_preserves_fifo_order(self):
        for i in range(5):
            self.queue.enqueue(make_message_packet(f"MSG-{i:03d}"),
                               now=_now())

        self.scheduler.on_link_up()
        self._wait_until(lambda: len(self.sent) >= 5)
        self.assertEqual(self.sent, [f"MSG-{i:03d}" for i in range(5)])

    def _wait_until(self, condition, timeout=5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if condition():
                return True
            time.sleep(0.02)
        return False


class TestReplaySchedulerRetryLimits(unittest.TestCase):
    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")

        def send_cb(packet):
            return ReplayResult.RETRYABLE

        self.scheduler = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=0.02,
            max_retries=3,
        )

    def tearDown(self):
        self.scheduler.stop()

    def test_retry_exceeded_drops_message(self):
        self.queue.enqueue(make_message_packet("MSG-FLAKY"), now=_now())
        self.scheduler.on_link_up()
        self._wait_until(
            lambda: self.scheduler.stats.skipped_retry_exceeded >= 1)

        self.assertEqual(self.scheduler.stats.retried, 3)
        self.assertEqual(self.scheduler.stats.skipped_retry_exceeded, 1)
        self.assertEqual(self.queue.depth, 0)

    def _wait_until(self, condition, timeout=5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if condition():
                return True
            time.sleep(0.02)
        return False


class TestReplaySchedulerPermanentFailure(unittest.TestCase):
    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")

        def send_cb(packet):
            return ReplayResult.PERMANENT

        self.scheduler = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=0.02,
        )

    def tearDown(self):
        self.scheduler.stop()

    def test_permanent_failure_drops_message(self):
        self.queue.enqueue(make_message_packet("MSG-BAD"), now=_now())
        self.scheduler.on_link_up()
        self._wait_until(
            lambda: self.scheduler.stats.dropped_permanent >= 1)

        self.assertEqual(self.scheduler.stats.dropped_permanent, 1)
        self.assertEqual(self.queue.depth, 0)

    def _wait_until(self, condition, timeout=5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if condition():
                return True
            time.sleep(0.02)
        return False


class TestReplaySchedulerLinkDown(unittest.TestCase):
    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")
        self.send_count = 0
        self.blocks_forever = threading.Event()

        def send_cb(packet):
            self.send_count += 1
            self.blocks_forever.set()
            time.sleep(0.5)
            return ReplayResult.SUCCESS

        self.scheduler = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=0.02,
        )

    def tearDown(self):
        self.scheduler.stop()

    def test_link_down_during_replay_aborts(self):
        for i in range(10):
            self.queue.enqueue(make_message_packet(f"MSG-{i:03d}"),
                               now=_now())

        self.scheduler.on_link_up()
        self.blocks_forever.wait(timeout=3.0)
        self.scheduler.on_link_down()

        self._wait_until(
            lambda: self.scheduler.stats.passes_aborted >= 1)
        remaining = self.queue.depth
        self.assertGreater(remaining, 0,
                           "some messages should remain after abort")

    def _wait_until(self, condition, timeout=5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if condition():
                return True
            time.sleep(0.05)
        return False


class TestReplaySchedulerConcurrentPrevention(unittest.TestCase):
    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")

        def send_cb(packet):
            time.sleep(0.05)
            return ReplayResult.SUCCESS

        self.scheduler = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=0.02,
        )

    def tearDown(self):
        self.scheduler.stop()

    def test_only_one_replay_pass_active(self):
        for i in range(5):
            self.queue.enqueue(make_message_packet(f"MSG-{i:03d}"),
                               now=_now())

        self.scheduler.on_link_up()
        time.sleep(0.05)
        self.scheduler.on_link_up()
        self.scheduler.on_link_up()

        self._wait_until(
            lambda: self.scheduler.stats.passes_completed >= 1)

        self.assertEqual(self.scheduler.stats.passes_completed, 1,
                         "only one replay pass should run")

    def _wait_until(self, condition, timeout=5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if condition():
                return True
            time.sleep(0.05)
        return False


class TestReplaySchedulerThrottling(unittest.TestCase):
    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")
        self.timestamps = []

        def send_cb(packet):
            self.timestamps.append(time.monotonic())
            return ReplayResult.SUCCESS

        self.scheduler = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=0.15,
        )

    def tearDown(self):
        self.scheduler.stop()

    def test_inter_message_delay_enforced(self):
        for i in range(3):
            self.queue.enqueue(make_message_packet(f"MSG-{i:03d}"),
                               now=_now())

        self.scheduler.on_link_up()
        self._wait_until(lambda: len(self.timestamps) >= 3)

        self.assertGreaterEqual(len(self.timestamps), 3)
        for i in range(1, len(self.timestamps)):
            gap = self.timestamps[i] - self.timestamps[i - 1]
            self.assertGreaterEqual(gap, 0.12,
                                    f"gap {gap:.4f}s at message {i}")

    def _wait_until(self, condition, timeout=5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if condition():
                return True
            time.sleep(0.05)
        return False


class TestReplaySchedulerStats(unittest.TestCase):
    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")

        def send_cb(packet):
            return ReplayResult.SUCCESS

        self.scheduler = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=0.02,
        )

    def tearDown(self):
        self.scheduler.stop()

    def test_replay_stats_accumulate(self):
        self.queue.enqueue(make_message_packet("MSG-001"), now=_now())
        self.scheduler.on_link_up()
        self._wait_until(
            lambda: self.scheduler.stats.passes_completed >= 1)

        d = self.scheduler.stats.to_dict()
        self.assertGreaterEqual(d["passes_completed"], 1)
        self.assertGreaterEqual(d["attempted"], 1)
        self.assertGreaterEqual(d["succeeded"], 1)

    def _wait_until(self, condition, timeout=5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if condition():
                return True
            time.sleep(0.05)
        return False


class TestReplayEngineAckCallback(unittest.TestCase):
    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")
        self.acks_sent = []

        def ack_cb(packet):
            self.acks_sent.append(packet)

        self.engine = ReplayEngine(
            queue=self.queue,
            send_callback=lambda p: ReplayResult.SUCCESS,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=0.02,
            ack_callback=ack_cb,
        )

    def tearDown(self):
        self.engine.stop()

    def test_expired_ack_sent_to_sender(self):
        self.queue.enqueue(make_message_packet("MSG-OLD"), now=1.0)
        self.engine._time = lambda: time.time() + 10000.0
        self.engine._link_up_at = time.time()
        self.engine.on_link_up()

        self._wait_until(
            lambda: self.engine.stats.skipped_expired >= 1)

        self.assertEqual(self.engine.stats.skipped_expired, 1)
        self.assertGreater(len(self.acks_sent), 0)
        ack = self.acks_sent[0]
        self.assertEqual(ack["payload"]["ackStatus"], "EXPIRED")
        self.assertEqual(ack["payload"]["ackType"], "FORWARD")

    def test_permanent_ack_sent_to_sender(self):
        def perm_send(packet):
            return ReplayResult.PERMANENT

        self.engine.send_callback = perm_send
        self.queue.enqueue(make_message_packet("MSG-BAD"), now=_now())
        self.engine.on_link_up()

        self._wait_until(
            lambda: self.engine.stats.dropped_permanent >= 1)

        self.assertEqual(self.engine.stats.dropped_permanent, 1)
        ack = self.acks_sent[0]
        self.assertEqual(ack["payload"]["ackStatus"], "DROPPED")
        self.assertIn("PEER_REJECT", ack["payload"]["reason"])

    def test_retry_exceeded_ack_sent_to_sender(self):
        def retry_send(packet):
            return ReplayResult.RETRYABLE

        self.engine.send_callback = retry_send
        self.engine.max_retries = 2
        self.queue.enqueue(make_message_packet("MSG-FLAKY"), now=_now())
        self.engine.on_link_up()

        self._wait_until(
            lambda: self.engine.stats.skipped_retry_exceeded >= 1)

        self.assertEqual(self.engine.stats.skipped_retry_exceeded, 1)
        ack = self.acks_sent[0]
        self.assertEqual(ack["payload"]["ackStatus"], "DROPPED")
        self.assertIn("RETRY_EXCEEDED", ack["payload"]["reason"])

    def test_no_ack_callback_no_error(self):
        engine = ReplayEngine(
            queue=self.queue,
            send_callback=lambda p: ReplayResult.SUCCESS,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=0.02,
            ack_callback=None,
        )
        engine.stop()

    def _wait_until(self, condition, timeout=5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if condition():
                return True
            time.sleep(0.05)
        return False


class TestReplaySchedulerCleanShutdown(unittest.TestCase):
    def test_stop_before_start_does_not_raise(self):
        q = StoreForwardQueue(gateway_id="A")
        s = ReplayScheduler(queue=q,
                            send_callback=lambda p: ReplayResult.SUCCESS,
                            gateway_id="A")
        s.stop()

    def test_double_stop_does_not_raise(self):
        q = StoreForwardQueue(gateway_id="A")
        s = ReplayScheduler(queue=q,
                            send_callback=lambda p: ReplayResult.SUCCESS,
                            gateway_id="A")
        s.stop()
        s.stop()


class TestReplaySchedulerLinkFlap(unittest.TestCase):
    def setUp(self):
        self.queue = StoreForwardQueue(gateway_id="A")
        self.count = 0

        def send_cb(packet):
            self.count += 1
            return ReplayResult.SUCCESS

        self.scheduler = ReplayScheduler(
            queue=self.queue,
            send_callback=send_cb,
            gateway_id="A",
            stability_seconds=SHORT_GATE,
            min_inter_message_delay=0.02,
        )

    def tearDown(self):
        self.scheduler.stop()

    def test_flapping_link_resets_stability_gate(self):
        self.queue.enqueue(make_message_packet("MSG-001"), now=_now())
        self.scheduler.on_link_up()
        time.sleep(0.02)
        self.scheduler.on_link_down()
        time.sleep(0.3)
        self.scheduler.on_link_up()

        self._wait_until(lambda: self.count >= 1)

        self.assertGreaterEqual(self.count, 1)

    def _wait_until(self, condition, timeout=5.0):
        deadline = time.time() + timeout
        while time.time() < deadline:
            if condition():
                return True
            time.sleep(0.05)
        return False


if __name__ == "__main__":
    unittest.main()
