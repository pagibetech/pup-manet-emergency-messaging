from __future__ import annotations

import json
from dataclasses import dataclass, field
from typing import List, Optional, Protocol

from .models import GatewayHeartbeat


@dataclass(frozen=True)
class LoRaSpiConfig:
    """Raspberry Pi LoRa SPI settings reserved for the hardware adapter."""

    frequency_hz: int = 433_000_000
    sync_word: int = 0x12
    spi_bus: int = 0
    spi_device: int = 0
    reset_pin: int = 22
    dio0_pin: int = 25


@dataclass(frozen=True)
class LoRaFrame:
    raw_payload: str
    rssi: float
    snr: float
    received_at: float


class LoRaSpiRadio(Protocol):
    def receive_frame(self, now: float) -> Optional[LoRaFrame]:
        """Return one LoRa frame when available, otherwise None."""


@dataclass
class SimulatedLoRaSpiRadio:
    """Deterministic LoRa SPI stand-in for Mac and Raspberry Pi dry runs."""

    config: LoRaSpiConfig = field(default_factory=LoRaSpiConfig)
    _frames: List[LoRaFrame] = field(default_factory=list)

    def seed_heartbeat(
        self,
        node_id: str,
        gateway_id: str,
        now: float,
        rssi: float = -57.0,
        snr: float = 9.5,
        uptime_s: int = 0,
    ) -> None:
        payload = f"HB1|{node_id}|{gateway_id}|{uptime_s}"
        self._frames.append(LoRaFrame(payload, rssi=rssi, snr=snr, received_at=now))

    def receive_frame(self, now: float) -> Optional[LoRaFrame]:
        if not self._frames:
            return None
        return self._frames.pop(0)


def parse_gateway_heartbeat(frame: LoRaFrame) -> Optional[GatewayHeartbeat]:
    payload = frame.raw_payload.strip()
    if payload.startswith("HB1|"):
        parts = payload.split("|")
        if len(parts) >= 3:
            return GatewayHeartbeat(
                node_id=parts[1],
                gateway_id=parts[2],
                received_at=frame.received_at,
                rssi=frame.rssi,
                snr=frame.snr,
                raw_payload=payload,
            )
        return None

    try:
        data = json.loads(payload)
    except json.JSONDecodeError:
        return None

    packet_type = data.get("packetType") or data.get("type")
    if packet_type != "HEARTBEAT":
        return None

    node_id = data.get("sourceNode") or data.get("nodeId")
    gateway_id = data.get("gatewayId") or data.get("destinationNode")
    if not node_id or not gateway_id:
        return None

    return GatewayHeartbeat(
        node_id=str(node_id),
        gateway_id=str(gateway_id),
        received_at=frame.received_at,
        rssi=frame.rssi,
        snr=frame.snr,
        raw_payload=payload,
    )
