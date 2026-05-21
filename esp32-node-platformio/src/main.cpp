#include <Arduino.h>
#include <BluetoothSerial.h>
#if ENABLE_LORA
#include <SPI.h>
#include <LoRa.h>
#endif
#include <ctype.h>

#ifndef SIM_NODE_ID
#define SIM_NODE_ID "NODE_A"
#endif

#ifndef DEFAULT_DEST_ID
#define DEFAULT_DEST_ID "NODE_B"
#endif

#ifndef LORA_SS_PIN
#define LORA_SS_PIN 5
#endif

#ifndef LORA_RST_PIN
#define LORA_RST_PIN 14
#endif

#ifndef LORA_DIO0_PIN
#define LORA_DIO0_PIN 26
#endif

#ifndef LORA_SCK_PIN
#define LORA_SCK_PIN 18
#endif

#ifndef LORA_MISO_PIN
#define LORA_MISO_PIN 19
#endif

#ifndef LORA_MOSI_PIN
#define LORA_MOSI_PIN 23
#endif

#ifndef LORA_FREQUENCY
#define LORA_FREQUENCY 433E6
#endif

#ifndef LORA_SYNC_WORD
#define LORA_SYNC_WORD 0x12
#endif

#ifndef LORA_TX_POWER
#define LORA_TX_POWER 17
#endif

const String PROTOCOL_VERSION = "BT-MANET-1.0";
const String CHECKSUM_PLACEHOLDER = "checksum pending / simulated";
const String BLUETOOTH_SERVICE_PREFIX = "PUP-MANET-";
const String LORA_RELAY_PREFIX = "BT1";
const char *SUPPORTED_PACKET_TYPES[] = {
  "HELLO",
  "ACK",
  "MESSAGE",
  "ROUTE_DISCOVERY",
  "ROUTE_REPLY",
  "STATUS",
  "ERROR"
};
constexpr size_t SUPPORTED_PACKET_TYPE_COUNT = sizeof(SUPPORTED_PACKET_TYPES) / sizeof(SUPPORTED_PACKET_TYPES[0]);
const char *SUPPORTED_MANUAL_MODES[] = {
  "AUTO",
  "LORA",
  "WIFI",
  "GSM"
};
constexpr size_t SUPPORTED_MANUAL_MODE_COUNT = sizeof(SUPPORTED_MANUAL_MODES) / sizeof(SUPPORTED_MANUAL_MODES[0]);

struct SimMessage {
  String msgId;
  String src;
  String dest;
  int hop;
  String payload;
  unsigned long timestamp;
};

struct Neighbor {
  String nodeId;
  int rssi;
  unsigned long lastSeen;
  bool online;
};

struct ProtocolPacket {
  String protocolVersion;
  String packetType;
  String packetId;
  String sourceNode;
  String destinationNode;
  String payload;
  String hopPath;
  int retryCount;
  unsigned long timestamp;
  String status;
  String checksum;
};

struct PacketParseResult {
  bool valid;
  String errorReason;
  ProtocolPacket packet;
};

constexpr int MAX_HOP_COUNT = 5;
constexpr size_t DUPLICATE_CACHE_SIZE = 32;
constexpr size_t NEIGHBOR_COUNT = 2;

String seenMessageIds[DUPLICATE_CACHE_SIZE];
Neighbor neighbors[NEIGHBOR_COUNT];
size_t seenMessageIndex = 0;
unsigned long outboundCounter = 0;
String serialBuffer;
String bluetoothBuffer;
bool localNodeOnline = true;
bool bluetoothServiceStarted = false;
bool loraReady = false;
unsigned long loraTxCounter = 0;
unsigned long loraRxCounter = 0;
BluetoothSerial SerialBT;

unsigned long simulationTimestamp() {
  return millis() / 1000UL;
}

String jsonEscape(const String &value) {
  String escaped;
  escaped.reserve(value.length() + 8);

  for (size_t i = 0; i < value.length(); ++i) {
    const char c = value.charAt(i);
    if (c == '\\' || c == '"') {
      escaped += '\\';
    }
    escaped += c;
  }

  return escaped;
}

String toUpperCopy(String value) {
  value.toUpperCase();
  return value;
}

String bluetoothServiceName() {
  return BLUETOOTH_SERVICE_PREFIX + String(SIM_NODE_ID);
}

String extractJsonString(const String &json, const String &key) {
  const String marker = "\"" + key + "\"";
  int keyIndex = json.indexOf(marker);
  if (keyIndex < 0) {
    return "";
  }

  int colonIndex = json.indexOf(':', keyIndex + marker.length());
  if (colonIndex < 0) {
    return "";
  }

  int valueStart = json.indexOf('"', colonIndex + 1);
  if (valueStart < 0) {
    return "";
  }

  String value;
  bool escaped = false;
  for (int i = valueStart + 1; i < json.length(); ++i) {
    const char c = json.charAt(i);
    if (escaped) {
      value += c;
      escaped = false;
      continue;
    }
    if (c == '\\') {
      escaped = true;
      continue;
    }
    if (c == '"') {
      return value;
    }
    value += c;
  }

  return "";
}

long extractJsonInteger(const String &json, const String &key, long fallback) {
  const String marker = "\"" + key + "\"";
  int keyIndex = json.indexOf(marker);
  if (keyIndex < 0) {
    return fallback;
  }

  int colonIndex = json.indexOf(':', keyIndex + marker.length());
  if (colonIndex < 0) {
    return fallback;
  }

  int valueStart = colonIndex + 1;
  while (valueStart < json.length() && isspace(static_cast<unsigned char>(json.charAt(valueStart)))) {
    ++valueStart;
  }

  bool quoted = false;
  if (valueStart < json.length() && json.charAt(valueStart) == '"') {
    quoted = true;
    ++valueStart;
  }

  int valueEnd = valueStart;
  while (valueEnd < json.length()) {
    const char c = json.charAt(valueEnd);
    if ((quoted && c == '"') || (!quoted && (c == ',' || c == '}'))) {
      break;
    }
    ++valueEnd;
  }

  return json.substring(valueStart, valueEnd).toInt();
}

bool hasJsonField(const String &json, const String &key) {
  const String marker = "\"" + key + "\"";
  const int keyIndex = json.indexOf(marker);
  if (keyIndex < 0) {
    return false;
  }
  return json.indexOf(':', keyIndex + marker.length()) >= 0;
}

bool parseMessage(const String &line, SimMessage &message) {
  message.msgId = extractJsonString(line, "msg_id");
  message.src = extractJsonString(line, "src");
  message.dest = extractJsonString(line, "dest");
  message.hop = static_cast<int>(extractJsonInteger(line, "hop", 0));
  message.payload = extractJsonString(line, "payload");
  message.timestamp = static_cast<unsigned long>(extractJsonInteger(line, "timestamp", 0));

  return message.msgId.length() > 0 &&
         message.src.length() > 0 &&
         message.dest.length() > 0;
}

bool isSupportedPacketType(const String &packetType) {
  for (size_t i = 0; i < SUPPORTED_PACKET_TYPE_COUNT; ++i) {
    if (packetType == SUPPORTED_PACKET_TYPES[i]) {
      return true;
    }
  }
  return false;
}

bool isSupportedManualMode(const String &mode) {
  for (size_t i = 0; i < SUPPORTED_MANUAL_MODE_COUNT; ++i) {
    if (mode == SUPPORTED_MANUAL_MODES[i]) {
      return true;
    }
  }
  return false;
}

String extractManualMode(const String &payload) {
  const String marker = "MODE=";
  const int markerIndex = payload.indexOf(marker);
  if (markerIndex < 0) {
    return "AUTO";
  }

  int valueStart = markerIndex + marker.length();
  int valueEnd = valueStart;
  while (valueEnd < payload.length()) {
    const char c = payload.charAt(valueEnd);
    if (c == ';' || c == ',' || isspace(static_cast<unsigned char>(c))) {
      break;
    }
    ++valueEnd;
  }

  return toUpperCopy(payload.substring(valueStart, valueEnd));
}

ProtocolPacket emptyProtocolPacket() {
  ProtocolPacket packet;
  packet.protocolVersion = "";
  packet.packetType = "";
  packet.packetId = "";
  packet.sourceNode = "";
  packet.destinationNode = "";
  packet.payload = "";
  packet.hopPath = "";
  packet.retryCount = 0;
  packet.timestamp = 0;
  packet.status = "";
  packet.checksum = "";
  return packet;
}

PacketParseResult parseProtocolPacket(const String &line) {
  PacketParseResult result;
  result.valid = false;
  result.errorReason = "";
  result.packet = emptyProtocolPacket();

  result.packet.protocolVersion = extractJsonString(line, "protocolVersion");
  result.packet.packetType = extractJsonString(line, "packetType");
  result.packet.packetId = extractJsonString(line, "packetId");
  result.packet.sourceNode = extractJsonString(line, "sourceNode");
  result.packet.destinationNode = extractJsonString(line, "destinationNode");
  result.packet.payload = extractJsonString(line, "payload");
  result.packet.hopPath = extractJsonString(line, "hopPath");
  result.packet.retryCount = static_cast<int>(extractJsonInteger(line, "retryCount", 0));
  result.packet.timestamp = static_cast<unsigned long>(extractJsonInteger(line, "timestamp", 0));
  result.packet.status = extractJsonString(line, "status");
  result.packet.checksum = extractJsonString(line, "checksum");

  if (!hasJsonField(line, "protocolVersion") ||
      !hasJsonField(line, "packetType") ||
      !hasJsonField(line, "packetId") ||
      !hasJsonField(line, "sourceNode") ||
      !hasJsonField(line, "destinationNode") ||
      !hasJsonField(line, "payload") ||
      !hasJsonField(line, "hopPath") ||
      !hasJsonField(line, "retryCount") ||
      !hasJsonField(line, "timestamp") ||
      !hasJsonField(line, "status") ||
      !hasJsonField(line, "checksum") ||
      result.packet.protocolVersion.length() == 0 ||
      result.packet.packetType.length() == 0 ||
      result.packet.packetId.length() == 0 ||
      result.packet.sourceNode.length() == 0 ||
      result.packet.destinationNode.length() == 0 ||
      result.packet.status.length() == 0 ||
      result.packet.checksum.length() == 0) {
    result.errorReason = "missing required field";
    return result;
  }

  if (result.packet.protocolVersion != PROTOCOL_VERSION) {
    result.errorReason = "unsupported protocol version";
    return result;
  }

  if (!isSupportedPacketType(result.packet.packetType)) {
    result.errorReason = "unsupported packet type";
    return result;
  }

  if (result.packet.checksum != CHECKSUM_PLACEHOLDER) {
    result.errorReason = "checksum placeholder invalid";
    return result;
  }

  result.valid = true;
  result.errorReason = "valid";
  return result;
}

String serializeMessage(const SimMessage &message) {
  String json = "{";
  json += "\"msg_id\":\"" + jsonEscape(message.msgId) + "\",";
  json += "\"src\":\"" + jsonEscape(message.src) + "\",";
  json += "\"dest\":\"" + jsonEscape(message.dest) + "\",";
  json += "\"hop\":" + String(message.hop) + ",";
  json += "\"payload\":\"" + jsonEscape(message.payload) + "\",";
  json += "\"timestamp\":\"" + String(message.timestamp) + "\"";
  json += "}";
  return json;
}

String serializeProtocolPacket(const ProtocolPacket &packet) {
  String json = "{";
  json += "\"protocolVersion\":\"" + jsonEscape(packet.protocolVersion) + "\",";
  json += "\"packetType\":\"" + jsonEscape(packet.packetType) + "\",";
  json += "\"packetId\":\"" + jsonEscape(packet.packetId) + "\",";
  json += "\"sourceNode\":\"" + jsonEscape(packet.sourceNode) + "\",";
  json += "\"destinationNode\":\"" + jsonEscape(packet.destinationNode) + "\",";
  json += "\"payload\":\"" + jsonEscape(packet.payload) + "\",";
  json += "\"hopPath\":\"" + jsonEscape(packet.hopPath) + "\",";
  json += "\"retryCount\":\"" + String(packet.retryCount) + "\",";
  json += "\"timestamp\":\"" + String(packet.timestamp) + "\",";
  json += "\"status\":\"" + jsonEscape(packet.status) + "\",";
  json += "\"checksum\":\"" + jsonEscape(packet.checksum) + "\"";
  json += "}";
  return json;
}

bool hasSeenMessage(const String &msgId) {
  for (size_t i = 0; i < DUPLICATE_CACHE_SIZE; ++i) {
    if (seenMessageIds[i] == msgId) {
      return true;
    }
  }
  return false;
}

size_t duplicateCacheCount() {
  size_t count = 0;
  for (size_t i = 0; i < DUPLICATE_CACHE_SIZE; ++i) {
    if (seenMessageIds[i].length() > 0) {
      ++count;
    }
  }
  return count;
}

void rememberMessage(const String &msgId) {
  seenMessageIds[seenMessageIndex] = msgId;
  seenMessageIndex = (seenMessageIndex + 1) % DUPLICATE_CACHE_SIZE;
}

Neighbor *findNeighbor(const String &nodeId) {
  for (size_t i = 0; i < NEIGHBOR_COUNT; ++i) {
    if (neighbors[i].nodeId == nodeId) {
      return &neighbors[i];
    }
  }
  return nullptr;
}

void setNeighborState(const String &nodeId, bool online) {
  Neighbor *neighbor = findNeighbor(nodeId);
  if (neighbor == nullptr) {
    Serial.print("[ERROR] Unknown simulated neighbor: ");
    Serial.println(nodeId);
    return;
  }

  neighbor->online = online;
  neighbor->lastSeen = online ? simulationTimestamp() : neighbor->lastSeen;
  Serial.print("[NEIGHBOR] ");
  Serial.print(nodeId);
  Serial.println(online ? " ONLINE" : " OFFLINE");
}

void initNeighbors() {
  const unsigned long now = simulationTimestamp();
  const String nodeId = SIM_NODE_ID;

  if (nodeId == "NODE_A") {
    neighbors[0] = Neighbor{"NODE_B", -55, now, true};
    neighbors[1] = Neighbor{"NODE_C", -72, now, true};
  } else if (nodeId == "NODE_B") {
    neighbors[0] = Neighbor{"NODE_A", -58, now, true};
    neighbors[1] = Neighbor{"NODE_C", -49, now, true};
  } else {
    neighbors[0] = Neighbor{"NODE_B", -52, now, true};
    neighbors[1] = Neighbor{"NODE_A", -76, now, true};
  }
}

Neighbor *selectBestNeighbor() {
  Neighbor *best = nullptr;
  for (size_t i = 0; i < NEIGHBOR_COUNT; ++i) {
    if (!neighbors[i].online) {
      continue;
    }
    if (best == nullptr || neighbors[i].rssi > best->rssi) {
      best = &neighbors[i];
    }
  }
  return best;
}

SimMessage createOutboundMessage(const String &dest, const String &payload) {
  ++outboundCounter;

  SimMessage message;
  message.msgId = String(SIM_NODE_ID) + "-" + String(millis()) + "-" + String(outboundCounter);
  message.src = SIM_NODE_ID;
  message.dest = dest;
  message.hop = 0;
  message.payload = payload;
  message.timestamp = simulationTimestamp();
  return message;
}

void logMessage(const String &label, const SimMessage &message) {
  Serial.print("[");
  Serial.print(label);
  Serial.print("] ");
  Serial.println(serializeMessage(message));
}

void dropMessage(const String &reason, const SimMessage &message) {
  Serial.print("[DROPPED] reason=");
  Serial.print(reason);
  Serial.print(" msg_id=");
  Serial.print(message.msgId);
  Serial.print(" src=");
  Serial.print(message.src);
  Serial.print(" dest=");
  Serial.print(message.dest);
  Serial.print(" hop=");
  Serial.println(message.hop);
}

void routeMessage(SimMessage message) {
  if (!localNodeOnline) {
    dropMessage("local node offline", message);
    return;
  }

  if (message.dest == SIM_NODE_ID) {
    logMessage("DELIVERED", message);
    Serial.print("[DELIVERY] msg_id=");
    Serial.print(message.msgId);
    Serial.print(" delivered_to=");
    Serial.println(SIM_NODE_ID);
    return;
  }

  if (message.hop >= MAX_HOP_COUNT) {
    dropMessage("max hop count reached", message);
    return;
  }

  Neighbor *nextHop = selectBestNeighbor();
  if (nextHop == nullptr) {
    dropMessage("no online neighbors", message);
    return;
  }

  message.hop += 1;
  nextHop->lastSeen = simulationTimestamp();

  Serial.print("[ROUTE] msg_id=");
  Serial.print(message.msgId);
  Serial.print(" dest=");
  Serial.print(message.dest);
  Serial.print(" next_hop=");
  Serial.print(nextHop->nodeId);
  Serial.print(" rssi=");
  Serial.print(nextHop->rssi);
  Serial.print(" path=");
  Serial.print(SIM_NODE_ID);
  Serial.print("->");
  Serial.println(nextHop->nodeId);

  logMessage("FORWARDED", message);
  Serial.println(serializeMessage(message));
}

bool sendLoRaLine(const String &line) {
#if ENABLE_LORA
  if (!loraReady) {
    Serial.println("[LORA_ERROR] LoRa radio is not ready.");
    return false;
  }

  LoRa.beginPacket();
  LoRa.print(line);
  const int result = LoRa.endPacket();
  if (result == 1) {
    ++loraTxCounter;
    Serial.print("[LORA_TX] ");
    Serial.println(line);
    return true;
  }

  Serial.println("[LORA_ERROR] LoRa packet transmit failed.");
  return false;
#else
  (void)line;
  Serial.println("[LORA_DISABLED] Build with node_a_lora or node_b_lora to enable SX1278 live test.");
  return false;
#endif
}

void processIncomingMessage(const String &line) {
  SimMessage message;
  if (!parseMessage(line, message)) {
    Serial.println("[ERROR] Invalid simulation JSON. Expected msg_id, src, dest, hop, payload, timestamp.");
    return;
  }

  logMessage("RECEIVED", message);

  if (hasSeenMessage(message.msgId)) {
    dropMessage("duplicate msg_id", message);
    return;
  }

  rememberMessage(message.msgId);
  routeMessage(message);
}

bool protocolPacketTargetsLocalNode(const ProtocolPacket &packet) {
  return packet.destinationNode == String(SIM_NODE_ID) ||
         packet.destinationNode == "ESP32_BRIDGE" ||
         packet.destinationNode == "ANDROID_APP";
}

String sanitizeLoRaRelayField(String value) {
  value.replace("|", "/");
  value.replace("\r", " ");
  value.replace("\n", " ");
  return value;
}

String loRaRelayFieldAt(const String &line, int fieldIndex) {
  int currentField = 0;
  int fieldStart = 0;

  for (int i = 0; i <= line.length(); ++i) {
    if (i == line.length() || line.charAt(i) == '|') {
      if (currentField == fieldIndex) {
        return line.substring(fieldStart, i);
      }
      currentField += 1;
      fieldStart = i + 1;
    }
  }

  return "";
}

String serializeLoRaRelayPacket(const ProtocolPacket &packet) {
  String relay = LORA_RELAY_PREFIX;
  relay += "|" + sanitizeLoRaRelayField(packet.packetId);
  relay += "|" + sanitizeLoRaRelayField(packet.sourceNode);
  relay += "|" + sanitizeLoRaRelayField(packet.destinationNode);
  relay += "|" + sanitizeLoRaRelayField(packet.payload);
  relay += "|" + String(packet.timestamp);
  return relay;
}

bool parseLoRaRelayPacket(const String &line, ProtocolPacket &packet) {
  if (!line.startsWith(LORA_RELAY_PREFIX + "|")) {
    return false;
  }

  packet.protocolVersion = PROTOCOL_VERSION;
  packet.packetType = "MESSAGE";
  packet.packetId = loRaRelayFieldAt(line, 1);
  packet.sourceNode = loRaRelayFieldAt(line, 2);
  packet.destinationNode = loRaRelayFieldAt(line, 3);
  packet.payload = loRaRelayFieldAt(line, 4);
  packet.hopPath = packet.sourceNode + ">" + String(SIM_NODE_ID);
  packet.retryCount = 0;
  packet.timestamp = static_cast<unsigned long>(loRaRelayFieldAt(line, 5).toInt());
  packet.status = "RECEIVED_OVER_LORA";
  packet.checksum = CHECKSUM_PLACEHOLDER;

  return packet.packetId.length() > 0 &&
         packet.sourceNode.length() > 0 &&
         packet.destinationNode.length() > 0 &&
         packet.payload.length() > 0;
}

void sendBluetoothPacket(const ProtocolPacket &packet);

void deliverLoRaProtocolPacketToBluetooth(const ProtocolPacket &packet) {
  Serial.print("[LORA_PROTOCOL_RX] packet_id=");
  Serial.print(packet.packetId);
  Serial.print(" src=");
  Serial.print(packet.sourceNode);
  Serial.print(" dest=");
  Serial.println(packet.destinationNode);

  if (hasSeenMessage(packet.packetId)) {
    Serial.print("[LORA_PROTOCOL_DUPLICATE] packet_id=");
    Serial.println(packet.packetId);
    return;
  }
  rememberMessage(packet.packetId);

  if (!protocolPacketTargetsLocalNode(packet)) {
    Serial.print("[LORA_PROTOCOL_IGNORED] destination=");
    Serial.println(packet.destinationNode);
    return;
  }

  if (!SerialBT.hasClient()) {
    Serial.print("[BT_PENDING_FROM_LORA] no Android Bluetooth client for packet_id=");
    Serial.println(packet.packetId);
    return;
  }

  sendBluetoothPacket(packet);
}

void processIncomingLoRaRelayPacket(const String &line) {
  ProtocolPacket packet;
  if (!parseLoRaRelayPacket(line, packet)) {
    Serial.println("[LORA_PROTOCOL_ERROR] invalid compact relay packet");
    return;
  }

  deliverLoRaProtocolPacketToBluetooth(packet);
}

void processIncomingLoRaProtocolPacket(const String &line) {
  const PacketParseResult result = parseProtocolPacket(line);
  if (!result.valid) {
    Serial.print("[LORA_PROTOCOL_ERROR] ");
    Serial.println(result.errorReason);
    return;
  }

  deliverLoRaProtocolPacketToBluetooth(result.packet);
}

void processIncomingLoRaLine(const String &line, int rssi, float snr) {
  ++loraRxCounter;
  Serial.print("[LORA_RX] rssi=");
  Serial.print(rssi);
  Serial.print(" snr=");
  Serial.print(snr);
  Serial.print(" payload=");
  Serial.println(line);

  if (line.startsWith(LORA_RELAY_PREFIX + "|")) {
    processIncomingLoRaRelayPacket(line);
  } else if (line.startsWith("{")) {
    if (line.indexOf("\"protocolVersion\"") >= 0) {
      processIncomingLoRaProtocolPacket(line);
    } else {
      processIncomingMessage(line);
    }
  } else {
    Serial.println("[LORA_ERROR] Expected simulation JSON packet from LoRa peer.");
  }
}

ProtocolPacket createProtocolPacket(
  const String &packetType,
  const String &packetId,
  const String &sourceNode,
  const String &destinationNode,
  const String &payload,
  const String &hopPath,
  int retryCount,
  const String &status
) {
  ProtocolPacket packet;
  packet.protocolVersion = PROTOCOL_VERSION;
  packet.packetType = packetType;
  packet.packetId = packetId;
  packet.sourceNode = sourceNode;
  packet.destinationNode = destinationNode;
  packet.payload = payload;
  packet.hopPath = hopPath;
  packet.retryCount = retryCount;
  packet.timestamp = simulationTimestamp();
  packet.status = status;
  packet.checksum = CHECKSUM_PLACEHOLDER;
  return packet;
}

String bluetoothPacketId(const String &prefix) {
  return prefix + "-" + String(SIM_NODE_ID) + "-" + String(millis());
}

ProtocolPacket createAckPacket(const ProtocolPacket &request, const String &status, const String &payload) {
  return createProtocolPacket(
    "ACK",
    bluetoothPacketId("BT-ACK"),
    String(SIM_NODE_ID),
    request.sourceNode,
    payload,
    String(SIM_NODE_ID) + ">" + request.sourceNode,
    0,
    status
  );
}

ProtocolPacket createErrorPacket(const ProtocolPacket &request, const String &reason) {
  const String destination = request.sourceNode.length() > 0 ? request.sourceNode : "ANDROID_APP";
  return createProtocolPacket(
    "ERROR",
    bluetoothPacketId("BT-ERROR"),
    String(SIM_NODE_ID),
    destination,
    reason,
    String(SIM_NODE_ID) + ">" + destination,
    0,
    "REJECTED"
  );
}

ProtocolPacket createStatusResponsePacket(const ProtocolPacket &request) {
  String payload = "node=" + String(SIM_NODE_ID);
  payload += ";state=" + String(localNodeOnline ? "ONLINE" : "OFFLINE");
  payload += ";bluetoothService=" + String(bluetoothServiceStarted ? "STARTED" : "STOPPED");
  payload += ";bluetoothClient=" + String(SerialBT.hasClient() ? "CONNECTED" : "DISCONNECTED");
  payload += ";loRa=" + String(loraReady ? "READY" : "SIMULATION_PLACEHOLDER");
  payload += ";manualModes=AUTO,LORA,WIFI,GSM";

  return createProtocolPacket(
    "STATUS",
    bluetoothPacketId("BT-STATUS"),
    String(SIM_NODE_ID),
    request.sourceNode,
    payload,
    String(SIM_NODE_ID) + ">" + request.sourceNode,
    0,
    "ONLINE"
  );
}

void sendBluetoothPacket(const ProtocolPacket &packet) {
  const String serialized = serializeProtocolPacket(packet);
  if (SerialBT.hasClient()) {
    SerialBT.println(serialized);
  }
  Serial.print("[BT_TX] ");
  Serial.println(serialized);
}

void printProtocolPacketFields(const ProtocolPacket &packet) {
  Serial.print("  protocolVersion=");
  Serial.println(packet.protocolVersion);
  Serial.print("  packetType=");
  Serial.println(packet.packetType);
  Serial.print("  packetId=");
  Serial.println(packet.packetId);
  Serial.print("  sourceNode=");
  Serial.println(packet.sourceNode);
  Serial.print("  destinationNode=");
  Serial.println(packet.destinationNode);
  Serial.print("  payload=");
  Serial.println(packet.payload);
  Serial.print("  hopPath=");
  Serial.println(packet.hopPath);
  Serial.print("  retryCount=");
  Serial.println(packet.retryCount);
  Serial.print("  timestamp=");
  Serial.println(packet.timestamp);
  Serial.print("  status=");
  Serial.println(packet.status);
  Serial.print("  checksum=");
  Serial.println(packet.checksum);
}

void printProtocolParseResult(const String &label, const String &rawPacket) {
  Serial.print("[");
  Serial.print(label);
  Serial.println("]");
  Serial.print("  raw=");
  Serial.println(rawPacket);

  const PacketParseResult result = parseProtocolPacket(rawPacket);
  Serial.print("  validation=");
  Serial.println(result.valid ? "VALID" : "INVALID");
  Serial.print("  reason=");
  Serial.println(result.errorReason);
  printProtocolPacketFields(result.packet);

  if (result.valid) {
    Serial.print("  serialized=");
    Serial.println(serializeProtocolPacket(result.packet));
  }
}

void processIncomingProtocolPacket(const String &line) {
  printProtocolParseResult("PROTOCOL_PARSE", line);
}

void processIncomingBluetoothProtocolPacket(const String &line) {
  const PacketParseResult result = parseProtocolPacket(line);
  printProtocolParseResult("BT_PROTOCOL_PARSE", line);

  if (!result.valid) {
    sendBluetoothPacket(createErrorPacket(result.packet, result.errorReason));
    return;
  }

  if (result.packet.packetType == "STATUS") {
    sendBluetoothPacket(createStatusResponsePacket(result.packet));
    return;
  }

  if (result.packet.packetType == "MESSAGE") {
    const String requestedMode = extractManualMode(result.packet.payload);
    if (!isSupportedManualMode(requestedMode)) {
      sendBluetoothPacket(createErrorPacket(result.packet, "unsupported manual mode"));
      return;
    }

    const bool shouldForwardToLoRa =
      (requestedMode == "LORA" || requestedMode == "AUTO") &&
      result.packet.destinationNode != String(SIM_NODE_ID) &&
      result.packet.destinationNode != "ESP32_BRIDGE";
    bool forwardedToLoRa = false;

    if (shouldForwardToLoRa) {
      forwardedToLoRa = sendLoRaLine(serializeLoRaRelayPacket(result.packet));
    }

    String payload = "accepted=" + result.packet.packetId;
    payload += ";mode=" + requestedMode;
    payload += ";destination=" + result.packet.destinationNode;
    payload += ";loRa=" + String(loraReady ? "READY" : "SIMULATION_PLACEHOLDER");
    payload += ";forwarding=";
    payload += forwardedToLoRa ? "FORWARDED_OVER_LORA" : (shouldForwardToLoRa ? "LORA_NOT_READY" : "LOCAL_OR_SIMULATION_ONLY");
    sendBluetoothPacket(createAckPacket(result.packet, forwardedToLoRa ? "FORWARDED_OVER_LORA" : "QUEUED_FOR_SIMULATION", payload));
    return;
  }

  sendBluetoothPacket(createAckPacket(result.packet, "ACCEPTED", "accepted=" + result.packet.packetId));
}

String sampleHelloPacket() {
  ProtocolPacket packet = createProtocolPacket(
    "HELLO",
    "BT-HELLO-001",
    "ANDROID_APP",
    String(SIM_NODE_ID),
    "HELLO",
    "ANDROID_APP>" + String(SIM_NODE_ID),
    0,
    "PENDING_ACK"
  );
  return serializeProtocolPacket(packet);
}

String sampleMessagePacket() {
  ProtocolPacket packet = createProtocolPacket(
    "MESSAGE",
    "BT-MSG-001",
    "ANDROID_APP",
    String(DEFAULT_DEST_ID),
    "Emergency test message",
    "ANDROID_APP>" + String(SIM_NODE_ID) + ">" + String(DEFAULT_DEST_ID),
    0,
    "QUEUED"
  );
  return serializeProtocolPacket(packet);
}

String sampleStatusPacket() {
  ProtocolPacket packet = createProtocolPacket(
    "STATUS",
    "BT-STATUS-001",
    "ANDROID_APP",
    String(SIM_NODE_ID),
    "REQUEST_STATUS",
    "ANDROID_APP>" + String(SIM_NODE_ID),
    0,
    "REQUEST"
  );
  return serializeProtocolPacket(packet);
}

String sampleBadPacket() {
  ProtocolPacket packet = createProtocolPacket(
    "UNKNOWN",
    "BT-BAD-001",
    "ANDROID_APP",
    String(SIM_NODE_ID),
    "BAD_PACKET",
    "ANDROID_APP>" + String(SIM_NODE_ID),
    0,
    "REQUEST"
  );
  packet.protocolVersion = "BT-MANET-0.0";
  packet.checksum = "invalid checksum";
  return serializeProtocolPacket(packet);
}

void printProtocolSpec() {
  Serial.println("[PROTOCOL]");
  Serial.print("  protocolVersion=");
  Serial.println(PROTOCOL_VERSION);
  Serial.print("  checksum_placeholder=");
  Serial.println(CHECKSUM_PLACEHOLDER);
  Serial.println("  supported_packet_types=");
  for (size_t i = 0; i < SUPPORTED_PACKET_TYPE_COUNT; ++i) {
    Serial.print("    - ");
    Serial.println(SUPPORTED_PACKET_TYPES[i]);
  }
  Serial.println("  required_fields=protocolVersion, packetType, packetId, sourceNode, destinationNode, payload, hopPath, retryCount, timestamp, status, checksum");
  Serial.println("  manual_modes=AUTO, LORA, WIFI, GSM");
  Serial.println("  sample_message=");
  Serial.println(sampleMessagePacket());
}

void printBluetoothStatus() {
  Serial.println("[BT_SERVICE]");
  Serial.print("  service_name=");
  Serial.println(bluetoothServiceName());
  Serial.print("  state=");
  Serial.println(bluetoothServiceStarted ? "STARTED" : "STOPPED");
  Serial.print("  client=");
  Serial.println(SerialBT.hasClient() ? "CONNECTED" : "DISCONNECTED");
  Serial.print("  protocolVersion=");
  Serial.println(PROTOCOL_VERSION);
  Serial.println("  transport=Classic Bluetooth SPP");
  Serial.println("  android_transport=Bluetooth only; USB Serial remains out of Android scope");
  Serial.print("  loRa=");
  Serial.println(loraReady ? "READY" : "SIMULATION_PLACEHOLDER");
  Serial.println("  manual_modes=AUTO, LORA, WIFI, GSM");
}

void printLoRaStatus() {
  Serial.println("[LORA_STATUS]");
#if ENABLE_LORA
  Serial.println("  build=ENABLED");
#else
  Serial.println("  build=DISABLED");
#endif
  Serial.print("  ready=");
  Serial.println(loraReady ? "YES" : "NO");
  Serial.print("  frequency=");
  Serial.println(static_cast<long>(LORA_FREQUENCY));
  Serial.print("  sync_word=0x");
  Serial.println(LORA_SYNC_WORD, HEX);
  Serial.print("  pins=ss:");
  Serial.print(LORA_SS_PIN);
  Serial.print(" rst:");
  Serial.print(LORA_RST_PIN);
  Serial.print(" dio0:");
  Serial.print(LORA_DIO0_PIN);
  Serial.print(" sck:");
  Serial.print(LORA_SCK_PIN);
  Serial.print(" miso:");
  Serial.print(LORA_MISO_PIN);
  Serial.print(" mosi:");
  Serial.println(LORA_MOSI_PIN);
  Serial.print("  tx_count=");
  Serial.println(loraTxCounter);
  Serial.print("  rx_count=");
  Serial.println(loraRxCounter);
}

void loraSendCommand(const String &line) {
  String trimmedLine = line;
  trimmedLine.trim();

  String args = trimmedLine.substring(String("LORA_SEND").length());
  args.trim();

  const int payloadStart = args.indexOf(' ');
  if (payloadStart <= 0) {
    Serial.println("[ERROR] Usage: LORA_SEND <DEST> <MESSAGE>");
    return;
  }

  String dest = args.substring(0, payloadStart);
  String payload = args.substring(payloadStart + 1);
  dest.trim();
  payload.trim();

  if (dest.length() == 0 || payload.length() == 0) {
    Serial.println("[ERROR] Usage: LORA_SEND <DEST> <MESSAGE>");
    return;
  }

  SimMessage message = createOutboundMessage(dest, payload);
  rememberMessage(message.msgId);
  const String serialized = serializeMessage(message);
  if (sendLoRaLine(serialized)) {
    Serial.print("[LORA_TEST] sent_to=");
    Serial.print(dest);
    Serial.print(" msg_id=");
    Serial.println(message.msgId);
  }
}

void loraPingCommand() {
  SimMessage message = createOutboundMessage(DEFAULT_DEST_ID, "LORA_PING");
  rememberMessage(message.msgId);
  const String serialized = serializeMessage(message);
  if (sendLoRaLine(serialized)) {
    Serial.print("[LORA_TEST] ping_default_dest=");
    Serial.print(DEFAULT_DEST_ID);
    Serial.print(" msg_id=");
    Serial.println(message.msgId);
  }
}

void sendCommand(const String &line) {
  String trimmedLine = line;
  trimmedLine.trim();

  String args = trimmedLine.substring(4);
  args.trim();

  const int payloadStart = args.indexOf(' ');
  if (payloadStart <= 0) {
    Serial.println("[ERROR] Usage: SEND <DEST> <MESSAGE>");
    return;
  }

  String dest = args.substring(0, payloadStart);
  String payload = args.substring(payloadStart + 1);
  dest.trim();
  payload.trim();

  if (dest.length() == 0 || payload.length() == 0) {
    Serial.println("[ERROR] Usage: SEND <DEST> <MESSAGE>");
    return;
  }

  SimMessage message = createOutboundMessage(dest, payload);
  rememberMessage(message.msgId);
  logMessage("RECEIVED", message);
  routeMessage(message);
}

void sendPlainTextFallback(const String &line) {
  String payload = line;
  payload.trim();
  if (payload.length() == 0) {
    return;
  }

  Serial.println("[FALLBACK] Plain text routed to default destination. Prefer: SEND <DEST> <MESSAGE>");
  SimMessage message = createOutboundMessage(DEFAULT_DEST_ID, payload);
  rememberMessage(message.msgId);
  logMessage("RECEIVED", message);
  routeMessage(message);
}

void printNeighbors() {
  Serial.println("[NEIGHBORS]");
  for (size_t i = 0; i < NEIGHBOR_COUNT; ++i) {
    Serial.print("  node=");
    Serial.print(neighbors[i].nodeId);
    Serial.print(" rssi=");
    Serial.print(neighbors[i].rssi);
    Serial.print(" last_seen=");
    Serial.print(neighbors[i].lastSeen);
    Serial.print(" state=");
    Serial.println(neighbors[i].online ? "ONLINE" : "OFFLINE");
  }
}

void printStatus() {
  Serial.println("[STATUS]");
  Serial.print("  node=");
  Serial.println(SIM_NODE_ID);
  Serial.print("  state=");
  Serial.println(localNodeOnline ? "ONLINE" : "OFFLINE");
  Serial.print("  default_dest=");
  Serial.println(DEFAULT_DEST_ID);
  Serial.print("  max_hop=");
  Serial.println(MAX_HOP_COUNT);
  Serial.print("  duplicate_cache_count=");
  Serial.print(duplicateCacheCount());
  Serial.print("/");
  Serial.println(DUPLICATE_CACHE_SIZE);
  Serial.print("  message_counter=");
  Serial.println(outboundCounter);
}

void setOfflineCommand(const String &line) {
  const int spaceIndex = line.indexOf(' ');
  if (spaceIndex < 0) {
    localNodeOnline = false;
    Serial.print("[STATE] ");
    Serial.print(SIM_NODE_ID);
    Serial.println(" OFFLINE");
    return;
  }

  String nodeId = line.substring(spaceIndex + 1);
  nodeId.trim();
  setNeighborState(nodeId, false);
}

void setOnlineCommand(const String &line) {
  const int spaceIndex = line.indexOf(' ');
  if (spaceIndex < 0) {
    localNodeOnline = true;
    Serial.print("[STATE] ");
    Serial.print(SIM_NODE_ID);
    Serial.println(" ONLINE");
    return;
  }

  String nodeId = line.substring(spaceIndex + 1);
  nodeId.trim();
  setNeighborState(nodeId, true);
}

void processSerialLine(String line) {
  line.trim();
  if (line.length() == 0) {
    return;
  }

  if (line.startsWith("{")) {
    if (line.indexOf("\"protocolVersion\"") >= 0) {
      processIncomingProtocolPacket(line);
    } else {
      processIncomingMessage(line);
    }
    return;
  }

  const int commandEnd = line.indexOf(' ');
  const String command = toUpperCopy(commandEnd < 0 ? line : line.substring(0, commandEnd));
  if (command == "SEND") {
    sendCommand(line);
  } else if (command == "STATUS") {
    printStatus();
  } else if (command == "NEIGHBORS") {
    printNeighbors();
  } else if (command == "OFFLINE") {
    setOfflineCommand(toUpperCopy(line));
  } else if (command == "ONLINE") {
    setOnlineCommand(toUpperCopy(line));
  } else if (command == "PARSE_HELLO") {
    printProtocolParseResult("PARSE_HELLO", sampleHelloPacket());
  } else if (command == "PARSE_MESSAGE") {
    printProtocolParseResult("PARSE_MESSAGE", sampleMessagePacket());
  } else if (command == "PARSE_STATUS") {
    printProtocolParseResult("PARSE_STATUS", sampleStatusPacket());
  } else if (command == "PARSE_BAD_PACKET") {
    printProtocolParseResult("PARSE_BAD_PACKET", sampleBadPacket());
  } else if (command == "PRINT_PROTOCOL") {
    printProtocolSpec();
  } else if (command == "BT_STATUS") {
    printBluetoothStatus();
  } else if (command == "LORA_STATUS") {
    printLoRaStatus();
  } else if (command == "LORA_SEND") {
    loraSendCommand(line);
  } else if (command == "LORA_PING") {
    loraPingCommand();
  } else {
    sendPlainTextFallback(line);
  }
}

void processBluetoothLine(String line) {
  line.trim();
  if (line.length() == 0) {
    return;
  }

  Serial.print("[BT_RX] ");
  Serial.println(line);

  if (line.startsWith("{") && line.indexOf("\"protocolVersion\"") >= 0) {
    processIncomingBluetoothProtocolPacket(line);
    return;
  }

  ProtocolPacket errorPacket = createProtocolPacket(
    "ERROR",
    bluetoothPacketId("BT-ERROR"),
    String(SIM_NODE_ID),
    "ANDROID_APP",
    "Bluetooth service expects BT-MANET-1.0 protocol JSON",
    String(SIM_NODE_ID) + ">ANDROID_APP",
    0,
    "REJECTED"
  );
  sendBluetoothPacket(errorPacket);
}

void readBluetoothInput() {
  while (SerialBT.available() > 0) {
    const char c = static_cast<char>(SerialBT.read());
    if (c == '\n' || c == '\r') {
      processBluetoothLine(bluetoothBuffer);
      bluetoothBuffer = "";
    } else {
      bluetoothBuffer += c;
    }
  }
}

void readLoRaInput() {
#if ENABLE_LORA
  if (!loraReady) {
    return;
  }

  const int packetSize = LoRa.parsePacket();
  if (packetSize <= 0) {
    return;
  }

  String line;
  while (LoRa.available()) {
    line += static_cast<char>(LoRa.read());
  }
  line.trim();

  if (line.length() > 0) {
    processIncomingLoRaLine(line, LoRa.packetRssi(), LoRa.packetSnr());
  }
#endif
}

void printStartupBanner() {
  Serial.println();
  Serial.println("PUP MANET ESP32 Multi-Node Simulation");
  Serial.print("Node ID: ");
  Serial.println(SIM_NODE_ID);
  Serial.print("Default destination: ");
  Serial.println(DEFAULT_DEST_ID);
  Serial.println("Simulation only: Serial input/output represents MANET packets.");
  Serial.println("Commands: SEND <DEST> <MESSAGE>, STATUS, NEIGHBORS, OFFLINE, ONLINE");
  Serial.println("Optional neighbor state commands: OFFLINE <NODE_ID>, ONLINE <NODE_ID>");
  Serial.println("Protocol parser commands: PARSE_HELLO, PARSE_MESSAGE, PARSE_STATUS, PARSE_BAD_PACKET, PRINT_PROTOCOL");
  Serial.println("Bluetooth service command: BT_STATUS");
  Serial.println("LoRa live-test commands: LORA_STATUS, LORA_PING, LORA_SEND <DEST> <MESSAGE>");
  Serial.println("Paste a JSON message to simulate receiving a packet from another node.");
  Serial.println("Paste a BT-MANET-1.0 protocol JSON packet to test parser validation.");
  Serial.println();
}

void beginBluetoothService() {
  bluetoothServiceStarted = SerialBT.begin(bluetoothServiceName());
  if (bluetoothServiceStarted) {
    Serial.print("[BT_SERVICE] started name=");
    Serial.println(bluetoothServiceName());
    Serial.println("[BT_SERVICE] Classic Bluetooth SPP accepts BT-MANET-1.0 JSON lines.");
  } else {
    Serial.println("[BT_SERVICE] failed to start");
  }
}

void beginLoRaService() {
#if ENABLE_LORA
  SPI.begin(LORA_SCK_PIN, LORA_MISO_PIN, LORA_MOSI_PIN, LORA_SS_PIN);
  LoRa.setPins(LORA_SS_PIN, LORA_RST_PIN, LORA_DIO0_PIN);

  loraReady = LoRa.begin(LORA_FREQUENCY);
  if (loraReady) {
    LoRa.setSyncWord(LORA_SYNC_WORD);
    LoRa.setTxPower(LORA_TX_POWER);
    Serial.print("[LORA_SERVICE] started frequency=");
    Serial.print(static_cast<long>(LORA_FREQUENCY));
    Serial.print(" sync_word=0x");
    Serial.println(LORA_SYNC_WORD, HEX);
  } else {
    Serial.println("[LORA_SERVICE] failed to start. Check SX1278 wiring and power.");
  }
#else
  loraReady = false;
  Serial.println("[LORA_SERVICE] disabled in this build. Use node_a_lora or node_b_lora for Step 022.");
#endif
}

void setup() {
  Serial.begin(115200);
  delay(500);
  initNeighbors();
  beginBluetoothService();
  beginLoRaService();
  printStartupBanner();
}

void loop() {
  while (Serial.available() > 0) {
    const char c = static_cast<char>(Serial.read());
    if (c == '\n' || c == '\r') {
      processSerialLine(serialBuffer);
      serialBuffer = "";
    } else {
      serialBuffer += c;
    }
  }

  if (bluetoothServiceStarted) {
    readBluetoothInput();
  }

  readLoRaInput();
}
