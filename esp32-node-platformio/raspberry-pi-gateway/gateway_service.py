#!/usr/bin/env python3
"""
PUP MANET Emergency Messaging — Raspberry Pi Gateway TCP Relay Service
Lightweight peer-to-peer JSON relay over Tailscale.

Modes:
  server  — binds to a TCP port and listens for peer connections
  client  — actively connects to a peer and can send JSON packets
"""

import json
import logging
import socket
import sys
import threading
import time
from pathlib import Path

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

                # Track remote nodes advertised by peer gateway
                if msg_type == "nodes":
                    self._ingest_remote_nodes(msg)
                    self._inject_remote_nodes_to_serial()
                    continue

                # Auto-ack anything that isn't a heartbeat or ack to avoid loops
                if msg_type not in ("heartbeat", "ack"):
                    reply = {
                        "type": "ack",
                        "ref_type": msg_type,
                        "timestamp": time.time(),
                    }
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
            self._close(sk)
            self.conn = None
            self._log(LOG_TAGS["peer"], f"Connection lost, reconnecting in {self.reconnect_delay}s ...")
            time.sleep(self.reconnect_delay)

    # ------------------------------------------------------------------
    # Public send API
    # ------------------------------------------------------------------
    def send(self, payload: dict) -> bool:
        if not self._connected or self.conn is None:
            self._log(LOG_TAGS["tx"], "No active peer connection — dropping packet")
            return False
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

    def stop(self) -> None:
        self._log(LOG_TAGS["start"], "Shutting down ...")
        self._stop_event.set()
        self._close(self.conn)
        self._close(self.sock)
        if hasattr(self, "_worker"):
            self._worker.join(timeout=3)
        if hasattr(self, "_serial_worker"):
            self._serial_worker.join(timeout=1)
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
