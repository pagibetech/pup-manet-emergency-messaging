# [HERMES] ADM MANET — Session Report

**Date:** 2026-06-16  
**Commit:** `daa3d7e`  
**Branch:** `step-002-003-esp32-simulation`  
**Repo:** `https://github.com/pagibetech/pup-manet-emergency-messaging`  
**Local path:** `/Users/macbookm1max321tb/A_Design/A_Coding/ADM_Manet`

---

## 1. Infrastructure Setup

### SSH Chain
```
ai-stack → MacBook (100.83.27.73) → Gateway A (192.168.50.43, betg1)
                                  → Gateway B (192.168.50.44, betg2)
```

### Tailscale VPN
- **Gateway A:** `100.123.79.41` (pup-gateway-a) — SERVER mode, port 5050
- **Gateway B:** `100.79.214.18` (pup-gateway-b) — CLIENT mode
- **Status:** ESTABLISHED TCP via Tailscale

### Gateway Services
Both running in `screen` sessions on each RPi:

| Gateway | Mode | Config File | Status |
|---------|------|-------------|--------|
| A (betg1) | SERVER | `~/raspberry-pi-gateway/config.json` | ✅ Running |
| B (betg2) | CLIENT | `~/raspberry-pi-gateway/config.json` | ✅ Running |

Latest `gateway_service.py` (990 lines, STEP048A-G) deployed to both.

---

## 2. Firmware Status

| Target | Build | Flashed | Status |
|--------|-------|---------|--------|
| nodeA1_lora | ✅ SUCCESS | ✅ `/dev/tty.usbserial-0001` | ✅ LoRa active, HELLO broadcasts, RSSI -38dBm |
| nodeA2_lora | ✅ SUCCESS | ✅ `/dev/tty.usbserial-4` | ✅ LoRa active, HELLO broadcasts |
| gatewayA_lora | ✅ SUCCESS | ✅ via RPi-A `/dev/ttyUSB1` | ⚠️ Watchdog reset loop |
| gatewayB_lora | ✅ SUCCESS | ✅ via RPi-B `/dev/ttyUSB1` | ⚠️ Watchdog reset loop |

### Key Firmware Features (from `main.cpp`, 2894 lines)
- `RSSI_DEGRADE_THRESHOLD_DBM = -78` — degradation detection built in
- Compact BT1 LoRa protocol with STEP044 strict validation
- Mesh-wide HELLO propagation (STEP045A)
- Delivery tracking (STEP042C): MESSAGE → DELIVERED → SEEN
- Bridge ACK reliability (STEP046B/C)
- Bluetooth phone-to-node access layer (STEP047)

---

## 3. Test Validation

### Unit Tests
```
python -m unittest discover -s rpi-gateway/tests -p "test_*.py"
240 tests in ~6s
OK (0 failures)
```

### Store-and-Forward Integration Test
**Result: PASS** — 5/5 messages queued during simulated outage, replayed on reconnect.

```
[ENQUEUE] id=sf_000 ok=True depth=1
[ENQUEUE] id=sf_001 ok=True depth=2
[ENQUEUE] id=sf_002 ok=True depth=3
[ENQUEUE] id=sf_003 ok=True depth=4
[ENQUEUE] id=sf_004 ok=True depth=5
--- Link UP (reconnect) ---
[SEND_CB] Replayed: packetId=sf_000
[SEND_CB] Replayed: packetId=sf_001
[SEND_CB] Replayed: packetId=sf_002
[SEND_CB] Replayed: packetId=sf_003
[SEND_CB] Replayed: packetId=sf_004
[PASS] All 5 messages replayed!
```

### Test Campaign 8.3 — Gateway Failover
**Result: PASS** — 26/26 all tests passed.

| Phase | Description | Result |
|-------|-------------|--------|
| 1 | RouteStateMachine: PRIMARY_LORA start | ✅ |
| 2 | DEGRADED → FAILOVER_ACTIVE (3s timeout) | ✅ |
| 3 | RECOVERING → PRIMARY_LORA (2s timeout) | ✅ |
| 4 | force_state() + get_info() | ✅ |
| 5 | Health event parser (DEGRADATION/RECOVERY) | ✅ |
| 6 | Store-forward queuing during FAILOVER_ACTIVE | ✅ |

---

## 4. Workbook Updates

| Sheet | Changes |
|-------|---------|
| Detailed Steps | Added STEP048E (row 62), STEP048F (row 63), STEP048G (row 64) |
| Progress Tracker | Added 5 entries (STEP048C-G) starting at row 205 |
| Dashboard | Updated current stage + next action |
| Codex Task Log | Added STEP048F + STEP048G entries (rows 35-36) |

---

## 5. Repository Changes

### Commit `daa3d7e`
```
docs: ARCHITECTURE.md update + workbook STEP048E/F/G + test runner fix
```

**Files changed (5):**

| File | Change |
|------|--------|
| `docs/ARCHITECTURE.md` | Updated to 240/240 state, added STEP048G to module inventory + milestone list |
| `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx` | Added STEP048E/F/G entries |
| `rpi-gateway/tests/conftest.py` | New — unittest discover works without PYTHONPATH |
| `docs/codex-task-logs/STEP048B_ROUTE_STATE_MACHINE_SPEC.md` | New — pre-existing spec doc tracked |
| `docs/codex-task-logs/TEST_CAMPAIGN_8_2_DEGRADATION_TEST_PLAN.md` | New — pre-existing test plan tracked |

---

## 6. Known Issues

### Gateway ESP32 Watchdog Reset (RTCWDT_RTC_RESET)
Both gateway ESP32s (connected to RPi-A and RPi-B via USB serial) continuously watchdog-reset. Confirmed hardware issue — even the working `nodeA1_lora` firmware watchdog-resets when flashed to the gateway ESP32 hardware.

**Suspected causes:**
- Power instability from RPi USB port
- Defective ESP32 board or voltage regulator
- LoRa module wiring conflict

**Workaround:** Replace gateway ESP32 boards or power them independently.

### nodeA1/A2 USB Serial
Occasionally disconnects from MacBook. Reseating USB resolves.

---

## 7. Pending Items

| Item | Priority | Requires |
|------|----------|----------|
| Test Campaign 8.2 (Degradation) | Medium | Physical space to separate nodes |
| Gateway ESP32 replacement | High | New ESP32 boards |
| nodeA3 deployment + 6-node topology | Low | nodeA3 ESP32 + USB |
| GatewayB serial reset investigation | Low | Physical access to RPi-B |

---

## 8. Build Artifacts

### Android APK
```
Path:   android-chat-app/app/build/outputs/apk/debug/app-debug.apk
Size:   9.4 MB
Build:  CLEAN BUILD SUCCESSFUL (39 tasks, 17s)
```
