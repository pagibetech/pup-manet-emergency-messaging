# STEP046C - Bridge ACK UI Sanitization

Date: 2026-06-08

Status: IMPLEMENTED / Android Build PASS / Physical UI Retest Pending

## Context

STEP046B hardware validation completed.

PASS:

- `nodeA1 -> nodeA2` Delivered.
- `nodeA2 -> nodeA1` Delivered.
- Timeout condition detected.
- Failed delivery shown correctly.
- Message was not incorrectly marked Delivered.

FAIL:

- Failed card still displayed raw Bridge ACK JSON.
- Acceptance criteria require user-friendly Bridge ACK states and never raw ACK JSON for end users.

## Scope

Android UI only.

No firmware, routing, route discovery, Bluetooth architecture, Raspberry Pi gateway service, or compact `BT1` protocol changes.

## Changes

- Updated `android-chat-app/app/src/main/java/ph/edu/pup/manetmessenger/MainActivity.kt`.
- Added user-facing Bridge ACK display sanitization.
- Message cards now render Bridge ACK states as:
  - `Bridge ACK: Delivered`
  - `Bridge ACK: Pending`
  - `Bridge ACK: Unknown`
  - `Bridge ACK: Failed`
- Failed Bridge ACK cards suppress raw diagnostic note text.
- Status rows and protocol payload display sanitize raw ACK JSON into the same user-facing states.
- Raw ACK JSON remains available to parsing/log/debug paths only.

## Validation

- Android build: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug` - PASS.

## Pending Physical Retest

Retest Android failed-delivery UI and confirm:

- Failed card shows `Bridge ACK: Failed`.
- No raw Bridge ACK JSON appears in user-facing cards/status sections.
- Delivered, Pending, and Unknown Bridge ACK states remain user-friendly.
