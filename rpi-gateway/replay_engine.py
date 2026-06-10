#!/usr/bin/env python3
"""
PUP MANET -- Store-and-Forward Replay Engine (STEP042D-C)

FIFO replay scheduler with stability gating, throttling, TTL verification,
retry limits, and diagnostic logging.  Operates on a StoreForwardQueue.

No ESP32/Android changes.  No routing-core changes.  Gateway code only.

Conforms to STEP042D-A Store-and-Forward Requirements Definition, Section 6.
"""

from __future__ import annotations

import logging
import threading
import time
from dataclasses import dataclass, field
from enum import Enum, auto
from typing import Callable, Dict, List, Optional

from store_forward_queue import (
    QueuedMessage,
    StoreForwardQueue,
    build_dropped_ack,
    build_expired_ack,
    build_buffered_ack,
)

DEFAULT_REPLAY_STABILITY_SECONDS = 5.0
DEFAULT_MAX_REPLAY_RATE = 10
DEFAULT_MIN_INTER_MESSAGE_DELAY = 0.1
DEFAULT_MAX_RETRIES = 3

LOG_TAG = "[STORE_FORWARD_REPLAY]"
LOG = logging.getLogger(__name__)


class ReplayResult(Enum):
    SUCCESS = auto()
    RETRYABLE = auto()
    PERMANENT = auto()
    EXPIRED = auto()
    RETRY_EXCEEDED = auto()


@dataclass
class ReplayStats:
    passes_completed: int = 0
    passes_aborted: int = 0
    attempted: int = 0
    succeeded: int = 0
    retried: int = 0
    dropped_permanent: int = 0
    skipped_expired: int = 0
    skipped_retry_exceeded: int = 0
    last_pass_at: float = 0.0
    last_pass_duration: float = 0.0

    def to_dict(self) -> dict:
        return {
            "passes_completed": self.passes_completed,
            "passes_aborted": self.passes_aborted,
            "attempted": self.attempted,
            "succeeded": self.succeeded,
            "retried": self.retried,
            "dropped_permanent": self.dropped_permanent,
            "skipped_expired": self.skipped_expired,
            "skipped_retry_exceeded": self.skipped_retry_exceeded,
            "last_pass_at": self.last_pass_at,
            "last_pass_duration": self.last_pass_duration,
        }


SendCallback = Callable[[dict], Optional[ReplayResult]]


class ReplayScheduler:
    def __init__(
        self,
        queue: StoreForwardQueue,
        send_callback: SendCallback,
        gateway_id: str = "A",
        stability_seconds: float = DEFAULT_REPLAY_STABILITY_SECONDS,
        max_replay_rate: int = DEFAULT_MAX_REPLAY_RATE,
        min_inter_message_delay: float = DEFAULT_MIN_INTER_MESSAGE_DELAY,
        max_retries: int = DEFAULT_MAX_RETRIES,
        _time_func: Callable[[], float] = time.time,
    ):
        self.queue = queue
        self.send_callback = send_callback
        self.gateway_id = str(gateway_id).upper().strip()
        self.stability_seconds = stability_seconds
        self.max_replay_rate = max(1, int(max_replay_rate))
        self.min_inter_message_delay = max(0.01, float(min_inter_message_delay))
        self.max_retries = max(1, int(max_retries))
        self._time = _time_func

        self._link_up_at: Optional[float] = None
        self._replay_active = False
        self._replay_lock = threading.Lock()
        self._stop_event = threading.Event()
        self._worker: Optional[threading.Thread] = None
        self.stats = ReplayStats()

    @property
    def is_replaying(self) -> bool:
        with self._replay_lock:
            return self._replay_active

    @property
    def link_stable(self) -> bool:
        if self._link_up_at is None:
            return False
        elapsed = self._time() - self._link_up_at
        return elapsed >= self.stability_seconds

    def on_link_up(self, now: float | None = None) -> None:
        if now is None:
            now = self._time()
        with self._replay_lock:
            if self._link_up_at is None:
                self._link_up_at = now
                self._log(f"link_up detected at t={now:.1f}, "
                          f"stability gate={self.stability_seconds}s")
            worker_alive = self._worker is not None and self._worker.is_alive()
            if self._replay_active or worker_alive:
                return
        self._start_replay_worker()

    def on_link_down(self) -> None:
        with self._replay_lock:
            self._link_up_at = None
            if self._replay_active:
                self._log("link_down during replay -- will complete current message then stop")
        self._stop_event.set()

    def stop(self) -> None:
        self._stop_event.set()
        if self._worker is not None:
            self._worker.join(timeout=5)

    def _log(self, message: str) -> None:
        LOG.info("%s %s", LOG_TAG, message)

    def _start_replay_worker(self) -> None:
        self._stop_event.clear()
        self._worker = threading.Thread(target=self._replay_pass, daemon=True)
        self._worker.start()

    def _replay_pass(self) -> None:
        with self._replay_lock:
            if self._replay_active:
                return
            self._replay_active = True

        start_time = self._time()
        replayed = 0
        remaining = 0
        aborted = False

        try:
            while not self._stop_event.is_set():
                self._check_stability_gate()
                if not self.link_stable:
                    time.sleep(0.1)
                    continue

                if self.queue.is_empty:
                    break

                entry = self.queue.peek()
                if entry is None:
                    break

                self.stats.attempted += 1

                if self._stop_event.is_set():
                    aborted = True
                    break

                if entry.is_expired():
                    self._handle_expired(entry)
                    continue

                if entry.retry_count >= self.max_retries:
                    self._handle_retry_exceeded(entry)
                    continue

                result = self.send_callback(entry.original_packet)

                if self._stop_event.is_set():
                    aborted = True
                    break

                if result == ReplayResult.SUCCESS:
                    self._handle_success(entry)
                    replayed += 1
                elif result == ReplayResult.RETRYABLE:
                    self._handle_retryable(entry)
                elif result == ReplayResult.PERMANENT:
                    self._handle_permanent(entry)
                elif result == ReplayResult.EXPIRED:
                    self._handle_expired(entry)
                elif result == ReplayResult.RETRY_EXCEEDED:
                    self._handle_retry_exceeded(entry)
                else:
                    self._handle_retryable(entry)

                if not self.link_stable:
                    break

                time.sleep(self.min_inter_message_delay)

            remaining = self.queue.depth

        finally:
            duration = self._time() - start_time
            with self._replay_lock:
                self.stats.last_pass_at = start_time
                self.stats.last_pass_duration = duration
                if aborted:
                    self.stats.passes_aborted += 1
                else:
                    self.stats.passes_completed += 1
                self._replay_active = False

            state = "ABORTED" if aborted else "END"
            self._log(f"{state} replayed={replayed} remaining={remaining} "
                      f"duration={duration:.2f}s")

    def _check_stability_gate(self) -> None:
        if self._link_up_at is None:
            time.sleep(0.1)
            return
        while not self._stop_event.is_set() and not self.link_stable:
            remaining = self.stability_seconds - (self._time() - self._link_up_at)
            if remaining <= 0:
                break
            time.sleep(min(remaining, 0.5))
        if self.link_stable and not self._stop_event.is_set():
            depth = self.queue.depth
            self._log(f"BEGIN depth={depth}")

    def _handle_success(self, entry: QueuedMessage) -> None:
        self.queue.dequeue()
        self.stats.succeeded += 1
        self._log(f"OK messageId={entry.message_id} "
                  f"src={entry.src_node} dest={entry.dest_node}")

    def _handle_retryable(self, entry: QueuedMessage) -> None:
        entry.retry_count += 1
        self.stats.retried += 1
        self._log(f"RETRY messageId={entry.message_id} "
                  f"retry={entry.retry_count}/{self.max_retries}")

    def _handle_permanent(self, entry: QueuedMessage) -> None:
        self.queue.mark_acked(entry.message_id)
        self.stats.dropped_permanent += 1
        self._log(f"DROP messageId={entry.message_id} reason=PEER_REJECT")

    def _handle_expired(self, entry: QueuedMessage) -> None:
        self.queue.mark_acked(entry.message_id)
        self.stats.skipped_expired += 1
        age = entry.age_seconds()
        self._log(f"EXPIRED messageId={entry.message_id} age={age:.1f}s")

    def _handle_retry_exceeded(self, entry: QueuedMessage) -> None:
        self.queue.mark_acked(entry.message_id)
        self.stats.skipped_retry_exceeded += 1
        self._log(f"DROP messageId={entry.message_id} "
                  f"reason=RETRY_EXCEEDED retries={entry.retry_count}")


class ReplayEngine(ReplayScheduler):

    def __init__(
        self,
        queue: StoreForwardQueue,
        send_callback: SendCallback,
        gateway_id: str = "A",
        stability_seconds: float = DEFAULT_REPLAY_STABILITY_SECONDS,
        max_replay_rate: int = DEFAULT_MAX_REPLAY_RATE,
        min_inter_message_delay: float = DEFAULT_MIN_INTER_MESSAGE_DELAY,
        max_retries: int = DEFAULT_MAX_RETRIES,
        ack_callback: Optional[Callable[[dict], bool]] = None,
        _time_func: Callable[[], float] = time.time,
    ):
        super().__init__(
            queue=queue,
            send_callback=send_callback,
            gateway_id=gateway_id,
            stability_seconds=stability_seconds,
            max_replay_rate=max_replay_rate,
            min_inter_message_delay=min_inter_message_delay,
            max_retries=max_retries,
            _time_func=_time_func,
        )
        self.ack_callback = ack_callback

    def _send_ack_to_sender(self, ack_packet: dict) -> None:
        if self.ack_callback is not None:
            try:
                self.ack_callback(ack_packet)
            except Exception:
                pass

    def _handle_expired(self, entry: QueuedMessage) -> None:
        super()._handle_expired(entry)
        ack = build_expired_ack(entry.original_packet, self.gateway_id)
        self._send_ack_to_sender(ack)

    def _handle_permanent(self, entry: QueuedMessage) -> None:
        super()._handle_permanent(entry)
        ack = build_dropped_ack(entry.original_packet, self.gateway_id,
                                reason="PEER_REJECT")
        self._send_ack_to_sender(ack)

    def _handle_retry_exceeded(self, entry: QueuedMessage) -> None:
        super()._handle_retry_exceeded(entry)
        ack = build_dropped_ack(entry.original_packet, self.gateway_id,
                                reason="RETRY_EXCEEDED")
        self._send_ack_to_sender(ack)


def evaluate_send_result(send_ok: bool, peer_ack: bool = False) -> ReplayResult:
    if send_ok and peer_ack:
        return ReplayResult.SUCCESS
    if send_ok:
        return ReplayResult.SUCCESS
    return ReplayResult.RETRYABLE


__all__ = [
    "ReplayScheduler",
    "ReplayEngine",
    "ReplayResult",
    "ReplayStats",
    "evaluate_send_result",
    "SendCallback",
    "DEFAULT_REPLAY_STABILITY_SECONDS",
    "DEFAULT_MAX_REPLAY_RATE",
    "DEFAULT_MIN_INTER_MESSAGE_DELAY",
    "DEFAULT_MAX_RETRIES",
]
