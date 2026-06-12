#!/usr/bin/env python3
"""
PUP MANET -- Gateway Route State Machine (STEP048B)

Deterministic route-state decision logic.  Reads NodeHealthMonitor
health data (read-only).  No routing actions.  No failover execution.

States: PRIMARY_LORA -> DEGRADED -> FAILOVER_ACTIVE -> RECOVERING

No ESP32/Android changes.  No gateway_service.py integration required.
"""

from __future__ import annotations

import threading
import time
from typing import Callable, List, Optional

DEFAULT_FAILOVER_SECONDS = 10.0
DEFAULT_RECOVERY_SECONDS = 10.0
DEFAULT_HEALTH_CHECK_INTERVAL = 1.0

STATE_PRIMARY_LORA = "PRIMARY_LORA"
STATE_DEGRADED = "DEGRADED"
STATE_FAILOVER_ACTIVE = "FAILOVER_ACTIVE"
STATE_RECOVERING = "RECOVERING"

ALL_STATES = {STATE_PRIMARY_LORA, STATE_DEGRADED,
              STATE_FAILOVER_ACTIVE, STATE_RECOVERING}


class RouteStateMachine:

    def __init__(
        self,
        health_monitor,   # duck-typed: has is_degraded(node_id) -> bool
        gateway_id: str = "A",
        failover_seconds: float = DEFAULT_FAILOVER_SECONDS,
        recovery_seconds: float = DEFAULT_RECOVERY_SECONDS,
        _time_func: Callable[[], float] = time.time,
    ):
        self._health = health_monitor
        self.gateway_id = str(gateway_id).upper().strip()
        self.failover_seconds = max(1.0, float(failover_seconds))
        self.recovery_seconds = max(1.0, float(recovery_seconds))
        self._time = _time_func

        self._state: str = STATE_PRIMARY_LORA
        self._degraded_since: float = 0.0
        self._recovered_since: float = 0.0
        self._lock = threading.Lock()
        self._transition_count: int = 0

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    @property
    def state(self) -> str:
        with self._lock:
            return self._state

    @property
    def transition_count(self) -> int:
        with self._lock:
            return self._transition_count

    def tick(self, local_node_ids: Optional[List[str]] = None) -> str:
        now = self._time()

        degraded_local = self._find_degraded_local(local_node_ids)

        with self._lock:
            previous = self._state

            if self._state == STATE_PRIMARY_LORA:
                self._tick_primary_lora(degraded_local, now)

            elif self._state == STATE_DEGRADED:
                self._tick_degraded(degraded_local, now)

            elif self._state == STATE_FAILOVER_ACTIVE:
                self._tick_failover_active(degraded_local, now)

            elif self._state == STATE_RECOVERING:
                self._tick_recovering(degraded_local, now)

            if self._state != previous:
                self._transition_count += 1

            return self._state

    def force_state(self, new_state: str) -> bool:
        if new_state not in ALL_STATES:
            return False
        with self._lock:
            old = self._state
            self._state = new_state
            self._degraded_since = 0.0
            self._recovered_since = 0.0
            if new_state != old:
                self._transition_count += 1
        return True

    # ------------------------------------------------------------------
    # State transition handlers
    # ------------------------------------------------------------------

    def _tick_primary_lora(self, degraded: List[str], now: float) -> None:
        if degraded:
            self._state = STATE_DEGRADED
            self._degraded_since = now

    def _tick_degraded(self, degraded: List[str], now: float) -> None:
        if not degraded:
            self._state = STATE_PRIMARY_LORA
            self._degraded_since = 0.0
            return

        elapsed = now - self._degraded_since
        if elapsed >= self.failover_seconds:
            self._state = STATE_FAILOVER_ACTIVE
            self._degraded_since = 0.0

    def _tick_failover_active(self, degraded: List[str], now: float) -> None:
        if not degraded:
            self._state = STATE_RECOVERING
            self._recovered_since = now

    def _tick_recovering(self, degraded: List[str], now: float) -> None:
        if degraded:
            self._state = STATE_FAILOVER_ACTIVE
            self._recovered_since = 0.0
            return

        elapsed = now - self._recovered_since
        if elapsed >= self.recovery_seconds:
            self._state = STATE_PRIMARY_LORA
            self._recovered_since = 0.0

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    def _find_degraded_local(
        self, local_node_ids: Optional[List[str]]
    ) -> List[str]:
        if not local_node_ids:
            return []
        result: List[str] = []
        for node_id in local_node_ids:
            try:
                if self._health.is_degraded(node_id):
                    result.append(node_id)
            except Exception:
                pass
        return result

    def time_in_state(self) -> float:
        with self._lock:
            if self._state in (STATE_DEGRADED, STATE_FAILOVER_ACTIVE):
                ref = self._degraded_since
            elif self._state == STATE_RECOVERING:
                ref = self._recovered_since
            else:
                return 0.0
            if ref <= 0:
                return 0.0
            return max(0.0, self._time() - ref)

    # ------------------------------------------------------------------
    # Diagnostics
    # ------------------------------------------------------------------

    def get_info(self) -> dict:
        with self._lock:
            return {
                "gateway_id": self.gateway_id,
                "state": self._state,
                "transition_count": self._transition_count,
                "degraded_since": self._degraded_since,
                "recovered_since": self._recovered_since,
                "failover_seconds": self.failover_seconds,
                "recovery_seconds": self.recovery_seconds,
            }


__all__ = [
    "RouteStateMachine",
    "STATE_PRIMARY_LORA",
    "STATE_DEGRADED",
    "STATE_FAILOVER_ACTIVE",
    "STATE_RECOVERING",
    "ALL_STATES",
    "DEFAULT_FAILOVER_SECONDS",
    "DEFAULT_RECOVERY_SECONDS",
]
