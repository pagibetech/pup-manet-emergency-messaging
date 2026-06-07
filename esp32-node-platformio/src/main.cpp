#include <Arduino.h>
#ifndef ENABLE_BLUETOOTH
#define ENABLE_BLUETOOTH 1
#endif

#if ENABLE_BLUETOOTH
#include <BluetoothSerial.h>
#endif
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

#ifndef GATEWAY_ID
#define GATEWAY_ID "A"
#endif

#ifndef TEST_FORCE_GATEWAY_ROUTE
#define TEST_FORCE_GATEWAY_ROUTE 0
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
const String GW_JSON_PREFIX = "[GW_JSON]";
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

struct NodeEntry {
  String nodeId;
  String gatewayId;
  int rssi;
  unsigned long lastSeen;
  int hopCount;
  bool online;
  bool degraded;
  unsigned long degradedSinceMs;
};

struct ProtocolPacket {
  String protocolVersion;
  String packetType;
  String packetId;
  String sourceNode;
  String destinationNode;
  String payload;
  String hopPath;
  int hopCount;
  int ttl;
  String previousHop;
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

enum class ForcedRouteAction {
  NotForced,
  Ignore,
  Relay,
  Deliver
};

constexpr int DEFAULT_TTL = 5;
constexpr int MAX_HOP_COUNT = 5;
constexpr int RSSI_DEGRADE_THRESHOLD_DBM = -78;
constexpr unsigned long DEGRADE_TIMEOUT_MS = 10000UL;
constexpr size_t DUPLICATE_CACHE_SIZE = 32;
constexpr size_t HELLO_RELAY_CACHE_SIZE = 24;
constexpr size_t MAX_NODE_TABLE_SIZE = 16;
constexpr unsigned long HELLO_INTERVAL_MS = 10000UL;
constexpr unsigned long NODE_EXPIRE_MS = 30000UL;

String seenMessageIds[DUPLICATE_CACHE_SIZE];
String relayedHelloPacketIds[HELLO_RELAY_CACHE_SIZE];
NodeEntry nodeTable[MAX_NODE_TABLE_SIZE];
size_t nodeTableCount = 0;
size_t seenMessageIndex = 0;
size_t helloRelayIndex = 0;
unsigned long outboundCounter = 0;
String serialBuffer;
String bluetoothBuffer;
bool localNodeOnline = true;
bool bluetoothServiceStarted = false;
bool loraReady = false;
unsigned long loraTxCounter = 0;
unsigned long loraRxCounter = 0;
unsigned long lastHelloSentMs = 0;
#if ENABLE_BLUETOOTH
BluetoothSerial SerialBT;
#endif

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

bool hasBluetoothClient() {
#if ENABLE_BLUETOOTH
  return bluetoothServiceStarted && SerialBT.hasClient();
#else
  return false;
#endif
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
  packet.hopCount = 0;
  packet.ttl = 0;
  packet.previousHop = "";
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
  result.packet.hopCount = static_cast<int>(extractJsonInteger(line, "hopCount", 0));
  result.packet.ttl = static_cast<int>(extractJsonInteger(line, "ttl", DEFAULT_TTL));
  result.packet.previousHop = extractJsonString(line, "previousHop");
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
  json += "\"hopCount\":" + String(packet.hopCount) + ",";
  json += "\"ttl\":" + String(packet.ttl) + ",";
  json += "\"previousHop\":\"" + jsonEscape(packet.previousHop) + "\",";
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

bool hasRelayedHelloPacket(const String &packetId) {
  for (size_t i = 0; i < HELLO_RELAY_CACHE_SIZE; ++i) {
    if (relayedHelloPacketIds[i] == packetId) {
      return true;
    }
  }
  return false;
}

void rememberRelayedHelloPacket(const String &packetId) {
  relayedHelloPacketIds[helloRelayIndex] = packetId;
  helloRelayIndex = (helloRelayIndex + 1) % HELLO_RELAY_CACHE_SIZE;
}

// ------------------------------------------------------------------
// Dynamic Node Table (replaces fixed Neighbor array)
// ------------------------------------------------------------------

NodeEntry *findNodeEntry(const String &nodeId) {
  for (size_t i = 0; i < nodeTableCount; ++i) {
    if (nodeTable[i].nodeId == nodeId) {
      return &nodeTable[i];
    }
  }
  return nullptr;
}

void logNodeTableChange(const char *label, const NodeEntry &entry) {
  Serial.print("[");
  Serial.print(label);
  Serial.print("] node=");
  Serial.print(entry.nodeId);
  Serial.print(" gateway=");
  Serial.print(entry.gatewayId);
  Serial.print(" lastSeen=");
  Serial.print(entry.lastSeen);
  Serial.print(" hopCount=");
  Serial.print(entry.hopCount);
  Serial.print(" rssi=");
  Serial.print(entry.rssi);
  Serial.print(" nodeCount=");
  Serial.println(nodeTableCount);
}

NodeEntry *upsertNodeEntry(const String &nodeId, const String &gatewayId, int rssi, bool online, int hopCount = 0) {
  NodeEntry *existing = findNodeEntry(nodeId);
  if (existing != nullptr) {
    existing->gatewayId = gatewayId;
    existing->rssi = rssi;
    existing->lastSeen = millis();
    existing->hopCount = hopCount;
    existing->online = online;
    logNodeTableChange("NEIGHBOR_UPDATE", *existing);
    return existing;
  }
  if (nodeTableCount < MAX_NODE_TABLE_SIZE) {
    nodeTable[nodeTableCount] = NodeEntry{
      nodeId,
      gatewayId,
      rssi,
      millis(),
      hopCount,
      online,
      false,
      0
    };
    NodeEntry *created = &nodeTable[nodeTableCount++];
    logNodeTableChange("NEIGHBOR_ADD", *created);
    return created;
  }
  return nullptr;
}

void removeExpiredNodes() {
  const unsigned long now = millis();
  size_t i = 0;
  while (i < nodeTableCount) {
    if (nodeTable[i].nodeId != String(SIM_NODE_ID) && (now - nodeTable[i].lastSeen >= NODE_EXPIRE_MS)) {
      Serial.print("[NODE_EXPIRE] ");
      Serial.println(nodeTable[i].nodeId);
      for (size_t j = i; j + 1 < nodeTableCount; ++j) {
        nodeTable[j] = nodeTable[j + 1];
      }
      --nodeTableCount;
    } else {
      ++i;
    }
  }
}

void setNodeState(const String &nodeId, bool online) {
  NodeEntry *entry = findNodeEntry(nodeId);
  if (entry == nullptr) {
    Serial.print("[ERROR] Unknown node: ");
    Serial.println(nodeId);
    return;
  }
  entry->online = online;
  entry->lastSeen = online ? millis() : entry->lastSeen;
  Serial.print("[NODE] ");
  Serial.print(nodeId);
  Serial.println(online ? " ONLINE" : " OFFLINE");
}

void initNodeTable() {
  nodeTableCount = 0;
  upsertNodeEntry(String(SIM_NODE_ID), String(GATEWAY_ID), 0, true);
}

String loRaRelayFieldAt(const String &line, int fieldIndex);

String extractLoRaSourceNode(const String &line) {
  if (line.startsWith(LORA_RELAY_PREFIX + "|")) {
    return loRaRelayFieldAt(line, 2);
  }
  if (line.startsWith("{")) {
    return extractJsonString(line, "sourceNode");
  }
  return "";
}

void updateNodeRssi(const String &nodeId, int rssi) {
  NodeEntry *entry = findNodeEntry(nodeId);
  if (entry == nullptr) {
    return;
  }
  entry->rssi = rssi;
  entry->lastSeen = millis();
}

void updateNodeHealth() {
  const unsigned long now = millis();

  for (size_t i = 0; i < nodeTableCount; ++i) {
    if (!nodeTable[i].online) {
      continue;
    }

    const bool wasDegraded = nodeTable[i].degraded;

    if (nodeTable[i].rssi < RSSI_DEGRADE_THRESHOLD_DBM) {
      if (nodeTable[i].degradedSinceMs == 0) {
        nodeTable[i].degradedSinceMs = now;
      }
      if (!nodeTable[i].degraded && (now - nodeTable[i].degradedSinceMs >= DEGRADE_TIMEOUT_MS)) {
        nodeTable[i].degraded = true;
      }
      if (nodeTable[i].degraded) {
        nodeTable[i].degradedSinceMs = now;
      }
    } else {
      nodeTable[i].degraded = false;
      nodeTable[i].degradedSinceMs = 0;
    }

    if (nodeTable[i].degraded && !wasDegraded) {
      Serial.print("[DEGRADATION] node=");
      Serial.print(nodeTable[i].nodeId);
      Serial.print(" rssi=");
      Serial.print(nodeTable[i].rssi);
      Serial.println(" state=DEGRADED");
    }

    if (!nodeTable[i].degraded && wasDegraded) {
      Serial.print("[RECOVERY] node=");
      Serial.print(nodeTable[i].nodeId);
      Serial.print(" rssi=");
      Serial.println(nodeTable[i].rssi);
    }
  }
}

NodeEntry *selectBestNextHop() {
  NodeEntry *best = nullptr;
  NodeEntry *bestDegraded = nullptr;

  for (size_t i = 0; i < nodeTableCount; ++i) {
    if (nodeTable[i].nodeId == String(SIM_NODE_ID)) {
      continue;
    }
    if (!nodeTable[i].online) {
      continue;
    }
    if (!nodeTable[i].degraded) {
      if (best == nullptr || nodeTable[i].rssi > best->rssi) {
        best = &nodeTable[i];
      }
    } else {
      if (bestDegraded == nullptr || nodeTable[i].rssi > bestDegraded->rssi) {
        bestDegraded = &nodeTable[i];
      }
    }
  }

  if (best != nullptr) {
    return best;
  }
  return bestDegraded;
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

  NodeEntry *nextHop = selectBestNextHop();
  if (nextHop == nullptr) {
    dropMessage("no online nodes", message);
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

bool testForceGatewayRouteEnabled() {
  return TEST_FORCE_GATEWAY_ROUTE == 1;
}

bool isForcedGatewayRoutePair(const String &sourceNode, const String &destinationNode) {
  return (sourceNode == "nodeA1" && destinationNode == "nodeA2") ||
         (sourceNode == "nodeA2" && destinationNode == "nodeA1");
}

bool shouldForceOriginatingGatewayRoute(const String &sourceNode, const String &destinationNode) {
  return testForceGatewayRouteEnabled() &&
         isForcedGatewayRoutePair(sourceNode, destinationNode);
}

String forcedRouteNodeAt(const ProtocolPacket &packet, int index) {
  if (packet.sourceNode == "nodeA1" && packet.destinationNode == "nodeA2") {
    switch (index) {
      case 0: return "nodeA1";
      case 1: return "gatewayA";
      case 2: return "gatewayB";
      case 3: return "nodeA2";
      default: return "";
    }
  }
  if (packet.sourceNode == "nodeA2" && packet.destinationNode == "nodeA1") {
    switch (index) {
      case 0: return "nodeA2";
      case 1: return "gatewayB";
      case 2: return "gatewayA";
      case 3: return "nodeA1";
      default: return "";
    }
  }
  return "";
}

int forcedRouteIndexOf(const ProtocolPacket &packet, const String &nodeId) {
  for (int i = 0; i < 4; ++i) {
    if (forcedRouteNodeAt(packet, i) == nodeId) {
      return i;
    }
  }
  return -1;
}

String forcedOriginGatewayFor(const String &sourceNode, const String &destinationNode) {
  if (sourceNode == "nodeA1" && destinationNode == "nodeA2") {
    return "gatewayA";
  }
  if (sourceNode == "nodeA2" && destinationNode == "nodeA1") {
    return "gatewayB";
  }
  return "";
}

String appendHopIfMissing(const String &hopPath, const String &nodeId) {
  if (nodeId.length() == 0) {
    return hopPath;
  }
  if (hopPath.length() == 0) {
    return nodeId;
  }
  const String suffix = String(">") + nodeId;
  if (hopPath == nodeId || hopPath.endsWith(suffix)) {
    return hopPath;
  }
  return hopPath + ">" + nodeId;
}

void logForcedRoute(const String &sourceNode, const String &destinationNode, const String &viaNode) {
  Serial.println("[FORCED_ROUTE]");
  Serial.print("source=");
  Serial.println(sourceNode);
  Serial.print("dest=");
  Serial.println(destinationNode);
  Serial.print("via=");
  Serial.println(viaNode);
}

void logForcedRouteIgnore(const ProtocolPacket &packet, const String &reason) {
  Serial.print("[FORCED_ROUTE] ignore reason=");
  Serial.print(reason);
  Serial.print(" current=");
  Serial.print(SIM_NODE_ID);
  Serial.print(" source=");
  Serial.print(packet.sourceNode);
  Serial.print(" dest=");
  Serial.print(packet.destinationNode);
  Serial.print(" previousHop=");
  Serial.println(packet.previousHop);
}

void logForcedForward(const String &fromNode, const String &toNode) {
  Serial.println("[FORWARD]");
  Serial.print("from=");
  Serial.println(fromNode);
  Serial.print("to=");
  Serial.println(toNode);
}

ForcedRouteAction forcedRouteActionForCurrentNode(const ProtocolPacket &packet) {
  if (!testForceGatewayRouteEnabled() ||
      packet.packetType != "MESSAGE" ||
      !isForcedGatewayRoutePair(packet.sourceNode, packet.destinationNode)) {
    return ForcedRouteAction::NotForced;
  }

  const int currentIndex = forcedRouteIndexOf(packet, String(SIM_NODE_ID));
  if (currentIndex <= 0) {
    return ForcedRouteAction::Ignore;
  }

  const String expectedPreviousHop = forcedRouteNodeAt(packet, currentIndex - 1);
  if (packet.previousHop != expectedPreviousHop) {
    return ForcedRouteAction::Ignore;
  }

  if (String(SIM_NODE_ID) == packet.destinationNode) {
    return ForcedRouteAction::Deliver;
  }

  return ForcedRouteAction::Relay;
}

String forcedRouteNextHopForCurrentNode(const ProtocolPacket &packet) {
  const int currentIndex = forcedRouteIndexOf(packet, String(SIM_NODE_ID));
  if (currentIndex < 0 || currentIndex >= 3) {
    return "";
  }
  return forcedRouteNodeAt(packet, currentIndex + 1);
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

String shortLoRaPayload(String payload) {
  payload.replace("\r", " ");
  payload.replace("\n", " ");
  for (int i = 0; i < payload.length(); ++i) {
    const char c = payload.charAt(i);
    if (!isprint(static_cast<unsigned char>(c))) {
      payload.setCharAt(i, '?');
    }
  }
  if (payload.length() > 120) {
    return payload.substring(0, 120) + "...";
  }
  return payload;
}

void logCorruptLoRaPacket(const String &reason, const String &line) {
  Serial.print("[LORA_DROP_CORRUPT] reason=");
  Serial.print(reason);
  Serial.print(" payload=");
  Serial.println(shortLoRaPayload(line));
}

int loRaRelayFieldCount(const String &line) {
  if (line.length() == 0) {
    return 0;
  }

  int count = 1;
  for (int i = 0; i < line.length(); ++i) {
    if (line.charAt(i) == '|') {
      ++count;
    }
  }
  return count;
}

bool isValidLoRaNodeId(const String &nodeId) {
  if (nodeId.length() == 0 || nodeId.length() > 32) {
    return false;
  }

  for (int i = 0; i < nodeId.length(); ++i) {
    const char c = nodeId.charAt(i);
    if (!isalnum(static_cast<unsigned char>(c)) && c != '_' && c != '-') {
      return false;
    }
  }

  return true;
}

bool isKnownLoRaNodeId(const String &nodeId) {
  if (nodeId == "gatewayA" || nodeId == "gatewayB") {
    return true;
  }

  return nodeId.length() == 6 &&
         nodeId.startsWith("node") &&
         (nodeId.charAt(4) == 'A' || nodeId.charAt(4) == 'B') &&
         nodeId.charAt(5) >= '1' &&
         nodeId.charAt(5) <= '3';
}

bool isDigitsOnly(const String &value) {
  if (value.length() == 0) {
    return false;
  }

  for (int i = 0; i < value.length(); ++i) {
    if (!isdigit(static_cast<unsigned char>(value.charAt(i)))) {
      return false;
    }
  }

  return true;
}

bool parseStrictInt(const String &value, int &parsed) {
  if (value.length() == 0) {
    return false;
  }

  int start = 0;
  bool negative = false;
  if (value.charAt(0) == '-') {
    negative = true;
    start = 1;
  }
  if (start >= value.length()) {
    return false;
  }

  long result = 0;
  for (int i = start; i < value.length(); ++i) {
    const char c = value.charAt(i);
    if (!isdigit(static_cast<unsigned char>(c))) {
      return false;
    }
    result = (result * 10) + (c - '0');
    if (result > 32767L) {
      return false;
    }
  }

  parsed = static_cast<int>(negative ? -result : result);
  return true;
}

bool isValidGatewayId(const String &gatewayId) {
  return gatewayId == "A" || gatewayId == "B";
}

void skipJsonWhitespace(const String &value, int &index) {
  while (index < value.length() && isspace(static_cast<unsigned char>(value.charAt(index)))) {
    ++index;
  }
}

bool consumeExactQuotedJsonString(const String &value, int &index, const char *expected) {
  if (index >= value.length() || value.charAt(index) != '"') {
    return false;
  }
  ++index;

  for (int i = 0; expected[i] != '\0'; ++i) {
    if (index >= value.length() || value.charAt(index) != expected[i]) {
      return false;
    }
    ++index;
  }

  if (index >= value.length() || value.charAt(index) != '"') {
    return false;
  }
  ++index;
  return true;
}

bool parseStrictGatewayIdPayload(const String &payload, String &gatewayId) {
  gatewayId = "";
  int index = 0;

  skipJsonWhitespace(payload, index);
  if (index >= payload.length() || payload.charAt(index) != '{') {
    return false;
  }
  ++index;

  skipJsonWhitespace(payload, index);
  if (!consumeExactQuotedJsonString(payload, index, "gatewayId")) {
    return false;
  }

  skipJsonWhitespace(payload, index);
  if (index >= payload.length() || payload.charAt(index) != ':') {
    return false;
  }
  ++index;

  skipJsonWhitespace(payload, index);
  if (index >= payload.length() || payload.charAt(index) != '"') {
    return false;
  }
  ++index;

  if (index >= payload.length()) {
    return false;
  }
  const char gatewayChar = payload.charAt(index);
  ++index;

  if (index >= payload.length() || payload.charAt(index) != '"') {
    return false;
  }
  ++index;

  gatewayId = String(gatewayChar);

  skipJsonWhitespace(payload, index);
  if (index >= payload.length() || payload.charAt(index) != '}') {
    return false;
  }
  ++index;

  skipJsonWhitespace(payload, index);
  return index == payload.length();
}

bool isValidHelloPacketIdForSource(const String &packetId, const String &sourceNode) {
  const String expectedPrefix = "HELLO-" + sourceNode + "-";
  if (!packetId.startsWith(expectedPrefix)) {
    return false;
  }

  const String timestamp = packetId.substring(expectedPrefix.length());
  return isDigitsOnly(timestamp);
}

String serializeLoRaRelayPacket(const ProtocolPacket &packet) {
  String relay = LORA_RELAY_PREFIX;
  relay += "|" + sanitizeLoRaRelayField(packet.packetId);
  relay += "|" + sanitizeLoRaRelayField(packet.sourceNode);
  relay += "|" + sanitizeLoRaRelayField(packet.destinationNode);
  relay += "|" + sanitizeLoRaRelayField(packet.payload);
  relay += "|" + String(packet.timestamp);
  relay += "|" + String(packet.ttl);
  relay += "|" + String(packet.hopCount);
  relay += "|" + sanitizeLoRaRelayField(packet.previousHop);
  relay += "|" + sanitizeLoRaRelayField(packet.hopPath);
  return relay;
}

bool parseLoRaRelayPacket(const String &line, ProtocolPacket &packet, String &errorReason) {
  errorReason = "";

  if (!line.startsWith(LORA_RELAY_PREFIX + "|")) {
    errorReason = "bad_prefix";
    return false;
  }

  if (loRaRelayFieldCount(line) != 10) {
    errorReason = "bad_field_count";
    return false;
  }

  const String packetId = loRaRelayFieldAt(line, 1);
  const String sourceNode = loRaRelayFieldAt(line, 2);
  const String destinationNode = loRaRelayFieldAt(line, 3);
  const String payload = loRaRelayFieldAt(line, 4);
  const String ttlField = loRaRelayFieldAt(line, 6);
  const String hopCountField = loRaRelayFieldAt(line, 7);

  if (packetId.length() == 0) {
    errorReason = "blank_packetId";
    return false;
  }
  if (sourceNode.length() == 0) {
    errorReason = "blank_sourceNode";
    return false;
  }
  if (destinationNode.length() == 0) {
    errorReason = "blank_destinationNode";
    return false;
  }
  if (!isValidLoRaNodeId(sourceNode)) {
    errorReason = "invalid_sourceNode";
    return false;
  }
  if (!isValidLoRaNodeId(destinationNode)) {
    errorReason = "invalid_destinationNode";
    return false;
  }

  int ttl = 0;
  if (!parseStrictInt(ttlField, ttl)) {
    errorReason = "invalid_ttl";
    return false;
  }
  if (ttl < 0 || ttl > DEFAULT_TTL) {
    errorReason = "ttl_out_of_range";
    return false;
  }

  int hopCount = 0;
  if (!parseStrictInt(hopCountField, hopCount)) {
    errorReason = "invalid_hopCount";
    return false;
  }
  if (hopCount < 0 || hopCount > DEFAULT_TTL) {
    errorReason = "hopCount_out_of_range";
    return false;
  }

  packet.protocolVersion = PROTOCOL_VERSION;
  packet.packetType = "MESSAGE";
  packet.packetId = packetId;
  packet.sourceNode = sourceNode;
  packet.destinationNode = destinationNode;
  packet.payload = payload;
  packet.hopPath = packet.sourceNode + ">" + String(SIM_NODE_ID);
  packet.retryCount = 0;
  packet.timestamp = static_cast<unsigned long>(loRaRelayFieldAt(line, 5).toInt());
  packet.ttl = ttl;
  packet.hopCount = hopCount;
  packet.previousHop = loRaRelayFieldAt(line, 8);
  String parsedHopPath = loRaRelayFieldAt(line, 9);
  if (parsedHopPath.length() > 0) {
    packet.hopPath = parsedHopPath;
  }
  packet.status = "RECEIVED_OVER_LORA";
  packet.checksum = CHECKSUM_PLACEHOLDER;

  const bool looksLikeHello =
    packet.packetId.startsWith("HELLO-") ||
    (packet.destinationNode == "BROADCAST" && packet.payload.indexOf("gatewayId") >= 0);
  if (looksLikeHello) {
    if (packet.sourceNode == "BROADCAST") {
      errorReason = "hello_source_broadcast";
      return false;
    }
    if (!isKnownLoRaNodeId(packet.sourceNode)) {
      errorReason = "unknown_hello_source";
      return false;
    }
    if (packet.destinationNode != "BROADCAST") {
      errorReason = "hello_destination_not_broadcast";
      return false;
    }
    if (!isValidHelloPacketIdForSource(packet.packetId, packet.sourceNode)) {
      errorReason = "invalid_hello_packetId";
      return false;
    }

    String gatewayId;
    if (!parseStrictGatewayIdPayload(packet.payload, gatewayId)) {
      errorReason = "malformed_gatewayId_json";
      return false;
    }
    if (!isValidGatewayId(gatewayId)) {
      errorReason = "invalid_gatewayId";
      return false;
    }
    packet.packetType = "HELLO";
    packet.status = "ALIVE";
  } else {
    if (packet.packetId.startsWith("ACK-")) {
      packet.packetType = "ACK";
      packet.status = "ACK";
    }
  }

  return true;
}

void learnNodeFromHelloPacket(const ProtocolPacket &packet, int rssi, const String &sourceLabel) {
  if (packet.packetType != "HELLO") {
    return;
  }
  if (packet.sourceNode.length() == 0 || packet.sourceNode == String(SIM_NODE_ID)) {
    return;
  }
  if (packet.sourceNode == "BROADCAST") {
    logCorruptLoRaPacket("hello_source_broadcast", packet.packetId);
    return;
  }
  if (!isKnownLoRaNodeId(packet.sourceNode)) {
    logCorruptLoRaPacket("unknown_hello_source", packet.packetId);
    return;
  }
  if (!isValidHelloPacketIdForSource(packet.packetId, packet.sourceNode)) {
    logCorruptLoRaPacket("invalid_hello_packetId", packet.packetId);
    return;
  }

  String gatewayId;
  if (!parseStrictGatewayIdPayload(packet.payload, gatewayId)) {
    logCorruptLoRaPacket("malformed_gatewayId_json", packet.payload);
    return;
  }
  if (!isValidGatewayId(gatewayId)) {
    logCorruptLoRaPacket("invalid_gatewayId", packet.payload);
    return;
  }

  upsertNodeEntry(packet.sourceNode, gatewayId, rssi, true, packet.hopCount);
  Serial.print("[HELLO] discovered ");
  Serial.print(packet.sourceNode);
  Serial.print(" gateway=");
  Serial.print(gatewayId);
  Serial.print(" hopCount=");
  Serial.print(packet.hopCount);
  Serial.print(" rssi=");
  Serial.print(rssi);
  Serial.print(" source=");
  Serial.println(sourceLabel);
  Serial.print("[DISCOVERY] HELLO from ");
  Serial.print(packet.sourceNode);
  Serial.print(" gateway=");
  Serial.print(gatewayId);
  Serial.print(" hopCount=");
  Serial.print(packet.hopCount);
  Serial.print(" rssi=");
  Serial.print(rssi);
  Serial.print(" source=");
  Serial.println(sourceLabel);
}

void sendBluetoothPacket(const ProtocolPacket &packet);

String hopPathJsonArray(const String &hopPath) {
  String result = "[";
  int start = 0;
  bool first = true;
  for (int i = 0; i <= hopPath.length(); ++i) {
    if (i == hopPath.length() || hopPath.charAt(i) == '>') {
      const String hop = hopPath.substring(start, i);
      if (hop.length() > 0) {
        if (!first) {
          result += ",";
        }
        result += "\"" + jsonEscape(hop) + "\"";
        first = false;
      }
      start = i + 1;
    }
  }
  result += "]";
  return result;
}

String firstHopInPath(const String &hopPath) {
  const int separator = hopPath.indexOf('>');
  if (separator < 0) {
    return hopPath;
  }
  return hopPath.substring(0, separator);
}

String deliveryAckDestinationFor(const ProtocolPacket &messagePacket) {
  if (messagePacket.sourceNode != "ANDROID_APP") {
    return messagePacket.sourceNode;
  }

  const String firstHop = firstHopInPath(messagePacket.hopPath);
  if (firstHop.length() > 0 && firstHop != "ANDROID_APP") {
    return firstHop;
  }

  if (messagePacket.previousHop.length() > 0 && messagePacket.previousHop != String(SIM_NODE_ID)) {
    return messagePacket.previousHop;
  }

  return messagePacket.sourceNode;
}

ProtocolPacket createDeliveryAckPacket(const ProtocolPacket &messagePacket) {
  ProtocolPacket ack = emptyProtocolPacket();
  const String ackDestination = deliveryAckDestinationFor(messagePacket);
  ack.protocolVersion = PROTOCOL_VERSION;
  ack.packetType = "ACK";
  ack.packetId = "ACK-" + messagePacket.packetId + "-" + String(SIM_NODE_ID) + "-" + String(millis());
  ack.sourceNode = String(SIM_NODE_ID);
  ack.destinationNode = ackDestination;

  const String ackRoute = appendHopIfMissing(messagePacket.hopPath, String(SIM_NODE_ID));
  String payload = "{";
  payload += "\"ackVersion\":1,";
  payload += "\"ackType\":\"DELIVERY\",";
  payload += "\"ackFor\":\"" + jsonEscape(messagePacket.packetId) + "\",";
  payload += "\"ackStatus\":\"DELIVERED\",";
  payload += "\"originNode\":\"" + jsonEscape(ackDestination) + "\",";
  payload += "\"finalDestinationNode\":\"" + jsonEscape(messagePacket.destinationNode) + "\",";
  payload += "\"ackSource\":\"" + jsonEscape(String(SIM_NODE_ID)) + "\",";
  payload += "\"reason\":\"\",";
  payload += "\"route\":" + hopPathJsonArray(ackRoute);
  payload += "}";

  ack.payload = payload;
  ack.hopPath = String(SIM_NODE_ID) + ">" + ackDestination;
  ack.hopCount = 0;
  ack.ttl = DEFAULT_TTL;
  ack.previousHop = String(SIM_NODE_ID);
  ack.retryCount = 0;
  ack.timestamp = simulationTimestamp();
  ack.status = "DELIVERED";
  ack.checksum = CHECKSUM_PLACEHOLDER;
  return ack;
}

void sendDeliveryAckForMessage(const ProtocolPacket &messagePacket) {
  if (messagePacket.packetType != "MESSAGE" || messagePacket.sourceNode == String(SIM_NODE_ID)) {
    return;
  }

  ProtocolPacket ack = createDeliveryAckPacket(messagePacket);
  const bool sent = sendLoRaLine(serializeLoRaRelayPacket(ack));
  Serial.print("[DELIVERY_ACK_TX] ackFor=");
  Serial.print(messagePacket.packetId);
  Serial.print(" dest=");
  Serial.print(ack.destinationNode);
  Serial.print(" status=");
  Serial.println(sent ? "SENT" : "NOT_SENT");
}

void deliverToBluetooth(const ProtocolPacket &packet) {
  if (!hasBluetoothClient()) {
    Serial.print("[BT_PENDING_FROM_LORA] no Android Bluetooth client for packet_id=");
    Serial.println(packet.packetId);
    return;
  }

  Serial.print("[LORA_RX] Forwarding LoRa packet to Bluetooth SPP. packet_id=");
  Serial.println(packet.packetId);

  sendBluetoothPacket(packet);
}

void routeLoRaProtocolPacket(const ProtocolPacket &packet, const String &sourceLabel) {
  const ForcedRouteAction forcedRouteAction = forcedRouteActionForCurrentNode(packet);
  if (forcedRouteAction == ForcedRouteAction::Ignore) {
    logForcedRouteIgnore(packet, "unexpected_path");
    return;
  }

  if (hasSeenMessage(packet.packetId)) {
    Serial.print("[DUPLICATE_DROP] packet_id=");
    Serial.print(packet.packetId);
    if (sourceLabel.indexOf("test") >= 0) {
      Serial.print(" test_packet=true");
    }
    Serial.println();
    return;
  }
  rememberMessage(packet.packetId);

  if (packet.ttl <= 0) {
    Serial.print("[TTL_DROP] packet_id=");
    Serial.print(packet.packetId);
    Serial.print(" ttl_exhausted_at=");
    Serial.print(SIM_NODE_ID);
    if (sourceLabel.indexOf("test") >= 0) {
      Serial.print(" test_packet=true");
    }
    Serial.println();
    return;
  }

  if (protocolPacketTargetsLocalNode(packet)) {
    ProtocolPacket deliveryPacket = packet;
    if (forcedRouteAction == ForcedRouteAction::Deliver) {
      deliveryPacket.hopPath = appendHopIfMissing(deliveryPacket.hopPath, String(SIM_NODE_ID));
    }

    Serial.print("[ROUTE_DECISION] deliver_local packet_id=");
    Serial.print(packet.packetId);
    Serial.print(" source=");
    Serial.print(sourceLabel);
    if (sourceLabel.indexOf("test") >= 0) {
      Serial.print(" test_packet=true");
    }
    Serial.println();

    if (sourceLabel == "lora") {
      Serial.print(GW_JSON_PREFIX);
      Serial.print(" ");
      Serial.println(serializeProtocolPacket(deliveryPacket));
    }

    deliverToBluetooth(deliveryPacket);
    sendDeliveryAckForMessage(deliveryPacket);
    return;
  }

  ProtocolPacket relayPacket = packet;
  relayPacket.ttl -= 1;
  relayPacket.hopCount += 1;
  relayPacket.previousHop = String(SIM_NODE_ID);
  relayPacket.hopPath = relayPacket.hopPath.length() > 0
    ? relayPacket.hopPath + ">" + String(SIM_NODE_ID)
    : String(SIM_NODE_ID);

  if (relayPacket.ttl <= 0) {
    Serial.print("[TTL_DROP] packet_id=");
    Serial.print(packet.packetId);
    Serial.print(" ttl_exhausted_at=");
    Serial.println(SIM_NODE_ID);
    return;
  }

  Serial.print("[ROUTE_DECISION] relay packet_id=");
  Serial.print(packet.packetId);
  Serial.print(" dest=");
  Serial.print(packet.destinationNode);
  Serial.print(" next_ttl=");
  Serial.print(relayPacket.ttl);
  Serial.print(" next_hopCount=");
  Serial.print(relayPacket.hopCount);
  Serial.print(" source=");
  Serial.print(sourceLabel);
  if (sourceLabel.indexOf("test") >= 0) {
    Serial.print(" test_packet=true");
  }
  Serial.println();

  if (forcedRouteAction == ForcedRouteAction::Relay) {
    const String nextHop = forcedRouteNextHopForCurrentNode(packet);
    if (nextHop.length() > 0) {
      logForcedForward(String(SIM_NODE_ID), nextHop);
    }
  }

  const String relayLine = serializeLoRaRelayPacket(relayPacket);
  sendLoRaLine(relayLine);
  Serial.print("[LORA_RELAY] packet_id=");
  Serial.print(packet.packetId);
  Serial.print(" relayed_by=");
  Serial.print(SIM_NODE_ID);
  Serial.print(" new_path=");
  Serial.print(relayPacket.hopPath);
  if (sourceLabel.indexOf("test") >= 0) {
    Serial.print(" test_packet=true");
  }
  Serial.println();
}

void forwardHelloPresencePacket(const ProtocolPacket &packet) {
  if (packet.packetType != "HELLO") {
    return;
  }
  if (packet.sourceNode == String(SIM_NODE_ID)) {
    return;
  }
  if (packet.previousHop == String(SIM_NODE_ID)) {
    return;
  }
  if (hasRelayedHelloPacket(packet.packetId)) {
    Serial.print("[HELLO_RELAY] duplicate packet_id=");
    Serial.print(packet.packetId);
    Serial.print(" relayed_by=");
    Serial.println(SIM_NODE_ID);
    return;
  }
  if (packet.ttl <= 1 || packet.hopCount >= DEFAULT_TTL) {
    Serial.print("[HELLO_RELAY] ttl_exhausted packet_id=");
    Serial.print(packet.packetId);
    Serial.print(" source=");
    Serial.print(packet.sourceNode);
    Serial.print(" ttl=");
    Serial.print(packet.ttl);
    Serial.print(" hopCount=");
    Serial.println(packet.hopCount);
    return;
  }

  ProtocolPacket relayPacket = packet;
  relayPacket.ttl -= 1;
  relayPacket.hopCount += 1;
  relayPacket.previousHop = String(SIM_NODE_ID);
  relayPacket.hopPath = relayPacket.hopPath.length() > 0
    ? relayPacket.hopPath + ">" + String(SIM_NODE_ID)
    : String(SIM_NODE_ID);

  const String relayLine = serializeLoRaRelayPacket(relayPacket);
  if (sendLoRaLine(relayLine)) {
    rememberRelayedHelloPacket(packet.packetId);
    Serial.print("[HELLO_RELAY] packet_id=");
    Serial.print(packet.packetId);
    Serial.print(" source=");
    Serial.print(packet.sourceNode);
    Serial.print(" relayed_by=");
    Serial.print(SIM_NODE_ID);
    Serial.print(" next_ttl=");
    Serial.print(relayPacket.ttl);
    Serial.print(" next_hopCount=");
    Serial.print(relayPacket.hopCount);
    Serial.print(" path=");
    Serial.println(relayPacket.hopPath);
  }
}

void processIncomingLoRaRelayPacket(const String &line, int rssi = -70) {
  ProtocolPacket packet;
  String errorReason;
  if (!parseLoRaRelayPacket(line, packet, errorReason)) {
    logCorruptLoRaPacket(errorReason.length() > 0 ? errorReason : "invalid_compact_packet", line);
    return;
  }

  Serial.print("[LORA_RELAY_PARSE] valid packet_id=");
  Serial.print(packet.packetId);
  Serial.print(" type=");
  Serial.print(packet.packetType);
  Serial.print(" source=");
  Serial.print(packet.sourceNode);
  Serial.print(" dest=");
  Serial.print(packet.destinationNode);
  Serial.print(" hopCount=");
  Serial.print(packet.hopCount);
  Serial.print(" ttl=");
  Serial.println(packet.ttl);

  learnNodeFromHelloPacket(packet, rssi, "lora_relay");
  if (packet.packetType == "HELLO") {
    forwardHelloPresencePacket(packet);
    return;
  }

  routeLoRaProtocolPacket(packet, "lora");
}

void processIncomingLoRaProtocolPacket(const String &line, int rssi = -70) {
  const PacketParseResult result = parseProtocolPacket(line);
  if (!result.valid) {
    Serial.print("[LORA_PROTOCOL_ERROR] ");
    Serial.println(result.errorReason);
    return;
  }

  learnNodeFromHelloPacket(result.packet, rssi, "lora_json");
  if (result.packet.packetType == "HELLO") {
    return;
  }

  routeLoRaProtocolPacket(result.packet, "lora");
}

void processIncomingLoRaLine(const String &line, int rssi, float snr) {
  ++loraRxCounter;
  Serial.print("[LORA_RX] rssi=");
  Serial.print(rssi);
  Serial.print(" snr=");
  Serial.print(snr);
  Serial.print(" payload=");
  Serial.println(line);

  const String sourceNode = extractLoRaSourceNode(line);
  if (sourceNode.length() > 0) {
    updateNodeRssi(sourceNode, rssi);
  }

  if (line.startsWith(LORA_RELAY_PREFIX + "|")) {
    processIncomingLoRaRelayPacket(line, rssi);
  } else if (line.startsWith("{")) {
    if (line.indexOf("\"protocolVersion\"") >= 0) {
      processIncomingLoRaProtocolPacket(line, rssi);
    } else {
      processIncomingMessage(line);
    }
  } else {
    logCorruptLoRaPacket("bad_prefix", line);
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
  packet.hopCount = 0;
  packet.ttl = 0;
  packet.previousHop = "";
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
  payload += ";bluetoothClient=" + String(hasBluetoothClient() ? "CONNECTED" : "DISCONNECTED");
  payload += ";bluetoothRole=PHONE_NODE_ACCESS";
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

ProtocolPacket createNodeListResponsePacket(const ProtocolPacket &request) {
  removeExpiredNodes();

  String payload = "type=NODE_LIST";
  payload += ";localNode=" + String(SIM_NODE_ID);
  payload += ";gateway=" + String(GATEWAY_ID);
  payload += ";count=" + String(nodeTableCount);

  Serial.print("[NODE_LIST_EXPORT] requester=");
  Serial.print(request.sourceNode);
  Serial.print(" localNode=");
  Serial.print(SIM_NODE_ID);
  Serial.print(" nodeCount=");
  Serial.println(nodeTableCount);

  for (size_t i = 0; i < nodeTableCount; ++i) {
    payload += ";" + nodeTable[i].nodeId + "," + nodeTable[i].gatewayId + "," + String(nodeTable[i].online ? "ONLINE" : "OFFLINE");
    Serial.print("[NODE_LIST_EXPORT] node=");
    Serial.print(nodeTable[i].nodeId);
    Serial.print(" gateway=");
    Serial.print(nodeTable[i].gatewayId);
    Serial.print(" state=");
    Serial.print(nodeTable[i].online ? "ONLINE" : "OFFLINE");
    Serial.print(" lastSeen=");
    Serial.print(nodeTable[i].lastSeen);
    Serial.print(" hopCount=");
    Serial.print(nodeTable[i].hopCount);
    Serial.print(" exportedCount=");
    Serial.println(nodeTableCount);
  }

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
#if ENABLE_BLUETOOTH
  if (hasBluetoothClient()) {
    SerialBT.println(serialized);
  }
#endif
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
  Serial.print("  hopCount=");
  Serial.println(packet.hopCount);
  Serial.print("  ttl=");
  Serial.println(packet.ttl);
  Serial.print("  previousHop=");
  Serial.println(packet.previousHop);
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

void processGatewaySerialProtocolPacket(const String &line) {
  printProtocolParseResult("GATEWAY_SERIAL_PARSE", line);
  const PacketParseResult result = parseProtocolPacket(line);
  if (!result.valid) {
    return;
  }

  // Gateway may inject a NODE_LIST from the remote mesh
  if (result.packet.packetType == "STATUS") {
    if (result.packet.payload.indexOf("type=NODE_LIST") >= 0) {
      Serial.println("[GATEWAY_NODE_LIST] injecting remote nodes");
      // Parse out remote nodes after count field
      int countIndex = result.packet.payload.indexOf("count=");
      if (countIndex >= 0) {
        int afterCount = result.packet.payload.indexOf(';', countIndex);
        if (afterCount < 0) {
          afterCount = countIndex + String("count=").length();
        }
        String rest = result.packet.payload.substring(afterCount + 1);
        int pos = 0;
        while (pos < rest.length()) {
          int nextSemi = rest.indexOf(';', pos);
          String token;
          if (nextSemi < 0) {
            token = rest.substring(pos);
            pos = rest.length();
          } else {
            token = rest.substring(pos, nextSemi);
            pos = nextSemi + 1;
          }
          int comma1 = token.indexOf(',');
          int comma2 = token.indexOf(',', comma1 + 1);
          if (comma1 > 0 && comma2 > comma1) {
            String remoteNodeId = token.substring(0, comma1);
            String remoteGateway = token.substring(comma1 + 1, comma2);
            upsertNodeEntry(remoteNodeId, remoteGateway, -80, true);
            Serial.print("[REMOTE_NODE] ");
            Serial.print(remoteNodeId);
            Serial.print(" gw=");
            Serial.println(remoteGateway);
          }
        }
      }
      return;
    }
  }

  routeLoRaProtocolPacket(result.packet, "gateway_serial");
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
    if (result.packet.payload == "REQUEST_NODE_LIST") {
      sendBluetoothPacket(createNodeListResponsePacket(result.packet));
      return;
    }
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
      ProtocolPacket relayPacket = result.packet;
      const bool forceGatewayRoute =
        shouldForceOriginatingGatewayRoute(String(SIM_NODE_ID), relayPacket.destinationNode);

      if (forceGatewayRoute) {
        relayPacket.sourceNode = String(SIM_NODE_ID);
      }

      relayPacket.hopCount = 0;
      relayPacket.ttl = DEFAULT_TTL;
      relayPacket.previousHop = String(SIM_NODE_ID);
      relayPacket.hopPath = forceGatewayRoute
        ? String(SIM_NODE_ID)
        : String(SIM_NODE_ID) + ">" + result.packet.destinationNode;

      if (forceGatewayRoute) {
        logForcedRoute(
          relayPacket.sourceNode,
          relayPacket.destinationNode,
          forcedOriginGatewayFor(relayPacket.sourceNode, relayPacket.destinationNode)
        );
      }

      forwardedToLoRa = sendLoRaLine(serializeLoRaRelayPacket(relayPacket));
    }

    String payload = "ackVersion=1";
    payload += ";ackType=FORWARD";
    payload += ";ackFor=" + result.packet.packetId;
    payload += ";ackStatus=";
    payload += forwardedToLoRa ? "FORWARDED" : (shouldForwardToLoRa ? "UNKNOWN" : "ACCEPTED");
    payload += ";originNode=" + String(SIM_NODE_ID);
    payload += ";finalDestinationNode=" + result.packet.destinationNode;
    payload += ";ackSource=" + String(SIM_NODE_ID);
    payload += ";reason=";
    payload += forwardedToLoRa ? "" : (shouldForwardToLoRa ? "LORA_NOT_READY" : "LOCAL_OR_SIMULATION_ONLY");
    payload += ";route=" + String(SIM_NODE_ID) + ">" + result.packet.destinationNode;
    payload += ";accepted=" + result.packet.packetId;
    payload += ";mode=" + requestedMode;
    payload += ";destination=" + result.packet.destinationNode;
    payload += ";loRa=" + String(loraReady ? "READY" : "SIMULATION_PLACEHOLDER");
    payload += ";forwarding=";
    payload += forwardedToLoRa ? "FORWARDED_OVER_LORA" : (shouldForwardToLoRa ? "LORA_NOT_READY" : "LOCAL_OR_SIMULATION_ONLY");
    const String ackStatus = forwardedToLoRa ? "FORWARDED_OVER_LORA" : (shouldForwardToLoRa ? "LORA_NOT_READY" : "QUEUED_FOR_SIMULATION");
    sendBluetoothPacket(createAckPacket(result.packet, ackStatus, payload));
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

String sampleNodeListPacket() {
  ProtocolPacket packet = createProtocolPacket(
    "STATUS",
    "BT-NODELIST-001",
    "ANDROID_APP",
    String(SIM_NODE_ID),
    "REQUEST_NODE_LIST",
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
  Serial.println("  required_fields=protocolVersion, packetType, packetId, sourceNode, destinationNode, payload, hopPath, hopCount, ttl, previousHop, retryCount, timestamp, status, checksum");
  Serial.println("  manual_modes=AUTO, LORA, WIFI, GSM");
  Serial.println("  sample_message=");
  Serial.println(sampleMessagePacket());
}

void printBluetoothStatus() {
  Serial.println("[BT_SERVICE]");
  Serial.print("  build=");
  Serial.println(ENABLE_BLUETOOTH ? "ENABLED" : "DISABLED");
  Serial.print("  service_name=");
  Serial.println(bluetoothServiceName());
  Serial.print("  state=");
  Serial.println(bluetoothServiceStarted ? "STARTED" : "STOPPED");
  Serial.print("  client=");
  Serial.println(hasBluetoothClient() ? "CONNECTED" : "DISCONNECTED");
  Serial.print("  protocolVersion=");
  Serial.println(PROTOCOL_VERSION);
  Serial.println("  access=Android phone <-> local ESP32 node");
  Serial.println("  transport=Classic Bluetooth SPP phone-node access");
  Serial.println("  manet_backbone=LoRa");
  Serial.println("  node_to_node_bluetooth=DISABLED");
  Serial.println("  android_scope=Bluetooth SPP access only; USB Serial remains gateway/debug scope");
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

void relayTestDestCommand(const String &line) {
  String args = line.substring(String("RELAY_DEST").length());
  args.trim();

  const int payloadStart = args.indexOf(' ');
  if (payloadStart <= 0) {
    Serial.println("[ERROR] Usage: RELAY_DEST <DEST> <PAYLOAD>");
    return;
  }

  String dest = args.substring(0, payloadStart);
  String payload = args.substring(payloadStart + 1);
  dest.trim();
  payload.trim();

  if (dest.length() == 0 || payload.length() == 0) {
    Serial.println("[ERROR] Usage: RELAY_DEST <DEST> <PAYLOAD>");
    return;
  }

  ProtocolPacket packet = emptyProtocolPacket();
  packet.protocolVersion = PROTOCOL_VERSION;
  packet.packetType = "MESSAGE";
  packet.packetId = "RELAY_TEST-" + String(SIM_NODE_ID) + "-" + String(millis());
  packet.sourceNode = "NODE_A";
  packet.destinationNode = dest;
  packet.payload = payload;
  packet.hopPath = "NODE_A";
  packet.hopCount = 0;
  packet.ttl = DEFAULT_TTL;
  packet.previousHop = "NODE_A";
  packet.retryCount = 0;
  packet.timestamp = simulationTimestamp();
  packet.status = "RECEIVED_OVER_LORA";
  packet.checksum = CHECKSUM_PLACEHOLDER;

  Serial.print("[RELAY_TEST] injecting packet dest=");
  Serial.print(dest);
  Serial.println(" test_packet=true");
  routeLoRaProtocolPacket(packet, "serial_test");
}

void relayTestDuplicateCommand() {
  const String packetId = "RELAY_TEST_DUPLICATE";
  ProtocolPacket packet = emptyProtocolPacket();
  packet.protocolVersion = PROTOCOL_VERSION;
  packet.packetType = "MESSAGE";
  packet.packetId = packetId;
  packet.sourceNode = "NODE_A";
  packet.destinationNode = "NODE_C";
  packet.payload = "Duplicate test payload";
  packet.hopPath = "NODE_A";
  packet.hopCount = 0;
  packet.ttl = DEFAULT_TTL;
  packet.previousHop = "NODE_A";
  packet.retryCount = 0;
  packet.timestamp = simulationTimestamp();
  packet.status = "RECEIVED_OVER_LORA";
  packet.checksum = CHECKSUM_PLACEHOLDER;

  Serial.println("[RELAY_TEST] injecting packet first time test_packet=true");
  routeLoRaProtocolPacket(packet, "serial_test");

  Serial.println("[RELAY_TEST] injecting same packet again (duplicate) test_packet=true");
  routeLoRaProtocolPacket(packet, "serial_test");
}

void relayTestTtl0Command(const String &line) {
  String args = line.substring(String("RELAY_TTL0").length());
  args.trim();

  const int payloadStart = args.indexOf(' ');
  if (payloadStart <= 0) {
    Serial.println("[ERROR] Usage: RELAY_TTL0 <DEST> <PAYLOAD>");
    return;
  }

  String dest = args.substring(0, payloadStart);
  String payload = args.substring(payloadStart + 1);
  dest.trim();
  payload.trim();

  if (dest.length() == 0 || payload.length() == 0) {
    Serial.println("[ERROR] Usage: RELAY_TTL0 <DEST> <PAYLOAD>");
    return;
  }

  ProtocolPacket packet = emptyProtocolPacket();
  packet.protocolVersion = PROTOCOL_VERSION;
  packet.packetType = "MESSAGE";
  packet.packetId = "RELAY_TEST_TTL0-" + String(millis());
  packet.sourceNode = "NODE_A";
  packet.destinationNode = dest;
  packet.payload = payload;
  packet.hopPath = "NODE_A";
  packet.hopCount = 0;
  packet.ttl = 0;
  packet.previousHop = "NODE_A";
  packet.retryCount = 0;
  packet.timestamp = simulationTimestamp();
  packet.status = "RECEIVED_OVER_LORA";
  packet.checksum = CHECKSUM_PLACEHOLDER;

  Serial.println("[RELAY_TEST] injecting packet with ttl=0 test_packet=true");
  routeLoRaProtocolPacket(packet, "serial_test");
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
  Serial.println("[NODES]");
  for (size_t i = 0; i < nodeTableCount; ++i) {
    Serial.print("  node=");
    Serial.print(nodeTable[i].nodeId);
    Serial.print(" gw=");
    Serial.print(nodeTable[i].gatewayId);
    Serial.print(" rssi=");
    Serial.print(nodeTable[i].rssi);
    Serial.print(" last_seen=");
    Serial.print(nodeTable[i].lastSeen);
    Serial.print(" hopCount=");
    Serial.print(nodeTable[i].hopCount);
    Serial.print(" state=");
    Serial.println(nodeTable[i].online ? "ONLINE" : "OFFLINE");
  }
}

void printStatus() {
  Serial.println("[STATUS]");
  Serial.print("  node=");
  Serial.println(SIM_NODE_ID);
  Serial.print("  gateway=");
  Serial.println(GATEWAY_ID);
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
  Serial.print("  node_table_count=");
  Serial.println(nodeTableCount);
  Serial.print("  test_force_gateway_route=");
  Serial.println(testForceGatewayRouteEnabled() ? "ENABLED" : "DISABLED");
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
  setNodeState(nodeId, false);
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
  setNodeState(nodeId, true);
}

void processSerialLine(String line) {
  line.trim();
  if (line.length() == 0) {
    return;
  }

  if (line.startsWith(GW_JSON_PREFIX)) {
    String jsonPart = line.substring(GW_JSON_PREFIX.length());
    jsonPart.trim();
    if (jsonPart.length() > 0) {
      processGatewaySerialProtocolPacket(jsonPart);
    }
    return;
  }

  if (line.startsWith("{")) {
    if (line.indexOf("\"protocolVersion\"") >= 0) {
      processGatewaySerialProtocolPacket(line);
    } else {
      processIncomingMessage(line);
    }
    return;
  }

  if (line.startsWith(LORA_RELAY_PREFIX + "|")) {
    processIncomingLoRaRelayPacket(line);
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
  } else if (command == "PARSE_NODE_LIST") {
    printProtocolParseResult("PARSE_NODE_LIST", sampleNodeListPacket());
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
  } else if (command == "RELAY_DEST") {
    relayTestDestCommand(line);
  } else if (command == "RELAY_DUPLICATE") {
    relayTestDuplicateCommand();
  } else if (command == "RELAY_TTL0") {
    relayTestTtl0Command(line);
  } else {
    sendPlainTextFallback(line);
  }
}

void processBluetoothLine(String line) {
#if ENABLE_BLUETOOTH
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
    "Bluetooth phone-node access expects BT-MANET-1.0 protocol JSON",
    String(SIM_NODE_ID) + ">ANDROID_APP",
    0,
    "REJECTED"
  );
  sendBluetoothPacket(errorPacket);
#else
  (void)line;
#endif
}

void readBluetoothInput() {
#if ENABLE_BLUETOOTH
  while (SerialBT.available() > 0) {
    const char c = static_cast<char>(SerialBT.read());
    if (c == '\n' || c == '\r') {
      processBluetoothLine(bluetoothBuffer);
      bluetoothBuffer = "";
    } else {
      bluetoothBuffer += c;
    }
  }
#endif
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

void sendHelloBroadcast() {
  String payload = "{\"gatewayId\":\"" + jsonEscape(String(GATEWAY_ID)) + "\"}";
  ProtocolPacket packet = createProtocolPacket(
    "HELLO",
    bluetoothPacketId("HELLO"),
    String(SIM_NODE_ID),
    "BROADCAST",
    payload,
    String(SIM_NODE_ID),
    0,
    "ALIVE"
  );
  packet.ttl = DEFAULT_TTL;

  const String relayLine = serializeLoRaRelayPacket(packet);
  sendLoRaLine(relayLine);

  Serial.print("[HELLO] broadcast gateway=");
  Serial.println(GATEWAY_ID);
}

void printStartupBanner() {
  Serial.println();
  Serial.println("PUP MANET ESP32 Multi-Node Simulation");
  Serial.print("Node ID: ");
  Serial.println(SIM_NODE_ID);
  Serial.print("Gateway: ");
  Serial.println(GATEWAY_ID);
  Serial.print("Default destination: ");
  Serial.println(DEFAULT_DEST_ID);
  Serial.print("Bluetooth: ");
  Serial.println(ENABLE_BLUETOOTH ? "ENABLED" : "DISABLED");
  Serial.print("Test force gateway route: ");
  Serial.println(testForceGatewayRouteEnabled() ? "ENABLED" : "DISABLED");
  Serial.println("Simulation only: Serial input/output represents MANET packets.");
  Serial.println("Commands: SEND <DEST> <MESSAGE>, STATUS, NEIGHBORS, OFFLINE, ONLINE");
  Serial.println("Optional node state commands: OFFLINE <NODE_ID>, ONLINE <NODE_ID>");
  Serial.println("Protocol parser commands: PARSE_HELLO, PARSE_MESSAGE, PARSE_STATUS, PARSE_NODE_LIST, PARSE_BAD_PACKET, PRINT_PROTOCOL");
  Serial.println("Bluetooth service command: BT_STATUS");
  Serial.println("LoRa live-test commands: LORA_STATUS, LORA_PING, LORA_SEND <DEST> <MESSAGE>");
  Serial.println("Relay validation commands: RELAY_DEST <DEST> <PAYLOAD>, RELAY_DUPLICATE, RELAY_TTL0 <DEST> <PAYLOAD>");
  Serial.println("Paste a JSON message to simulate receiving a packet from another node.");
  Serial.println("Paste a BT-MANET-1.0 protocol JSON packet to test parser validation.");
  Serial.println("Gateway serial bridge: [GW_JSON] <json> for Pi backhaul. Raw protocol JSON also accepted.");
  Serial.println();
}

void beginBluetoothService() {
#if ENABLE_BLUETOOTH
  bluetoothServiceStarted = SerialBT.begin(bluetoothServiceName());
  if (bluetoothServiceStarted) {
    Serial.print("[BT_SERVICE] started name=");
    Serial.println(bluetoothServiceName());
    Serial.println("[BT_SERVICE] Classic Bluetooth SPP phone-node access accepts BT-MANET-1.0 JSON lines.");
  } else {
    Serial.println("[BT_SERVICE] failed to start");
  }
#else
  bluetoothServiceStarted = false;
  Serial.println("[BT_SERVICE] disabled in this build");
#endif
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
  initNodeTable();
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

#if ENABLE_BLUETOOTH
  if (bluetoothServiceStarted) {
    readBluetoothInput();
  }
#endif

  readLoRaInput();

  updateNodeHealth();
  removeExpiredNodes();

  const unsigned long now = millis();
  if (now - lastHelloSentMs >= HELLO_INTERVAL_MS) {
    lastHelloSentMs = now;
    sendHelloBroadcast();
  }
}
