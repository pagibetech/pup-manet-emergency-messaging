# STEP 032 - Android Bluetooth Pairing Validation

Date: 2026-05-13

## Workbook Step

Detailed Steps row `7.2` - Bluetooth Pairing.

## Scope

This step records existing physical demo evidence for Android-to-ESP32 Bluetooth pairing and live send behavior. No new Android code was changed.

## Evidence Recorded

- Android phones paired with `PUP-MANET-NODE_A` and `PUP-MANET-NODE_B`.
- User tested the Android demo APK with both ESP32 LoRa nodes.
- Terminal logs and screenshots showed Bluetooth messages entering ESP32 and crossing LoRa during the Step 023 demo.
- Related task log: `docs/codex-task-logs/ANDROID_DEMO_APK_MESSAGE_FLOW.md`.

## Validation

Existing validation evidence:

- Phone B sent a typed message through `PUP-MANET-NODE_B`.
- LoRa carried the message to `PUP-MANET-NODE_A`.
- Phone A displayed the readable incoming message.

## Notes

- No new hardware retest was requested for this documentation update.
- Android Bluetooth implementation and demo APK remain preserved.
