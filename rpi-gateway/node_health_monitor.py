#!/usr/bin/env python3
"""
PUP MANET -- Node Health Monitor (STEP048A)

Tracks ESP32-reported node degradation and recovery state.
Thread-safe.  Builds on health_event_parser for line parsing.

No ESP32 changes.  No Android changes.  Gateway code only.
"""

from __future__ import annotations

import threading
import time
from dataclasses import dataclass, field
from typing import Dict, List, Optional

from health_event_parser import parse_health_event


@dataclass
class NodeHealthRecord:
    node_id: str
    degraded: bool = False
    last_rssi: int = 0
    degraded_at: float = 0.0
    recovered_at: float = 0.0
    event_count: int = 0


class NodeHealthMonitor:

    def __init__(self, gateway_id: str = "A"):
        self.gateway_id = str(gateway_id).upper().strip()
        self._nodes: Dict[str, NodeHealthRecord] = {}
        self._lock = threading.Lock()

    def process_line(self, line: str) -> Optional[dict]:
        event = parse_health_event(line)
        if event is None:
            return None
        self._apply_event(event)
        return event

    def _apply_event(self, event: dict) -> None:
        event_type = event.get("event", "")
        node_id = event.get("nodeId", "")
        rssi = event.get("rssi", 0)
        now = time.time()

        if not node_id:
            return

        with self._lock:
            record = self._nodes.get(node_id)
            if record is None:
                record = NodeHealthRecord(node_id=node_id)
                self._nodes[node_id] = record

            record.last_rssi = rssi

            if event_type == "degradation":
                if not record.degraded:
                    record.degraded = True
                    record.degraded_at = now
                    record.event_count += 1
            elif event_type == "recovery":
                if record.degraded:
                    record.degraded = False
                    record.recovered_at = now

    def is_degraded(self, node_id: str) -> bool:
        with self._lock:
            record = self._nodes.get(node_id)
            if record is None:
                return False
            return record.degraded

    def get_record(self, node_id: str) -> Optional[NodeHealthRecord]:
        with self._lock:
            return self._nodes.get(node_id)

    def get_snapshot(self) -> dict:
        with self._lock:
            nodes = {}
            degraded_count = 0
            healthy_count = 0
            for node_id, record in self._nodes.items():
                nodes[node_id] = {
                    "degraded": record.degraded,
                    "rssi": record.last_rssi,
                    "degraded_at": record.degraded_at,
                    "recovered_at": record.recovered_at,
                    "event_count": record.event_count,
                }
                if record.degraded:
                    degraded_count += 1
                else:
                    healthy_count += 1

        return {
            "gateway_id": self.gateway_id,
            "timestamp": time.time(),
            "nodes": nodes,
            "degraded_count": degraded_count,
            "healthy_count": healthy_count,
            "total_count": len(nodes),
        }

    def get_degraded_nodes(self) -> List[str]:
        with self._lock:
            return [
                node_id for node_id, record in self._nodes.items()
                if record.degraded
            ]

    def get_healthy_nodes(self) -> List[str]:
        with self._lock:
            return [
                node_id for node_id, record in self._nodes.items()
                if not record.degraded
            ]


__all__ = ["NodeHealthMonitor", "NodeHealthRecord"]
