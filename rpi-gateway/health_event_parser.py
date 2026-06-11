#!/usr/bin/env python3
"""
PUP MANET -- Health Event Parser (STEP048A)

Parses ESP32 serial lines for degradation and recovery events.
Zero dependencies beyond stdlib.  Standalone, no side effects.

ESP32 line formats:
    [DEGRADATION] node=nodeA2 rssi=-82 state=DEGRADED
    [RECOVERY] node=nodeA2 rssi=-55
"""

from __future__ import annotations

import re
from typing import Optional

DEGRADATION_PREFIX = "[DEGRADATION]"
RECOVERY_PREFIX = "[RECOVERY]"

_DEGRADATION_RE = re.compile(
    r"^\[DEGRADATION\]\s+node=([^\s]+)\s+rssi=(-?\d+).*$"
)
_RECOVERY_RE = re.compile(
    r"^\[RECOVERY\]\s+node=([^\s]+)\s+rssi=(-?\d+).*$"
)


def parse_health_event(line: str) -> Optional[dict]:
    if not isinstance(line, str) or not line:
        return None

    stripped = line.strip()

    if stripped.startswith(DEGRADATION_PREFIX):
        return _parse_degradation(stripped)

    if stripped.startswith(RECOVERY_PREFIX):
        return _parse_recovery(stripped)

    return None


def _parse_degradation(line: str) -> Optional[dict]:
    m = _DEGRADATION_RE.match(line)
    if m is None:
        return None
    node_id = m.group(1).strip()
    try:
        rssi = int(m.group(2))
    except (ValueError, IndexError):
        return None
    if not node_id:
        return None
    return {
        "event": "degradation",
        "nodeId": node_id,
        "rssi": rssi,
    }


def _parse_recovery(line: str) -> Optional[dict]:
    m = _RECOVERY_RE.match(line)
    if m is None:
        return None
    node_id = m.group(1).strip()
    try:
        rssi = int(m.group(2))
    except (ValueError, IndexError):
        return None
    if not node_id:
        return None
    return {
        "event": "recovery",
        "nodeId": node_id,
        "rssi": rssi,
    }


__all__ = ["parse_health_event", "DEGRADATION_PREFIX", "RECOVERY_PREFIX"]
