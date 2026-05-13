from __future__ import annotations

from dataclasses import asdict
from typing import Dict, List, Optional

from .lora_spi import LoRaFrame, LoRaSpiRadio, parse_gateway_heartbeat
from .models import DeliveryResult, GatewaySnapshot, RouterLink, SimGateway, SimNode, SimPacket


class GatewaySimulator:
    """Simulation-first gateway for two MANET networks and a WiFi-router link."""

    FAILOVER_SECONDS = 10.0
    RECOVERY_SECONDS = 10.0

    def __init__(self) -> None:
        self.nodes: Dict[str, SimNode] = {
            "A1": SimNode("A1", "NET_A", "GWA"),
            "A2": SimNode("A2", "NET_A", "GWA"),
            "A3": SimNode("A3", "NET_A", "GWA"),
            "B1": SimNode("B1", "NET_B", "GWB"),
            "B2": SimNode("B2", "NET_B", "GWB"),
            "B3": SimNode("B3", "NET_B", "GWB"),
        }
        self.gateways: Dict[str, SimGateway] = {
            "GWA": SimGateway("GWA", "NET_A", ["A1", "A2", "A3"], "GWB"),
            "GWB": SimGateway("GWB", "NET_B", ["B1", "B2", "B3"], "GWA"),
        }
        self.router_link = RouterLink("ROUTER_LINK_A_B", "GWA", "GWB")
        self.satellite_link_available = self.router_link.available
        self.heartbeats = {}
        self.queue: List[SimPacket] = []
        self.delivered: List[SimPacket] = []
        self.failed: List[SimPacket] = []
        self.events: List[str] = []
        self._counter = 0

    def send_message(self, src: str, dest: str, payload: str, now: float = 0.0) -> DeliveryResult:
        if src not in self.nodes:
            raise ValueError(f"unknown source node: {src}")
        if dest not in self.nodes:
            raise ValueError(f"unknown destination node: {dest}")

        packet = SimPacket(
            msg_id=self._next_msg_id(src),
            src=src,
            dest=dest,
            payload=payload,
            created_at=now,
        )
        self.queue.append(packet)
        self._event(now, f"QUEUED {packet.msg_id} {src}->{dest}")
        self.tick(now)
        return DeliveryResult(packet.status == "DELIVERED", packet, list(self.events))

    def tick(self, now: float) -> None:
        self._update_recovery(now)
        for packet in list(self.queue):
            if packet.status == "QUEUED":
                self._attempt_delivery(packet, now)
            elif packet.status == "WAITING_FAILOVER" and now - packet.created_at >= self.FAILOVER_SECONDS:
                self._deliver_via_gateway(packet, now, reason="FAILOVER_AFTER_10S")
            elif packet.status == "WAITING_ACK":
                self._ack_packet(packet, now)

    def set_lora_available(self, gateway_id: str, available: bool, now: float) -> None:
        gateway = self.gateways[gateway_id]
        gateway.lora_available = available
        gateway.lora_changed_at = now
        gateway.lora_stable_since = now if available else 0.0
        if not available:
            gateway.route_state = "DEGRADED"
            self._event(now, f"{gateway_id} LORA_DEGRADED")
        else:
            if gateway.route_state == "DEGRADED":
                gateway.route_state = "RECOVERING"
            self._event(now, f"{gateway_id} LORA_RECOVERY_OBSERVED")

    def set_satellite_link_available(self, available: bool, now: float) -> None:
        self.set_router_link_available(available, now)

    def set_router_link_available(self, available: bool, now: float) -> None:
        self.router_link.available = available
        self.satellite_link_available = available
        self._event(now, f"ROUTER_LINK {'ONLINE' if available else 'OFFLINE'}")

    def ping_gateway(self, source_gateway_id: str, target_gateway_id: str, now: float) -> bool:
        if source_gateway_id not in self.gateways:
            raise ValueError(f"unknown source gateway: {source_gateway_id}")
        if target_gateway_id not in self.gateways:
            raise ValueError(f"unknown target gateway: {target_gateway_id}")

        expected_pair = {self.router_link.gateway_a_id, self.router_link.gateway_b_id}
        actual_pair = {source_gateway_id, target_gateway_id}
        ok = self.router_link.available and actual_pair == expected_pair
        self.router_link.last_ping_at = now
        self.router_link.last_ping_ok = ok
        if ok:
            self._event(
                now,
                f"PING_OK {source_gateway_id}->{target_gateway_id} "
                f"path={self.router_link.router_a_name}<->{self.router_link.router_b_name}",
            )
        else:
            self._event(now, f"PING_FAIL {source_gateway_id}->{target_gateway_id} reason=ROUTER_LINK_UNAVAILABLE")
        return ok

    def receive_lora_frame(self, frame: LoRaFrame) -> bool:
        heartbeat = parse_gateway_heartbeat(frame)
        if heartbeat is None:
            self._event(frame.received_at, "LORA_FRAME_IGNORED reason=NOT_HEARTBEAT")
            return False

        node = self.nodes.get(heartbeat.node_id)
        if node is None:
            self._event(frame.received_at, f"HEARTBEAT_IGNORED unknown_node={heartbeat.node_id}")
            return False

        if heartbeat.gateway_id != node.gateway_id:
            self._event(
                frame.received_at,
                f"HEARTBEAT_IGNORED node={heartbeat.node_id} expected_gateway={node.gateway_id} "
                f"actual_gateway={heartbeat.gateway_id}",
            )
            return False

        self.heartbeats[heartbeat.node_id] = heartbeat
        gateway = self.gateways[heartbeat.gateway_id]
        gateway.lora_available = True
        gateway.lora_stable_since = heartbeat.received_at
        self._event(
            heartbeat.received_at,
            f"HEARTBEAT_RX node={heartbeat.node_id} gateway={heartbeat.gateway_id} "
            f"rssi={heartbeat.rssi:.1f} snr={heartbeat.snr:.1f}",
        )
        return True

    def poll_lora_radio(self, radio: LoRaSpiRadio, now: float, max_frames: int = 25) -> int:
        received = 0
        for _ in range(max_frames):
            frame = radio.receive_frame(now)
            if frame is None:
                break
            if self.receive_lora_frame(frame):
                received += 1
        return received

    def snapshot(self) -> GatewaySnapshot:
        return GatewaySnapshot(
            gateways=self.gateways,
            nodes=self.nodes,
            heartbeats=self.heartbeats,
            router_link=self.router_link,
            queue_depth=len(self.queue),
            delivered_count=len(self.delivered),
            failed_count=len(self.failed),
            events=list(self.events[-25:]),
        )

    def snapshot_dict(self) -> dict:
        snap = self.snapshot()
        return {
            "gateways": {key: asdict(value) for key, value in snap.gateways.items()},
            "nodes": {key: asdict(value) for key, value in snap.nodes.items()},
            "heartbeats": {key: asdict(value) for key, value in snap.heartbeats.items()},
            "router_link": asdict(snap.router_link),
            "queue_depth": snap.queue_depth,
            "heartbeat_count": len(snap.heartbeats),
            "delivered_count": snap.delivered_count,
            "failed_count": snap.failed_count,
            "events": snap.events,
        }

    def run_demo(self) -> dict:
        self.send_message("A1", "A2", "local baseline", now=0.0)
        self.send_message("A1", "B2", "cross network baseline", now=1.0)
        self.set_lora_available("GWA", False, now=2.0)
        self.send_message("A2", "A3", "local failover candidate", now=2.0)
        self.tick(12.0)
        self.set_lora_available("GWA", True, now=13.0)
        self.tick(23.0)
        return self.snapshot_dict()

    def run_router_link_demo(self) -> dict:
        ping_ok = self.ping_gateway("GWA", "GWB", now=0.0)
        remote_result = self.send_message("A1", "B2", "router link demo message", now=1.0)
        self.set_router_link_available(False, now=2.0)
        ping_down = self.ping_gateway("GWA", "GWB", now=2.1)
        failed_result = self.send_message("A2", "B1", "router link down message", now=3.0)
        self.set_router_link_available(True, now=4.0)
        ping_recovered = self.ping_gateway("GWB", "GWA", now=4.1)
        snapshot = self.snapshot_dict()
        snapshot["router_link_demo"] = {
            "initial_ping_ok": ping_ok,
            "remote_delivery_route": remote_result.packet.route_used,
            "remote_delivery_status": remote_result.packet.status,
            "link_down_ping_ok": ping_down,
            "link_down_delivery_status": failed_result.packet.status,
            "recovered_ping_ok": ping_recovered,
        }
        return snapshot

    def _attempt_delivery(self, packet: SimPacket, now: float) -> None:
        src_node = self.nodes[packet.src]
        dest_node = self.nodes[packet.dest]
        src_gateway = self.gateways[src_node.gateway_id]

        if src_node.network_id == dest_node.network_id and src_gateway.lora_available:
            packet.path = [packet.src, src_gateway.gateway_id, packet.dest]
            packet.route_used = "LOCAL_LORA"
            packet.status = "WAITING_ACK"
            self._event(now, f"LOCAL_LORA_SENT {packet.msg_id} path={'->'.join(packet.path)}")
            self._ack_packet(packet, now)
            return

        if src_node.network_id != dest_node.network_id:
            self._deliver_via_gateway(packet, now, reason="DESTINATION_REMOTE")
            return

        packet.status = "WAITING_FAILOVER"
        packet.retry_count += 1
        self._event(now, f"WAITING_FAILOVER {packet.msg_id} retry={packet.retry_count}")

    def _deliver_via_gateway(self, packet: SimPacket, now: float, reason: str) -> None:
        src_node = self.nodes[packet.src]
        dest_node = self.nodes[packet.dest]
        src_gateway = self.gateways[src_node.gateway_id]
        dest_gateway = self.gateways[dest_node.gateway_id]

        if not self.router_link.available:
            packet.status = "FAILED"
            packet.route_used = "NO_GATEWAY_LINK"
            self.queue.remove(packet)
            self.failed.append(packet)
            self._event(now, f"FAILED {packet.msg_id} reason=ROUTER_LINK_OFFLINE")
            return

        packet.path = [packet.src, src_gateway.gateway_id, "WIFI_ROUTER_LINK", dest_gateway.gateway_id, packet.dest]
        packet.route_used = "GATEWAY_WIFI_ROUTER"
        packet.status = "WAITING_ACK"
        self._event(now, f"GATEWAY_FORWARD {packet.msg_id} reason={reason} path={'->'.join(packet.path)}")
        self._ack_packet(packet, now)

    def _ack_packet(self, packet: SimPacket, now: float) -> None:
        packet.status = "DELIVERED"
        packet.acked_at = now
        if packet in self.queue:
            self.queue.remove(packet)
        self.delivered.append(packet)
        self._event(now, f"ACK {packet.msg_id} route={packet.route_used}")

    def _update_recovery(self, now: float) -> None:
        for gateway in self.gateways.values():
            if not gateway.lora_available:
                continue
            if gateway.route_state == "RECOVERING" and now - gateway.lora_stable_since >= self.RECOVERY_SECONDS:
                gateway.route_state = "PRIMARY_LORA"
                self._event(now, f"{gateway.gateway_id} PRIMARY_LORA_RESTORED")

    def _next_msg_id(self, src: str) -> str:
        self._counter += 1
        return f"{src}-{self._counter:04d}"

    def _event(self, now: float, message: str) -> None:
        self.events.append(f"{now:06.1f} {message}")
