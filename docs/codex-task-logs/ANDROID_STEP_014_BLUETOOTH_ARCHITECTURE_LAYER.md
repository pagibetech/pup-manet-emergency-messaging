# ANDROID STEP 014 - Bluetooth Architecture Layer Preparation

## What Was Implemented

- Added platform-free Bluetooth architecture models:
  - `BluetoothTransportManager`
  - `BluetoothDeviceState`
  - `BluetoothPacketBridge`
  - `BluetoothConnectionSession`
- Added Bluetooth lifecycle states:
  - Idle
  - Scanning
  - Pairing
  - Connecting
  - Connected
  - Disconnected
  - Failed
  - Retrying
- Expanded the Sim tab Bluetooth panel into a simulated lifecycle workflow:
  - Scan for fake ESP32 devices
  - Select a device
  - Pair with the selected device
  - Connect
  - Exchange simulated HELLO / ESP32_ACK packets
  - Disconnect
  - Simulate timeout and reconnect
- Added Bluetooth diagnostics:
  - Current paired ESP32
  - Signal placeholder
  - Packet counters
  - Last reconnect attempt
  - Connection uptime
  - Retry counter
  - Reconnect countdown
  - Timeout status

## Architecture Note

Future hardware integration should follow this layer order:

```text
Android Bluetooth API
-> BluetoothTransportManager
-> BluetoothPacketBridge
-> MANET routing engine
```

The current implementation intentionally stops before Android platform Bluetooth APIs.

## Simulation-Only Behavior

- No Android Bluetooth permissions were added.
- No BLE APIs were added.
- No Classic Bluetooth APIs were added.
- No socket code was added.
- No ESP32 code was modified.
- No Raspberry Pi code was modified.
- Existing message simulation, packet abstraction, routing engine, queue engine, transport placeholders, and failover behavior were preserved.

## Files Updated

- `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`
- `android-chat-app/README.md`
- `docs/codex-task-logs/ANDROID_STEP_014_BLUETOOTH_ARCHITECTURE_LAYER.md`

## Validation Notes

- The Bluetooth workflow remains local and simulated.
- Bluetooth remains the official Android-to-ESP32 path for future work.
- The field architecture remains Android Phone to ESP32 over Bluetooth, then ESP32 to other ESP32 nodes over LoRa.

## Next Step

Continue with the next workbook-defined Android task. Real Bluetooth permissions, BLE, Classic Bluetooth, socket code, or ESP32 firmware integration should only be added when the workbook explicitly requests hardware communication.
