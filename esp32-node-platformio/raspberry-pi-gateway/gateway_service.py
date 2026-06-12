#!/usr/bin/env python3
"""
PUP MANET Emergency Messaging — Raspberry Pi Gateway TCP Relay Service
Lightweight peer-to-peer JSON relay over Tailscale.

Modes:
  server  — binds to a TCP port and listens for peer connections
  client  — actively connects to a peer and can send JSON packets
"""

from __future__ import annotations

import json
import logging
import socket
import sys
import threading
import time
from pathlib import Path

try:
    from rpi_gateway_health import parse_health_event, NodeHealthMonitor
except ImportError:
    try:
        from node_health_monitor import NodeHealthMonitor
        from health_event_parser import parse_health_event
    except ImportError:
        parse_health_event = None
        NodeHealthMonitor = None

try:
    from store_forward_queue import (
        StoreForwardQueue,
        build_buffered_ack,
        build_dropped_ack,
    )
    from replay_engine import ReplayScheduler, ReplayResult, evaluate_send_result
    from route_state_machine import (
        RouteStateMachine,
        STATE_FAILOVER_ACTIVE,
        STATE_RECOVERING,
        STATE_PRIMARY_LORA,
        DEFAULT_HEALTH_CHECK_INTERVAL,
    )
except ImportError:
    StoreForwardQueue = None
    ReplayScheduler = None
    ReplayResult = None
    evaluate_send_result = None
    RouteStateMachine = None
    build_buffered_ack = None
    build_dropped_ack = None
    STATE_FAILOVER_ACTIVE = "FAILOVER_ACTIVE"
    STATE_RECOVERING = "RECOVERING"
    STATE_PRIMARY_LORA = "PRIMARY_LORA"
    DEFAULT_HEALTH_CHECK_INTERVAL = 1.0

DEFAULT_CONFIG = {
    "mode": "server",
    "bind_host": "0.0.0.0",
    "bind_port": 5050,
    "peer_host": "100.79.214.18",
    "peer_port": 5050,
    "heartbeat_interval_sec": 10,
    "reconnect_delay_sec": 5,
    "log_level": "INFO",
    "serial_enabled": False,
    "serial_port": "/dev/ttyUSB0",
    "serial_baud": 115200,
    "serial_read_timeout_sec": 0.1,
    "gateway_id": "A",
    "node_advertise_interval_sec": 10,
    "store_forward_max_depth": 256,
    "store_forward_ttl_sec": 300,
    "replay_stability_sec": 5.0,
    "route_health_check_interval_sec": DEFAULT_HEALTH_CHECK_INTERVAL,
    "peer_online_timeout_sec": 30,
    "peer_health_check_interval_sec": 5,
    }

LOG_TAGS = {
    "start": "[GATEWAY_START]",
    "server": "[TCP_SERVER]",
    "rx": "[TCP_RX]",
    "tx": "[TCP_TX]",
    "heartbeat": "[HEARTBEAT]",
    "peer": "[PEER_STATUS]",
    "nodes": "[NODES]",
}


def load_config(path: str) -> dict:
    config = dict(DEFAULT_CONFIG)
    p = Path(path)
    if p.exists():
        try:
            with p.open("r", encoding="utf-8") as f:
                config.update(json.load(f))
        except Exception as exc:
            logging.warning("%s Failed to load config %s: %s", LOG_TAGS["start"], path, exc)
    return config


def setup_logging(level: str) -> None:
    numeric = getattr(logging, level.upper(), logging.INFO)
    logging.basicConfig(
        level=numeric,
        format="%(asctime)s %(levelname)-8s %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


class GatewayRelay:
    def __init__(self, config: dict):
        self.cfg = config
        self.mode = str(config.get("mode", "server")).lower().strip()
        self.bind_host = config.get("bind_host", "0.0.0.0")
        self.bind_port = int(config.get("bind_port", 5050))
        self.peer_host = config.get("peer_host", "100.79.214.18")
        self.peer_port = int(config.get("peer_port", 5050))
        self.heartbeat_interval = int(config.get("heartbeat_interval_sec", 10))
        self.reconnect_delay = int(config.get("reconnect_delay_sec", 5))
        self.gateway_id = str(config.get("gateway_id", "A")).upper().strip()
        self.node_advertise_interval = int(config.get("node_advertise_interval_sec", 10))

        self.serial_enabled = bool(config.get("serial_enabled", False))
        self.serial_port = config.get("serial_port", "/dev/ttyUSB0")
        self.serial_baud = int(config.get("serial_baud", 115200))
        self.serial_read_timeout = float(config.get("serial_read_timeout_sec", 0.1))

        self.sock: socket.socket | None = None
        self.conn: socket.socket | None = None
        self.peer_addr: tuple[str, int] | None = None
        self._stop_event = threading.Event()
        self._tx_lock = threading.Lock()
        self._connected = False
        self._serial = None
        self._serial_lock = threading.Lock()

        # Node tracking for STEP042A
        self._local_nodes: dict[str, dict] = {}   # node_id -> {"last_seen": float, "gateway": str}
        self._remote_nodes: dict[str, dict] = {}  # node_id -> {"gateway": str, "last_seen": float}
        self._nodes_lock = threading.Lock()

        # STEP048A Gateway Degradation Awareness
        if NodeHealthMonitor is not None:
            self._health_monitor = NodeHealthMonitor(gateway_id=self.gateway_id)
        else:
            self._health_monitor = None

        # STEP048C Store-and-Forward Gateway Integration
        if StoreForwardQueue is not None:
            self._store_forward_queue = StoreForwardQueue(
                max_depth=int(config.get("store_forward_max_depth", 256)),
                ttl_seconds=int(config.get("store_forward_ttl_sec", 300)),
                gateway_id=self.gateway_id,
            )
        else:
            self._store_forward_queue = None

        if RouteStateMachine is not None and self._health_monitor is not None:
            self._route_state_machine = RouteStateMachine(
                self._health_monitor,
                gateway_id=self.gateway_id,
            )
        else:
            self._route_state_machine = None

        if ReplayScheduler is not None and self._store_forward_queue is not None:
            self._replay_scheduler = ReplayScheduler(
                queue=self._store_forward_queue,
                send_callback=self._send_replay_packet,
                gateway_id=self.gateway_id,
                stability_seconds=float(config.get("replay_stability_sec", 5.0)),
            )
        else:
            self._replay_scheduler = None

        self.route_health_check_interval = float(
            config.get("route_health_check_interval_sec", DEFAULT_HEALTH_CHECK_INTERVAL)
        )

        # STEP048D Gateway Relay Mode diagnostics. These counters are
        # intentionally in gateway_service.py only; packet payloads are not
        # mutated and ESP32/Android protocol semantics remain unchanged.
        self._gateway_relay_stats = {
            "relayed": 0,
            "skipped": 0,
            "failed": 0,
            "last_relay_at": 0.0,
        }

        # STEP048E Gateway Relay ACK Tracking
        self._relay_tracking: dict[str, dict] = {}
        self._relay_tracking_lock = threading.Lock()
        self._relay_tracking_max_entries = 100

        # STEP048F Peer Online Detection Hardening
        self.peer_online_timeout_sec = float(
            config.get("peer_online_timeout_sec", 30)
        )
        self.peer_health_check_interval = float(
            config.get("peer_health_check_interval_sec", 5)
        )
        self._peer_last_seen: float | None = None
        self._peer_lost_count = 0
        self._peer_was_online = False

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------
    def _log(self, tag: str, message: str) -> None:
        logging.info("%s %s", tag, message)

    def _open_serial(self) -> None:
        if not self.serial_enabled:
            return
        try:
            import serial

            self._serial = serial.Serial(
                port=self.serial_port,
                baudrate=self.serial_baud,
                timeout=self.serial_read_timeout,
            )
            self._log("[SERIAL_START]", f"Opened {self.serial_port} at {self.serial_baud}")
        except Exception as exc:
            self._log("[SERIAL_START]", f"Failed to open serial: {exc}")

    def _serial_read_loop(self) -> None:
        if self._serial is None:
            return
        while not self._stop_event.is_set():
            try:
                raw = self._serial.readline()
                if not raw:
                    continue
                line = raw.decode("utf-8", errors="replace").strip()
                if not line:
                    continue
                self._log("[SERIAL_RX]", line[:256])

                # STEP048A: detect health events before JSON parsing
                if self._health_monitor is not None and parse_health_event is not None:
                    health = parse_health_event(line)
                    if health is not None:
                        self._health_monitor._apply_event(health)
                        self._log("[HEALTH_EVENT]",
                                  f"{health['event']} node={health['nodeId']} "
                                  f"rssi={health['rssi']}")
                        continue

                json_str = line
                if line.startswith("[GW_JSON]"):
                    json_str = line[len("[GW_JSON]"):].strip()

                if not json_str:
                    continue

                try:
                    msg = json.loads(json_str)
                except json.JSONDecodeError as exc:
                    self._log("[SERIAL_RX]", f"Invalid JSON: {exc}")
                    continue

                if not isinstance(msg, dict):
                    continue

                # Track local nodes from ESP32 serial output
                self._ingest_local_node_from_packet(msg)

                self.send(msg)
            except Exception as exc:
                self._log("[SERIAL_RX]", f"Error: {exc}")
                time.sleep(1)

    def _serial_write(self, payload: dict) -> bool:
        if self._serial is None:
            return False
        try:
            raw = json.dumps(payload, separators=(",", ":"))
            line = f"[GW_JSON] {raw}\n"
            with self._serial_lock:
                self._serial.write(line.encode("utf-8"))
            self._log("[SERIAL_TX]", raw[:256])
            return True
        except Exception as exc:
            self._log("[SERIAL_TX]", f"Write failed: {exc}")
            return False

    def _close(self, sk: socket.socket | None) -> None:
        if sk is None:
            return
        try:
            sk.close()
        except Exception:
            pass

    def _recv_line(self, sk: socket.socket, buffer_size: int = 4096) -> bytes:
        """Read until newline (JSONL style). Blocking."""
        data = b""
        while not self._stop_event.is_set():
            try:
                chunk = sk.recv(buffer_size)
            except socket.timeout:
                continue
            except OSError:
                break
            if not chunk:
                break
            data += chunk
            if b"\n" in data:
                break
        return data

    def _send_json(self, sk: socket.socket, payload: dict) -> bool:
        try:
            raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
            with self._tx_lock:
                sk.sendall(raw + b"\n")
            return True
        except Exception as exc:
            self._log(LOG_TAGS["tx"], f"Send failed: {exc}")
            return False

    # ------------------------------------------------------------------
    # Node tracking (STEP042A)
    # ------------------------------------------------------------------
    def _ingest_local_node_from_packet(self, msg: dict) -> None:
        packet_type = msg.get("packetType", "").upper()
        source_node = msg.get("sourceNode", "")
        if not source_node:
            return
        if packet_type in ("HELLO", "STATUS", "MESSAGE", "ACK"):
            with self._nodes_lock:
                self._local_nodes[source_node] = {
                    "last_seen": time.time(),
                    "gateway": self.gateway_id,
                }

    def _ingest_remote_nodes(self, payload: dict) -> None:
        nodes = payload.get("nodes", [])
        gw = payload.get("gateway_id", "UNKNOWN")
        if not isinstance(nodes, list):
            return
        now = time.time()
        with self._nodes_lock:
            for node in nodes:
                if isinstance(node, dict):
                    node_id = node.get("id", "")
                elif isinstance(node, str):
                    node_id = node
                else:
                    continue
                if node_id:
                    self._remote_nodes[node_id] = {
                        "gateway": gw,
                        "last_seen": now,
                    }

    def _build_node_list_payload(self) -> dict:
        now = time.time()
        with self._nodes_lock:
            local_list = [
                {"id": nid, "gw": info["gateway"]}
                for nid, info in self._local_nodes.items()
                if (now - info["last_seen"]) < 35
            ]
        return {
            "type": "nodes",
            "gateway_id": self.gateway_id,
            "nodes": local_list,
            "timestamp": now,
        }

    def _build_esp32_status_node_list(self) -> dict:
        """Build a BT-MANET-1.0 STATUS packet for injection into local ESP32 serial."""
        now = time.time()
        with self._nodes_lock:
            all_nodes = list(self._local_nodes.items()) + list(self._remote_nodes.items())
            active_nodes = [
                (nid, info)
                for nid, info in all_nodes
                if (now - info["last_seen"]) < 35
            ]
        payload_parts = ["type=NODE_LIST", f"count={len(active_nodes)}"]
        for nid, info in active_nodes:
            payload_parts.append(f"{nid},{info['gateway']},ONLINE")

        return {
            "protocolVersion": "BT-MANET-1.0",
            "packetType": "STATUS",
            "packetId": f"GW-NODELIST-{int(now*1000)}",
            "sourceNode": "GATEWAY",
            "destinationNode": "ESP32_BRIDGE",
            "payload": ";".join(payload_parts),
            "hopPath": "GATEWAY>ESP32_BRIDGE",
            "hopCount": 0,
            "ttl": 5,
            "previousHop": "",
            "retryCount": 0,
            "timestamp": int(now),
            "status": "ONLINE",
            "checksum": "checksum pending / simulated",
        }

    def _advertise_nodes_loop(self, sk: socket.socket) -> None:
        while not self._stop_event.is_set() and self._connected:
            time.sleep(self.node_advertise_interval)
            if self._stop_event.is_set() or not self._connected:
                break
            payload = self._build_node_list_payload()
            ok = self._send_json(sk, payload)
            if ok:
                self._log(LOG_TAGS["nodes"], f"Advertised {len(payload['nodes'])} local nodes to peer")
            else:
                self._log(LOG_TAGS["peer"], "Node advertisement send failed")
                self._connected = False
                break

    def _inject_remote_nodes_to_serial(self) -> None:
        packet = self._build_esp32_status_node_list()
        self._serial_write(packet)
        self._log(LOG_TAGS["nodes"], f"Injected node list to local ESP32 serial ({len(packet['payload'].split(';')) - 2} nodes)")

    # ------------------------------------------------------------------
    # STEP048C route state / store-forward integration
    # ------------------------------------------------------------------
    def _get_active_local_node_ids(self) -> list[str]:
        now = time.time()
        with self._nodes_lock:
            return [
                node_id
                for node_id, info in self._local_nodes.items()
                if (now - info.get("last_seen", 0.0)) < 35
            ]

    def _tick_route_state_once(self) -> str:
        if self._route_state_machine is None:
            return STATE_PRIMARY_LORA
        previous = self._route_state_machine.state
        state = self._route_state_machine.tick(self._get_active_local_node_ids())
        if state != previous:
            self._log("[ROUTE_STATE]", f"{previous} -> {state}")
        return state

    def _route_state_loop(self) -> None:
        while not self._stop_event.is_set():
            self._tick_route_state_once()
            self._stop_event.wait(self.route_health_check_interval)

    def get_route_state(self) -> str:
        if self._route_state_machine is None:
            return STATE_PRIMARY_LORA
        return self._route_state_machine.state

    def _handle_peer_link_up(self) -> None:
        self._touch_peer_seen()
        if self._replay_scheduler is not None:
            self._replay_scheduler.on_link_up()

    def _handle_peer_link_down(self) -> None:
        self._peer_last_seen = None
        if self._replay_scheduler is not None:
            self._replay_scheduler.on_link_down()

    def _should_store_forward(self) -> bool:
        return self.get_route_state() in (STATE_FAILOVER_ACTIVE, STATE_RECOVERING)

    def _queue_for_store_forward(self, payload: dict) -> bool:
        if self._store_forward_queue is None or not self._should_store_forward():
            return False
        entry = self._store_forward_queue.enqueue(payload)
        if entry is None:
            self._log("[STORE_FORWARD]", "Packet not queued")
            if build_dropped_ack is not None:
                self._serial_write(build_dropped_ack(payload, self.gateway_id))
            return False
        self._log(
            "[STORE_FORWARD]",
            f"Queued messageId={entry.message_id} depth={self._store_forward_queue.depth}",
        )
        if build_buffered_ack is not None:
            self._serial_write(build_buffered_ack(payload, self.gateway_id))
        return True

    def _send_replay_packet(self, payload: dict):
        if not self._connected or self.conn is None:
            return ReplayResult.RETRYABLE if ReplayResult is not None else False
        ok = self._send_json(self.conn, payload)
        if evaluate_send_result is not None:
            return evaluate_send_result(ok)
        return ok

    # ------------------------------------------------------------------
    # STEP048D Gateway Relay Mode
    # ------------------------------------------------------------------
    def _is_gateway_relay_candidate(self, payload: dict) -> bool:
        if not isinstance(payload, dict):
            return False
        packet_type = str(payload.get("packetType", "")).upper().strip()
        if packet_type in ("MESSAGE", "ACK"):
            return True
        return False

    def _should_gateway_relay(self, payload: dict) -> bool:
        if not self._connected or self.conn is None:
            return False
        if self.get_route_state() not in (STATE_FAILOVER_ACTIVE, STATE_RECOVERING):
            return False
        return self._is_gateway_relay_candidate(payload)

    def _relay_gateway_packet(self, payload: dict) -> bool:
        if self.conn is None:
            return False
        packet_id = str(payload.get("packetId", "")).strip()
        src_node = str(payload.get("sourceNode", "")).strip()
        dest_node = str(payload.get("destinationNode", "")).strip()
        if packet_id:
            self._relay_track(packet_id, src_node, dest_node)
        ok = self._send_json(self.conn, payload)
        if ok:
            self._gateway_relay_stats["relayed"] += 1
            self._gateway_relay_stats["last_relay_at"] = time.time()
            self._log(
                "[GATEWAY_RELAY]",
                f"Relayed packetId={payload.get('packetId', '')} "
                f"state={self.get_route_state()}",
            )
            return True
        self._gateway_relay_stats["failed"] += 1
        self._log(
            "[GATEWAY_RELAY]",
            f"Relay failed packetId={payload.get('packetId', '')}; attempting store-forward",
        )
        return False

    def get_gateway_relay_status(self) -> dict:
        return {
            "gateway_id": self.gateway_id,
            "route_state": self.get_route_state(),
            "peer_connected": self.is_connected(),
            "stats": dict(self._gateway_relay_stats),
        }

    # ------------------------------------------------------------------
    # STEP048E Gateway Relay ACK Tracking
    # ------------------------------------------------------------------
    def _relay_track(self, packet_id: str, src_node: str, dest_node: str) -> None:
        if not packet_id:
            return
        now = time.time()
        with self._relay_tracking_lock:
            if packet_id in self._relay_tracking:
                return
            if len(self._relay_tracking) >= self._relay_tracking_max_entries:
                oldest = min(
                    self._relay_tracking,
                    key=lambda k: self._relay_tracking[k].get("sent_at", 0.0),
                    default=None,
                )
                if oldest:
                    del self._relay_tracking[oldest]
            self._relay_tracking[packet_id] = {
                "packet_id": packet_id,
                "src_node": src_node,
                "dest_node": dest_node,
                "sent_at": now,
                "acked_at": 0.0,
                "status": "pending",
            }

    def _relay_ack(self, packet_id: str) -> bool:
        if not packet_id:
            return False
        now = time.time()
        with self._relay_tracking_lock:
            entry = self._relay_tracking.get(packet_id)
            if entry is None:
                return False
            if entry["status"] == "acknowledged":
                return True
            entry["status"] = "acknowledged"
            entry["acked_at"] = now
            self._log(
                "[RELAY_ACK]",
                f"Relay acknowledged packetId={packet_id} "
                f"src={entry['src_node']} dest={entry['dest_node']} "
                f"latency={now - entry['sent_at']:.3f}s",
            )
            return True

    def _process_peer_ack(self, ack_msg: dict) -> None:
        if not isinstance(ack_msg, dict):
            return
        ref_packet_id = str(ack_msg.get("ref_packet_id", "")).strip()
        if ref_packet_id:
            self._relay_ack(ref_packet_id)

    def get_relay_tracking(self) -> dict:
        with self._relay_tracking_lock:
            entries = {}
            pending = 0
            acknowledged = 0
            for pkt_id, entry in self._relay_tracking.items():
                entries[pkt_id] = dict(entry)
                if entry["status"] == "pending":
                    pending += 1
                elif entry["status"] == "acknowledged":
                    acknowledged += 1
            return {
                "gateway_id": self.gateway_id,
                "pending": pending,
                "acknowledged": acknowledged,
                "total_tracked": len(entries),
                "max_entries": self._relay_tracking_max_entries,
                "entries": entries,
            }

    # ------------------------------------------------------------------
    # STEP048F Peer Online Detection Hardening
    # ------------------------------------------------------------------
    def _touch_peer_seen(self) -> None:
        self._peer_last_seen = time.time()

    def is_peer_online(self) -> bool:
        if self._peer_last_seen is None:
            if self._peer_was_online:
                self._peer_lost_count += 1
                self._peer_was_online = False
            return False
        now = time.time()
        online = (now - self._peer_last_seen) < self.peer_online_timeout_sec
        if online:
            if not self._peer_was_online:
                self._peer_lost_count = 0
            self._peer_was_online = True
            return True
        if self._peer_was_online:
            self._peer_lost_count += 1
            self._peer_was_online = False
        return False

    def get_peer_status(self) -> dict:
        online = self.is_peer_online()
        return {
            "gateway_id": self.gateway_id,
            "online": online,
            "last_seen": self._peer_last_seen,
            "lost_count": self._peer_lost_count,
            "timeout_sec": self.peer_online_timeout_sec,
        }

    # ------------------------------------------------------------------
    # Heartbeat
    # ------------------------------------------------------------------
    def _heartbeat_loop(self, sk: socket.socket) -> None:
        while not self._stop_event.is_set():
            time.sleep(self.heartbeat_interval)
            if self._stop_event.is_set():
                break
            payload = {
                "type": "heartbeat",
                "timestamp": time.time(),
                "src": self.bind_host,
            }
            ok = self._send_json(sk, payload)
            if ok:
                self._log(LOG_TAGS["heartbeat"], f"Sent to {self.peer_host}:{self.peer_port}")
            else:
                self._log(LOG_TAGS["peer"], "Heartbeat send failed — marking disconnected")
                self._connected = False
                break

    # ------------------------------------------------------------------
    # RX loop (shared between server handler and client connection)
    # ------------------------------------------------------------------
    def _rx_loop(self, sk: socket.socket, label: str) -> None:
        while not self._stop_event.is_set() and self._connected:
            try:
                sk.settimeout(1.0)
                data = self._recv_line(sk)
            except Exception as exc:
                self._log(LOG_TAGS["rx"], f"Receive error on {label}: {exc}")
                self._connected = False
                break

            if not data:
                self._log(LOG_TAGS["peer"], f"Peer closed connection ({label})")
                self._connected = False
                break

            for line in data.split(b"\n"):
                line = line.strip()
                if not line:
                    continue
                try:
                    msg = json.loads(line.decode("utf-8"))
                except json.JSONDecodeError as exc:
                    self._log(LOG_TAGS["rx"], f"Invalid JSON from {label}: {exc}")
                    continue

                msg_type = msg.get("type", "unknown")
                self._log(LOG_TAGS["rx"], f"From {label} type={msg_type} body={json.dumps(msg)}")

                self._touch_peer_seen()

                # Track remote nodes advertised by peer gateway
                if msg_type == "nodes":
                    self._ingest_remote_nodes(msg)
                    self._inject_remote_nodes_to_serial()
                    continue

                # STEP048E: process peer ACK for relay tracking
                if msg_type == "ack" and isinstance(msg, dict):
                    self._process_peer_ack(msg)
                    continue

                # Auto-ack anything that isn't a heartbeat or ack to avoid loops
                if msg_type not in ("heartbeat", "ack"):
                    reply = {
                        "type": "ack",
                        "ref_type": msg_type,
                        "timestamp": time.time(),
                    }
                    ref_pkt_id = str(msg.get("packetId", "")).strip()
                    if ref_pkt_id:
                        reply["ref_packet_id"] = ref_pkt_id
                    self._send_json(sk, reply)
                    self._log(LOG_TAGS["tx"], f"ACK sent to {label}")

                    # Bridge to ESP32 serial
                    self._serial_write(msg)

    # ------------------------------------------------------------------
    # Server mode
    # ------------------------------------------------------------------
    def _server_accept_loop(self) -> None:
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.sock.bind((self.bind_host, self.bind_port))
        self.sock.listen(1)
        self.sock.settimeout(1.0)
        self._log(LOG_TAGS["server"], f"Listening on {self.bind_host}:{self.bind_port}")

        while not self._stop_event.is_set():
            try:
                conn, addr = self.sock.accept()
            except socket.timeout:
                continue
            except OSError:
                break

            self.conn = conn
            self.peer_addr = addr
            self._connected = True
            self._handle_peer_link_up()
            self._log(LOG_TAGS["peer"], f"Peer connected from {addr[0]}:{addr[1]}")

            # Start heartbeat toward the connected peer
            hb_thread = threading.Thread(
                target=self._heartbeat_loop,
                args=(conn,),
                daemon=True,
            )
            hb_thread.start()

            # Start node advertisement toward the connected peer
            adv_thread = threading.Thread(
                target=self._advertise_nodes_loop,
                args=(conn,),
                daemon=True,
            )
            adv_thread.start()

            self._rx_loop(conn, f"{addr[0]}:{addr[1]}")

            self._connected = False
            self._handle_peer_link_down()
            self._close(conn)
            self.conn = None
            self._log(LOG_TAGS["peer"], "Peer disconnected, returning to accept()")

        self._close(self.sock)
        self.sock = None

    # ------------------------------------------------------------------
    # Client mode
    # ------------------------------------------------------------------
    def _client_connect(self) -> socket.socket | None:
        sk = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sk.settimeout(5)
        try:
            sk.connect((self.peer_host, self.peer_port))
            sk.settimeout(None)
            return sk
        except Exception as exc:
            self._log(LOG_TAGS["peer"], f"Connect to {self.peer_host}:{self.peer_port} failed: {exc}")
            self._close(sk)
            return None

    def _client_loop(self) -> None:
        while not self._stop_event.is_set():
            sk = self._client_connect()
            if sk is None:
                self._log(LOG_TAGS["peer"], f"Reconnecting in {self.reconnect_delay}s ...")
                time.sleep(self.reconnect_delay)
                continue

            self.conn = sk
            self._connected = True
            self._handle_peer_link_up()
            self._log(LOG_TAGS["peer"], f"Connected to {self.peer_host}:{self.peer_port}")

            hb_thread = threading.Thread(
                target=self._heartbeat_loop,
                args=(sk,),
                daemon=True,
            )
            hb_thread.start()

            adv_thread = threading.Thread(
                target=self._advertise_nodes_loop,
                args=(sk,),
                daemon=True,
            )
            adv_thread.start()

            self._rx_loop(sk, f"server:{self.peer_host}:{self.peer_port}")

            self._connected = False
            self._handle_peer_link_down()
            self._close(sk)
            self.conn = None
            self._log(LOG_TAGS["peer"], f"Connection lost, reconnecting in {self.reconnect_delay}s ...")
            time.sleep(self.reconnect_delay)

    # ------------------------------------------------------------------
    # Public send API
    # ------------------------------------------------------------------
    def send(self, payload: dict) -> bool:
        if not self._connected or self.conn is None:
            if self._queue_for_store_forward(payload):
                return True
            self._log(LOG_TAGS["tx"], "No active peer connection — dropping packet")
            return False
        if self._should_gateway_relay(payload):
            if self._relay_gateway_packet(payload):
                return True
            if self._queue_for_store_forward(payload):
                return True
            return False
        self._gateway_relay_stats["skipped"] += 1
        ok = self._send_json(self.conn, payload)
        if ok:
            self._log(LOG_TAGS["tx"], f"Sent type={payload.get('type','unknown')} to peer")
        return ok

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------
    def start(self) -> None:
        self._log(LOG_TAGS["start"], f"Starting in {self.mode.upper()} mode (gateway={self.gateway_id})")
        self._open_serial()
        if self.mode == "server":
            self._worker = threading.Thread(target=self._server_accept_loop, daemon=True)
        else:
            self._worker = threading.Thread(target=self._client_loop, daemon=True)
        self._worker.start()
        if self._serial is not None:
            self._serial_worker = threading.Thread(target=self._serial_read_loop, daemon=True)
            self._serial_worker.start()
        if self._route_state_machine is not None:
            self._route_worker = threading.Thread(target=self._route_state_loop, daemon=True)
            self._route_worker.start()

    def stop(self) -> None:
        self._log(LOG_TAGS["start"], "Shutting down ...")
        self._stop_event.set()
        self._close(self.conn)
        self._close(self.sock)
        if hasattr(self, "_worker"):
            self._worker.join(timeout=3)
        if hasattr(self, "_serial_worker"):
            self._serial_worker.join(timeout=1)
        if hasattr(self, "_route_worker"):
            self._route_worker.join(timeout=1)
        if self._replay_scheduler is not None:
            self._replay_scheduler.stop()
        if self._serial is not None:
            try:
                self._serial.close()
            except Exception:
                pass
            self._serial = None

    def is_connected(self) -> bool:
        return self._connected


def main() -> None:
    config_path = sys.argv[1] if len(sys.argv) > 1 else "config.json"
    config = load_config(config_path)
    setup_logging(config.get("log_level", "INFO"))

    relay = GatewayRelay(config)
    relay.start()

    # In client mode, offer a tiny interactive prompt for test packets.
    # In server mode, just keep running and log everything.
    try:
        if relay.mode == "client":
            # Wait a moment for initial connection
            for _ in range(30):
                if relay.is_connected():
                    break
                time.sleep(0.1)

            print("\n--- Interactive send (type 'quit' to exit) ---")
            print("Examples:")
            print('  {"type":"chat","msg":"hello"}')
            print('  {"type":"ping"}')
            while not relay._stop_event.is_set():
                try:
                    line = input("> ").strip()
                except EOFError:
                    break
                if line.lower() in ("quit", "exit", "q"):
                    break
                if not line:
                    continue
                try:
                    payload = json.loads(line)
                    relay.send(payload)
                except json.JSONDecodeError as exc:
                    print(f"Invalid JSON: {exc}")
        else:
            while True:
                time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        relay.stop()


if __name__ == "__main__":
    main()
