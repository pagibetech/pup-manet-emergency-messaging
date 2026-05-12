# Android Demo APK Message Flow

## Scope

Demo-usability update for the already validated Step 023 path:

```text
Android Phone A -> Bluetooth -> NODE_A -> LoRa -> NODE_B -> Bluetooth -> Android Phone B
```

This does not add a new transport or skip ahead in the workbook. It makes the current working path easier to demonstrate to a client.

## What Was Implemented

- Added a **Message to send** field in the Real Bluetooth Socket panel.
- Changed the demo send button to **Send Msg**.
- Sent the typed message as a `BT-MANET-1.0` `MESSAGE` payload:
  - `MODE=LORA;TEXT=<message>`
- Added a readable received status on Phone B:
  - `Incoming: <message>`
- Updated **Check In** to wait up to 5 seconds for the incoming Bluetooth line instead of checking only once.
- Kept the raw protocol packet in **Last received** for technical evidence.

## Scope Preserved

- No ESP32 firmware changes.
- No Raspberry Pi gateway changes.
- No continuous Android background socket listener.
- No broadcast mode.
- No GSM implementation.
- No Play Store/release signing setup.

## Validation

Validation commands:

```text
./gradlew :app:assembleDebug
```

Result:

- `BUILD SUCCESSFUL`

Demo APK created:

```text
android-chat-app/demo-apk/PUP-MANET-Messenger-Step023-Demo-debug.apk
```

APK size:

- `9.5M`

Current APK SHA-256:

```text
93631c38921f4804feefa348403bd8027a65d0426cc679b23f911137642c7306
```

## Demo Procedure

1. Install the generated APK on both Android phones.
2. Pair Phone A with `PUP-MANET-NODE_A`.
3. Pair Phone B with `PUP-MANET-NODE_B`.
4. On both phones, open the app and go to the Sim tab.
5. On both phones, press **Load Paired**, select the matching ESP32, then press **Connect ESP32**.
6. On Phone A, type a short message and press **Send Msg**.
7. On Phone B, press **Check In**.

Expected:

- Phone A shows `Sent to NODE_B: <message>`.
- Phone B shows `Incoming: <message>`.
