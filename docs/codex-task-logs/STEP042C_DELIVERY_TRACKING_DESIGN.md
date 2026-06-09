# STEP042C - Delivery Tracking Design Review

Date: 2026-06-08

Status: DESIGN ONLY / NO CODE CHANGES

## Scope

STEP042C adds end-to-end message state tracking after the STEP046B/STEP046C Bridge ACK work.

This design does not implement code. It defines the expected Android, Bluetooth, ESP32, and LoRa behavior before implementation.

STEP042C must preserve:

- LoRa as the MANET backbone.
- Bluetooth as Android phone-to-local ESP32 access only.
- Compact `BT1` LoRa relay format.
- STEP044 strict corrupt-packet validation.
- STEP045A mesh-wide discovery.
- STEP045B live discovered-node destination selection.
- STEP046B destination-node delivery ACK authority.
- STEP046C user-friendly Bridge ACK UI with no raw ACK JSON shown to end users.

STEP042D Store-and-Forward remains out of scope.

## 1. Message State Machine

### States

- `MESSAGE`: Android sender created and sent a message into the local ESP32 bridge. This means the message has a canonical message ID and has entered the MANET path.
- `DELIVERED`: final destination node accepted the message for local delivery and returned a correlated destination delivery ACK.
- `SEEN`: recipient Android app displayed/read the message and returned a correlated seen receipt.
- `FAILED`: explicit correlated failure was received, or the local Bluetooth send failed before the message entered the MANET path.
- `UNKNOWN`: no correlated final state was received within the allowed wait window, or the sender cannot prove final state.

### Transition Rules

Allowed transitions:

- `MESSAGE -> DELIVERED`
- `MESSAGE -> UNKNOWN`
- `MESSAGE -> FAILED`
- `DELIVERED -> SEEN`
- `UNKNOWN -> DELIVERED`
- `UNKNOWN -> SEEN`
- `FAILED -> MESSAGE` only through a new manual retry/new message ID

Disallowed transitions:

- `DELIVERED -> MESSAGE`
- `SEEN -> DELIVERED`
- `SEEN -> UNKNOWN`
- `FAILED -> DELIVERED` for the same failed attempt unless a late correlated receipt is explicitly accepted by a future recovery rule

State precedence:

1. `SEEN`
2. `DELIVERED`
3. `MESSAGE`
4. `UNKNOWN`
5. `FAILED`

`SEEN` is terminal for the original message ID. Duplicate `DELIVERED` or `SEEN` receipts must be idempotent.

Timeout does not imply failure. A timed-out message becomes `UNKNOWN`, matching STEP046B/STEP046C behavior.

## 2. Android UI Changes

### Sender Chat UI

Add a delivery tracking label separate from raw diagnostic data:

- `MESSAGE`: Sent
- `DELIVERED`: Delivered
- `SEEN`: Seen
- `UNKNOWN`: Unknown
- `FAILED`: Failed

The sender message card should continue showing the user-friendly Bridge ACK status from STEP046C, but delivery tracking should present the end-to-end lifecycle state.

Suggested visible sender card fields:

- Message text
- Destination node
- Tracking state
- Last update time
- Route/hop path if already available

Debug-only sender fields:

- `messageId`
- `ackFor`
- `seenFor`
- `receiptId`
- route metadata

### Recipient Chat UI

When Phone B receives and displays the incoming message, Android records `SEEN` for that message ID.

For STEP042C, define `SEEN` as:

> The recipient Android app has rendered the message in the Chat tab or incoming message list.

This is not a cryptographic proof that a human read the text. It is an app-level read/display receipt.

Recipient UI should not show raw tracking packets or raw ACK JSON.

### UI Safety Rule

No user-facing UI may display raw tracking JSON, raw ACK JSON, compact `BT1` lines, or escaped packet payloads.

Raw protocol data may remain in logs/debug panels only.

## 3. Bluetooth Protocol Additions

Bluetooth remains phone-to-local ESP32 access only.

### MESSAGE Additions

Existing `MESSAGE` packets should keep their current format and remain backward compatible.

Add tracking metadata in the payload or supported structured fields:

```json
{
  "trackingVersion": 1,
  "messageId": "<originalMessagePacketId>",
  "state": "MESSAGE",
  "originNode": "nodeA1",
  "finalDestinationNode": "nodeA2",
  "createdAt": 0
}
```

If the metadata is absent, implementations must use the existing `packetId` as `messageId`.

### DELIVERY Receipt

STEP046B already defines delivery ACK behavior. STEP042C should treat this as the `DELIVERED` transition:

```json
{
  "ackVersion": 1,
  "ackType": "DELIVERY",
  "ackFor": "<messageId>",
  "ackStatus": "DELIVERED",
  "originNode": "nodeA1",
  "finalDestinationNode": "nodeA2",
  "ackSource": "nodeA2",
  "route": ["nodeA1", "gatewayA", "nodeA2"]
}
```

### SEEN Receipt

Use an `ACK` packet with a new receipt type rather than a new route mechanism:

```json
{
  "ackVersion": 1,
  "ackType": "SEEN",
  "ackFor": "<messageId>",
  "seenFor": "<messageId>",
  "ackStatus": "SEEN",
  "originNode": "nodeA1",
  "finalDestinationNode": "nodeA2",
  "ackSource": "PHONE_B",
  "seenAt": 0,
  "route": ["nodeA2", "gatewayA", "nodeA1"]
}
```

Reasoning:

- Reuses existing ACK correlation concepts from STEP046B.
- Keeps routing unchanged.
- Lets older receivers ignore unknown `ackType=SEEN` safely.
- Keeps raw payload hidden by STEP046C UI rules.

## 4. ESP32 Firmware Changes

Firmware changes should be minimal and should not alter routing decisions.

Required behavior:

- Preserve existing `MESSAGE` forwarding.
- Preserve destination-node `DELIVERY` ACK generation from STEP046B.
- Accept local Bluetooth `ACK` packets with `ackType=SEEN` from the connected Android phone.
- Relay valid `SEEN` ACK packets over LoRa toward the original source node.
- Deliver incoming `SEEN` ACK packets to local Android over Bluetooth when this node is the ACK destination.
- Maintain duplicate protection for `messageId` and `receiptId`.
- Log tracking events without changing route selection.

Suggested logs:

- `[TRACK_MESSAGE] messageId=<id> state=MESSAGE source=<node> dest=<node>`
- `[TRACK_DELIVERED] messageId=<id> ackSource=<node>`
- `[TRACK_SEEN] messageId=<id> seenBy=<phone-or-node>`
- `[TRACK_DUPLICATE] receiptId=<id>`
- `[TRACK_DROP] reason=<reason>`

Out of scope:

- Store-and-forward queue.
- Automatic retransmission.
- Alternate route selection.
- New Bluetooth node-to-node transport.

## 5. LoRa Packet Changes

Compact `BT1` remains the LoRa packet format.

### MESSAGE Over LoRa

Existing message relay remains valid:

```text
BT1|<messageId>|nodeA1|nodeA2|<payload>|nodeA1>gatewayA>nodeA2|<hopCount>|<ttl>|<previousHop>|<checksum>
```

The payload may include optional tracking metadata. If absent, `packetId` is the canonical `messageId`.

### DELIVERY Over LoRa

Use existing STEP046B delivery ACK:

```text
BT1|ACK-<messageId>-nodeA2-<timestamp>|nodeA2|nodeA1|{"ackVersion":1,"ackType":"DELIVERY","ackFor":"<messageId>","ackStatus":"DELIVERED","originNode":"nodeA1","finalDestinationNode":"nodeA2","ackSource":"nodeA2","route":["nodeA1","gatewayA","nodeA2"]}|nodeA2>gatewayA>nodeA1|<hopCount>|<ttl>|<previousHop>|<checksum>
```

### SEEN Over LoRa

Add `ACK` with `ackType=SEEN`:

```text
BT1|ACK-SEEN-<messageId>-nodeA2-<timestamp>|nodeA2|nodeA1|{"ackVersion":1,"ackType":"SEEN","ackFor":"<messageId>","seenFor":"<messageId>","ackStatus":"SEEN","originNode":"nodeA1","finalDestinationNode":"nodeA2","ackSource":"PHONE_B","seenAt":0,"route":["nodeA2","gatewayA","nodeA1"]}|nodeA2>gatewayA>nodeA1|<hopCount>|<ttl>|<previousHop>|<checksum>
```

The `SEEN` packet should follow normal TTL, hopCount, duplicate suppression, and compact packet validation rules.

## 6. Correlation ID Handling

Canonical ID:

- `messageId = original MESSAGE packetId`

All receipts must include:

- `ackFor=<messageId>`

SEEN receipts additionally include:

- `seenFor=<messageId>`
- `receiptId=ACK-SEEN-<messageId>-<ackSource>-<timestamp>` via packet ID

Correlation rules:

- Android sender updates only the message whose `message.packetId == ackFor`.
- Android recipient sends at most one `SEEN` receipt per `messageId` unless a future manual resend rule is added.
- ESP32 nodes use `packetId` for duplicate suppression and `ackFor` for state correlation.
- Late `DELIVERED` or `SEEN` receipts may update `UNKNOWN` messages if the `ackFor` matches.
- Unmatched receipts are logged as diagnostics and must not update the wrong card.

## 7. Duplicate Protection

Android:

- Keep `autoReceivedPacketIds` or equivalent for received messages.
- Add `seenReceiptSentIds` to prevent multiple `SEEN` receipts for the same message display.
- Add `trackingReceiptIds` to ignore duplicate incoming `DELIVERY` / `SEEN` receipts.
- State updates must be monotonic by precedence.

ESP32:

- Use existing duplicate cache for compact `BT1` packet IDs.
- Treat `ACK-SEEN-*` packet IDs as duplicate-cache participants.
- Never learn neighbors or change routing based on malformed tracking payloads.
- Drop malformed or uncorrelated tracking receipts with `[TRACK_DROP]`.

Duplicate behavior:

- Duplicate `MESSAGE`: do not create a second visible message.
- Duplicate `DELIVERY`: do not change state if already `DELIVERED` or `SEEN`.
- Duplicate `SEEN`: do not change state if already `SEEN`.

## 8. Backward Compatibility With STEP046C

STEP042C must preserve STEP046C UI safety:

- No raw ACK JSON in user-facing UI.
- No raw tracking JSON in user-facing UI.
- User-facing Bridge ACK states remain friendly.

Backward compatibility rules:

- Old messages without tracking metadata use `packetId` as `messageId`.
- Existing STEP046B `DELIVERY` ACK remains authoritative for `DELIVERED`.
- Nodes/apps that do not understand `ackType=SEEN` may ignore it without breaking `DELIVERED`.
- Unknown or unsupported tracking receipts must not display raw JSON.
- Timeout still maps to `UNKNOWN`, not `FAILED`.
- `FAILED` requires explicit failure or local send failure, not missing `SEEN`.

No changes should weaken:

- STEP044 corrupt packet rejection.
- STEP045A HELLO propagation.
- STEP045B live destination selection.
- STEP046B delivery ACK correlation.
- STEP046C UI sanitization.

## 9. Example Packet Flow

Example route:

`Phone A -> nodeA1 -> gatewayA -> nodeA2 -> Phone B`

### A. MESSAGE

Phone A creates:

```json
{
  "packetType": "MESSAGE",
  "packetId": "BT-MESSAGE-1001",
  "sourceNode": "ANDROID_APP",
  "destinationNode": "nodeA2",
  "payload": "MODE=LORA;TEXT=hello;trackingVersion=1;messageId=BT-MESSAGE-1001",
  "status": "MESSAGE"
}
```

Phone A UI:

```text
Tracking: MESSAGE
Bridge ACK: Pending
```

nodeA1 sends compact LoRa:

```text
BT1|BT-MESSAGE-1001|nodeA1|nodeA2|MODE=LORA;TEXT=hello;trackingVersion=1;messageId=BT-MESSAGE-1001|nodeA1>gatewayA|1|5|nodeA1|CHECKSUM_PLACEHOLDER
```

gatewayA forwards:

```text
BT1|BT-MESSAGE-1001|nodeA1|nodeA2|MODE=LORA;TEXT=hello;trackingVersion=1;messageId=BT-MESSAGE-1001|nodeA1>gatewayA>nodeA2|2|4|gatewayA|CHECKSUM_PLACEHOLDER
```

nodeA2 receives and sends to Phone B over Bluetooth.

Phone B UI:

```text
Incoming: hello
Tracking: MESSAGE received
```

### B. DELIVERED

nodeA2 emits delivery ACK:

```text
BT1|ACK-BT-MESSAGE-1001-nodeA2-2001|nodeA2|nodeA1|{"ackVersion":1,"ackType":"DELIVERY","ackFor":"BT-MESSAGE-1001","ackStatus":"DELIVERED","originNode":"nodeA1","finalDestinationNode":"nodeA2","ackSource":"nodeA2","route":["nodeA1","gatewayA","nodeA2"]}|nodeA2>gatewayA>nodeA1|2|4|gatewayA|CHECKSUM_PLACEHOLDER
```

nodeA1 delivers ACK to Phone A over Bluetooth.

Phone A UI:

```text
Tracking: DELIVERED
Bridge ACK: Delivered
```

### C. SEEN

Phone B displays the message in Chat and sends a seen receipt to nodeA2:

```json
{
  "packetType": "ACK",
  "packetId": "ACK-SEEN-BT-MESSAGE-1001-PHONE_B-3001",
  "sourceNode": "PHONE_B",
  "destinationNode": "nodeA1",
  "payload": {
    "ackVersion": 1,
    "ackType": "SEEN",
    "ackFor": "BT-MESSAGE-1001",
    "seenFor": "BT-MESSAGE-1001",
    "ackStatus": "SEEN",
    "originNode": "nodeA1",
    "finalDestinationNode": "nodeA2",
    "ackSource": "PHONE_B",
    "seenAt": 3001,
    "route": ["nodeA2", "gatewayA", "nodeA1"]
  },
  "status": "SEEN"
}
```

nodeA2 relays compact LoRa:

```text
BT1|ACK-SEEN-BT-MESSAGE-1001-PHONE_B-3001|nodeA2|nodeA1|{"ackVersion":1,"ackType":"SEEN","ackFor":"BT-MESSAGE-1001","seenFor":"BT-MESSAGE-1001","ackStatus":"SEEN","originNode":"nodeA1","finalDestinationNode":"nodeA2","ackSource":"PHONE_B","seenAt":3001,"route":["nodeA2","gatewayA","nodeA1"]}|nodeA2>gatewayA>nodeA1|2|4|gatewayA|CHECKSUM_PLACEHOLDER
```

nodeA1 delivers the `SEEN` ACK to Phone A over Bluetooth.

Phone A UI:

```text
Tracking: SEEN
Bridge ACK: Delivered
Seen by Phone B
```

## Acceptance Criteria For Future Implementation

- Sender shows `MESSAGE` immediately after sending.
- Sender shows `DELIVERED` after a correlated delivery ACK.
- Recipient sends one `SEEN` receipt when the message is displayed.
- Sender shows `SEEN` only for the matching `messageId`.
- Timeout without delivery or seen receipt shows `UNKNOWN`, not `FAILED`.
- Explicit send/transport failure shows `FAILED`.
- Duplicate messages and duplicate receipts are idempotent.
- No raw tracking/ACK JSON appears in user-facing UI.
- Existing `nodeA1 -> nodeA2` and `nodeA2 -> nodeA1` messaging remains non-regressed.
- STEP046C Bridge ACK display remains PASS.

## Implementation Guardrails

- Do not start STEP042D Store-and-Forward during STEP042C.
- Do not introduce node-to-node Bluetooth transport.
- Do not change routing decisions.
- Do not weaken compact `BT1` validation.
- Keep first implementation narrow: Android + ESP32 receipt handling only, with existing LoRa routing.
