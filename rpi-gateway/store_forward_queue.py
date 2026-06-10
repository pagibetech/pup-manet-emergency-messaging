#!/usr/bin/env python3
"""
PUP MANET -- Store-and-Forward Queue Engine (STEP042D-B)

Lightweight in-memory queue with optional persistent JSON-Lines backing.
No replay logic.  No ESP32/Android changes.  No routing-core changes.

Conforms to STEP042D-A Store-and-Forward Requirements Definition.
"""

from __future__ import annotations

import json
import os
import time
from collections import deque
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set

DEFAULT_MAX_DEPTH = 256
DEFAULT_TTL_SECONDS = 300
DEFAULT_MAX_PERSISTENT_FILE_SIZE = 8 * 1024 * 1024

QUEUEABLE_PACKET_TYPES = {"MESSAGE", "ACK"}
QUEUEABLE_ACK_TYPES = {"DELIVERY", "SEEN"}
DIAGNOSTIC_ACK_TYPES = {"FORWARD", "HOP"}

NON_QUEUEABLE_PACKET_TYPES = {"HELLO", "STATUS", "NODE_LIST", "NEIGHBORS"}


@dataclass
class QueuedMessage:
    message_id: str
    src_node: str
    dest_node: str
    original_packet: dict
    received_at: float = field(default_factory=time.time)
    expires_at: float = 0.0
    retry_count: int = 0

    def __post_init__(self) -> None:
        if self.expires_at <= 0:
            self.expires_at = self.received_at + DEFAULT_TTL_SECONDS

    def is_expired(self, now: float | None = None) -> bool:
        if now is None:
            now = time.time()
        return now >= self.expires_at

    def age_seconds(self, now: float | None = None) -> float:
        if now is None:
            now = time.time()
        return max(0.0, now - self.received_at)


class StoreForwardQueue:
    def __init__(
        self,
        max_depth: int = DEFAULT_MAX_DEPTH,
        ttl_seconds: int = DEFAULT_TTL_SECONDS,
        gateway_id: str = "A",
    ):
        self.max_depth = max_depth
        self.ttl_seconds = ttl_seconds
        self.gateway_id = str(gateway_id).upper().strip()
        self._deque: deque[QueuedMessage] = deque()
        self._message_ids: Set[str] = set()
        self.stats: Dict[str, int] = {
            "enqueued": 0,
            "dropped_duplicate": 0,
            "dropped_full": 0,
            "dropped_filtered": 0,
            "dropped_corrupt": 0,
            "expired": 0,
            "dequeued": 0,
        }

    @property
    def depth(self) -> int:
        return len(self._deque)

    @property
    def is_empty(self) -> bool:
        return len(self._deque) == 0

    @property
    def is_full(self) -> bool:
        return len(self._deque) >= self.max_depth

    def _extract_message_id(self, packet: dict) -> str:
        if not isinstance(packet, dict):
            return ""
        msg_id = packet.get("packetId", "")
        if not msg_id:
            msg_id = packet.get("messageId", "")
        if not msg_id:
            payload = packet.get("payload", "")
            if isinstance(payload, dict):
                msg_id = payload.get("messageId", "")
                if not msg_id:
                    msg_id = payload.get("ackFor", "")
        if not msg_id:
            return ""
        return str(msg_id).strip()

    def _extract_source_node(self, packet: dict) -> str:
        if not isinstance(packet, dict):
            return ""
        return str(packet.get("sourceNode", "")).strip()

    def _extract_dest_node(self, packet: dict) -> str:
        if not isinstance(packet, dict):
            return ""
        return str(packet.get("destinationNode", "")).strip()

    def _should_queue_packet_type(self, packet: dict) -> bool:
        packet_type = str(packet.get("packetType", "")).upper().strip()

        if packet_type in NON_QUEUEABLE_PACKET_TYPES:
            return False

        if packet_type == "ACK":
            payload = packet.get("payload", {})
            if isinstance(payload, dict):
                ack_type = str(payload.get("ackType", "")).upper().strip()
                if ack_type not in QUEUEABLE_ACK_TYPES and ack_type not in DIAGNOSTIC_ACK_TYPES:
                    return False
            return True

        if packet_type in QUEUEABLE_PACKET_TYPES:
            return True

        type_field = str(packet.get("type", "")).lower().strip()
        if type_field == "nodes":
            return False
        if type_field in ("heartbeat", "ack", "ping"):
            return False

        return True

    def enqueue(self, packet: dict, now: float | None = None) -> Optional[QueuedMessage]:
        if now is None:
            now = time.time()

        if not isinstance(packet, dict):
            self.stats["dropped_corrupt"] += 1
            return None

        if not self._should_queue_packet_type(packet):
            self.stats["dropped_filtered"] += 1
            return None

        message_id = self._extract_message_id(packet)
        if not message_id:
            self.stats["dropped_corrupt"] += 1
            return None

        if message_id in self._message_ids:
            self.stats["dropped_duplicate"] += 1
            return None

        if self.is_full:
            self.stats["dropped_full"] += 1
            return None

        src_node = self._extract_source_node(packet)
        dest_node = self._extract_dest_node(packet)

        entry = QueuedMessage(
            message_id=message_id,
            src_node=src_node,
            dest_node=dest_node,
            original_packet=packet,
            received_at=now,
            expires_at=now + self.ttl_seconds,
        )

        self._deque.append(entry)
        self._message_ids.add(message_id)
        self.stats["enqueued"] += 1
        return entry

    def dequeue(self) -> Optional[QueuedMessage]:
        if self.is_empty:
            return None
        entry = self._deque.popleft()
        self._message_ids.discard(entry.message_id)
        self.stats["dequeued"] += 1
        return entry

    def peek(self) -> Optional[QueuedMessage]:
        if self.is_empty:
            return None
        return self._deque[0]

    def remove_expired(self, now: float | None = None) -> List[QueuedMessage]:
        if now is None:
            now = time.time()
        expired: List[QueuedMessage] = []
        kept: deque[QueuedMessage] = deque()
        for entry in self._deque:
            if entry.is_expired(now):
                self._message_ids.discard(entry.message_id)
                self.stats["expired"] += 1
                expired.append(entry)
            else:
                kept.append(entry)
        self._deque = kept
        return expired

    def is_duplicate(self, message_id: str) -> bool:
        return message_id in self._message_ids

    def mark_acked(self, message_id: str) -> bool:
        if message_id not in self._message_ids:
            return False
        self._message_ids.discard(message_id)
        new_deque: deque[QueuedMessage] = deque()
        found = False
        for entry in self._deque:
            if entry.message_id == message_id:
                found = True
                continue
            new_deque.append(entry)
        self._deque = new_deque
        return found

    def to_list(self) -> List[QueuedMessage]:
        return list(self._deque)

    def to_dict_list(self) -> List[dict]:
        result: List[dict] = []
        for entry in self._deque:
            result.append({
                "messageId": entry.message_id,
                "srcNode": entry.src_node,
                "destNode": entry.dest_node,
                "originalPacket": entry.original_packet,
                "receivedAt": entry.received_at,
                "expiresAt": entry.expires_at,
                "retryCount": entry.retry_count,
            })
        return result

    def __len__(self) -> int:
        return len(self._deque)

    def __repr__(self) -> str:
        return (
            f"StoreForwardQueue(depth={self.depth}/{self.max_depth}, "
            f"enqueued={self.stats['enqueued']}, "
            f"dup_drops={self.stats['dropped_duplicate']}, "
            f"full_drops={self.stats['dropped_full']})"
        )


class StoreForwardPersistentQueue(StoreForwardQueue):
    def __init__(
        self,
        max_depth: int = DEFAULT_MAX_DEPTH,
        ttl_seconds: int = DEFAULT_TTL_SECONDS,
        gateway_id: str = "A",
        persist_path: str = "store_forward_queue.jsonl",
        max_file_size: int = DEFAULT_MAX_PERSISTENT_FILE_SIZE,
    ):
        super().__init__(max_depth=max_depth, ttl_seconds=ttl_seconds, gateway_id=gateway_id)
        self.persist_path = persist_path
        self.max_file_size = max_file_size

    def load(self, now: float | None = None) -> int:
        if now is None:
            now = time.time()
        if not os.path.exists(self.persist_path):
            return 0

        loaded = 0
        skipped = 0
        with open(self.persist_path, "r", encoding="utf-8") as fh:
            for line_num, line in enumerate(fh, 1):
                line = line.strip()
                if not line:
                    continue
                try:
                    record = json.loads(line)
                except json.JSONDecodeError:
                    skipped += 1
                    self._log_persist(
                        f"[STORE_FORWARD_LOAD_SKIP] line={line_num} reason=CORRUPT"
                    )
                    continue

                if not isinstance(record, dict):
                    skipped += 1
                    continue

                message_id = record.get("messageId", "")
                if not message_id:
                    skipped += 1
                    continue

                packet = record.get("originalPacket", {})
                if not isinstance(packet, dict):
                    skipped += 1
                    continue

                received_at = float(record.get("receivedAt", now))
                expires_at = float(record.get("expiresAt", now + self.ttl_seconds))

                if expires_at <= received_at:
                    expires_at = received_at + self.ttl_seconds

                if now >= expires_at:
                    skipped += 1
                    self._log_persist(
                        f"[STORE_FORWARD_LOAD_EXPIRED] messageId={message_id}"
                    )
                    continue

                if message_id in self._message_ids:
                    skipped += 1
                    self._log_persist(
                        f"[STORE_FORWARD_LOAD_DUPLICATE] messageId={message_id}"
                    )
                    continue

                if len(self._deque) >= self.max_depth:
                    skipped += 1
                    self._log_persist(
                        f"[STORE_FORWARD_LOAD_FULL] messageId={message_id}"
                    )
                    continue

                entry = QueuedMessage(
                    message_id=message_id,
                    src_node=str(record.get("srcNode", "")).strip(),
                    dest_node=str(record.get("destNode", "")).strip(),
                    original_packet=packet,
                    received_at=received_at,
                    expires_at=expires_at,
                    retry_count=int(record.get("retryCount", 0)),
                )
                self._deque.append(entry)
                self._message_ids.add(message_id)
                loaded += 1

        self._log_persist(
            f"[STORE_FORWARD_LOAD] loaded={loaded} skipped={skipped} depth={self.depth}"
        )
        return loaded

    def _persist_entry(self, entry: QueuedMessage) -> bool:
        try:
            record = json.dumps({
                "messageId": entry.message_id,
                "srcNode": entry.src_node,
                "destNode": entry.dest_node,
                "originalPacket": entry.original_packet,
                "receivedAt": entry.received_at,
                "expiresAt": entry.expires_at,
                "retryCount": entry.retry_count,
            }, separators=(",", ":"))
        except (TypeError, ValueError):
            return False

        try:
            self._rotate_if_needed()
            with open(self.persist_path, "a", encoding="utf-8") as fh:
                fh.write(record + "\n")
                fh.flush()
                os.fsync(fh.fileno())
            return True
        except OSError:
            return False

    def _rotate_if_needed(self) -> None:
        if not os.path.exists(self.persist_path):
            return
        try:
            size = os.path.getsize(self.persist_path)
        except OSError:
            return
        if size >= self.max_file_size:
            backup = self.persist_path + ".bak"
            try:
                os.replace(self.persist_path, backup)
            except OSError:
                pass
            self._log_persist(
                f"[STORE_FORWARD_ROTATE] size={size} max={self.max_file_size}"
            )

    def enqueue(self, packet: dict, now: float | None = None) -> Optional[QueuedMessage]:
        entry = super().enqueue(packet, now=now)
        if entry is not None:
            self._persist_entry(entry)
            self._log_persist(
                f"[STORE_FORWARD_BUFFERED] messageId={entry.message_id} "
                f"src={entry.src_node} dest={entry.dest_node} depth={self.depth}"
            )
        return entry

    def _remove_persisted_entry(self, message_id: str) -> None:
        if not os.path.exists(self.persist_path):
            return
        try:
            temp_path = self.persist_path + ".tmp"
            with open(self.persist_path, "r", encoding="utf-8") as fin, \
                 open(temp_path, "w", encoding="utf-8") as fout:
                for line in fin:
                    line_stripped = line.strip()
                    if not line_stripped:
                        continue
                    try:
                        record = json.loads(line_stripped)
                    except json.JSONDecodeError:
                        fout.write(line)
                        continue
                    if record.get("messageId") == message_id:
                        continue
                    fout.write(line)
            os.replace(temp_path, self.persist_path)
        except OSError:
            pass

    def mark_acked(self, message_id: str) -> bool:
        result = super().mark_acked(message_id)
        if result:
            self._remove_persisted_entry(message_id)
            self._log_persist(
                f"[STORE_FORWARD_SKIP_ACKED] messageId={message_id}"
            )
        return result

    def remove_expired(self, now: float | None = None) -> List[QueuedMessage]:
        expired = super().remove_expired(now=now)
        for entry in expired:
            self._remove_persisted_entry(entry.message_id)
            self._log_persist(
                f"[STORE_FORWARD_EXPIRED] messageId={entry.message_id} "
                f"age={entry.age_seconds(now):.1f}s"
            )
        return expired

    def _log_persist(self, message: str) -> None:
        pass


def build_buffered_ack(
    original_packet: dict,
    gateway_id: str,
    reason: str = "QUEUED_FOR_STORE_FORWARD",
) -> dict:
    original_packet_id = ""
    if isinstance(original_packet, dict):
        original_packet_id = str(original_packet.get("packetId", "")).strip() or \
            str(original_packet.get("messageId", "")).strip()

    src_node = ""
    dest_node = ""
    if isinstance(original_packet, dict):
        src_node = str(original_packet.get("sourceNode", "")).strip()
        dest_node = str(original_packet.get("destinationNode", "")).strip()

    origin_node = src_node
    final_dest = dest_node
    route = [src_node, gateway_id] if src_node else [gateway_id]

    now = time.time()
    ack_packet_id = f"ACK-{original_packet_id}-{gateway_id}-{int(now * 1000)}"

    return {
        "protocolVersion": "BT-MANET-1.0",
        "packetType": "ACK",
        "packetId": ack_packet_id,
        "sourceNode": gateway_id,
        "destinationNode": origin_node,
        "payload": {
            "ackVersion": 1,
            "ackType": "FORWARD",
            "ackFor": original_packet_id,
            "ackStatus": "BUFFERED",
            "originNode": origin_node,
            "finalDestinationNode": final_dest,
            "ackSource": gateway_id,
            "reason": reason,
            "route": route,
        },
        "hopPath": f"{gateway_id}>{origin_node}" if origin_node else gateway_id,
        "hopCount": 0,
        "ttl": 6,
        "previousHop": gateway_id,
        "retryCount": 0,
        "timestamp": int(now),
        "status": "ACK",
        "checksum": "CHECKSUM_PLACEHOLDER",
    }


def build_dropped_ack(
    original_packet: dict,
    gateway_id: str,
    reason: str = "QUEUE_FULL",
) -> dict:
    ack = build_buffered_ack(original_packet, gateway_id, reason=reason)
    if isinstance(ack.get("payload"), dict):
        ack["payload"]["ackStatus"] = "DROPPED"
        ack["payload"]["reason"] = f"STORE_FORWARD_{reason}"
    return ack


def build_expired_ack(
    original_packet: dict,
    gateway_id: str,
) -> dict:
    ack = build_buffered_ack(original_packet, gateway_id, reason="STORE_FORWARD_TTL_EXPIRED")
    if isinstance(ack.get("payload"), dict):
        ack["payload"]["ackStatus"] = "EXPIRED"
        ack["payload"]["reason"] = "STORE_FORWARD_TTL_EXPIRED"
    return ack


__all__ = [
    "StoreForwardQueue",
    "StoreForwardPersistentQueue",
    "QueuedMessage",
    "build_buffered_ack",
    "build_dropped_ack",
    "build_expired_ack",
    "DEFAULT_MAX_DEPTH",
    "DEFAULT_TTL_SECONDS",
    "QUEUEABLE_PACKET_TYPES",
    "QUEUEABLE_ACK_TYPES",
    "NON_QUEUEABLE_PACKET_TYPES",
]
