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

constexpr size_t DUPLICATE_CACHE_SIZE = 32;

String seenMessageIds[DUPLICATE_CACHE_SIZE];
size_t seenMessageIndex = 0;
unsigned long outboundCounter = 0;
String serialBuffer;

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

void rememberMessage(const String &msgId) {
  seenMessageIds[seenMessageIndex] = msgId;
  seenMessageIndex = (seenMessageIndex + 1) % DUPLICATE_CACHE_SIZE;
}

unsigned long simulationTimestamp() {
  return millis() / 1000UL;
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

void forwardMessage(SimMessage message) {
  message.hop += 1;
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
    Serial.print("[DUPLICATE] Ignored msg_id=");
    Serial.println(message.msgId);
    return;
  }

  rememberMessage(message.msgId);

  if (message.dest == SIM_NODE_ID) {
    logMessage("DELIVERED", message);
    return;
  }

  forwardMessage(message);
}

void processTextCommand(const String &line) {
  String dest = DEFAULT_DEST_ID;
  String payload = line;

  if (line.startsWith("send ")) {
    const int payloadStart = line.indexOf(' ', 5);
    if (payloadStart > 0) {
      dest = line.substring(5, payloadStart);
      payload = line.substring(payloadStart + 1);
    }
  }

  payload.trim();
  if (payload.length() == 0) {
    Serial.println("[ERROR] Empty payload. Use plain text or: send <DEST_NODE_ID> <message>");
    return;
  }

  SimMessage message = createOutboundMessage(dest, payload);
  rememberMessage(message.msgId);
  logMessage("RECEIVED", message);

  if (message.dest == SIM_NODE_ID) {
    logMessage("DELIVERED", message);
    return;
  }

  forwardMessage(message);
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

  processTextCommand(line);
}

void printStartupBanner() {
  Serial.println();
  Serial.println("PUP MANET ESP32 Simulation Node");
  Serial.print("Node ID: ");
  Serial.println(SIM_NODE_ID);
  Serial.print("Default destination: ");
  Serial.println(DEFAULT_DEST_ID);
  Serial.println("Simulation only: Serial input/output represents the network.");
  Serial.println("Type plain text to send to the default destination.");
  Serial.println("Use: send <DEST_NODE_ID> <message>");
  Serial.println("Or paste a JSON message to simulate receiving from another node.");
  Serial.println();
}

void setup() {
  Serial.begin(115200);
  delay(500);
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
