#include <Arduino.h>
#include <ctype.h>

#ifndef SIM_NODE_ID
#define SIM_NODE_ID "NODE_A"
#endif

#ifndef DEFAULT_DEST_ID
#define DEFAULT_DEST_ID "NODE_B"
#endif

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

constexpr int MAX_HOP_COUNT = 5;
constexpr size_t DUPLICATE_CACHE_SIZE = 32;
constexpr size_t NEIGHBOR_COUNT = 2;

String seenMessageIds[DUPLICATE_CACHE_SIZE];
Neighbor neighbors[NEIGHBOR_COUNT];
size_t seenMessageIndex = 0;
unsigned long outboundCounter = 0;
String serialBuffer;
bool localNodeOnline = true;

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
    processIncomingMessage(line);
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
  } else {
    sendPlainTextFallback(line);
  }
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
  Serial.println("Paste a JSON message to simulate receiving a packet from another node.");
  Serial.println();
}

void setup() {
  Serial.begin(115200);
  delay(500);
  initNeighbors();
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
}
