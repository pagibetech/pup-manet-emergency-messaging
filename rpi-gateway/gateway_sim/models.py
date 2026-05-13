from dataclasses import dataclass, field
from typing import Dict, List, Optional


@dataclass(frozen=True)
class SimNode:
    node_id: str
    network_id: str
    gateway_id: str


@dataclass
class SimGateway:
    gateway_id: str
    network_id: str
    local_nodes: List[str]
    peer_gateway_id: str
    lora_available: bool = True
    lora_changed_at: float = 0.0
    lora_stable_since: float = 0.0
    route_state: str = "PRIMARY_LORA"


@dataclass
class SimPacket:
    msg_id: str
    src: str
    dest: str
    payload: str
    created_at: float
    status: str = "QUEUED"
    route_used: str = "UNASSIGNED"
    retry_count: int = 0
    ack_required: bool = True
    acked_at: Optional[float] = None
    path: List[str] = field(default_factory=list)


@dataclass(frozen=True)
class GatewayHeartbeat:
    node_id: str
    gateway_id: str
    received_at: float
    rssi: float
    snr: float
    raw_payload: str
    status: str = "ONLINE"


@dataclass
class DeliveryResult:
    delivered: bool
    packet: SimPacket
    events: List[str]


@dataclass
class GatewaySnapshot:
    gateways: Dict[str, SimGateway]
    nodes: Dict[str, SimNode]
    heartbeats: Dict[str, GatewayHeartbeat]
    queue_depth: int
    delivered_count: int
    failed_count: int
    events: List[str]
