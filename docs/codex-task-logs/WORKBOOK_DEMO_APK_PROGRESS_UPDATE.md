# Workbook Demo APK Progress Update

Date: 2026-05-13

## Workbook Step

STEP 023 DEMO APK - Android to ESP32 Bluetooth to LoRa to ESP32 Bluetooth to Android demo readiness.

## What Changed

- Updated `docs/workbook/PUP_MANET_Implementation_Workbook.xlsx`.
- Added a new `Progress Tracker` entry for the physical typed-message demo.
- Updated the `Detailed Steps` row for `STEP 023` with demo APK evidence and the confirmed phone-to-phone result.
- Updated the `Dashboard` `User App` row to `Demo APK Ready`.

## Confirmed Demo Result

- Phone B sent a typed message through `PUP-MANET-NODE_B`.
- `NODE_B` forwarded over LoRa to `NODE_A`.
- Phone A received and displayed the readable incoming message.

## Scope Notes

- The current demo is addressed node-to-node messaging.
- Broadcast messaging is not implemented yet.
- GSM network behavior is not implemented yet and remains future workbook scope.

## Validation

- Workbook reopened successfully after update.
- Android demo APK evidence was recorded in the workbook.
