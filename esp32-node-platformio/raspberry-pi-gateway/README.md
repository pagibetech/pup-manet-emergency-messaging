# Raspberry Pi Gateway TCP Relay Service

Lightweight Python TCP relay for peer-to-peer JSON messaging between Raspberry Pi 3B gateways over Tailscale.

## Files

| File | Purpose |
|------|---------|
| `gateway_service.py` | TCP relay service (server/client modes, heartbeat, reconnect) |
| `config.example.json` | Example configuration — copy to `config.json` and edit |

## Requirements

- Raspberry Pi OS (any recent version)
- Python 3.9+ (pre-installed on Raspberry Pi OS)
- Tailscale connected and validated between gateways

## Quick Start

### 1. Copy and edit configuration

```bash
cp config.example.json config.json
nano config.json
```

### 2. Choose mode

**Server mode** — listens for incoming peer connections:
```json
{
  "mode": "server",
  "bind_host": "0.0.0.0",
  "bind_port": 5050,
  "peer_host": "100.123.79.41",
  "peer_port": 5050,
  "heartbeat_interval_sec": 10,
  "reconnect_delay_sec": 5,
  "log_level": "INFO"
}
```

**Client mode** — actively connects to a peer and can send packets:
```json
{
  "mode": "client",
  "bind_host": "0.0.0.0",
  "bind_port": 5050,
  "peer_host": "100.79.214.18",
  "peer_port": 5050,
  "heartbeat_interval_sec": 10,
  "reconnect_delay_sec": 5,
  "log_level": "INFO"
}
```

### 3. Run the service

```bash
python3 gateway_service.py config.json
```

Or without a config file (uses defaults):
```bash
python3 gateway_service.py
```

## Test Design — Gateway A ↔ Gateway B

| Gateway | Tailscale IP | Role in test |
|---------|-------------|--------------|
| Gateway A | `100.123.79.41` | Client — sends JSON test packet |
| Gateway B | `100.79.214.18` | Server — listens on port `5050` |

### On Gateway B (server)

```bash
cp config.example.json config.json
# Ensure mode is "server"
python3 gateway_service.py config.json
```

Expected logs:
```
[GATEWAY_START] Starting in SERVER mode
[TCP_SERVER]   Listening on 0.0.0.0:5050
```

### On Gateway A (client)

```bash
cp config.example.json config.json
# Edit config.json: set mode to "client", peer_host to 100.79.214.18
python3 gateway_service.py config.json
```

At the interactive prompt, send a test packet:
```
> {"type":"test","msg":"hello from Gateway A"}
```

### Expected Results

**Gateway A (client) logs:**
```
[TCP_TX] Sent type=test to peer
[TCP_RX] From server:100.79.214.18:5050 type=ack body={...}
```

**Gateway B (server) logs:**
```
[TCP_RX] From <Gateway-A-IP>:<port> type=test body={"type":"test","msg":"hello from Gateway A"}
[TCP_TX] ACK sent to <Gateway-A-IP>:<port>
```

## Log Tags Reference

| Tag | Meaning |
|-----|---------|
| `[GATEWAY_START]` | Service startup / shutdown |
| `[TCP_SERVER]` | Server bind / listen events |
| `[TCP_RX]` | Incoming JSON packet received |
| `[TCP_TX]` | Outgoing JSON packet sent |
| `[HEARTBEAT]` | Periodic heartbeat send/receive |
| `[PEER_STATUS]` | Peer connect / disconnect / reconnect events |

## Service Features

- **TCP server mode** — bind and accept a single peer connection
- **TCP client/send mode** — connect to peer and send JSON packets interactively
- **JSON packet I/O** — newline-delimited JSON (JSONL) over TCP
- **Peer IP/hostname configuration** — via `config.json`
- **Simple heartbeat** — periodic `{"type":"heartbeat"}` every N seconds
- **Reconnect-safe design** — client auto-reconnects with backoff; server returns to `accept()`
- **Tagged logging** — consistent log tags for grepping and monitoring

## Notes

- No ESP32 serial integration yet.
- No database persistence yet.
- No changes to Android or ESP32 firmware.
- Designed to be beginner-friendly and fully self-contained.

## License

Part of the PUP MANET Emergency Messaging project.
