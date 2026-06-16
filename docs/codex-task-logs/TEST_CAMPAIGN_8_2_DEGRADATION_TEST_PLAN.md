# Test Campaign 8.2 -- Network Degradation Detection Test Plan

Date: 2026-06-10

Status: **Planning / Ready for User Execution**

---

## 1. Test Objectives

Verify that the PUP MANET system correctly detects network degradation when RSSI falls below the threshold and triggers the degradation state within the required time window.

**Primary objective**: Confirm that RSSI < -78 dBm sustained for 10 seconds is detected as a degradation event and logged with the required fields.

**Secondary objective**: Verify that the degradation state does not cause false-positive message delivery failures -- messages should still deliver via the remaining functional paths.

---

## 2. Test Environment

### Physical topology

```
Network A (Local MANET)               Network B (Remote MANET)
  nodeA1 -> Phone A                     nodeB1 (not deployed)
  nodeA2 -> Phone B                     nodeB2 (not deployed)
  gatewayA (RPi3B, LoRa, USB serial)   gatewayB (RPi3B, LoRa, USB serial)
       |                                       |
       +--- Tailscale VPN (internet) ----------+
```

### Current deployment state

| Component | Status |
|---|---|
| nodeA1 | Flashed, running, Phone A paired |
| nodeA2 | Flashed, running, Phone B paired |
| nodeA3 | Not deployed |
| gatewayA | Flashed (gatewayA_lora), USB serial to RPi-A |
| gatewayB | Flashed (gatewayB_lora), USB serial to RPi-B |
| nodeB1-B3 | Not deployed |
| Tailscale VPN | Configured and validated (STEP040B) |
| ESP32-RPi serial bridge | Validated (STEP041) |

### Software versions

| Component | Branch | HEAD |
|---|---|---|
| ESP32 firmware | `step-002-003-esp32-simulation` | `c338438` |
| RPi gateway service | `esp32-node-platformio/raspberry-pi-gateway/gateway_service.py` | Stable STEP038+ |
| Android app | `android-chat-app/` | STEP046C sanitized |
| Gateway simulation | `rpi-gateway/` | STEP042D validated |

### LoRa configuration

- Radio: SX1278 Ra-02
- Frequency: 433 MHz
- Bandwidth: 125 kHz (default)
- Spreading factor: SF7 (default)
- Coding rate: 4/5 (default)
- TX power: 17 dBm (default)

---

## 3. Required Hardware

| Item | Quantity | Purpose |
|---|---|---|
| ESP32 + SX1278 Ra-02 nodes | 2 minimum | nodeA1 and nodeA2 for degradation pair |
| Android phones | 2 | Phone A paired to nodeA1, Phone B to nodeA2 |
| USB-C cables | 2 | Serial monitor for each ESP32 |
| Raspberry Pi 3B gateways | 2 | gatewayA and gatewayB (optional for this test) |
| Mac/PC with serial terminals | 1 | Monitor ESP32 logs |
| Physical distance/obstacles | As needed | Introduce attenuation to lower RSSI |

---

## 4. Test Cases

### Test Case 8.2.1: Baseline RSSI measurement

**Objective**: Establish normal RSSI values at close range.

**Procedure**:
1. Place nodeA1 and nodeA2 within 1 meter, no obstructions.
2. Open serial monitors at 115200 baud for both nodes.
3. Verify both nodes are online and exchanging HELLO packets.
4. Record 10 consecutive RSSI values from each node's serial log.
5. Calculate mean RSSI.

**Expected**: RSSI between -30 dBm and -55 dBm at close range.

**Log pattern**: `[HEARTBEAT]` entries with RSSI, or `[LORA_RX]` with RSSI field.

---

### Test Case 8.2.2: Degradation threshold crossing

**Objective**: Verify RSSI drops below -78 dBm when attenuation is introduced.

**Procedure**:
1. Start with nodes at close range (1m).
2. Gradually increase distance or introduce obstacles (walls, floors).
3. Monitor RSSI trend in serial output.
4. Continue until RSSI drops below -78 dBm for at least 3 consecutive readings.
5. Record the distance/obstacle configuration and RSSI values.

**Expected**: RSSI decreases monotonically with distance; -78 dBm is reachable with sufficient separation (typically 20-50m indoors with walls).

---

### Test Case 8.2.3: Degradation time-to-detect

**Objective**: Confirm degradation is detected within the 10-second window after RSSI crosses the threshold.

**Procedure**:
1. Place nodes at a distance where RSSI oscillates between -70 and -82 dBm.
2. Note the timestamp `t0` of the first RSSI reading below -78 dBm.
3. Monitor serial output for a degradation log event (`[ROUTE_DEGRADE]`, `[DEGRADATION]`, or equivalent).
4. Record the timestamp `t1` of the degradation event.
5. Calculate detection time = `t1 - t0`.

**Acceptance**: Detection time <= 10 seconds from the first sustained sub-threshold reading.

**Note**: The current ESP32 firmware (STEP038 baseline) may not include a dedicated degradation state machine. If no `[DEGRADATION]` log exists, this test verifies that the RSSI reporting infrastructure works and degradation detection logic would have the correct inputs when implemented.

---

### Test Case 8.2.4: Message delivery during degradation

**Objective**: Confirm that messages still deliver during degradation state.

**Procedure**:
1. Place nodes in degradation condition (RSSI < -78 dBm).
2. From Phone A, send a test message to nodeA2 via the Chat tab.
3. Verify Phone B receives the message.
4. Record the delivery latency and any retry/relay behavior.
5. Repeat for the reverse direction (Phone B -> nodeA1).

**Expected**: Messages still deliver via local LoRa MANET even at degraded RSSI, possibly with higher retry counts or longer latency. Messages should not be lost.

**Acceptance**: At least 3 of 5 messages delivered successfully in each direction.

---

### Test Case 8.2.5: Degradation recovery threshold

**Objective**: Verify RSSI returns above -78 dBm when attenuation is removed.

**Procedure**:
1. From the degraded state (RSSI < -78 dBm), return nodes to close range (1m).
2. Monitor RSSI recovery in serial output.
3. Record the timestamp when RSSI returns above -78 dBm and stays there.

**Expected**: RSSI returns to -30 to -55 dBm range within 1-2 seconds of proximity restoration. Serial logs show RSSI values climbing back above -78 dBm.

---

### Test Case 8.2.6: Repeated ACK failure degradation

**Objective**: Verify that repeated ACK failure is an alternative degradation trigger.

**Procedure**:
1. Place nodes at a distance where RSSI is marginal (e.g., -75 to -80 dBm).
2. Send 10 rapid-fire test messages from Phone A to nodeA2.
3. Monitor serial output for ACK timeouts or delivery failures.
4. Record the number of ACK failures.

**Expected**: ACK failure count should increase when RSSI is near threshold. The system should log ACK failures (`[ACK_TIMEOUT]` or equivalent).

**Acceptance**: ACK failure events are observable in serial logs. Degradation from ACK failure alone is a future implementation item.

---

## 5. Pass/Fail Criteria

| Criterion | Threshold | Status |
|---|---|---|
| Baseline RSSI measurable | -30 to -55 dBm at 1m | Required |
| RSSI drops below -78 dBm with attenuation | Achievable in test environment | Required |
| Degradation detection within 10 seconds | <= 10s from first sustained sub-threshold reading | Required |
| Messages deliver during degradation | >= 3 of 5 per direction | Required |
| RSSI recovers above -78 dBm when attenuation removed | Within 1-2 seconds | Required |
| ACK failure logging observable | At least 1 event at marginal RSSI | Optional (future) |
| No spurious neighbor entries during degradation | No UNKNOWN/BROADCAST/malformed nodes | Required (STEP044) |
| No regression in compact BT1 validation | Corrupt packets still rejected | Required (STEP044) |

---

## 6. Evidence Collection Procedure

### For each test case, collect:

1. **Serial monitor screenshots** showing:
   - RSSI values over time
   - Degradation detection log (if implemented)
   - Message delivery/ACK logs
   
2. **Timestamps** for:
   - First sub-threshold RSSI reading (t0)
   - Degradation detection event (t1)
   - Recovery above threshold (t2)

3. **Android screenshots** showing:
   - NODE_LIST before, during, and after degradation
   - Chat messages sent/received during degradation
   - Bridge ACK status for each message

4. **Physical setup photos** showing:
   - Node placement and distance
   - Any obstacles introduced

### File naming convention

```
test_8_2/<case_id>_<description>/<timestamp>_<content>.png
test_8_2/<case_id>_<description>/serial_<node>_<timestamp>.txt
```

---

## 7. Log Collection Procedure

### ESP32 serial logs to capture

```
[LORA_RX]        -- LoRa receive events with RSSI/SNR
[LORA_TX]        -- LoRa transmit events
[HEARTBEAT]      -- HELLO broadcast events (STEP045A)
[BT_RX]          -- Bluetooth receive from phone
[BT_TX]          -- Bluetooth transmit to phone
[ROUTE_DECISION] -- Routing decisions
[ACK]            -- ACK events (STEP046B)
[NEIGHBOR_*]     -- Neighbor table changes (STEP042A)
[NODE_LIST_EXPORT] -- Node list export (STEP042A)
```

### RPi gateway logs to capture (if gateways are active)

```
[GATEWAY_START]
[PEER_STATUS]    -- Tailscale connection state
[TCP_RX] / [TCP_TX] -- Gateway relay traffic
[SERIAL_RX] / [SERIAL_TX] -- ESP32 serial bridge
[STORE_FORWARD_*] -- Queue events (STEP042D)
```

### Log capture method

```bash
# On macOS terminal, save serial output:
screen -L -Logfile serial_nodeA1.log /dev/tty.usbserial-* 115200

# Or use ESP32 serial monitor:
pio device monitor -b 115200 --filter direct | tee serial_nodeA1.log
```

---

## 8. Regression Checks

Before running degradation tests, verify the following still pass:

| Check | How to verify |
|---|---|
| Android build | `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug` |
| ESP32 builds | `pio run -e gatewayA_lora -e gatewayB_lora -e nodeA1_lora -e nodeA2_lora` |
| Node discovery | Android Nodes shows nodeA1, nodeA2, gatewayA, gatewayB ONLINE |
| Messaging | Phone A -> nodeA2 and nodeA2 -> Phone A both deliver |
| Bridge ACK | Chat messages show `Pending` then `Delivered to <nodeId>`, no raw JSON |
| Bluetooth boundary | gateway ESP32 devices NOT in Android Bluetooth scan |
| Store-and-forward tests | `cd rpi-gateway && python -m unittest discover -s tests -p 'test_*.py'` 105/105 |
| No ghost neighbors | `NEIGHBORS` command shows only valid nodeA1/nodeA2/gatewayA/gatewayB |

---

## 9. Expected Outcomes

### If degradation detection is NOT yet implemented in firmware

The current Stable STEP038 baseline ESP32 firmware may not include a dedicated degradation state machine. If so, this test campaign serves as a **baseline data collection exercise**:

1. **RSSI trend data is recorded** -- provides real-world attenuation curves for future threshold calibration
2. **Message delivery at low RSSI is characterized** -- proves the LoRa link can survive marginal conditions
3. **Gaps are identified** -- documents exactly what firmware changes are needed before a full degradation detection test can pass
4. **Evidence is ready** -- when degradation detection firmware is implemented, this test plan provides the procedures and acceptance criteria

### If degradation detection IS implemented

1. `[DEGRADATION]` or equivalent log appears within 10 seconds of sustained RSSI < -78 dBm
2. Route state changes from PRIMARY_LORA to DEGRADED
3. Messages continue to deliver during degradation
4. Recovery is detected when RSSI returns above threshold
5. No regression in any prior validated behavior

---

## Next Steps After Test

1. Collect all evidence per Section 6
2. Update workbook Test Campaign sheet with results
3. Create task log: `docs/codex-task-logs/TEST_CAMPAIGN_8_2_DEGRADATION_RESULTS.md`
4. If degradation detection firmware is needed, define and implement STEP 048 or equivalent
5. Proceed to Test Campaign 8.3 (Failover Test) only after 8.2 passes
