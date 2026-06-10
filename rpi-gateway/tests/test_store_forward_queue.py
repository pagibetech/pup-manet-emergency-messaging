#!/usr/bin/env python3
"""
Unit tests for STEP042D-B Store-and-Forward Queue Engine.

Covers:
  - Enqueue / dequeue
  - Duplicate prevention
  - Queue depth limit (256 default)
  - Message type filtering (HELLO, STATUS rejected; MESSAGE, ACK queued)
  - Expiration (300s TTL default)
  - BUFFERED / DROPPED / EXPIRED ack packet construction
  - Persistent queue write / load / rotation
  - Mark-acked removal
  - Edge cases: corrupt packets, missing messageId, mixed types
"""

import json
import os
import sys
import tempfile
import time
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from store_forward_queue import (
    DEFAULT_MAX_DEPTH,
    DEFAULT_TTL_SECONDS,
    NON_QUEUEABLE_PACKET_TYPES,
    QUEUEABLE_ACK_TYPES,
    QUEUEABLE_PACKET_TYPES,
    QueuedMessage,
    StoreForwardPersistentQueue,
    StoreForwardQueue,
    build_buffered_ack,
    build_dropped_ack,
    build_expired_ack,
)

NOW = 1718000000.0

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
        "timestamp": int(NOW),
        "status": "MESSAGE",
        "checksum": "CHECKSUM_PLACEHOLDER",
    }


def make_ack_packet(ack_type="DELIVERY", ack_for="MSG-001",
                    src="nodeB1", dest="nodeA1"):
    return {
        "protocolVersion": "BT-MANET-1.0",
        "packetType": "ACK",
        "packetId": f"ACK-{ack_for}-{src}-{int(NOW*1000)}",
        "sourceNode": src,
        "destinationNode": dest,
        "payload": {
            "ackVersion": 1,
            "ackType": ack_type,
            "ackFor": ack_for,
            "ackStatus": "DELIVERED" if ack_type == "DELIVERY" else "SEEN",
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
        "timestamp": int(NOW),
        "status": "ACK",
        "checksum": "CHECKSUM_PLACEHOLDER",
    }


class TestQueuedMessage(unittest.TestCase):
    def test_defaults_populated(self):
        qm = QueuedMessage(
            message_id="MSG-001",
            src_node="nodeA1",
            dest_node="nodeB1",
            original_packet={"packetId": "MSG-001"},
            received_at=NOW,
        )
        self.assertEqual(qm.message_id, "MSG-001")
        self.assertAlmostEqual(qm.expires_at, NOW + DEFAULT_TTL_SECONDS)
        self.assertFalse(qm.is_expired(NOW))
        self.assertTrue(qm.is_expired(NOW + DEFAULT_TTL_SECONDS + 1))
        self.assertEqual(qm.age_seconds(NOW + 10), 10.0)

    def test_is_expired_no_arg_uses_now(self):
        qm = QueuedMessage(
            message_id="OLD",
            src_node="x",
            dest_node="y",
            original_packet={},
            received_at=0,
        )
        self.assertTrue(qm.is_expired())


class TestStoreForwardQueueEnqueue(unittest.TestCase):
    def setUp(self):
        self.q = StoreForwardQueue(gateway_id="A")

    def assert_enqueued(self, entry):
        self.assertIsNotNone(entry)
        self.assertEqual(self.q.depth, 1)

    def assert_dropped(self, entry, stat_key, expected_depth=0):
        self.assertIsNone(entry)
        self.assertEqual(self.q.depth, expected_depth)
        self.assertGreater(self.q.stats.get(stat_key, 0), 0)

    def test_enqueue_message_packet(self):
        pkt = make_message_packet("MSG-001")
        entry = self.q.enqueue(pkt, now=NOW)
        self.assert_enqueued(entry)
        self.assertEqual(entry.message_id, "MSG-001")
        self.assertEqual(entry.src_node, "nodeA1")
        self.assertEqual(entry.dest_node, "nodeB1")

    def test_enqueue_delivery_ack(self):
        pkt = make_ack_packet("DELIVERY", "MSG-001")
        entry = self.q.enqueue(pkt, now=NOW)
        self.assert_enqueued(entry)

    def test_enqueue_seen_ack(self):
        pkt = make_ack_packet("SEEN", "MSG-001")
        entry = self.q.enqueue(pkt, now=NOW)
        self.assert_enqueued(entry)

    def test_reject_hello_packet(self):
        pkt = {"packetType": "HELLO", "packetId": "HELLO-nodeA1-1001",
               "sourceNode": "nodeA1", "destinationNode": "BROADCAST"}
        entry = self.q.enqueue(pkt, now=NOW)
        self.assert_dropped(entry, "dropped_filtered")

    def test_reject_status_packet(self):
        pkt = {"packetType": "STATUS", "packetId": "STATUS-1",
               "sourceNode": "nodeA1", "destinationNode": "ESP32_BRIDGE"}
        entry = self.q.enqueue(pkt, now=NOW)
        self.assert_dropped(entry, "dropped_filtered")

    def test_reject_nodes_type(self):
        pkt = {"type": "nodes", "gateway_id": "A", "nodes": []}
        entry = self.q.enqueue(pkt, now=NOW)
        self.assert_dropped(entry, "dropped_filtered")

    def test_reject_heartbeat_type(self):
        pkt = {"type": "heartbeat", "timestamp": NOW}
        entry = self.q.enqueue(pkt, now=NOW)
        self.assert_dropped(entry, "dropped_filtered")

    def test_drop_corrupt_none(self):
        entry = self.q.enqueue(None, now=NOW)
        self.assert_dropped(entry, "dropped_corrupt")

    def test_drop_corrupt_empty_dict(self):
        entry = self.q.enqueue({}, now=NOW)
        self.assert_dropped(entry, "dropped_corrupt")

    def test_drop_missing_packet_id(self):
        pkt = {"packetType": "MESSAGE", "sourceNode": "x", "destinationNode": "y"}
        entry = self.q.enqueue(pkt, now=NOW)
        self.assert_dropped(entry, "dropped_corrupt")

    def test_drop_duplicate(self):
        pkt = make_message_packet("MSG-001")
        self.q.enqueue(pkt, now=NOW)
        self.assertEqual(self.q.depth, 1)
        entry2 = self.q.enqueue(pkt, now=NOW)
        self.assert_dropped(entry2, "dropped_duplicate", expected_depth=1)


class TestStoreForwardQueueDepthLimit(unittest.TestCase):
    def test_depth_limit_enforced(self):
        q = StoreForwardQueue(max_depth=5, gateway_id="A")
        for i in range(5):
            entry = q.enqueue(make_message_packet(f"MSG-{i:03d}"), now=NOW)
            self.assertIsNotNone(entry)
        self.assertEqual(q.depth, 5)
        self.assertTrue(q.is_full)

        entry6 = q.enqueue(make_message_packet("MSG-006"), now=NOW)
        self.assertIsNone(entry6)
        self.assertEqual(q.depth, 5)
        self.assertEqual(q.stats["dropped_full"], 1)

    def test_default_max_depth_is_256(self):
        q = StoreForwardQueue(gateway_id="A")
        self.assertEqual(q.max_depth, 256)


class TestStoreForwardQueueDequeue(unittest.TestCase):
    def test_dequeue_fifo_order(self):
        q = StoreForwardQueue(gateway_id="A")
        pkt_a = make_message_packet("MSG-A")
        pkt_b = make_message_packet("MSG-B")
        q.enqueue(pkt_a, now=NOW)
        q.enqueue(pkt_b, now=NOW + 1)
        self.assertEqual(q.depth, 2)

        first = q.dequeue()
        self.assertEqual(first.message_id, "MSG-A")
        self.assertEqual(q.depth, 1)

        second = q.dequeue()
        self.assertEqual(second.message_id, "MSG-B")
        self.assertEqual(q.depth, 0)

    def test_dequeue_empty_returns_none(self):
        q = StoreForwardQueue(gateway_id="A")
        self.assertIsNone(q.dequeue())

    def test_peek_does_not_remove(self):
        q = StoreForwardQueue(gateway_id="A")
        q.enqueue(make_message_packet("MSG-001"), now=NOW)
        self.assertEqual(q.peek().message_id, "MSG-001")
        self.assertEqual(q.depth, 1)

    def test_peek_empty_returns_none(self):
        q = StoreForwardQueue(gateway_id="A")
        self.assertIsNone(q.peek())


class TestStoreForwardQueueExpiration(unittest.TestCase):
    def test_remove_expired_removes_only_expired(self):
        q = StoreForwardQueue(ttl_seconds=60, gateway_id="A")
        q.enqueue(make_message_packet("MSG-OLD"), now=NOW)
        q.enqueue(make_message_packet("MSG-NEW"), now=NOW + 100)
        q.enqueue(make_message_packet("MSG-FRESH"), now=NOW + 200)

        expired = q.remove_expired(now=NOW + 100)
        self.assertEqual(len(expired), 1)
        self.assertEqual(expired[0].message_id, "MSG-OLD")
        self.assertEqual(q.depth, 2)
        self.assertEqual(q.stats["expired"], 1)

    def test_remove_expired_none_when_all_fresh(self):
        q = StoreForwardQueue(ttl_seconds=300, gateway_id="A")
        q.enqueue(make_message_packet("MSG-001"), now=NOW)
        expired = q.remove_expired(now=NOW + 10)
        self.assertEqual(len(expired), 0)
        self.assertEqual(q.depth, 1)


class TestStoreForwardQueueMarkAcked(unittest.TestCase):
    def test_mark_acked_removes_entry(self):
        q = StoreForwardQueue(gateway_id="A")
        q.enqueue(make_message_packet("MSG-001"), now=NOW)
        q.enqueue(make_message_packet("MSG-002"), now=NOW + 1)

        self.assertTrue(q.mark_acked("MSG-001"))
        self.assertEqual(q.depth, 1)
        self.assertEqual(q.peek().message_id, "MSG-002")
        self.assertFalse(q.is_duplicate("MSG-001"))

    def test_mark_acked_unknown_id(self):
        q = StoreForwardQueue(gateway_id="A")
        self.assertFalse(q.mark_acked("NOT_THERE"))

    def test_is_duplicate_detects_queued_id(self):
        q = StoreForwardQueue(gateway_id="A")
        q.enqueue(make_message_packet("MSG-001"), now=NOW)
        self.assertTrue(q.is_duplicate("MSG-001"))
        self.assertFalse(q.is_duplicate("MSG-002"))


class TestStoreForwardQueueSerialisation(unittest.TestCase):
    def test_to_dict_list_round_trippable(self):
        q = StoreForwardQueue(gateway_id="A")
        pkt = make_message_packet("MSG-001")
        q.enqueue(pkt, now=NOW)
        result = q.to_dict_list()
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0]["messageId"], "MSG-001")
        self.assertEqual(result[0]["originalPacket"], pkt)
        self.assertAlmostEqual(result[0]["receivedAt"], NOW)

    def test_to_list_returns_queued_messages(self):
        q = StoreForwardQueue(gateway_id="A")
        q.enqueue(make_message_packet("MSG-A"), now=NOW)
        q.enqueue(make_message_packet("MSG-B"), now=NOW + 1)
        items = q.to_list()
        self.assertEqual(len(items), 2)
        self.assertEqual(items[0].message_id, "MSG-A")
        self.assertEqual(items[1].message_id, "MSG-B")


class TestAckPacketConstruction(unittest.TestCase):
    def setUp(self):
        self.pkt = make_message_packet("MSG-001", "nodeA1", "nodeB1")

    def test_buffered_ack_structure(self):
        ack = build_buffered_ack(self.pkt, "GWA")
        self.assertEqual(ack["packetType"], "ACK")
        self.assertTrue(ack["packetId"].startswith("ACK-MSG-001-GWA-"))
        self.assertEqual(ack["sourceNode"], "GWA")
        payload = ack["payload"]
        self.assertEqual(payload["ackType"], "FORWARD")
        self.assertEqual(payload["ackStatus"], "BUFFERED")
        self.assertEqual(payload["ackFor"], "MSG-001")
        self.assertEqual(payload["reason"], "QUEUED_FOR_STORE_FORWARD")

    def test_dropped_ack(self):
        ack = build_dropped_ack(self.pkt, "GWA", "QUEUE_FULL")
        self.assertEqual(ack["payload"]["ackStatus"], "DROPPED")
        self.assertEqual(ack["payload"]["reason"], "STORE_FORWARD_QUEUE_FULL")

    def test_expired_ack(self):
        ack = build_expired_ack(self.pkt, "GWA")
        self.assertEqual(ack["payload"]["ackStatus"], "EXPIRED")
        self.assertEqual(ack["payload"]["reason"], "STORE_FORWARD_TTL_EXPIRED")

    def test_ack_for_falls_back_to_messageId(self):
        pkt = {
            "protocolVersion": "BT-MANET-1.0",
            "packetType": "MESSAGE",
            "messageId": "MSG-FALLBACK",
            "sourceNode": "nodeA1",
            "destinationNode": "nodeB1",
            "payload": {},
        }
        ack = build_buffered_ack(pkt, "GWA")
        self.assertEqual(ack["payload"]["ackFor"], "MSG-FALLBACK")


class TestStoreForwardPersistentQueue(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".jsonl")
        self.tmp.close()
        self.path = self.tmp.name

    def tearDown(self):
        for suffix in ("", ".bak", ".tmp"):
            p = self.path + suffix
            if os.path.exists(p):
                os.unlink(p)

    def test_enqueue_writes_file(self):
        q = StoreForwardPersistentQueue(persist_path=self.path, gateway_id="A")
        q.enqueue(make_message_packet("MSG-001"), now=NOW)
        self.assertTrue(os.path.exists(self.path))
        with open(self.path, "r") as fh:
            lines = fh.readlines()
        self.assertEqual(len([l for l in lines if l.strip()]), 1)

    def test_load_restores_queue(self):
        q1 = StoreForwardPersistentQueue(persist_path=self.path, gateway_id="A")
        q1.enqueue(make_message_packet("MSG-001"), now=NOW)
        q1.enqueue(make_message_packet("MSG-002", src="nodeA2", dest="nodeB2"), now=NOW + 1)

        q2 = StoreForwardPersistentQueue(persist_path=self.path, gateway_id="A")
        loaded = q2.load(now=NOW + 2)
        self.assertEqual(loaded, 2)
        self.assertEqual(q2.depth, 2)
        self.assertEqual(q2.peek().message_id, "MSG-001")

    def test_load_skips_expired(self):
        q1 = StoreForwardPersistentQueue(persist_path=self.path, ttl_seconds=10, gateway_id="A")
        q1.enqueue(make_message_packet("MSG-OLD"), now=NOW)
        time.sleep(0.01)

        q2 = StoreForwardPersistentQueue(persist_path=self.path, ttl_seconds=10, gateway_id="A")
        loaded = q2.load(now=NOW + 20)
        self.assertEqual(loaded, 0)
        self.assertEqual(q2.depth, 0)

    def test_load_skips_corrupt_lines(self):
        with open(self.path, "w") as fh:
            fh.write("not valid json\n")
            fh.write('{"messageId":"OK","originalPacket":{"packetType":"MESSAGE","packetId":"OK"}}\n')
            fh.write("also not json\n")
        q = StoreForwardPersistentQueue(persist_path=self.path, gateway_id="A")
        loaded = q.load(now=NOW)
        self.assertEqual(loaded, 1)
        self.assertEqual(q.depth, 1)

    def test_load_skips_duplicates(self):
        q1 = StoreForwardPersistentQueue(persist_path=self.path, gateway_id="A")
        q1.enqueue(make_message_packet("MSG-001"), now=NOW)

        q2 = StoreForwardPersistentQueue(persist_path=self.path, gateway_id="A")
        q2.enqueue(make_message_packet("MSG-001"), now=NOW)
        loaded = q2.load(now=NOW + 1)
        self.assertEqual(loaded, 0)
        self.assertEqual(q2.depth, 1)

    def test_mark_acked_removes_from_file(self):
        q = StoreForwardPersistentQueue(persist_path=self.path, gateway_id="A")
        q.enqueue(make_message_packet("MSG-001"), now=NOW)
        q.enqueue(make_message_packet("MSG-002"), now=NOW + 1)
        self.assertEqual(q.depth, 2)

        self.assertTrue(q.mark_acked("MSG-001"))
        self.assertEqual(q.depth, 1)

        q2 = StoreForwardPersistentQueue(persist_path=self.path, gateway_id="A")
        loaded = q2.load(now=NOW + 2)
        self.assertEqual(loaded, 1)
        self.assertEqual(q2.peek().message_id, "MSG-002")

    def test_remove_expired_persistent(self):
        q = StoreForwardPersistentQueue(persist_path=self.path, ttl_seconds=5, gateway_id="A")
        q.enqueue(make_message_packet("MSG-EXP"), now=NOW)
        q.enqueue(make_message_packet("MSG-KEEP"), now=NOW + 60)
        expired = q.remove_expired(now=NOW + 60)
        self.assertEqual(len(expired), 1)
        self.assertEqual(expired[0].message_id, "MSG-EXP")

        q2 = StoreForwardPersistentQueue(persist_path=self.path, gateway_id="A")
        loaded = q2.load(now=NOW + 61)
        self.assertEqual(loaded, 1)
        msg_ids = [m.message_id for m in q2.to_list()]
        self.assertIn("MSG-KEEP", msg_ids)
        self.assertNotIn("MSG-EXP", msg_ids)

    def test_file_rotation(self):
        q = StoreForwardPersistentQueue(
            persist_path=self.path, gateway_id="A", max_file_size=200,
        )
        for i in range(10):
            q.enqueue(make_message_packet(f"MSG-{i:03d}"), now=NOW)

        self.assertTrue(os.path.exists(self.path + ".bak"))

    def test_load_from_empty_file(self):
        with open(self.path, "w") as fh:
            fh.write("\n")
        q = StoreForwardPersistentQueue(persist_path=self.path, gateway_id="A")
        loaded = q.load(now=NOW)
        self.assertEqual(loaded, 0)

    def test_load_nonexistent_file(self):
        q = StoreForwardPersistentQueue(persist_path="/tmp/no_such_file_042d_test.jsonl", gateway_id="A")
        loaded = q.load(now=NOW)
        self.assertEqual(loaded, 0)

    def test_persistent_depth_limit_on_load(self):
        q1 = StoreForwardPersistentQueue(persist_path=self.path, max_depth=3, gateway_id="A")
        for i in range(5):
            q1.enqueue(make_message_packet(f"MSG-{i:03d}"), now=NOW)

        q2 = StoreForwardPersistentQueue(persist_path=self.path, max_depth=3, gateway_id="A")
        loaded = q2.load(now=NOW + 1)
        self.assertEqual(loaded, 3)


class TestStoreForwardQueueStats(unittest.TestCase):
    def test_stats_accumulate(self):
        q = StoreForwardQueue(gateway_id="A")
        q.enqueue(make_message_packet("MSG-001"), now=NOW)
        q.enqueue(make_message_packet("MSG-001"), now=NOW)
        q.enqueue(make_message_packet("MSG-002"), now=NOW)
        q.enqueue({"packetType": "HELLO", "packetId": "H-1"}, now=NOW)
        q.enqueue(None, now=NOW)
        q.dequeue()
        q.dequeue()

        self.assertEqual(q.stats["enqueued"], 2)
        self.assertEqual(q.stats["dropped_duplicate"], 1)
        self.assertEqual(q.stats["dropped_filtered"], 1)
        self.assertEqual(q.stats["dropped_corrupt"], 1)
        self.assertEqual(q.stats["dequeued"], 2)


if __name__ == "__main__":
    unittest.main()
