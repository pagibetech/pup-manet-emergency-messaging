from __future__ import annotations

import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Any

from .simulator import GatewaySimulator


def create_flask_app(simulator: GatewaySimulator | None = None) -> Any:
    """Create a Flask app when Flask is installed on the target machine."""
    from flask import Flask, jsonify

    sim = simulator or GatewaySimulator()
    app = Flask(__name__)

    @app.get("/")
    def index() -> str:
        return _html_dashboard(sim)

    @app.get("/api/status")
    def status() -> Any:
        return jsonify(sim.snapshot_dict())

    @app.post("/api/demo")
    def demo() -> Any:
        sim.run_demo()
        return jsonify(sim.snapshot_dict())

    return app


def run_dashboard(host: str = "127.0.0.1", port: int = 8080) -> None:
    simulator = GatewaySimulator()
    try:
        app = create_flask_app(simulator)
    except ModuleNotFoundError:
        _run_stdlib_dashboard(simulator, host, port)
        return

    app.run(host=host, port=port)


def _run_stdlib_dashboard(simulator: GatewaySimulator, host: str, port: int) -> None:
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:  # noqa: N802
            if self.path == "/api/status":
                self._send_json(simulator.snapshot_dict())
                return
            self._send_html(_html_dashboard(simulator))

        def do_POST(self) -> None:  # noqa: N802
            if self.path == "/api/demo":
                simulator.run_demo()
                self._send_json(simulator.snapshot_dict())
                return
            self.send_response(404)
            self.end_headers()

        def log_message(self, format: str, *args: object) -> None:
            return

        def _send_json(self, payload: dict) -> None:
            data = json.dumps(payload, indent=2).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def _send_html(self, html: str) -> None:
            data = html.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

    server = HTTPServer((host, port), Handler)
    print(f"Gateway dashboard running at http://{host}:{port}")
    server.serve_forever()


def _html_dashboard(simulator: GatewaySimulator) -> str:
    snap = simulator.snapshot_dict()
    events = "\n".join(snap["events"]) or "No events yet. POST /api/demo to run the demo."
    gateway_rows = "".join(
        f"<tr><td>{gid}</td><td>{gw['network_id']}</td><td>{gw['route_state']}</td>"
        f"<td>{'YES' if gw['lora_available'] else 'NO'}</td><td>{gw['peer_gateway_id']}</td></tr>"
        for gid, gw in snap["gateways"].items()
    )
    heartbeat_rows = "".join(
        f"<tr><td>{node_id}</td><td>{hb['gateway_id']}</td><td>{hb['received_at']:.1f}</td>"
        f"<td>{hb['rssi']:.1f}</td><td>{hb['snr']:.1f}</td><td>{hb['status']}</td></tr>"
        for node_id, hb in snap["heartbeats"].items()
    ) or "<tr><td colspan=\"6\">No LoRa SPI heartbeats received yet.</td></tr>"
    router_link = snap["router_link"]
    router_status = "ONLINE" if router_link["available"] else "OFFLINE"
    last_ping = "OK" if router_link["last_ping_ok"] else "Not passed yet"
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>PUP MANET Gateway Simulator</title>
  <style>
    body {{ font-family: Arial, sans-serif; margin: 24px; color: #1f2933; }}
    table {{ border-collapse: collapse; margin: 16px 0; min-width: 640px; }}
    th, td {{ border: 1px solid #ccd5df; padding: 8px 10px; text-align: left; }}
    th {{ background: #e9f2ef; }}
    pre {{ background: #f5f7f9; padding: 12px; border: 1px solid #d6dde5; }}
  </style>
</head>
<body>
  <h1>PUP MANET Gateway Simulator</h1>
  <p>Simulation-first gateway dashboard. Step 4.2 adds LoRa SPI heartbeat intake through a hardware abstraction.</p>
  <p>Queue: {snap['queue_depth']} | Delivered: {snap['delivered_count']} | Failed: {snap['failed_count']} | Heartbeats: {snap['heartbeat_count']} | Router Link: {router_status}</p>
  <table>
    <thead><tr><th>Gateway</th><th>Network</th><th>Route State</th><th>LoRa Ready</th><th>Peer</th></tr></thead>
    <tbody>{gateway_rows}</tbody>
  </table>
  <h2>LoRa SPI Heartbeats</h2>
  <table>
    <thead><tr><th>Node</th><th>Gateway</th><th>Received At</th><th>RSSI</th><th>SNR</th><th>Status</th></tr></thead>
    <tbody>{heartbeat_rows}</tbody>
  </table>
  <h2>Router-to-Router Link</h2>
  <table>
    <tbody>
      <tr><th>Link</th><td>{router_link['link_id']}</td></tr>
      <tr><th>Path</th><td>{router_link['router_a_name']} &lt;-&gt; {router_link['router_b_name']}</td></tr>
      <tr><th>Gateways</th><td>{router_link['gateway_a_id']} &lt;-&gt; {router_link['gateway_b_id']}</td></tr>
      <tr><th>Status</th><td>{router_status}</td></tr>
      <tr><th>Last Ping</th><td>{last_ping} at {router_link['last_ping_at']:.1f}s</td></tr>
    </tbody>
  </table>
  <h2>Recent Events</h2>
  <pre>{events}</pre>
</body>
</html>"""
