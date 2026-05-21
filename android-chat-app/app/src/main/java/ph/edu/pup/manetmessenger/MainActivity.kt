package ph.edu.pup.manetmessenger

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ph.edu.pup.manetmessenger.ui.theme.PUPMANETMessengerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

enum class NetworkMode(val label: String) {
    Auto("Auto"),
    Lora("LoRa"),
    Wifi("WiFi"),
    Gsm("GSM")
}

enum class RouteLabel(val label: String) {
    Lora("LoRa"),
    Wifi("WiFi"),
    Gsm("GSM"),
    Satellite("Simulated Satellite"),
    None("No route")
}

enum class MessageStatus(val label: String) {
    Queued("Queued"),
    Routing("Routing"),
    Relaying("Relaying"),
    Delivered("Delivered"),
    Failed("Failed"),
    Retrying("Retrying")
}

enum class BluetoothAvailability(val label: String) {
    Available("Available"),
    Disabled("Disabled"),
    NotSupported("Not supported"),
    Simulated("Simulated")
}

enum class PairingStatus(val label: String) {
    NotPaired("Not paired"),
    Scanning("Scanning"),
    Paired("Paired"),
    ConnectionFailed("Connection failed")
}

enum class BluetoothLifecycleState(val label: String) {
    Idle("Idle"),
    Scanning("Scanning"),
    Pairing("Pairing"),
    Connecting("Connecting"),
    Connected("Connected"),
    Disconnected("Disconnected"),
    Failed("Failed"),
    Retrying("Retrying")
}

enum class TransportOption(val label: String) {
    Simulation("Simulation"),
    BluetoothPlaceholder("Bluetooth Placeholder"),
    WiFiPlaceholder("WiFi Placeholder")
}

enum class TransportConnectionState(val label: String) {
    Connected("Connected"),
    PlaceholderReady("Placeholder ready"),
    Disconnected("Disconnected")
}

enum class SimulationSpeed(val label: String, val tickMs: Long, val volatility: Int) {
    Slow("Slow", 6000L, 1),
    Normal("Normal", 3500L, 2),
    Fast("Fast", 1600L, 3)
}

enum class SimulationCondition(val label: String) {
    Stable("Stable"),
    Congested("Congested"),
    Recovering("Recovering"),
    Partitioned("Partitioned")
}

enum class AppTab(val label: String) {
    Messaging("Chat"),
    Network("Nodes"),
    Routing("Route"),
    Simulation("Sim"),
    Diagnostics("Logs")
}

enum class ValidationStatus(val label: String) {
    Pass("Pass"),
    Fail("Fail"),
    NotTested("Not tested")
}

enum class HardwareMode(val label: String) {
    Simulation("Simulation Mode"),
    HardwareDisabled("Hardware Mode: Disabled")
}

enum class Esp32ConnectionType(val label: String) {
    Bluetooth("Bluetooth"),
    Wifi("WiFi")
}

enum class BluetoothProtocolPacketType(val wireName: String) {
    Hello("HELLO"),
    Ack("ACK"),
    Message("MESSAGE"),
    RouteDiscovery("ROUTE_DISCOVERY"),
    RouteReply("ROUTE_REPLY"),
    Status("STATUS"),
    Error("ERROR")
}

data class NetworkState(
    val loraAvailable: Boolean = true,
    val wifiAvailable: Boolean = true,
    val gsmAvailable: Boolean = true,
    val satelliteAvailable: Boolean = true
)

data class BluetoothState(
    val bluetoothStatus: BluetoothAvailability = BluetoothAvailability.Simulated,
    val discoveredNodes: List<String> = emptyList(),
    val selectedNode: String? = null,
    val connectedNode: String? = null,
    val pairingStatus: PairingStatus = PairingStatus.NotPaired
)

data class Esp32BridgeConfig(
    val deviceName: String = "ESP32-MANET-01",
    val connectionType: Esp32ConnectionType = Esp32ConnectionType.Bluetooth,
    val packetFormatVersion: String = "MANET-PACKET-v1",
    val connectionStatus: String = "Simulation placeholder only",
    val lastHandshakeTime: String = "Never",
    val handshakeStatus: String = "Not started"
)

data class BluetoothDeviceState(
    val lifecycleState: BluetoothLifecycleState = BluetoothLifecycleState.Idle,
    val discoveredDevices: List<String> = emptyList(),
    val selectedDevice: String? = null,
    val pairedDevice: String? = null,
    val signalPlaceholder: String = "Simulated -62 dBm"
)

data class BluetoothPacketBridge(
    val packetsSent: Int = 0,
    val packetsReceived: Int = 0,
    val lastOutboundPacket: String = "None",
    val lastInboundPacket: String = "None"
) {
    fun recordOutbound(packetId: String): BluetoothPacketBridge {
        return copy(
            packetsSent = packetsSent + 1,
            lastOutboundPacket = packetId
        )
    }

    fun recordInbound(packetId: String): BluetoothPacketBridge {
        return copy(
            packetsReceived = packetsReceived + 1,
            lastInboundPacket = packetId
        )
    }
}

data class BluetoothConnectionSession(
    val connectedDevice: String? = null,
    val startedAt: Long? = null,
    val lastReconnectAttempt: String = "Never",
    val reconnectCountdownSeconds: Int = 0,
    val retryCounter: Int = 0,
    val timeoutStatus: String = "No timeout"
)

data class BluetoothPermissionStatus(
    val platformLabel: String,
    val manifestPermissions: List<String>,
    val runtimePermissions: List<String>,
    val grantedPermissions: Set<String>,
    val lastRequestStatus: String
) {
    val allRuntimeGranted: Boolean
        get() = runtimePermissions.all { grantedPermissions.contains(it) }
}

data class RealBluetoothSocketState(
    val bondedDevices: List<String> = emptyList(),
    val selectedDevice: String? = null,
    val connectedDevice: String? = null,
    val socketStatus: String = "Not connected",
    val liveTestStatus: String = "Not started",
    val liveTestPassed: Int = 0,
    val liveTestFailed: Int = 0,
    val lastDemoMessage: String = "None",
    val lastSentLine: String = "None",
    val lastReceivedLine: String = "None",
    val lastError: String = "None"
) {
    val connected: Boolean
        get() = connectedDevice != null
}

data class BluetoothProtocolPacket(
    val protocolVersion: String = BLUETOOTH_PROTOCOL_VERSION,
    val packetType: BluetoothProtocolPacketType,
    val packetId: String,
    val sourceNode: String,
    val destinationNode: String,
    val payload: String,
    val hopPath: List<String>,
    val retryCount: Int,
    val timestamp: Long,
    val status: String,
    val checksumPlaceholder: String = CHECKSUM_PLACEHOLDER
)

data class ProtocolValidationStatus(
    val versionValid: Boolean,
    val requiredFieldsPresent: Boolean,
    val packetTypeSupported: Boolean,
    val checksumPlaceholderValid: Boolean
)

data class SimMetrics(
    val rssi: Int,
    val snr: Double,
    val hopCount: Int,
    val gatewayProximity: String,
    val satelliteStatus: String
)

data class RouteScore(
    val total: Int,
    val hopScore: Int,
    val rssiScore: Int,
    val snrScore: Int,
    val nodeHealthScore: Int,
    val gatewayScore: Int,
    val transportScore: Int
)

data class RouteCandidate(
    val name: String,
    val route: RouteLabel,
    val path: List<String>,
    val score: RouteScore,
    val available: Boolean,
    val reason: String
)

data class RoutingDecision(
    val preferredRoute: RouteLabel,
    val selectedRoute: RouteLabel,
    val routeScore: Int,
    val failoverReason: String,
    val availableCandidates: List<RouteCandidate>,
    val allCandidates: List<RouteCandidate>
)

data class RouteDecision(
    val route: RouteLabel,
    val status: MessageStatus,
    val metrics: SimMetrics,
    val path: List<String>,
    val note: String,
    val routingDecision: RoutingDecision
)

data class LoraManetPacket(
    val packetId: String,
    val sourceNodeId: String,
    val destinationNodeId: String,
    val selectedTransport: String,
    val payloadText: String,
    val timestamp: Long,
    val hopPath: List<String>,
    val hopCount: Int,
    val rssi: Int,
    val snr: Double,
    val gatewayStatus: String,
    val satelliteStatus: String,
    val deliveryStatus: String,
    val queuedAt: Long,
    val relayAt: Long? = null,
    val deliveredAt: Long? = null
)

data class PendingPacket(
    val packet: LoraManetPacket,
    val retryCount: Int,
    val status: MessageStatus
)

data class DeliveryTask(
    val packetId: String,
    val route: RouteLabel,
    val maxRetries: Int
)

data class QueueStats(
    val queuedCount: Int,
    val deliveredCount: Int,
    val failedCount: Int,
    val retryCount: Int
)

data class ValidationChecklistItem(
    val label: String,
    val status: ValidationStatus
)

data class ValidationSummary(
    val passedCount: Int,
    val failedCount: Int,
    val notTestedCount: Int
)

data class TransportStatus(
    val activeImplementation: String,
    val connectionState: String,
    val lastPacketSent: String = "None",
    val lastPacketReceived: String = "None"
)

interface ManetTransportInterface {
    fun connect(): TransportStatus
    fun disconnect(): TransportStatus
    fun sendPacket(packet: LoraManetPacket): LoraManetPacket
    fun receivePacket(): LoraManetPacket?
    fun getTransportStatus(): TransportStatus
}

data class SimNode(
    val name: String,
    val rssi: Int,
    val snr: Double,
    val hopCount: Int,
    val gatewayProximity: String,
    val loraAvailable: Boolean,
    val wifiAvailable: Boolean,
    val gsmAvailable: Boolean,
    val satelliteAvailable: Boolean
)

data class ChatMessage(
    val id: Long,
    val text: String,
    val sourceNode: String,
    val targetNode: String,
    val route: RouteLabel,
    val status: MessageStatus,
    val metrics: SimMetrics,
    val path: List<String>,
    val note: String,
    val sentAt: String,
    val progressStep: Int,
    val routeQuality: String,
    val delayMs: Long,
    val packet: LoraManetPacket,
    val retryCount: Int,
    val deliveryProgress: String
)

data class NetworkSimulationUpdate(
    val nodes: List<SimNode>,
    val networkState: NetworkState,
    val condition: SimulationCondition,
    val events: List<String>
)

private val simNodes = listOf(
    SimNode("Node Alpha", -58, 11.0, 0, "Near gateway", true, true, false, true),
    SimNode("Node Bravo", -64, 9.2, 1, "One relay", true, true, true, true),
    SimNode("Node Charlie", -71, 7.5, 2, "Two relays", true, false, true, true),
    SimNode("Node Delta", -78, 5.8, 3, "Edge relay", true, false, true, true),
    SimNode("Gateway Node", -48, 17.4, 0, "Gateway", true, true, true, true)
)

private val fakeEsp32Nodes = listOf(
    "ESP32-MANET-01",
    "ESP32-MANET-02",
    "ESP32-MANET-03"
)

private const val MAX_RETRY_COUNT = 2
private const val BLUETOOTH_PROTOCOL_VERSION = "BT-MANET-1.0"
private const val CHECKSUM_PLACEHOLDER = "checksum pending / simulated"
private val BLUETOOTH_SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

private fun bluetoothManifestPermissionLabels(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf("BLUETOOTH_SCAN", "BLUETOOTH_CONNECT")
    } else {
        listOf("BLUETOOTH", "BLUETOOTH_ADMIN", "ACCESS_FINE_LOCATION")
    }
}

private fun bluetoothRuntimePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun bluetoothPermissionLabel(permission: String): String {
    return permission.substringAfterLast('.')
}

private fun currentBluetoothPermissionStatus(
    context: Context,
    grantResults: Map<String, Boolean> = emptyMap()
): BluetoothPermissionStatus {
    val runtimePermissions = bluetoothRuntimePermissions().toList()
    val grantedPermissions = runtimePermissions.filter { permission ->
        grantResults[permission] == true ||
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }.toSet()

    val lastRequestStatus = when {
        grantResults.isEmpty() -> "Not requested"
        runtimePermissions.all { grantResults[it] == true } -> "Granted"
        else -> "Denied or partially granted"
    }

    return BluetoothPermissionStatus(
        platformLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "Android 12+"
        } else {
            "Android 11 or lower"
        },
        manifestPermissions = bluetoothManifestPermissionLabels(),
        runtimePermissions = runtimePermissions.map(::bluetoothPermissionLabel),
        grantedPermissions = grantedPermissions.map(::bluetoothPermissionLabel).toSet(),
        lastRequestStatus = lastRequestStatus
    )
}

private fun hasBluetoothConnectPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
}

private val validationLabels = listOf(
    "App opens on Chat tab",
    "Message send works",
    "Message queue states work",
    "Routing decision updates",
    "Failover works",
    "Packet log updates",
    "Event log updates",
    "Transport bridge status updates",
    "Bluetooth placeholder pairing works",
    "No battery level appears",
    "Simulation speed control works",
    "Network toggles work"
)

private open class BaseTransport(
    private val implementationName: String,
    private val connectedLabel: String
) : ManetTransportInterface {
    private var connected = false
    private var lastSentPacket: LoraManetPacket? = null
    private var lastReceivedPacket: LoraManetPacket? = null

    override fun connect(): TransportStatus {
        connected = true
        return getTransportStatus()
    }

    override fun disconnect(): TransportStatus {
        connected = false
        return getTransportStatus()
    }

    override fun sendPacket(packet: LoraManetPacket): LoraManetPacket {
        lastSentPacket = packet
        return packet
    }

    override fun receivePacket(): LoraManetPacket? {
        val receivedPacket = lastSentPacket?.copy(deliveryStatus = MessageStatus.Delivered.label)
        lastReceivedPacket = receivedPacket
        return receivedPacket
    }

    override fun getTransportStatus(): TransportStatus {
        return TransportStatus(
            activeImplementation = implementationName,
            connectionState = if (connected) connectedLabel else TransportConnectionState.Disconnected.label,
            lastPacketSent = lastSentPacket?.packetId ?: "None",
            lastPacketReceived = lastReceivedPacket?.packetId ?: "None"
        )
    }
}

private class SimulationTransport : BaseTransport(
    implementationName = "SimulationTransport",
    connectedLabel = TransportConnectionState.Connected.label
)

private class BluetoothTransportPlaceholder : BaseTransport(
    implementationName = "BluetoothTransportPlaceholder",
    connectedLabel = TransportConnectionState.PlaceholderReady.label
)

private class WiFiTransportPlaceholder : BaseTransport(
    implementationName = "WiFiTransportPlaceholder",
    connectedLabel = TransportConnectionState.PlaceholderReady.label
)

private class AndroidBluetoothSocketClient(private val context: Context) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var socket: BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    suspend fun bondedDeviceNames(): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!hasBluetoothConnectPermission(context)) {
            return@withContext Result.failure(IllegalStateException("Bluetooth permission required"))
        }

        val bluetoothAdapter = adapter
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth not supported"))
        if (!bluetoothAdapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth disabled"))
        }

        val names = bluetoothAdapter.bondedDevices
            .mapNotNull { it.name }
            .sorted()
        Result.success(names)
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(deviceName: String): Result<String> = withContext(Dispatchers.IO) {
        if (!hasBluetoothConnectPermission(context)) {
            return@withContext Result.failure(IllegalStateException("Bluetooth permission required"))
        }

        val bluetoothAdapter = adapter
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth not supported"))
        if (!bluetoothAdapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth disabled"))
        }

        val device: BluetoothDevice = bluetoothAdapter.bondedDevices.firstOrNull { it.name == deviceName }
            ?: return@withContext Result.failure(IllegalStateException("Pair ESP32 in Android Settings first"))

        runCatching { socket?.close() }
        bluetoothAdapter.cancelDiscovery()
        val newSocket = device.createRfcommSocketToServiceRecord(BLUETOOTH_SPP_UUID)
        newSocket.connect()
        socket = newSocket
        Result.success(deviceName)
    }

    suspend fun sendLine(line: String): Result<String> = withContext(Dispatchers.IO) {
        val activeSocket = socket
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth socket not connected"))

        runCatching {
            activeSocket.outputStream.write((line + "\n").toByteArray(Charsets.UTF_8))
            activeSocket.outputStream.flush()
            line
        }.onFailure { disconnect() }
    }

    suspend fun readAvailableLine(): Result<String?> = withContext(Dispatchers.IO) {
        val activeSocket = socket
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth socket not connected"))

        runCatching {
            val input = activeSocket.inputStream
            if (input.available() <= 0) {
                return@runCatching null
            }

            readLineFromInput(activeSocket)
        }.onFailure { disconnect() }
    }

    suspend fun waitForIncomingLine(timeoutMs: Long = 5000L): Result<String?> = withContext(Dispatchers.IO) {
        val activeSocket = socket
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth socket not connected"))

        runCatching {
            val deadline = System.currentTimeMillis() + timeoutMs
            val input = activeSocket.inputStream
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {
                    return@runCatching readLineFromInput(activeSocket)
                }
                Thread.sleep(100L)
            }
            null
        }.onFailure { disconnect() }
    }

    suspend fun waitForIncomingMessageLine(timeoutMs: Long = 7000L): Result<String?> = withContext(Dispatchers.IO) {
        val activeSocket = socket
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth socket not connected"))

        runCatching {
            val deadline = System.currentTimeMillis() + timeoutMs
            val input = activeSocket.inputStream
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {
                    val line = readLineFromInput(activeSocket)
                    if (line.contains("\"packetType\":\"MESSAGE\"") && line.contains("RECEIVED_OVER_LORA")) {
                        return@runCatching line
                    }
                }
                Thread.sleep(100L)
            }
            null
        }.onFailure { disconnect() }
    }

    suspend fun sendLineAndWaitForResponse(
        line: String,
        timeoutMs: Long = 3000L
    ): Result<Pair<String, String?>> = withContext(Dispatchers.IO) {
        val activeSocket = socket
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth socket not connected"))

        runCatching {
            drainAvailableLines(activeSocket)
            activeSocket.outputStream.write((line + "\n").toByteArray(Charsets.UTF_8))
            activeSocket.outputStream.flush()

            val deadline = System.currentTimeMillis() + timeoutMs
            val input = activeSocket.inputStream
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {
                    return@runCatching line to readLineFromInput(activeSocket)
                }
                Thread.sleep(100L)
            }
            line to null
        }.onFailure { disconnect() }
    }

    fun disconnect() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun readLineFromInput(activeSocket: BluetoothSocket): String {
        val input = activeSocket.inputStream
        val bytes = mutableListOf<Byte>()
        while (input.available() > 0) {
            val value = input.read()
            if (value < 0 || value.toChar() == '\n') {
                break
            }
            if (value.toChar() != '\r') {
                bytes.add(value.toByte())
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private fun drainAvailableLines(activeSocket: BluetoothSocket) {
        val input = activeSocket.inputStream
        while (input.available() > 0) {
            readLineFromInput(activeSocket)
        }
    }
}

/*
 * Future Bluetooth layer:
 * Android Bluetooth API -> BluetoothTransportManager -> BluetoothPacketBridge -> MANET routing engine.
 * This class is intentionally platform-free in Step 014; it models lifecycle transitions only.
 */
private class BluetoothTransportManager {
    fun scan(state: BluetoothDeviceState): BluetoothDeviceState {
        return state.copy(
            lifecycleState = BluetoothLifecycleState.Scanning,
            discoveredDevices = fakeEsp32Nodes,
            selectedDevice = state.selectedDevice ?: fakeEsp32Nodes.first()
        )
    }

    fun pair(state: BluetoothDeviceState): BluetoothDeviceState {
        val selected = state.selectedDevice ?: state.discoveredDevices.firstOrNull()
        return if (selected == null) {
            state.copy(lifecycleState = BluetoothLifecycleState.Failed)
        } else {
            state.copy(
                lifecycleState = BluetoothLifecycleState.Pairing,
                selectedDevice = selected,
                pairedDevice = selected
            )
        }
    }

    fun connect(
        state: BluetoothDeviceState,
        session: BluetoothConnectionSession
    ): Pair<BluetoothDeviceState, BluetoothConnectionSession> {
        val device = state.pairedDevice ?: state.selectedDevice
        return if (device == null) {
            state.copy(lifecycleState = BluetoothLifecycleState.Failed) to session.copy(
                timeoutStatus = "No paired ESP32 selected"
            )
        } else {
            state.copy(
                lifecycleState = BluetoothLifecycleState.Connected,
                pairedDevice = device,
                selectedDevice = device
            ) to session.copy(
                connectedDevice = device,
                startedAt = System.currentTimeMillis(),
                reconnectCountdownSeconds = 0,
                timeoutStatus = "Connected"
            )
        }
    }

    fun disconnect(
        state: BluetoothDeviceState,
        session: BluetoothConnectionSession
    ): Pair<BluetoothDeviceState, BluetoothConnectionSession> {
        return state.copy(lifecycleState = BluetoothLifecycleState.Disconnected) to session.copy(
            connectedDevice = null,
            startedAt = null,
            reconnectCountdownSeconds = 0,
            timeoutStatus = "Disconnected"
        )
    }

    fun failForTimeout(
        state: BluetoothDeviceState,
        session: BluetoothConnectionSession
    ): Pair<BluetoothDeviceState, BluetoothConnectionSession> {
        return state.copy(lifecycleState = BluetoothLifecycleState.Failed) to session.copy(
            connectedDevice = null,
            startedAt = null,
            timeoutStatus = "Connection timeout"
        )
    }

    fun scheduleReconnect(
        state: BluetoothDeviceState,
        session: BluetoothConnectionSession
    ): Pair<BluetoothDeviceState, BluetoothConnectionSession> {
        return state.copy(lifecycleState = BluetoothLifecycleState.Retrying) to session.copy(
            lastReconnectAttempt = currentTimeLabel(),
            reconnectCountdownSeconds = 3,
            retryCounter = session.retryCounter + 1,
            timeoutStatus = "Reconnect scheduled"
        )
    }
}

private class MessageQueueManager(
    val maxRetryCount: Int = MAX_RETRY_COUNT
) {
    fun routingDelayMs(condition: SimulationCondition): Long {
        return when (condition) {
            SimulationCondition.Stable -> 450L
            SimulationCondition.Recovering -> 700L
            SimulationCondition.Congested -> 1100L
            SimulationCondition.Partitioned -> 1500L
        }
    }

    fun relayDelayMs(route: RouteLabel, hopCount: Int, condition: SimulationCondition): Long {
        val routeBase = when (route) {
            RouteLabel.Lora -> 520L
            RouteLabel.Wifi -> 360L
            RouteLabel.Gsm -> 780L
            RouteLabel.Satellite -> 980L
            RouteLabel.None -> 0L
        }
        val congestionPenalty = when (condition) {
            SimulationCondition.Stable -> 0L
            SimulationCondition.Recovering -> 240L
            SimulationCondition.Congested -> 560L
            SimulationCondition.Partitioned -> 900L
        }
        return routeBase + (hopCount * 260L) + congestionPenalty
    }

    fun retryDelayMs(retryCount: Int): Long {
        return 650L + (retryCount * 350L)
    }

    fun shouldDropPacket(condition: SimulationCondition, route: RouteLabel): Boolean {
        if (route == RouteLabel.None) {
            return true
        }
        val baseChance = when (condition) {
            SimulationCondition.Stable -> 4
            SimulationCondition.Recovering -> 10
            SimulationCondition.Congested -> 18
            SimulationCondition.Partitioned -> 35
        }
        val routePenalty = when (route) {
            RouteLabel.Lora -> 3
            RouteLabel.Wifi -> 2
            RouteLabel.Gsm -> 7
            RouteLabel.Satellite -> 10
            RouteLabel.None -> 100
        }
        return Random.nextInt(100) < baseChance + routePenalty
    }

    fun retryRoutingDecision(
        routingDecision: RoutingDecision,
        failedRoute: RouteLabel
    ): RoutingDecision {
        val nextCandidate = routingDecision.availableCandidates
            .filter { it.route != failedRoute }
            .maxByOrNull { it.score.total }

        return if (nextCandidate == null) {
            routingDecision.copy(
                selectedRoute = RouteLabel.None,
                routeScore = 0,
                failoverReason = "No retry route available after ${failedRoute.label} failed."
            )
        } else {
            routingDecision.copy(
                selectedRoute = nextCandidate.route,
                routeScore = nextCandidate.score.total,
                failoverReason = "Failover to ${nextCandidate.route.label} after ${failedRoute.label} delivery timeout."
            )
        }
    }

    fun stats(messages: List<ChatMessage>): QueueStats {
        return QueueStats(
            queuedCount = messages.count {
                it.status == MessageStatus.Queued ||
                    it.status == MessageStatus.Routing ||
                    it.status == MessageStatus.Relaying ||
                    it.status == MessageStatus.Retrying
            },
            deliveredCount = messages.count { it.status == MessageStatus.Delivered },
            failedCount = messages.count { it.status == MessageStatus.Failed },
            retryCount = messages.sumOf { it.retryCount }
        )
    }
}

private class AdaptiveRoutingEngine {
    fun decide(
        selectedNetwork: NetworkMode,
        networkState: NetworkState,
        localNode: SimNode,
        targetNode: SimNode,
        nodes: List<SimNode> = simNodes
    ): RoutingDecision {
        val path = routePath(localNode, targetNode, nodes)
        val preferred = preferredRoute(selectedNetwork)
        val allCandidates = listOf(
            RouteLabel.Lora,
            RouteLabel.Wifi,
            RouteLabel.Gsm,
            RouteLabel.Satellite
        ).map { route ->
            buildCandidate(route, path, networkState, targetNode)
        }
        val consideredRoutes = failoverOrder(selectedNetwork)
        val availableCandidates = allCandidates.filter { candidate ->
            candidate.available && candidate.route in consideredRoutes
        }
        val selectedCandidate = availableCandidates.maxWithOrNull(
            compareBy<RouteCandidate> { it.score.total }
                .thenBy { -consideredRoutes.indexOf(it.route) }
        )
        val selectedRoute = selectedCandidate?.route ?: RouteLabel.None
        val preferredCandidate = allCandidates.firstOrNull { it.route == preferred }
        val failoverReason = when {
            selectedRoute == RouteLabel.None -> "No route candidate is available across the simulated MANET path."
            selectedRoute == preferred -> "Preferred route selected with the strongest available score."
            preferredCandidate?.available == false -> "Failover from ${preferred.label}: ${preferredCandidate.reason}"
            else -> "Adaptive scoring selected ${selectedRoute.label} over ${preferred.label}."
        }

        return RoutingDecision(
            preferredRoute = preferred,
            selectedRoute = selectedRoute,
            routeScore = selectedCandidate?.score?.total ?: 0,
            failoverReason = failoverReason,
            availableCandidates = availableCandidates,
            allCandidates = allCandidates
        )
    }

    private fun buildCandidate(
        route: RouteLabel,
        path: List<SimNode>,
        networkState: NetworkState,
        targetNode: SimNode
    ): RouteCandidate {
        val hopCount = (path.size - 1).coerceAtLeast(0)
        val globalAvailable = isRouteGloballyAvailable(route, networkState)
        val pathSupportsRoute = path.all { nodeRouteAvailable(it, route) }
        val routeAvailable = globalAvailable && pathSupportsRoute && path.size > 1
        val averageRssi = path.map { it.rssi }.average().toInt()
        val averageSnr = path.map { it.snr }.average()
        val healthyNodes = path.count { nodeHealth(it, networkState) == "Healthy" }
        val gatewayAvailable = path.any { it.name == "Gateway Node" && nodeHasAnyAvailableRoute(it, networkState) } ||
            targetNode.gatewayProximity == "Gateway"
        val score = if (routeAvailable) {
            val hopScore = ((6 - hopCount).coerceAtLeast(0)) * 25
            val rssiScore = (averageRssi + 100).coerceIn(0, 80)
            val snrScore = (averageSnr * 6.0).toInt().coerceIn(0, 120)
            val nodeHealthScore = healthyNodes * 15
            val gatewayScore = if (gatewayAvailable) 30 else 0
            val transportScore = 35
            RouteScore(
                total = hopScore + rssiScore + snrScore + nodeHealthScore + gatewayScore + transportScore,
                hopScore = hopScore,
                rssiScore = rssiScore,
                snrScore = snrScore,
                nodeHealthScore = nodeHealthScore,
                gatewayScore = gatewayScore,
                transportScore = transportScore
            )
        } else {
            RouteScore(0, 0, 0, 0, 0, 0, 0)
        }
        val reason = when {
            path.size < 2 -> "Source and destination are the same."
            !globalAvailable -> "${route.label} transport is simulated down."
            !pathSupportsRoute -> "One or more nodes on the path cannot use ${route.label}."
            routeAvailable -> "Available; scored by hop count, RSSI, SNR, node health, gateway availability, and transport link state."
            else -> "Unavailable."
        }

        return RouteCandidate(
            name = candidateName(route, hopCount),
            route = route,
            path = path.map { it.name },
            score = score,
            available = routeAvailable,
            reason = reason
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PUPMANETMessengerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MessengerApp()
                }
            }
        }
    }
}

@Composable
fun MessengerApp() {
    val context = LocalContext.current
    var selectedNetwork by remember { mutableStateOf(NetworkMode.Auto) }
    var networkState by remember { mutableStateOf(NetworkState()) }
    var selectedTab by remember { mutableStateOf(AppTab.Messaging) }
    var bluetoothState by remember { mutableStateOf(BluetoothState()) }
    var simulatedNodes by remember { mutableStateOf(simNodes) }
    var localNode by remember { mutableStateOf(simNodes.first()) }
    var targetNode by remember { mutableStateOf(simNodes.last()) }
    var simulationSpeed by remember { mutableStateOf(SimulationSpeed.Normal) }
    var simulationCondition by remember { mutableStateOf(SimulationCondition.Stable) }
    var draftMessage by remember { mutableStateOf("") }
    var nextMessageId by remember { mutableStateOf(1L) }
    var selectedTransport by remember { mutableStateOf(TransportOption.Simulation) }
    var hardwareMode by remember { mutableStateOf(HardwareMode.Simulation) }
    var esp32BridgeConfig by remember { mutableStateOf(Esp32BridgeConfig()) }
    var bluetoothDeviceState by remember { mutableStateOf(BluetoothDeviceState()) }
    var bluetoothPacketBridge by remember { mutableStateOf(BluetoothPacketBridge()) }
    var bluetoothSession by remember { mutableStateOf(BluetoothConnectionSession()) }
    var bluetoothPermissionStatus by remember {
        mutableStateOf(currentBluetoothPermissionStatus(context))
    }
    var realBluetoothSocketState by remember { mutableStateOf(RealBluetoothSocketState()) }
    var demoLoRaMessage by remember { mutableStateOf("Emergency message from Phone A") }
    var validationItems by remember { mutableStateOf(defaultValidationItems()) }
    var activeTransport by remember {
        mutableStateOf<ManetTransportInterface>(transportFor(TransportOption.Simulation))
    }
    var transportStatus by remember { mutableStateOf(activeTransport.connect()) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val packetLog = remember { mutableStateListOf<LoraManetPacket>() }
    val eventLog = remember { mutableStateListOf<String>() }
    val queueScope = rememberCoroutineScope()
    val queueManager = remember { MessageQueueManager() }
    val routingEngine = remember { AdaptiveRoutingEngine() }
    val bluetoothTransportManager = remember { BluetoothTransportManager() }
    val androidBluetoothSocketClient = remember { AndroidBluetoothSocketClient(context.applicationContext) }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        bluetoothPermissionStatus = currentBluetoothPermissionStatus(
            context = context,
            grantResults = grantResults
        )
    }
    val routedNetworkState = effectiveNetworkState(networkState, bluetoothState)
    val adaptiveRoutingDecision = routingEngine.decide(
        selectedNetwork = selectedNetwork,
        networkState = routedNetworkState,
        localNode = localNode,
        targetNode = targetNode,
        nodes = simulatedNodes
    )
    val previewDecision = decideRoute(
        selectedNetwork = selectedNetwork,
        networkState = routedNetworkState,
        bluetoothState = bluetoothState,
        localNode = localNode,
        targetNode = targetNode,
        adaptiveRoutingDecision = adaptiveRoutingDecision,
        nodes = simulatedNodes
    )
    val outgoingPacketPreview = packetLog.firstOrNull() ?: messageToPacket(
        packetId = "PKT-PREVIEW",
        payloadText = draftMessage.ifBlank { "<message text>" },
        localNode = localNode,
        targetNode = targetNode,
        decision = previewDecision
    )

    LaunchedEffect(simulationSpeed) {
        while (true) {
            delay(simulationSpeed.tickMs)
            val update = nextNetworkSimulationState(
                nodes = simulatedNodes,
                networkState = networkState,
                speed = simulationSpeed
            )
            simulatedNodes = update.nodes
            networkState = update.networkState
            simulationCondition = update.condition
            localNode = update.nodes.firstOrNull { it.name == localNode.name } ?: update.nodes.first()
            targetNode = update.nodes.firstOrNull { it.name == targetNode.name } ?: update.nodes.last()
            update.events.asReversed().forEach { event ->
                eventLog.add(0, event)
            }
            while (eventLog.size > 10) {
                eventLog.removeAt(eventLog.lastIndex)
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (selectedTab == AppTab.Messaging) {
                MessageComposer(
                    draftMessage = draftMessage,
                    onDraftChange = { draftMessage = it },
                    onSend = {
                        val trimmedMessage = draftMessage.trim()
                        if (trimmedMessage.isNotEmpty()) {
                            val decision = decideRoute(
                                selectedNetwork = selectedNetwork,
                                networkState = routedNetworkState,
                                bluetoothState = bluetoothState,
                                localNode = localNode,
                                targetNode = targetNode,
                                adaptiveRoutingDecision = adaptiveRoutingDecision,
                                nodes = simulatedNodes
                            )
                            val messageId = nextMessageId++
                            val delayMs = simulatedDelayMs(decision.route, decision.metrics)
                            val packet = messageToPacket(
                                packetId = packetIdFor(messageId),
                                payloadText = trimmedMessage,
                                localNode = localNode,
                                targetNode = targetNode,
                                decision = decision
                            )

                            val isLiveBridgeMode = realBluetoothSocketState.connected && decision.route == RouteLabel.Lora
                            if (isLiveBridgeMode) {
                                val destinationNode = peerNodeForConnectedEsp32(realBluetoothSocketState.connectedDevice)
                                val btPacket = createBluetoothProtocolPacket(
                                    packetType = BluetoothProtocolPacketType.Message,
                                    payload = "MODE=LORA;TEXT=${sanitizeDemoMessageText(trimmedMessage)}",
                                    destinationNode = destinationNode,
                                    status = "QUEUED_FOR_LORA"
                                )
                                val line = compactSerializedPacketText(btPacket)
                                messages.add(
                                    packetToChatMessage(
                                        messageId = messageId,
                                        packet = packet.copy(
                                            deliveryStatus = MessageStatus.Relaying.label,
                                            selectedTransport = RouteLabel.Lora.label
                                        ),
                                        decision = decision,
                                        sourceNodeName = localNode.name,
                                        targetNodeName = targetNode.name,
                                        note = "Live bridge to ${realBluetoothSocketState.connectedDevice} -> LoRa -> $destinationNode",
                                        sentAt = currentTimeLabel(),
                                        delayMs = delayMs
                                    )
                                )
                                draftMessage = ""
                                packetLog.add(0, packet)
                                if (packetLog.size > 8) {
                                    packetLog.removeAt(packetLog.lastIndex)
                                }
                                queueScope.launch {
                                    val result = androidBluetoothSocketClient.sendLineAndWaitForResponse(line, 7000L)
                                    val exchangeIndex = messages.indexOfFirst { it.id == messageId }
                                    if (exchangeIndex >= 0) {
                                        result.fold(
                                            onSuccess = { exchange ->
                                                val response = exchange.second
                                                val forwarded = response?.contains("FORWARDED_OVER_LORA") == true
                                                val finalPacket = messages[exchangeIndex].packet.copy(
                                                    deliveryStatus = if (forwarded) MessageStatus.Delivered.label else MessageStatus.Failed.label,
                                                    selectedTransport = RouteLabel.Lora.label
                                                )
                                                messages[exchangeIndex] = messages[exchangeIndex].copy(
                                                    status = if (forwarded) MessageStatus.Delivered else MessageStatus.Failed,
                                                    packet = finalPacket,
                                                    deliveryProgress = if (forwarded) "Forwarded over LoRa" else "Bridge ACK: ${response ?: "No response"}",
                                                    note = if (forwarded) "Live bridge forwarded to LoRa" else "Live bridge did not confirm LoRa forwarding"
                                                )
                                                bluetoothPacketBridge = bluetoothPacketBridge.recordOutbound(btPacket.packetId)
                                                if (forwarded) {
                                                    bluetoothPacketBridge = bluetoothPacketBridge.recordInbound("LORA_ACK")
                                                }
                                                updatePacketLog(packetLog, finalPacket)
                                                eventLog.add(0, "Live bridge ${if (forwarded) "forwarded" else "failed"} ${packet.packetId}")
                                                while (eventLog.size > 10) {
                                                    eventLog.removeAt(eventLog.lastIndex)
                                                }
                                            },
                                            onFailure = { error ->
                                                val failedPacket = messages[exchangeIndex].packet.copy(
                                                    deliveryStatus = MessageStatus.Failed.label
                                                )
                                                messages[exchangeIndex] = messages[exchangeIndex].copy(
                                                    status = MessageStatus.Failed,
                                                    packet = failedPacket,
                                                    deliveryProgress = "Bridge send failed: ${error.message ?: "Unknown error"}"
                                                )
                                                updatePacketLog(packetLog, failedPacket)
                                                eventLog.add(0, "Live bridge send failed for ${packet.packetId}")
                                                while (eventLog.size > 10) {
                                                    eventLog.removeAt(eventLog.lastIndex)
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                                val transportForMessage = activeTransport
                                val sentPacket = transportForMessage.sendPacket(packet)
                                if (bluetoothDeviceState.lifecycleState == BluetoothLifecycleState.Connected) {
                                    bluetoothPacketBridge = bluetoothPacketBridge.recordOutbound(sentPacket.packetId)
                                }
                                transportStatus = transportForMessage.getTransportStatus()
                                messages.add(
                                    packetToChatMessage(
                                        messageId = messageId,
                                        packet = sentPacket,
                                        decision = decision,
                                        sourceNodeName = localNode.name,
                                        targetNodeName = targetNode.name,
                                        note = decision.note,
                                        sentAt = currentTimeLabel(),
                                        delayMs = delayMs
                                    )
                                )
                                packetLog.add(0, sentPacket)
                                if (packetLog.size > 8) {
                                    packetLog.removeAt(packetLog.lastIndex)
                                }
                                draftMessage = ""
                                if (decision.route != RouteLabel.None) {
                                    queueScope.launch {
                                        var attempt = 0
                                        var currentDecision = decision
                                        var delivered = false

                                        while (attempt <= queueManager.maxRetryCount && !delivered) {
                                            val task = DeliveryTask(
                                                packetId = sentPacket.packetId,
                                                route = currentDecision.route,
                                                maxRetries = queueManager.maxRetryCount
                                            )
                                            val routingIndex = messages.indexOfFirst { it.id == messageId }
                                            if (routingIndex < 0) {
                                                break
                                            }
                                            val routingPacket = messages[routingIndex].packet.copy(
                                                deliveryStatus = MessageStatus.Routing.label
                                            )
                                            messages[routingIndex] = messages[routingIndex].copy(
                                                status = MessageStatus.Routing,
                                                packet = routingPacket,
                                                retryCount = attempt,
                                                deliveryProgress = "Routing via ${task.route.label}..."
                                            )
                                            updatePacketLog(packetLog, routingPacket)

                                            delay(queueManager.routingDelayMs(simulationCondition))

                                            val liveNetworkState = effectiveNetworkState(networkState, bluetoothState)
                                            val liveRouting = routingEngine.decide(
                                                selectedNetwork = selectedNetwork,
                                                networkState = liveNetworkState,
                                                localNode = localNode,
                                                targetNode = targetNode,
                                                nodes = simulatedNodes
                                            )
                                            val selectedRouting = if (queueManager.shouldDropPacket(simulationCondition, currentDecision.route)) {
                                                queueManager.retryRoutingDecision(liveRouting, currentDecision.route)
                                            } else {
                                                liveRouting
                                            }
                                            val liveDecision = decideRoute(
                                                selectedNetwork = selectedNetwork,
                                                networkState = liveNetworkState,
                                                bluetoothState = bluetoothState,
                                                localNode = localNode,
                                                targetNode = targetNode,
                                                adaptiveRoutingDecision = selectedRouting,
                                                nodes = simulatedNodes
                                            )

                                            if (liveDecision.route == RouteLabel.None) {
                                                attempt += 1
                                                val retryIndex = messages.indexOfFirst { it.id == messageId }
                                                if (retryIndex < 0) {
                                                    break
                                                }
                                                val retryStatus = if (attempt > queueManager.maxRetryCount) {
                                                    MessageStatus.Failed
                                                } else {
                                                    MessageStatus.Retrying
                                                }
                                                val retryPacket = messages[retryIndex].packet.copy(
                                                    selectedTransport = liveDecision.route.label,
                                                    deliveryStatus = retryStatus.label
                                                )
                                                messages[retryIndex] = messages[retryIndex].copy(
                                                    route = liveDecision.route,
                                                    status = retryStatus,
                                                    metrics = liveDecision.metrics,
                                                    path = liveDecision.path,
                                                    note = liveDecision.note,
                                                    packet = retryPacket,
                                                    retryCount = attempt,
                                                    deliveryProgress = if (retryStatus == MessageStatus.Failed) {
                                                        "Failed after $attempt retries."
                                                    } else {
                                                        "Retrying route discovery..."
                                                    }
                                                )
                                                updatePacketLog(packetLog, retryPacket)
                                                eventLog.add(0, "Retry $attempt for ${sentPacket.packetId}: ${liveDecision.note}")
                                                if (retryStatus == MessageStatus.Failed) {
                                                    break
                                                }
                                                delay(queueManager.retryDelayMs(attempt))
                                                currentDecision = liveDecision
                                                continue
                                            }

                                            val relayIndex = messages.indexOfFirst { it.id == messageId }
                                            if (relayIndex < 0) {
                                                break
                                            }
                                            val relayingPacket = messages[relayIndex].packet.copy(
                                                selectedTransport = liveDecision.route.label,
                                                deliveryStatus = MessageStatus.Relaying.label,
                                                relayAt = System.currentTimeMillis() / 1000L
                                            )
                                            val relayNode = liveDecision.path.drop(1).dropLast(1).firstOrNull() ?: liveDecision.path.lastOrNull().orEmpty()
                                            messages[relayIndex] = messages[relayIndex].copy(
                                                route = liveDecision.route,
                                                status = MessageStatus.Relaying,
                                                metrics = liveDecision.metrics,
                                                path = liveDecision.path,
                                                note = liveDecision.note,
                                                progressStep = if (liveDecision.path.size > 1) 1 else 0,
                                                packet = relayingPacket,
                                                retryCount = attempt,
                                                deliveryProgress = if (relayNode.isBlank()) {
                                                    "Relaying..."
                                                } else {
                                                    "Relaying through $relayNode..."
                                                }
                                            )
                                            updatePacketLog(packetLog, relayingPacket)

                                            delay(queueManager.relayDelayMs(liveDecision.route, liveDecision.metrics.hopCount, simulationCondition))

                                            val deliveredIndex = messages.indexOfFirst { it.id == messageId }
                                            if (deliveredIndex < 0) {
                                                break
                                            }
                                            if (queueManager.shouldDropPacket(simulationCondition, liveDecision.route)) {
                                                attempt += 1
                                                val retryPacket = messages[deliveredIndex].packet.copy(
                                                    deliveryStatus = if (attempt > queueManager.maxRetryCount) {
                                                        MessageStatus.Failed.label
                                                    } else {
                                                        MessageStatus.Retrying.label
                                                    }
                                                )
                                                val retryStatus = if (attempt > queueManager.maxRetryCount) {
                                                    MessageStatus.Failed
                                                } else {
                                                    MessageStatus.Retrying
                                                }
                                                messages[deliveredIndex] = messages[deliveredIndex].copy(
                                                    status = retryStatus,
                                                    packet = retryPacket,
                                                    retryCount = attempt,
                                                    deliveryProgress = if (retryStatus == MessageStatus.Failed) {
                                                        "Packet dropped after retry limit."
                                                    } else {
                                                        "Retrying after packet drop..."
                                                    }
                                                )
                                                updatePacketLog(packetLog, retryPacket)
                                                eventLog.add(0, "Packet drop on ${liveDecision.route.label}; retry $attempt for ${sentPacket.packetId}")
                                                if (retryStatus == MessageStatus.Failed) {
                                                    break
                                                }
                                                currentDecision = liveDecision
                                                delay(queueManager.retryDelayMs(attempt))
                                                continue
                                            }

                                            transportForMessage.receivePacket()
                                            if (bluetoothDeviceState.lifecycleState == BluetoothLifecycleState.Connected) {
                                                bluetoothPacketBridge = bluetoothPacketBridge.recordInbound(sentPacket.packetId)
                                            }
                                            val deliveredPacket = messages[deliveredIndex].packet.copy(
                                                selectedTransport = liveDecision.route.label,
                                                deliveryStatus = MessageStatus.Delivered.label,
                                                deliveredAt = System.currentTimeMillis() / 1000L
                                            )
                                            messages[deliveredIndex] = messages[deliveredIndex].copy(
                                                route = liveDecision.route,
                                                status = MessageStatus.Delivered,
                                                metrics = liveDecision.metrics,
                                                path = liveDecision.path,
                                                note = liveDecision.note,
                                                progressStep = (liveDecision.path.size - 1).coerceAtLeast(0),
                                                packet = deliveredPacket,
                                                retryCount = attempt,
                                                deliveryProgress = "Delivered"
                                            )
                                            updatePacketLog(packetLog, deliveredPacket)
                                            if (activeTransport === transportForMessage) {
                                                transportStatus = transportForMessage.getTransportStatus()
                                            }
                                            delivered = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding)
        ) {
            MainTabRow(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    AppTab.Messaging -> {
                        item { AppHeader() }
                        item { CurrentRouteSummary(routingDecision = adaptiveRoutingDecision) }
                        if (messages.isEmpty()) {
                            item { EmptyMessageState() }
                        } else {
                            items(messages, key = { it.id }) { message ->
                                MessageBubble(message = message)
                            }
                        }
                    }
                    AppTab.Network -> {
                        item {
                            NetworkSelector(
                                selectedNetwork = selectedNetwork,
                                onNetworkSelected = { selectedNetwork = it }
                            )
                        }
                        item {
                            NodeSelector(
                                nodes = simulatedNodes,
                                localNode = localNode,
                                targetNode = targetNode,
                                onLocalNodeSelected = { selected ->
                                    localNode = selected
                                    if (targetNode.name == selected.name) {
                                        targetNode = simulatedNodes.first { it.name != selected.name }
                                    }
                                },
                                onTargetNodeSelected = { targetNode = it }
                            )
                        }
                        item {
                            NodeSummaryPanel(
                                localNode = localNode,
                                targetNode = targetNode,
                                networkState = routedNetworkState
                            )
                        }
                        item {
                            TopologyOverviewPanel(
                                localNode = localNode,
                                targetNode = targetNode,
                                networkState = routedNetworkState,
                                nodes = simulatedNodes
                            )
                        }
                    }
                    AppTab.Routing -> {
                        item { RoutingDecisionPanel(routingDecision = adaptiveRoutingDecision) }
                        item {
                            TransportBridgePanel(
                                selectedTransport = selectedTransport,
                                transportStatus = transportStatus,
                                onTransportSelected = { option ->
                                    activeTransport.disconnect()
                                    val nextTransport = transportFor(option)
                                    selectedTransport = option
                                    activeTransport = nextTransport
                                    transportStatus = nextTransport.connect()
                                }
                            )
                        }
                        item {
                            Esp32TransportPreparationPanel(
                                hardwareMode = hardwareMode,
                                esp32BridgeConfig = esp32BridgeConfig,
                                packetPreview = outgoingPacketPreview,
                                bluetoothState = bluetoothState,
                                onHardwareModeSelected = { selectedMode ->
                                    hardwareMode = if (selectedMode == HardwareMode.Simulation) {
                                        HardwareMode.Simulation
                                    } else {
                                        HardwareMode.HardwareDisabled
                                    }
                                },
                                onBridgeConfigChanged = { esp32BridgeConfig = it },
                                onSendHello = {
                                    esp32BridgeConfig = esp32BridgeConfig.copy(
                                        connectionStatus = "Waiting for ESP32_ACK (simulated)",
                                        handshakeStatus = "HELLO sent"
                                    )
                                    eventLog.add(0, "ESP32 HELLO sent in placeholder mode.")
                                    while (eventLog.size > 10) {
                                        eventLog.removeAt(eventLog.lastIndex)
                                    }
                                    queueScope.launch {
                                        delay(650L)
                                        esp32BridgeConfig = esp32BridgeConfig.copy(
                                            connectionStatus = "ESP32_ACK received (simulated)",
                                            lastHandshakeTime = currentTimeLabel(),
                                            handshakeStatus = "ESP32_ACK received"
                                        )
                                        eventLog.add(0, "ESP32_ACK received from simulated bridge.")
                                        while (eventLog.size > 10) {
                                            eventLog.removeAt(eventLog.lastIndex)
                                        }
                                    }
                                }
                            )
                        }
                        item {
                            BluetoothProtocolPreviewPanel(
                                outgoingPacket = protocolPacketFromManetPacket(
                                    packet = outgoingPacketPreview,
                                    packetType = BluetoothProtocolPacketType.Message,
                                    retryCount = messages.firstOrNull { it.packet.packetId == outgoingPacketPreview.packetId }?.retryCount ?: 0
                                ),
                                incomingPacket = sampleIncomingAckPacket(outgoingPacketPreview)
                            )
                        }
                    }
                    AppTab.Simulation -> {
                        item {
                            BluetoothPanel(
                                bluetoothState = bluetoothState,
                                bluetoothDeviceState = bluetoothDeviceState,
                                bluetoothSession = bluetoothSession,
                                bluetoothPacketBridge = bluetoothPacketBridge,
                                bluetoothPermissionStatus = bluetoothPermissionStatus,
                                realBluetoothSocketState = realBluetoothSocketState,
                                demoLoRaMessage = demoLoRaMessage,
                                onRequestBluetoothPermissions = {
                                    bluetoothPermissionLauncher.launch(bluetoothRuntimePermissions())
                                },
                                onRefreshBondedDevices = {
                                    queueScope.launch {
                                        val result = androidBluetoothSocketClient.bondedDeviceNames()
                                        realBluetoothSocketState = result.fold(
                                            onSuccess = { devices ->
                                                realBluetoothSocketState.copy(
                                                    bondedDevices = devices,
                                                    selectedDevice = realBluetoothSocketState.selectedDevice
                                                        ?: devices.firstOrNull(),
                                                    socketStatus = if (devices.isEmpty()) {
                                                        "No paired devices found"
                                                    } else {
                                                        "Paired devices loaded"
                                                    },
                                                    lastError = "None"
                                                )
                                            },
                                            onFailure = { error ->
                                                realBluetoothSocketState.copy(
                                                    socketStatus = "Refresh failed",
                                                    lastError = error.message ?: "Unknown Bluetooth error"
                                                )
                                            }
                                        )
                                    }
                                },
                                onRealDeviceSelected = { device ->
                                    realBluetoothSocketState = realBluetoothSocketState.copy(selectedDevice = device)
                                },
                                onRealSocketConnect = {
                                    val device = realBluetoothSocketState.selectedDevice
                                    if (device == null) {
                                        realBluetoothSocketState = realBluetoothSocketState.copy(
                                            socketStatus = "Select paired ESP32 first",
                                            lastError = "No paired device selected"
                                        )
                                    } else {
                                        realBluetoothSocketState = realBluetoothSocketState.copy(
                                            socketStatus = "Connecting to $device",
                                            lastError = "None"
                                        )
                                        queueScope.launch {
                                            val result = androidBluetoothSocketClient.connect(device)
                                            realBluetoothSocketState = result.fold(
                                                onSuccess = { connectedDevice ->
                                                    realBluetoothSocketState.copy(
                                                        connectedDevice = connectedDevice,
                                                        socketStatus = "Connected",
                                                        lastError = "None"
                                                    )
                                                },
                                                onFailure = { error ->
                                                    realBluetoothSocketState.copy(
                                                        connectedDevice = null,
                                                        socketStatus = "Connection failed",
                                                        lastError = error.message ?: "Unknown Bluetooth error"
                                                    )
                                                }
                                            )
                                        }
                                    }
                                },
                                onRealSocketHello = {
                                    val packet = createBluetoothProtocolPacket(
                                        packetType = BluetoothProtocolPacketType.Hello,
                                        payload = "HELLO"
                                    )
                                    val serialized = compactSerializedPacketText(packet)
                                    queueScope.launch {
                                        val sent = androidBluetoothSocketClient.sendLine(serialized)
                                        realBluetoothSocketState = sent.fold(
                                            onSuccess = { line ->
                                                bluetoothPacketBridge = bluetoothPacketBridge.recordOutbound(packet.packetId)
                                                realBluetoothSocketState.copy(
                                                    socketStatus = "HELLO sent",
                                                    lastSentLine = line,
                                                    lastError = "None"
                                                )
                                            },
                                            onFailure = { error ->
                                                realBluetoothSocketState.copy(
                                                    socketStatus = "HELLO failed",
                                                    connectedDevice = null,
                                                    lastError = error.message ?: "Unknown Bluetooth error"
                                                )
                                            }
                                        )

                                        val received = androidBluetoothSocketClient.readAvailableLine()
                                        realBluetoothSocketState = received.fold(
                                            onSuccess = { line ->
                                                if (line.isNullOrBlank()) {
                                                    realBluetoothSocketState
                                                } else {
                                                    bluetoothPacketBridge = bluetoothPacketBridge.recordInbound("BT_RESPONSE")
                                                    realBluetoothSocketState.copy(lastReceivedLine = line)
                                                }
                                            },
                                            onFailure = { error ->
                                                realBluetoothSocketState.copy(
                                                    connectedDevice = null,
                                                    socketStatus = "Disconnected - reconnect ESP32",
                                                    lastError = error.message ?: "Unknown Bluetooth read error"
                                                )
                                            }
                                        )
                                    }
                                },
                                onRunLivePacketTest = {
                                    val tests = listOf(
                                        createBluetoothProtocolPacket(
                                            packetType = BluetoothProtocolPacketType.Hello,
                                            payload = "HELLO"
                                        ) to "ACK",
                                        createBluetoothProtocolPacket(
                                            packetType = BluetoothProtocolPacketType.Status,
                                            payload = "REQUEST_STATUS"
                                        ) to "STATUS",
                                        createBluetoothProtocolPacket(
                                            packetType = BluetoothProtocolPacketType.Message,
                                            payload = "MODE=AUTO;LIVE_TEST_MESSAGE"
                                        ) to "ACK"
                                    )

                                    realBluetoothSocketState = realBluetoothSocketState.copy(
                                        liveTestStatus = "Running ${tests.size} packet tests",
                                        liveTestPassed = 0,
                                        liveTestFailed = 0,
                                        lastError = "None"
                                    )

                                    queueScope.launch {
                                        var passed = 0
                                        var failed = 0
                                        tests.forEachIndexed { index, test ->
                                            val packet = test.first
                                            val expectedType = test.second
                                            val line = compactSerializedPacketText(packet)
                                            val result = androidBluetoothSocketClient.sendLineAndWaitForResponse(line)

                                            result.fold(
                                                onSuccess = { exchange ->
                                                    val response = exchange.second
                                                    bluetoothPacketBridge = bluetoothPacketBridge.recordOutbound(packet.packetId)
                                                    val matched = response?.contains("\"packetType\":\"$expectedType\"") == true
                                                    if (matched) {
                                                        passed += 1
                                                        bluetoothPacketBridge = bluetoothPacketBridge.recordInbound(expectedType)
                                                    } else {
                                                        failed += 1
                                                    }
                                                    realBluetoothSocketState = realBluetoothSocketState.copy(
                                                        liveTestStatus = "Test ${index + 1}/${tests.size}: ${packet.packetType.wireName}",
                                                        liveTestPassed = passed,
                                                        liveTestFailed = failed,
                                                        lastSentLine = exchange.first,
                                                        lastReceivedLine = response ?: "No response before timeout",
                                                        lastError = if (matched) {
                                                            "None"
                                                        } else {
                                                            "Expected $expectedType response"
                                                        }
                                                    )
                                                },
                                                onFailure = { error ->
                                                    failed += 1
                                                    realBluetoothSocketState = realBluetoothSocketState.copy(
                                                        connectedDevice = null,
                                                        socketStatus = "Disconnected - reconnect ESP32",
                                                        liveTestStatus = "Test ${index + 1}/${tests.size} failed",
                                                        liveTestPassed = passed,
                                                        liveTestFailed = failed,
                                                        lastSentLine = line,
                                                        lastError = error.message ?: "Unknown live test error"
                                                    )
                                                }
                                            )
                                        }
                                        realBluetoothSocketState = realBluetoothSocketState.copy(
                                            liveTestStatus = if (failed == 0) {
                                                "Passed: Android to ESP32 live packet test"
                                            } else {
                                                "Needs attention: $failed test(s) failed"
                                            }
                                        )
                                    }
                                },
                                onDemoLoRaMessageChanged = { value ->
                                    demoLoRaMessage = sanitizeDemoMessageText(value)
                                },
                                onSendLoRaMessage = {
                                    val destinationNode = peerNodeForConnectedEsp32(realBluetoothSocketState.connectedDevice)
                                    val demoText = sanitizeDemoMessageText(demoLoRaMessage)
                                    val packet = createBluetoothProtocolPacket(
                                        packetType = BluetoothProtocolPacketType.Message,
                                        payload = "MODE=LORA;TEXT=$demoText",
                                        destinationNode = destinationNode,
                                        status = "QUEUED_FOR_LORA"
                                    )
                                    val line = compactSerializedPacketText(packet)

                                    realBluetoothSocketState = realBluetoothSocketState.copy(
                                        liveTestStatus = "Sending \"$demoText\" to $destinationNode",
                                        lastDemoMessage = demoText,
                                        lastError = "None"
                                    )

                                    queueScope.launch {
                                        val result = androidBluetoothSocketClient.sendLineAndWaitForResponse(line, 5000L)
                                        realBluetoothSocketState = result.fold(
                                            onSuccess = { exchange ->
                                                val response = exchange.second
                                                val forwarded = response?.contains("FORWARDED_OVER_LORA") == true
                                                bluetoothPacketBridge = bluetoothPacketBridge.recordOutbound(packet.packetId)
                                                if (forwarded) {
                                                    bluetoothPacketBridge = bluetoothPacketBridge.recordInbound("LORA_ACK")
                                                }
                                                realBluetoothSocketState.copy(
                                                    liveTestStatus = if (forwarded) {
                                                        "Sent to $destinationNode: $demoText"
                                                    } else {
                                                        "LoRa message sent; check ESP32 logs"
                                                    },
                                                    lastSentLine = exchange.first,
                                                    lastReceivedLine = response ?: "No response before timeout",
                                                    lastError = if (forwarded) {
                                                        "None"
                                                    } else {
                                                        "Expected FORWARDED_OVER_LORA ACK"
                                                    }
                                                )
                                            },
                                            onFailure = { error ->
                                                realBluetoothSocketState.copy(
                                                    connectedDevice = null,
                                                    socketStatus = "Disconnected - reconnect ESP32",
                                                    liveTestStatus = "LoRa message failed",
                                                    lastSentLine = line,
                                                    lastError = error.message ?: "Unknown LoRa message error"
                                                )
                                            }
                                        )
                                    }
                                },
                                onReadIncomingPacket = {
                                    realBluetoothSocketState = realBluetoothSocketState.copy(
                                        liveTestStatus = "Checking for incoming message...",
                                        lastError = "None"
                                    )
                                    queueScope.launch {
                                        val received = androidBluetoothSocketClient.waitForIncomingMessageLine(7000L)
                                        realBluetoothSocketState = received.fold(
                                            onSuccess = { line ->
                                                if (line.isNullOrBlank()) {
                                                    realBluetoothSocketState.copy(
                                                        liveTestStatus = "No incoming packet yet",
                                                        lastError = "No LoRa MESSAGE within 7 seconds"
                                                    )
                                                } else {
                                                    val incomingText = readableDemoMessageFromProtocolLine(line)
                                                    val incomingPacket = deserializeBluetoothProtocolPacket(line)
                                                    if (incomingPacket != null && incomingText.isNotBlank()) {
                                                        val incomingMessageId = nextMessageId++
                                                        val incomingChatMessage = ChatMessage(
                                                            id = incomingMessageId,
                                                            text = incomingText,
                                                            sourceNode = incomingPacket.sourceNode,
                                                            targetNode = "ANDROID_APP",
                                                            route = RouteLabel.Lora,
                                                            status = MessageStatus.Delivered,
                                                            metrics = SimMetrics(0, 0f, 0, "live", "n/a"),
                                                            path = incomingPacket.hopPath,
                                                            note = "Received over LoRa bridge",
                                                            sentAt = currentTimeLabel(),
                                                            progressStep = 0,
                                                            routeQuality = "Live",
                                                            delayMs = 0L,
                                                            packet = LoraManetPacket(
                                                                packetId = incomingPacket.packetId,
                                                                sourceNodeId = incomingPacket.sourceNode,
                                                                destinationNodeId = incomingPacket.destinationNode,
                                                                selectedTransport = RouteLabel.Lora.label,
                                                                payloadText = incomingText,
                                                                timestamp = incomingPacket.timestamp,
                                                                hopPath = incomingPacket.hopPath,
                                                                hopCount = incomingPacket.hopPath.size - 1,
                                                                rssi = 0,
                                                                snr = 0f,
                                                                gatewayStatus = "live",
                                                                satelliteStatus = "n/a",
                                                                deliveryStatus = MessageStatus.Delivered.label,
                                                                queuedAt = incomingPacket.timestamp
                                                            ),
                                                            retryCount = 0,
                                                            deliveryProgress = "Received over LoRa"
                                                        )
                                                        messages.add(incomingChatMessage)
                                                    }
                                                    bluetoothPacketBridge = bluetoothPacketBridge.recordInbound("LORA_INCOMING")
                                                    realBluetoothSocketState.copy(
                                                        liveTestStatus = "Incoming: $incomingText",
                                                        lastDemoMessage = incomingText,
                                                        lastReceivedLine = line,
                                                        lastError = "None"
                                                    )
                                                }
                                            },
                                            onFailure = { error ->
                                                realBluetoothSocketState.copy(
                                                    connectedDevice = null,
                                                    socketStatus = "Disconnected - reconnect ESP32",
                                                    liveTestStatus = "Incoming read failed",
                                                    lastError = error.message ?: "Unknown Bluetooth read error"
                                                )
                                            }
                                        )
                                    }
                                },
                                onRealSocketDisconnect = {
                                    androidBluetoothSocketClient.disconnect()
                                    realBluetoothSocketState = realBluetoothSocketState.copy(
                                        connectedDevice = null,
                                        socketStatus = "Disconnected"
                                    )
                                },
                                onScan = {
                                    bluetoothDeviceState = bluetoothTransportManager.scan(bluetoothDeviceState)
                                    bluetoothState = bluetoothState.copy(
                                        discoveredNodes = fakeEsp32Nodes,
                                        selectedNode = bluetoothState.selectedNode ?: fakeEsp32Nodes.first(),
                                        connectedNode = null,
                                        pairingStatus = PairingStatus.Scanning
                                    )
                                },
                                onDeviceSelected = { device ->
                                    bluetoothDeviceState = bluetoothDeviceState.copy(
                                        selectedDevice = device,
                                        lifecycleState = if (bluetoothSession.connectedDevice == device) {
                                            BluetoothLifecycleState.Connected
                                        } else {
                                            BluetoothLifecycleState.Scanning
                                        }
                                    )
                                    bluetoothState = bluetoothState.copy(
                                        selectedNode = device,
                                        pairingStatus = if (bluetoothState.connectedNode == device) {
                                            PairingStatus.Paired
                                        } else {
                                            PairingStatus.Scanning
                                        }
                                    )
                                },
                                onPair = {
                                    val pairedState = bluetoothTransportManager.pair(bluetoothDeviceState)
                                    bluetoothDeviceState = pairedState
                                    val selected = pairedState.pairedDevice ?: pairedState.selectedDevice
                                    bluetoothState = if (selected == null) {
                                        bluetoothState.copy(pairingStatus = PairingStatus.ConnectionFailed)
                                    } else {
                                        bluetoothState.copy(
                                            selectedNode = selected,
                                            pairingStatus = PairingStatus.Paired
                                        )
                                    }
                                },
                                onConnect = {
                                    bluetoothDeviceState = bluetoothDeviceState.copy(
                                        lifecycleState = BluetoothLifecycleState.Connecting
                                    )
                                    queueScope.launch {
                                        delay(500L)
                                        val result = bluetoothTransportManager.connect(
                                            bluetoothDeviceState,
                                            bluetoothSession
                                        )
                                        bluetoothDeviceState = result.first
                                        bluetoothSession = result.second
                                        bluetoothState = bluetoothState.copy(
                                            selectedNode = result.first.selectedDevice,
                                            connectedNode = result.second.connectedDevice,
                                            pairingStatus = if (result.second.connectedDevice == null) {
                                                PairingStatus.ConnectionFailed
                                            } else {
                                                PairingStatus.Paired
                                            }
                                        )
                                    }
                                },
                                onExchangeHello = {
                                    bluetoothPacketBridge = bluetoothPacketBridge.recordOutbound("BT-HELLO")
                                    queueScope.launch {
                                        delay(450L)
                                        bluetoothPacketBridge = bluetoothPacketBridge.recordInbound("ESP32_ACK")
                                        esp32BridgeConfig = esp32BridgeConfig.copy(
                                            connectionStatus = "ESP32_ACK received over simulated Bluetooth layer",
                                            lastHandshakeTime = currentTimeLabel(),
                                            handshakeStatus = "ESP32_ACK received"
                                        )
                                    }
                                },
                                onDisconnect = {
                                    val result = bluetoothTransportManager.disconnect(bluetoothDeviceState, bluetoothSession)
                                    bluetoothDeviceState = result.first
                                    bluetoothSession = result.second
                                    bluetoothState = bluetoothState.copy(
                                        connectedNode = null,
                                        pairingStatus = PairingStatus.NotPaired
                                    )
                                },
                                onSimulateTimeout = {
                                    val failed = bluetoothTransportManager.failForTimeout(bluetoothDeviceState, bluetoothSession)
                                    bluetoothDeviceState = failed.first
                                    bluetoothSession = failed.second
                                    bluetoothState = bluetoothState.copy(
                                        connectedNode = null,
                                        pairingStatus = PairingStatus.ConnectionFailed
                                    )
                                    val retry = bluetoothTransportManager.scheduleReconnect(
                                        failed.first,
                                        failed.second
                                    )
                                    bluetoothDeviceState = retry.first
                                    bluetoothSession = retry.second
                                    queueScope.launch {
                                        for (remaining in 3 downTo 1) {
                                            bluetoothSession = bluetoothSession.copy(reconnectCountdownSeconds = remaining)
                                            delay(1000L)
                                        }
                                        val result = bluetoothTransportManager.connect(
                                            bluetoothDeviceState,
                                            bluetoothSession.copy(reconnectCountdownSeconds = 0)
                                        )
                                        bluetoothDeviceState = result.first
                                        bluetoothSession = result.second
                                        bluetoothState = bluetoothState.copy(
                                            selectedNode = result.first.selectedDevice,
                                            connectedNode = result.second.connectedDevice,
                                            pairingStatus = if (result.second.connectedDevice == null) {
                                                PairingStatus.ConnectionFailed
                                            } else {
                                                PairingStatus.Paired
                                            }
                                        )
                                    }
                                }
                            )
                        }
                        item {
                            SimulationControls(
                                networkState = networkState,
                                onNetworkStateChange = { networkState = it },
                                simulationSpeed = simulationSpeed,
                                onSimulationSpeedChange = { simulationSpeed = it },
                                simulationCondition = simulationCondition
                            )
                        }
                    }
                    AppTab.Diagnostics -> {
                        item {
                            StatusPanel(
                                selectedNetwork = selectedNetwork,
                                networkState = networkState,
                                bluetoothState = bluetoothState
                            )
                        }
                        item {
                            MetricsPanel(
                                selectedNetwork = selectedNetwork,
                                networkState = routedNetworkState,
                                bluetoothState = bluetoothState,
                                localNode = localNode,
                                targetNode = targetNode,
                                nodes = simulatedNodes
                            )
                        }
                        item {
                            QueueStatsPanel(
                                stats = queueManager.stats(messages),
                                maxRetryCount = queueManager.maxRetryCount
                            )
                        }
                        item {
                            BluetoothReadinessAndTestPlanPanel(
                                outgoingPacket = protocolPacketFromManetPacket(
                                    packet = outgoingPacketPreview,
                                    packetType = BluetoothProtocolPacketType.Message,
                                    retryCount = messages.firstOrNull { it.packet.packetId == outgoingPacketPreview.packetId }?.retryCount ?: 0
                                ),
                                incomingPacket = sampleIncomingAckPacket(outgoingPacketPreview)
                            )
                        }
                        item {
                            ValidationChecklistPanel(
                                items = validationItems,
                                onRunBasicValidation = {
                                    validationItems = runBasicValidation(
                                        selectedTab = selectedTab,
                                        messages = messages,
                                        routingDecision = adaptiveRoutingDecision,
                                        packetLog = packetLog,
                                        eventLog = eventLog,
                                        transportStatus = transportStatus,
                                        bluetoothState = bluetoothState,
                                        simulationSpeed = simulationSpeed,
                                        networkState = networkState
                                    )
                                },
                                onResetValidation = {
                                    validationItems = defaultValidationItems()
                                }
                            )
                        }
                        item { PacketLogPanel(packets = packetLog) }
                        item { EventLogPanel(events = eventLog) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabRow(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    TabRow(selectedTabIndex = AppTab.entries.indexOf(selectedTab)) {
        AppTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.adamson_logo),
            contentDescription = "Adamson University seal",
            modifier = Modifier.size(76.dp)
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "ADAMSON UNIVERSITY",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "ADAPTIVE FAILOVER via Simulated LEO Satcom-Mobile Ad Hoc Network",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NetworkSelector(
    selectedNetwork: NetworkMode,
    onNetworkSelected: (NetworkMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Network",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        NetworkMode.entries.chunked(2).forEach { rowModes ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowModes.forEach { mode ->
                    FilterChip(
                        selected = selectedNetwork == mode,
                        onClick = { onNetworkSelected(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeSelector(
    nodes: List<SimNode>,
    localNode: SimNode,
    targetNode: SimNode,
    onLocalNodeSelected: (SimNode) -> Unit,
    onTargetNodeSelected: (SimNode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "MANET Nodes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        NodeChipGroup(
            title = "Current local node",
            selectedNode = localNode,
            availableNodes = nodes,
            onNodeSelected = onLocalNodeSelected
        )
        NodeChipGroup(
            title = "Target destination node",
            selectedNode = targetNode,
            availableNodes = nodes.filter { it.name != localNode.name },
            onNodeSelected = onTargetNodeSelected
        )
    }
}

@Composable
private fun NodeChipGroup(
    title: String,
    selectedNode: SimNode,
    availableNodes: List<SimNode>,
    onNodeSelected: (SimNode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        availableNodes.chunked(2).forEach { rowNodes ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowNodes.forEach { node ->
                    FilterChip(
                        selected = selectedNode.name == node.name,
                        onClick = { onNodeSelected(node) },
                        label = { Text(node.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeSummaryPanel(
    localNode: SimNode,
    targetNode: SimNode,
    networkState: NetworkState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Selected Nodes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        NodeSummaryRow(label = "Local", node = localNode, networkState = networkState)
        NodeSummaryRow(label = "Target", node = targetNode, networkState = networkState)
    }
}

@Composable
private fun NodeSummaryRow(
    label: String,
    node: SimNode,
    networkState: NetworkState
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "$label: ${node.name}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "RSSI ${node.rssi} dBm | SNR ${node.snr} dB | Hop ${node.hopCount}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Routes ${routeAvailabilityLabel(node)} | Gateway ${node.gatewayProximity}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Health ${nodeHealth(node, networkState)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TopologyOverviewPanel(
    localNode: SimNode,
    targetNode: SimNode,
    networkState: NetworkState,
    nodes: List<SimNode>
) {
    val path = routePath(localNode, targetNode, nodes)
    val offlineNodes = nodes.filter { !nodeHasAnyAvailableRoute(it, networkState) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Topology",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Connected nodes", value = "${nodes.size - offlineNodes.size}/${nodes.size}")
        StatusRow(label = "Offline nodes", value = if (offlineNodes.isEmpty()) "None" else offlineNodes.joinToString { it.name })
        StatusRow(label = "Gateway node", value = "Gateway Node")
        Text(
            text = "Path: ${path.joinToString(" -> ") { it.name }}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = nodes.joinToString(" | ") { "${it.name.removePrefix("Node ")}: ${nodeHealth(it, networkState)}" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusPanel(
    selectedNetwork: NetworkMode,
    networkState: NetworkState,
    bluetoothState: BluetoothState
) {
    val loraStatus = if (bluetoothLinkedToEsp32(bluetoothState)) {
        "Bluetooth-linked to ESP32"
    } else {
        simulationStatus(networkState.loraAvailable)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Status",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Current selected network", value = selectedNetwork.label)
        StatusRow(label = "LoRa status", value = loraStatus)
        StatusRow(label = "WiFi status", value = simulationStatus(networkState.wifiAvailable))
        StatusRow(label = "GSM status", value = simulationStatus(networkState.gsmAvailable))
        StatusRow(label = "Satellite link", value = simulationStatus(networkState.satelliteAvailable))
        StatusRow(label = "Bluetooth status", value = bluetoothState.bluetoothStatus.label)
        StatusRow(label = "Connected ESP32", value = bluetoothState.connectedNode ?: "None")
        StatusRow(label = "Message states", value = "Queued, Routing, Relaying, Retrying, Delivered, Failed")
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            modifier = Modifier.weight(1f),
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun BluetoothPanel(
    bluetoothState: BluetoothState,
    bluetoothDeviceState: BluetoothDeviceState,
    bluetoothSession: BluetoothConnectionSession,
    bluetoothPacketBridge: BluetoothPacketBridge,
    bluetoothPermissionStatus: BluetoothPermissionStatus,
    realBluetoothSocketState: RealBluetoothSocketState,
    demoLoRaMessage: String,
    onRequestBluetoothPermissions: () -> Unit,
    onRefreshBondedDevices: () -> Unit,
    onRealDeviceSelected: (String) -> Unit,
    onRealSocketConnect: () -> Unit,
    onRealSocketHello: () -> Unit,
    onRunLivePacketTest: () -> Unit,
    onDemoLoRaMessageChanged: (String) -> Unit,
    onSendLoRaMessage: () -> Unit,
    onReadIncomingPacket: () -> Unit,
    onRealSocketDisconnect: () -> Unit,
    onScan: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onPair: () -> Unit,
    onConnect: () -> Unit,
    onExchangeHello: () -> Unit,
    onDisconnect: () -> Unit,
    onSimulateTimeout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Bluetooth Architecture",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Future path: Android Bluetooth API -> BluetoothTransportManager -> Packet bridge -> MANET routing engine.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StatusRow(label = "Bluetooth", value = bluetoothState.bluetoothStatus.label)
        StatusRow(label = "Connected ESP32 Node", value = bluetoothState.connectedNode ?: "None")
        StatusRow(label = "Pairing Status", value = bluetoothState.pairingStatus.label)
        StatusRow(label = "Lifecycle", value = bluetoothDeviceState.lifecycleState.label)
        BluetoothPermissionPanel(
            bluetoothPermissionStatus = bluetoothPermissionStatus,
            onRequestBluetoothPermissions = onRequestBluetoothPermissions
        )
        RealBluetoothSocketPanel(
            socketState = realBluetoothSocketState,
            demoLoRaMessage = demoLoRaMessage,
            permissionsReady = bluetoothPermissionStatus.allRuntimeGranted,
            onRefreshBondedDevices = onRefreshBondedDevices,
            onDeviceSelected = onRealDeviceSelected,
            onConnect = onRealSocketConnect,
            onSendHello = onRealSocketHello,
            onRunLivePacketTest = onRunLivePacketTest,
            onDemoLoRaMessageChanged = onDemoLoRaMessageChanged,
            onSendLoRaMessage = onSendLoRaMessage,
            onReadIncomingPacket = onReadIncomingPacket,
            onDisconnect = onRealSocketDisconnect
        )
        if (bluetoothLinkedToEsp32(bluetoothState)) {
            Text(
                text = "LoRa transport is simulated through paired ESP32.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onScan
            ) {
                Text("Scan")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = bluetoothDeviceState.discoveredDevices.isNotEmpty(),
                onClick = onPair
            ) {
                Text("Pair")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = bluetoothDeviceState.pairedDevice != null,
                onClick = onConnect
            ) {
                Text("Connect")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = bluetoothDeviceState.lifecycleState == BluetoothLifecycleState.Connected,
                onClick = onExchangeHello
            ) {
                Text("HELLO")
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = bluetoothState.connectedNode != null || bluetoothState.pairingStatus != PairingStatus.NotPaired,
            onClick = onDisconnect
        ) {
            Text("Disconnect")
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = bluetoothDeviceState.pairedDevice != null,
            onClick = onSimulateTimeout
        ) {
            Text("Simulate Timeout / Reconnect")
        }
        if (bluetoothDeviceState.discoveredDevices.isEmpty()) {
            Text(
                text = "No simulated ESP32 nodes scanned yet. These controls are simulation-only and do not connect to real Bluetooth hardware.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "Simulated ESP32 Nodes",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            bluetoothDeviceState.discoveredDevices.chunked(2).forEach { rowNodes ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowNodes.forEach { nodeName ->
                        FilterChip(
                            selected = bluetoothDeviceState.selectedDevice == nodeName,
                            onClick = { onDeviceSelected(nodeName) },
                            label = { Text(nodeName) }
                        )
                    }
                }
            }
        }
        BluetoothDiagnosticsPanel(
            bluetoothDeviceState = bluetoothDeviceState,
            bluetoothSession = bluetoothSession,
            bluetoothPacketBridge = bluetoothPacketBridge
        )
    }
}

@Composable
private fun RealBluetoothSocketPanel(
    socketState: RealBluetoothSocketState,
    demoLoRaMessage: String,
    permissionsReady: Boolean,
    onRefreshBondedDevices: () -> Unit,
    onDeviceSelected: (String) -> Unit,
    onConnect: () -> Unit,
    onSendHello: () -> Unit,
    onRunLivePacketTest: () -> Unit,
    onDemoLoRaMessageChanged: (String) -> Unit,
    onSendLoRaMessage: () -> Unit,
    onReadIncomingPacket: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Real Bluetooth Socket",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Socket", value = socketState.socketStatus)
        StatusRow(label = "Live test", value = socketState.liveTestStatus)
        StatusRow(label = "Passed", value = socketState.liveTestPassed.toString())
        StatusRow(label = "Failed", value = socketState.liveTestFailed.toString())
        StatusRow(label = "Selected", value = socketState.selectedDevice ?: "None")
        StatusRow(label = "Connected", value = socketState.connectedDevice ?: "None")
        StatusRow(label = "Demo message", value = socketState.lastDemoMessage)
        StatusRow(label = "Last sent", value = socketState.lastSentLine)
        StatusRow(label = "Last received", value = socketState.lastReceivedLine)
        StatusRow(label = "Last error", value = socketState.lastError)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = permissionsReady,
                onClick = onRefreshBondedDevices
            ) {
                Text("Load Paired")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = permissionsReady && socketState.selectedDevice != null && !socketState.connected,
                onClick = onConnect
            ) {
                Text("Connect ESP32")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = socketState.connected,
                onClick = onSendHello
            ) {
                Text("Send HELLO")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = socketState.connected,
                onClick = onRunLivePacketTest
            ) {
                Text("Run Test")
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = demoLoRaMessage,
            onValueChange = onDemoLoRaMessageChanged,
            enabled = socketState.connected,
            singleLine = true,
            label = { Text("Message to send") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = socketState.connected && demoLoRaMessage.isNotBlank(),
                onClick = onSendLoRaMessage
            ) {
                Text("Send Msg")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = socketState.connected,
                onClick = onReadIncomingPacket
            ) {
                Text("Check In")
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = socketState.connected,
            onClick = onDisconnect
        ) {
            Text("Close Socket")
        }
        if (socketState.bondedDevices.isEmpty()) {
            Text(
                text = "Real Bluetooth requires a physical Android phone. Pair the ESP32 in Android Bluetooth settings first, then load paired devices here. Android Emulator usually cannot use real Bluetooth SPP.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "Paired Bluetooth Devices",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            socketState.bondedDevices.chunked(2).forEach { rowDevices ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowDevices.forEach { deviceName ->
                        FilterChip(
                            selected = socketState.selectedDevice == deviceName,
                            onClick = { onDeviceSelected(deviceName) },
                            label = { Text(deviceName) }
                        )
                    }
                }
            }
        }
        Text(
            text = "Demo flow: type a short message on Phone A, press Send Msg, then press Check In on Phone B.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BluetoothPermissionPanel(
    bluetoothPermissionStatus: BluetoothPermissionStatus,
    onRequestBluetoothPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Bluetooth Permissions",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Platform", value = bluetoothPermissionStatus.platformLabel)
        StatusRow(
            label = "Manifest",
            value = bluetoothPermissionStatus.manifestPermissions.joinToString()
        )
        StatusRow(
            label = "Runtime",
            value = bluetoothPermissionStatus.runtimePermissions.joinToString()
        )
        StatusRow(
            label = "Granted",
            value = if (bluetoothPermissionStatus.allRuntimeGranted) {
                "Ready"
            } else {
                "Permission required"
            }
        )
        StatusRow(label = "Last request", value = bluetoothPermissionStatus.lastRequestStatus)
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = bluetoothPermissionStatus.runtimePermissions.isNotEmpty() &&
                !bluetoothPermissionStatus.allRuntimeGranted,
            onClick = onRequestBluetoothPermissions
        ) {
            Text("Request Permissions")
        }
        Text(
            text = "Step 019 only prepares Android Bluetooth permission access. Socket connection remains reserved for Step 020.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BluetoothDiagnosticsPanel(
    bluetoothDeviceState: BluetoothDeviceState,
    bluetoothSession: BluetoothConnectionSession,
    bluetoothPacketBridge: BluetoothPacketBridge
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Bluetooth Diagnostics",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Current paired ESP32", value = bluetoothDeviceState.pairedDevice ?: "None")
        StatusRow(label = "Signal placeholder", value = bluetoothDeviceState.signalPlaceholder)
        StatusRow(label = "Packets sent", value = bluetoothPacketBridge.packetsSent.toString())
        StatusRow(label = "Packets received", value = bluetoothPacketBridge.packetsReceived.toString())
        StatusRow(label = "Last outbound", value = bluetoothPacketBridge.lastOutboundPacket)
        StatusRow(label = "Last inbound", value = bluetoothPacketBridge.lastInboundPacket)
        StatusRow(label = "Last reconnect", value = bluetoothSession.lastReconnectAttempt)
        StatusRow(label = "Connection uptime", value = connectionUptimeLabel(bluetoothSession))
        StatusRow(label = "Retry counter", value = bluetoothSession.retryCounter.toString())
        StatusRow(label = "Reconnect countdown", value = "${bluetoothSession.reconnectCountdownSeconds}s")
        StatusRow(label = "Timeout", value = bluetoothSession.timeoutStatus)
    }
}

@Composable
private fun SimulationControls(
    networkState: NetworkState,
    onNetworkStateChange: (NetworkState) -> Unit,
    simulationSpeed: SimulationSpeed,
    onSimulationSpeedChange: (SimulationSpeed) -> Unit,
    simulationCondition: SimulationCondition
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Simulation Controls",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Network state", value = simulationCondition.label)
        Text(
            text = "Simulation speed",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SimulationSpeed.entries.forEach { speed ->
                FilterChip(
                    selected = simulationSpeed == speed,
                    onClick = { onSimulationSpeedChange(speed) },
                    label = { Text(speed.label) }
                )
            }
        }
        AvailabilityToggle(
            label = "LoRa",
            checked = networkState.loraAvailable,
            onCheckedChange = { onNetworkStateChange(networkState.copy(loraAvailable = it)) }
        )
        AvailabilityToggle(
            label = "WiFi",
            checked = networkState.wifiAvailable,
            onCheckedChange = { onNetworkStateChange(networkState.copy(wifiAvailable = it)) }
        )
        AvailabilityToggle(
            label = "GSM",
            checked = networkState.gsmAvailable,
            onCheckedChange = { onNetworkStateChange(networkState.copy(gsmAvailable = it)) }
        )
        AvailabilityToggle(
            label = "Satellite link",
            checked = networkState.satelliteAvailable,
            onCheckedChange = { onNetworkStateChange(networkState.copy(satelliteAvailable = it)) }
        )
    }
}

@Composable
private fun AvailabilityToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (checked) "Up" else "Down",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MetricsPanel(
    selectedNetwork: NetworkMode,
    networkState: NetworkState,
    bluetoothState: BluetoothState,
    localNode: SimNode,
    targetNode: SimNode,
    nodes: List<SimNode>
) {
    val decision = decideRoute(selectedNetwork, networkState, bluetoothState, localNode, targetNode, nodes = nodes)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Metrics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Route", value = decision.route.label)
        StatusRow(label = "Quality", value = routeQuality(decision.metrics))
        StatusRow(label = "Path", value = decision.path.joinToString(" -> "))
        StatusRow(label = "RSSI", value = "${formatRssi(decision.metrics.rssi)} dBm")
        StatusRow(label = "SNR", value = "${formatSnr(decision.metrics.snr)} dB")
        StatusRow(label = "Hop count", value = decision.metrics.hopCount.toString())
        StatusRow(label = "Gateway", value = decision.metrics.gatewayProximity)
        StatusRow(label = "Satellite", value = decision.metrics.satelliteStatus)
    }
}

@Composable
private fun CurrentRouteSummary(routingDecision: RoutingDecision) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Current Route",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                modifier = Modifier.weight(1f),
                text = routingDecision.selectedRoute.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = routingDecision.failoverReason,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RoutingDecisionPanel(routingDecision: RoutingDecision) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Routing Decision",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Preferred route", value = routingDecision.preferredRoute.label)
        StatusRow(label = "Selected route", value = routingDecision.selectedRoute.label)
        StatusRow(label = "Route score", value = routingDecision.routeScore.toString())
        Text(
            text = "Reason: ${routingDecision.failoverReason}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Available candidates",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        if (routingDecision.availableCandidates.isEmpty()) {
            Text(
                text = "No available route candidates.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            routingDecision.availableCandidates.forEach { candidate ->
                RouteCandidateRow(candidate = candidate)
            }
        }
        Text(
            text = "Candidate visualization",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        routingDecision.allCandidates.forEach { candidate ->
            RouteCandidateRow(candidate = candidate)
        }
    }
}

@Composable
private fun EventLogPanel(events: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Network Event Log",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (events.isEmpty()) {
            Text(
                text = "Waiting for simulated network changes.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            events.take(6).forEach { event ->
                Text(
                    text = event,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QueueStatsPanel(
    stats: QueueStats,
    maxRetryCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Queue Statistics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Queued / active", value = stats.queuedCount.toString())
        StatusRow(label = "Delivered", value = stats.deliveredCount.toString())
        StatusRow(label = "Failed", value = stats.failedCount.toString())
        StatusRow(label = "Retries", value = stats.retryCount.toString())
        StatusRow(label = "Max retries", value = maxRetryCount.toString())
    }
}

@Composable
private fun ValidationChecklistPanel(
    items: List<ValidationChecklistItem>,
    onRunBasicValidation: () -> Unit,
    onResetValidation: () -> Unit
) {
    val summary = validationSummary(items)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Validation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = onRunBasicValidation
            ) {
                Text("Run Basic")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onResetValidation
            ) {
                Text("Reset")
            }
        }
        StatusRow(label = "Passed", value = summary.passedCount.toString())
        StatusRow(label = "Failed", value = summary.failedCount.toString())
        StatusRow(label = "Not tested", value = summary.notTestedCount.toString())
        items.forEach { item ->
            StatusRow(label = item.label, value = item.status.label)
        }
    }
}

@Composable
private fun RouteCandidateRow(candidate: RouteCandidate) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "${candidate.name} | ${if (candidate.available) "Available" else "Unavailable"} | Score ${candidate.score.total}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = candidate.reason,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Path: ${candidate.path.joinToString(" -> ")}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransportBridgePanel(
    selectedTransport: TransportOption,
    transportStatus: TransportStatus,
    onTransportSelected: (TransportOption) -> Unit
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Transport Bridge",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { dropdownExpanded = true }
            ) {
                Text(selectedTransport.label)
            }
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                TransportOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            dropdownExpanded = false
                            onTransportSelected(option)
                        }
                    )
                }
            }
        }
        StatusRow(label = "Active implementation", value = transportStatus.activeImplementation)
        StatusRow(label = "Connection state", value = transportStatus.connectionState)
        StatusRow(label = "Last packet sent", value = transportStatus.lastPacketSent)
        StatusRow(label = "Last packet received", value = transportStatus.lastPacketReceived)
    }
}

@Composable
private fun Esp32TransportPreparationPanel(
    hardwareMode: HardwareMode,
    esp32BridgeConfig: Esp32BridgeConfig,
    packetPreview: LoraManetPacket,
    bluetoothState: BluetoothState,
    onHardwareModeSelected: (HardwareMode) -> Unit,
    onBridgeConfigChanged: (Esp32BridgeConfig) -> Unit,
    onSendHello: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "ESP32 Transport Preparation",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Hardware transport is prepared as a disabled placeholder. No Bluetooth or WiFi transport APIs are active.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = hardwareMode == HardwareMode.Simulation,
                onClick = { onHardwareModeSelected(HardwareMode.Simulation) },
                label = { Text(HardwareMode.Simulation.label) }
            )
            FilterChip(
                selected = false,
                enabled = false,
                onClick = { onHardwareModeSelected(HardwareMode.HardwareDisabled) },
                label = { Text(HardwareMode.HardwareDisabled.label) }
            )
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = esp32BridgeConfig.deviceName,
            onValueChange = { onBridgeConfigChanged(esp32BridgeConfig.copy(deviceName = it)) },
            label = { Text("ESP32 device name") },
            singleLine = true
        )
        Text(
            text = "Connection type",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Esp32ConnectionType.entries.chunked(2).forEach { connectionTypes ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                connectionTypes.forEach { type ->
                    FilterChip(
                        selected = esp32BridgeConfig.connectionType == type,
                        onClick = {
                            onBridgeConfigChanged(
                                esp32BridgeConfig.copy(connectionType = type)
                            )
                        },
                        label = { Text(type.label) }
                    )
                }
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = esp32BridgeConfig.packetFormatVersion,
            onValueChange = { onBridgeConfigChanged(esp32BridgeConfig.copy(packetFormatVersion = it)) },
            label = { Text("Packet format version") },
            singleLine = true
        )
        StatusRow(label = "Connection status", value = esp32BridgeConfig.connectionStatus)
        StatusRow(label = "Last handshake", value = esp32BridgeConfig.lastHandshakeTime)
        StatusRow(label = "Handshake state", value = esp32BridgeConfig.handshakeStatus)
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onSendHello
        ) {
            Text("Send HELLO to ESP32")
        }
        Text(
            text = "Expected response: ESP32_ACK (simulated only)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PacketFormatPreviewPanel(packet = packetPreview)
        HardwareReadinessPanel(bluetoothState = bluetoothState)
    }
}

@Composable
private fun PacketFormatPreviewPanel(packet: LoraManetPacket) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Outgoing Packet Preview",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = packetPreviewText(packet),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HardwareReadinessPanel(bluetoothState: BluetoothState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Hardware Readiness",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Android app ready", value = "Ready for simulation")
        StatusRow(label = "ESP32 firmware ready", value = "Not verified")
        StatusRow(
            label = "Bluetooth pairing ready",
            value = if (bluetoothState.pairingStatus == PairingStatus.Paired) {
                "Simulated paired"
            } else {
                "Placeholder only"
            }
        )
        StatusRow(label = "LoRa module wired", value = "Not verified")
        StatusRow(label = "Packet format matched", value = "Pending ESP32 test")
    }
}

@Composable
private fun BluetoothProtocolPreviewPanel(
    outgoingPacket: BluetoothProtocolPacket,
    incomingPacket: BluetoothProtocolPacket
) {
    val serializedOutgoing = serializeBluetoothProtocolPacket(outgoingPacket)
    val parsedOutgoing = deserializeBluetoothProtocolPacket(serializedOutgoing)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "ESP32 Bluetooth Packet Protocol",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Protocol version", value = BLUETOOTH_PROTOCOL_VERSION)
        CompactProtocolPacketRow(
            title = "Outgoing MESSAGE",
            packet = outgoingPacket
        )
        CompactProtocolPacketRow(
            title = "Incoming ACK",
            packet = incomingPacket
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Serialization Preview",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = compactSerializedPacketText(outgoingPacket),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusRow(
                label = "Parsed packet",
                value = parsedOutgoing?.packetId ?: "Parse failed"
            )
            StatusRow(
                label = "Checksum",
                value = outgoingPacket.checksumPlaceholder
            )
        }
    }
}

@Composable
private fun CompactProtocolPacketRow(
    title: String,
    packet: BluetoothProtocolPacket
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = "$title | ${packet.packetId} | ${packet.status}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "${packet.sourceNode} -> ${packet.destinationNode} | Retry ${packet.retryCount} | ${packet.packetType.wireName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Path: ${packet.hopPath.joinToString(" -> ")}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProtocolPacketCard(
    title: String,
    packet: BluetoothProtocolPacket,
    validation: ProtocolValidationStatus
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Packet type", value = packet.packetType.wireName)
        StatusRow(label = "Packet ID", value = packet.packetId)
        StatusRow(label = "Source", value = packet.sourceNode)
        StatusRow(label = "Destination", value = packet.destinationNode)
        StatusRow(label = "Retry count", value = packet.retryCount.toString())
        StatusRow(label = "Timestamp", value = packet.timestamp.toString())
        StatusRow(label = "Status", value = packet.status)
        StatusRow(label = "Checksum", value = packet.checksumPlaceholder)
        Text(
            text = "Payload: ${packet.payload}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Hop path: ${packet.hopPath.joinToString(" -> ")}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = protocolValidationText(validation),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun BluetoothReadinessAndTestPlanPanel(
    outgoingPacket: BluetoothProtocolPacket,
    incomingPacket: BluetoothProtocolPacket
) {
    val outgoingValidation = validateBluetoothProtocolPacket(outgoingPacket)
    val incomingValidation = validateBluetoothProtocolPacket(incomingPacket)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Bluetooth Readiness and Test Plan",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Android Phone <-> Bluetooth <-> ESP32 <-> LoRa <-> ESP32 <-> Bluetooth <-> Android Phone",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        ChecklistPanel(
            title = "Bluetooth Readiness",
            rows = listOf(
                "Android Bluetooth architecture ready" to "Ready",
                "Packet protocol defined" to "Ready",
                "ESP32 firmware packet parser" to "Ready",
                "ESP32 Bluetooth service" to "Ready",
                "SX1278 LoRa wiring pending" to "Pending",
                "LoRa send/receive test pending" to "Pending",
                "Android Bluetooth permissions" to "Ready",
                "Android Bluetooth socket layer" to "Ready",
                "Android to ESP32 live packet test" to "Ready"
            )
        )
        ChecklistPanel(
            title = "ESP32 Firmware Requirements",
            rows = listOf(
                "Expose Bluetooth connection" to "Required",
                "Accept HELLO" to "Required",
                "Return ESP32_ACK" to "Required",
                "Accept MESSAGE packet" to "Required",
                "Forward MESSAGE over LoRa" to "Required",
                "Report STATUS" to "Required"
            )
        )
        ChecklistPanel(
            title = "Protocol Test Cases",
            rows = listOf(
                "HELLO handshake test" to "Planned",
                "MESSAGE packet send test" to "Planned",
                "ACK receive test" to "Planned",
                "Invalid packet test" to "Planned",
                "Route discovery packet test" to "Planned",
                "Status packet test" to "Planned"
            )
        )
        ProtocolPacketCard(
            title = "Detailed outgoing protocol data",
            packet = outgoingPacket,
            validation = outgoingValidation
        )
        ProtocolPacketCard(
            title = "Detailed incoming protocol data",
            packet = incomingPacket,
            validation = incomingValidation
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Readable Serialization",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            serializeBluetoothProtocolPacket(outgoingPacket).lines().forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ChecklistPanel(
            title = "Next Implementation Stages",
            rows = listOf(
                "Step 017" to "ESP32 firmware packet parser",
                "Step 018" to "ESP32 Bluetooth service",
                "Step 019" to "Android real Bluetooth permissions",
                "Step 020" to "Android Bluetooth connection implementation",
                "Step 021" to "Android-to-ESP32 live packet test",
                "Step 022" to "ESP32-to-ESP32 LoRa packet test"
            )
        )
    }
}

@Composable
private fun ChecklistPanel(
    title: String,
    rows: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        rows.forEach { row ->
            StatusRow(label = row.first, value = row.second)
        }
    }
}

@Composable
private fun PacketLogPanel(packets: List<LoraManetPacket>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Packet Log",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (packets.isEmpty()) {
            Text(
                text = "No generated packets yet.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            packets.take(5).forEach { packet ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "${packet.packetId} | ${packet.selectedTransport} | ${packet.deliveryStatus}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${packet.sourceNodeId} -> ${packet.destinationNodeId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Path: ${packet.hopPath.joinToString(" -> ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "RSSI ${packet.rssi} dBm | SNR ${formatSnr(packet.snr)} dB | Hop ${packet.hopCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Queued ${packet.queuedAt} | Relay ${packet.relayAt ?: "-"} | Delivered ${packet.deliveredAt ?: "-"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyMessageState() {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        text = "No local simulation messages yet.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun MessageComposer(
    draftMessage: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = draftMessage,
                onValueChange = onDraftChange,
                placeholder = { Text("Type message") },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSend) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val statusColors = messageStatusColors(message.status)
    val progress = if (message.path.isEmpty()) {
        0f
    } else if (message.status == MessageStatus.Failed) {
        0f
    } else {
        ((message.progressStep + 1).coerceAtMost(message.path.size).toFloat() / message.path.size.toFloat())
    }
    val progressPath = if (message.status == MessageStatus.Failed) {
        emptyList()
    } else {
        message.path.take((message.progressStep + 1).coerceAtMost(message.path.size))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = statusColors.first,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = message.route.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = statusColors.second
            )
            Text(
                modifier = Modifier.weight(1f),
                text = "${message.status.label} ${message.sentAt}",
                style = MaterialTheme.typography.labelSmall,
                color = statusColors.second,
                textAlign = TextAlign.End
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = statusColors.second
        )
        Text(
            text = message.deliveryProgress,
            style = MaterialTheme.typography.labelSmall,
            color = statusColors.second
        )
        Text(
            text = "Progress: ${progressPath.joinToString(" -> ").ifBlank { "No active route" }}",
            style = MaterialTheme.typography.labelSmall,
            color = statusColors.second
        )
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyLarge,
            color = statusColors.second
        )
        Text(
            text = "Path: ${message.path.joinToString(" -> ")}",
            style = MaterialTheme.typography.labelSmall,
            color = statusColors.second
        )
        Text(
            text = "RSSI ${formatRssi(message.metrics.rssi)} dBm | SNR ${formatSnr(message.metrics.snr)} dB | Hop ${message.metrics.hopCount}",
            style = MaterialTheme.typography.labelSmall,
            color = statusColors.second
        )
        if (message.note.contains("Failover") || message.note.contains("Adaptive") || message.status == MessageStatus.Failed) {
            Text(
                text = message.note,
                style = MaterialTheme.typography.labelSmall,
                color = statusColors.second
            )
        }
    }
}

private fun messageToPacket(
    packetId: String,
    payloadText: String,
    localNode: SimNode,
    targetNode: SimNode,
    decision: RouteDecision
): LoraManetPacket {
    return LoraManetPacket(
        packetId = packetId,
        sourceNodeId = nodeId(localNode),
        destinationNodeId = nodeId(targetNode),
        selectedTransport = decision.route.label,
        payloadText = payloadText,
        timestamp = System.currentTimeMillis() / 1000L,
        hopPath = decision.path,
        hopCount = decision.metrics.hopCount,
        rssi = decision.metrics.rssi,
        snr = decision.metrics.snr,
        gatewayStatus = decision.metrics.gatewayProximity,
        satelliteStatus = decision.metrics.satelliteStatus,
        deliveryStatus = decision.status.label,
        queuedAt = System.currentTimeMillis() / 1000L
    )
}

private fun packetToChatMessage(
    messageId: Long,
    packet: LoraManetPacket,
    decision: RouteDecision,
    sourceNodeName: String,
    targetNodeName: String,
    note: String,
    sentAt: String,
    delayMs: Long
): ChatMessage {
    return ChatMessage(
        id = messageId,
        text = packet.payloadText,
        sourceNode = sourceNodeName,
        targetNode = targetNodeName,
        route = decision.route,
        status = decision.status,
        metrics = decision.metrics,
        path = packet.hopPath,
        note = note,
        sentAt = sentAt,
        progressStep = 0,
        routeQuality = routeQuality(decision.metrics),
        delayMs = delayMs,
        packet = packet,
        retryCount = 0,
        deliveryProgress = "Queued..."
    )
}

private fun updatePacketLog(packetLog: MutableList<LoraManetPacket>, packet: LoraManetPacket) {
    val packetIndex = packetLog.indexOfFirst { it.packetId == packet.packetId }
    if (packetIndex >= 0) {
        packetLog[packetIndex] = packet
    }
}

private fun packetPreviewText(packet: LoraManetPacket): String {
    return listOf(
        "packetId: ${packet.packetId}",
        "source: ${packet.sourceNodeId}",
        "destination: ${packet.destinationNodeId}",
        "transport: ${packet.selectedTransport}",
        "payload: ${packet.payloadText}",
        "hopPath: ${packet.hopPath.joinToString(" -> ")}",
        "status: ${packet.deliveryStatus}"
    ).joinToString(separator = "\n")
}

private fun createBluetoothProtocolPacket(
    packetType: BluetoothProtocolPacketType,
    payload: String,
    destinationNode: String = "ESP32_BRIDGE",
    status: String = "REQUEST"
): BluetoothProtocolPacket {
    return BluetoothProtocolPacket(
        packetType = packetType,
        packetId = "BT-${packetType.wireName}-${System.currentTimeMillis()}",
        sourceNode = "ANDROID_APP",
        destinationNode = destinationNode,
        payload = payload,
        hopPath = listOf("ANDROID_APP", destinationNode),
        retryCount = 0,
        timestamp = System.currentTimeMillis() / 1000L,
        status = status
    )
}

private fun localEsp32NodeId(connectedDevice: String?): String {
    return when {
        connectedDevice.isNullOrBlank() -> "NODE_A"
        connectedDevice.contains("NODE_A", ignoreCase = true) -> "NODE_A"
        connectedDevice.contains("NODE_B", ignoreCase = true) -> "NODE_B"
        else -> "NODE_A"
    }
}

private fun peerNodeForConnectedEsp32(connectedDevice: String?): String {
    return when (localEsp32NodeId(connectedDevice)) {
        "NODE_A" -> "NODE_B"
        else -> "NODE_A"
    }
}

private fun sanitizeDemoMessageText(value: String): String {
    return value
        .replace("|", "/")
        .replace(";", ",")
        .replace("\"", "'")
        .replace("\\", "/")
        .replace("\n", " ")
        .replace("\r", " ")
        .take(64)
}

private fun readableDemoMessageFromProtocolLine(line: String): String {
    val packet = deserializeBluetoothProtocolPacket(line) ?: return "Packet received"
    return demoTextFromPayload(packet.payload)
}

private fun demoTextFromPayload(payload: String): String {
    val marker = "TEXT="
    val start = payload.indexOf(marker)
    if (start < 0) {
        return payload.ifBlank { "Message received" }
    }

    return payload.substring(start + marker.length)
        .substringBefore(";")
        .ifBlank { "Message received" }
}

private fun protocolPacketFromManetPacket(
    packet: LoraManetPacket,
    packetType: BluetoothProtocolPacketType,
    retryCount: Int
): BluetoothProtocolPacket {
    return BluetoothProtocolPacket(
        packetType = packetType,
        packetId = packet.packetId,
        sourceNode = packet.sourceNodeId,
        destinationNode = packet.destinationNodeId,
        payload = packet.payloadText,
        hopPath = packet.hopPath,
        retryCount = retryCount,
        timestamp = packet.timestamp,
        status = packet.deliveryStatus
    )
}

private fun sampleIncomingAckPacket(packet: LoraManetPacket): BluetoothProtocolPacket {
    return BluetoothProtocolPacket(
        packetType = BluetoothProtocolPacketType.Ack,
        packetId = "ACK-${packet.packetId}",
        sourceNode = "ESP32_BRIDGE",
        destinationNode = packet.sourceNodeId,
        payload = "RECEIVED ${packet.packetId}",
        hopPath = listOf("ESP32_BRIDGE", packet.sourceNodeId),
        retryCount = 0,
        timestamp = System.currentTimeMillis() / 1000L,
        status = "RECEIVED/ACK"
    )
}

private fun serializeBluetoothProtocolPacket(packet: BluetoothProtocolPacket): String {
    return """
        {
          "protocolVersion": "${packet.protocolVersion}",
          "packetType": "${packet.packetType.wireName}",
          "packetId": "${packet.packetId}",
          "sourceNode": "${packet.sourceNode}",
          "destinationNode": "${packet.destinationNode}",
          "payload": "${packet.payload}",
          "hopPath": "${packet.hopPath.joinToString(">")}",
          "retryCount": "${packet.retryCount}",
          "timestamp": "${packet.timestamp}",
          "status": "${packet.status}",
          "checksum": "${packet.checksumPlaceholder}"
        }
    """.trimIndent()
}

private fun compactSerializedPacketText(packet: BluetoothProtocolPacket): String {
    return "{" +
        "\"protocolVersion\":\"${packet.protocolVersion}\"," +
        "\"packetType\":\"${packet.packetType.wireName}\"," +
        "\"packetId\":\"${packet.packetId}\"," +
        "\"sourceNode\":\"${packet.sourceNode}\"," +
        "\"destinationNode\":\"${packet.destinationNode}\"," +
        "\"payload\":\"${packet.payload}\"," +
        "\"hopPath\":\"${packet.hopPath.joinToString(">")}\"," +
        "\"retryCount\":\"${packet.retryCount}\"," +
        "\"timestamp\":\"${packet.timestamp}\"," +
        "\"status\":\"${packet.status}\"," +
        "\"checksum\":\"${packet.checksumPlaceholder}\"" +
        "}"
}

private fun deserializeBluetoothProtocolPacket(rawPacket: String): BluetoothProtocolPacket? {
    fun valueFor(key: String): String? {
        return Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
            .find(rawPacket)
            ?.groupValues
            ?.getOrNull(1)
    }

    val packetType = BluetoothProtocolPacketType.entries.firstOrNull {
        it.wireName == valueFor("packetType")
    } ?: return null

    return BluetoothProtocolPacket(
        protocolVersion = valueFor("protocolVersion") ?: return null,
        packetType = packetType,
        packetId = valueFor("packetId") ?: return null,
        sourceNode = valueFor("sourceNode") ?: return null,
        destinationNode = valueFor("destinationNode") ?: return null,
        payload = valueFor("payload") ?: "",
        hopPath = valueFor("hopPath")?.split(">")?.filter { it.isNotBlank() } ?: emptyList(),
        retryCount = valueFor("retryCount")?.toIntOrNull() ?: 0,
        timestamp = valueFor("timestamp")?.toLongOrNull() ?: 0L,
        status = valueFor("status") ?: return null,
        checksumPlaceholder = valueFor("checksum") ?: CHECKSUM_PLACEHOLDER
    )
}

private fun validateBluetoothProtocolPacket(packet: BluetoothProtocolPacket): ProtocolValidationStatus {
    return ProtocolValidationStatus(
        versionValid = packet.protocolVersion == BLUETOOTH_PROTOCOL_VERSION,
        requiredFieldsPresent = packet.packetId.isNotBlank() &&
            packet.sourceNode.isNotBlank() &&
            packet.destinationNode.isNotBlank() &&
            packet.status.isNotBlank(),
        packetTypeSupported = BluetoothProtocolPacketType.entries.contains(packet.packetType),
        checksumPlaceholderValid = packet.checksumPlaceholder == CHECKSUM_PLACEHOLDER
    )
}

private fun protocolValidationText(validation: ProtocolValidationStatus): String {
    return listOf(
        "version valid: ${validation.versionValid.passFailLabel()}",
        "required fields present: ${validation.requiredFieldsPresent.passFailLabel()}",
        "packet type supported: ${validation.packetTypeSupported.passFailLabel()}",
        "checksum placeholder valid: ${validation.checksumPlaceholderValid.passFailLabel()}"
    ).joinToString(separator = " | ")
}

private fun Boolean.passFailLabel(): String {
    return if (this) "Pass" else "Fail"
}

private fun defaultValidationItems(): List<ValidationChecklistItem> {
    return validationLabels.map { label ->
        ValidationChecklistItem(label = label, status = ValidationStatus.NotTested)
    }
}

private fun runBasicValidation(
    selectedTab: AppTab,
    messages: List<ChatMessage>,
    routingDecision: RoutingDecision,
    packetLog: List<LoraManetPacket>,
    eventLog: List<String>,
    transportStatus: TransportStatus,
    bluetoothState: BluetoothState,
    simulationSpeed: SimulationSpeed,
    networkState: NetworkState
): List<ValidationChecklistItem> {
    val knownStatuses = mapOf(
        "App opens on Chat tab" to if (selectedTab == AppTab.Messaging) ValidationStatus.Pass else ValidationStatus.NotTested,
        "Message send works" to if (messages.isNotEmpty()) ValidationStatus.Pass else ValidationStatus.NotTested,
        "Message queue states work" to if (messages.any { it.status != MessageStatus.Queued || it.retryCount > 0 }) {
            ValidationStatus.Pass
        } else {
            ValidationStatus.NotTested
        },
        "Routing decision updates" to if (routingDecision.allCandidates.isNotEmpty()) ValidationStatus.Pass else ValidationStatus.Fail,
        "Failover works" to if (
            messages.any { it.retryCount > 0 || it.note.contains("Failover") } ||
            eventLog.any { it.contains("failover", ignoreCase = true) || it.contains("retry", ignoreCase = true) }
        ) {
            ValidationStatus.Pass
        } else {
            ValidationStatus.NotTested
        },
        "Packet log updates" to if (packetLog.isNotEmpty()) ValidationStatus.Pass else ValidationStatus.NotTested,
        "Event log updates" to if (eventLog.isNotEmpty()) ValidationStatus.Pass else ValidationStatus.NotTested,
        "Transport bridge status updates" to if (transportStatus.connectionState != TransportConnectionState.Disconnected.label) {
            ValidationStatus.Pass
        } else {
            ValidationStatus.Fail
        },
        "Bluetooth placeholder pairing works" to if (bluetoothState.pairingStatus == PairingStatus.Paired) {
            ValidationStatus.Pass
        } else {
            ValidationStatus.NotTested
        },
        "No battery level appears" to ValidationStatus.Pass,
        "Simulation speed control works" to if (simulationSpeed in SimulationSpeed.entries) ValidationStatus.Pass else ValidationStatus.Fail,
        "Network toggles work" to if (
            listOf(
                networkState.loraAvailable,
                networkState.wifiAvailable,
                networkState.gsmAvailable,
                networkState.satelliteAvailable
            ).any { !it }
        ) {
            ValidationStatus.Pass
        } else {
            ValidationStatus.NotTested
        }
    )

    return validationLabels.map { label ->
        ValidationChecklistItem(
            label = label,
            status = knownStatuses[label] ?: ValidationStatus.NotTested
        )
    }
}

private fun validationSummary(items: List<ValidationChecklistItem>): ValidationSummary {
    return ValidationSummary(
        passedCount = items.count { it.status == ValidationStatus.Pass },
        failedCount = items.count { it.status == ValidationStatus.Fail },
        notTestedCount = items.count { it.status == ValidationStatus.NotTested }
    )
}

private fun packetIdFor(messageId: Long): String {
    return "PKT-${messageId.toString().padStart(4, '0')}"
}

private fun nodeId(node: SimNode): String {
    return node.name.uppercase(Locale.US).replace(" ", "_")
}

private fun transportFor(option: TransportOption): ManetTransportInterface {
    return when (option) {
        TransportOption.Simulation -> SimulationTransport()
        TransportOption.BluetoothPlaceholder -> BluetoothTransportPlaceholder()
        TransportOption.WiFiPlaceholder -> WiFiTransportPlaceholder()
    }
}

private fun decideRoute(
    selectedNetwork: NetworkMode,
    networkState: NetworkState,
    bluetoothState: BluetoothState,
    localNode: SimNode,
    targetNode: SimNode,
    nodes: List<SimNode> = simNodes,
    adaptiveRoutingDecision: RoutingDecision = AdaptiveRoutingEngine().decide(
        selectedNetwork = selectedNetwork,
        networkState = networkState,
        localNode = localNode,
        targetNode = targetNode,
        nodes = nodes
    )
): RouteDecision {
    val path = routePath(localNode, targetNode, nodes)
    if (path.size < 2) {
        val noRouteDecision = adaptiveRoutingDecision.copy(
            selectedRoute = RouteLabel.None,
            routeScore = 0,
            failoverReason = "Source and destination are the same."
        )
        return RouteDecision(
            route = RouteLabel.None,
            status = MessageStatus.Failed,
            metrics = metricsFor(RouteLabel.None, networkState, localNode, path),
            path = path.map { it.name },
            note = "Source and destination are the same.",
            routingDecision = noRouteDecision
        )
    }
    val route = adaptiveRoutingDecision.selectedRoute

    if (route != RouteLabel.None) {
        val preferred = preferredRoute(selectedNetwork)
        val routeNote = if (route == RouteLabel.Lora && bluetoothLinkedToEsp32(bluetoothState)) {
            " LoRa transport is simulated through paired ESP32 ${bluetoothState.connectedNode}."
        } else {
            ""
        }
        return RouteDecision(
            route = route,
            status = MessageStatus.Queued,
            metrics = metricsFor(route, networkState, targetNode, path),
            path = path.map { it.name },
            note = if (route == preferred) {
                "${adaptiveRoutingDecision.failoverReason}$routeNote"
            } else {
                "${adaptiveRoutingDecision.failoverReason}$routeNote"
            },
            routingDecision = adaptiveRoutingDecision
        )
    }

    return RouteDecision(
        route = RouteLabel.None,
        status = MessageStatus.Failed,
        metrics = metricsFor(RouteLabel.None, networkState, targetNode, path),
        path = path.map { it.name },
        note = adaptiveRoutingDecision.failoverReason,
        routingDecision = adaptiveRoutingDecision
    )
}

private fun routePath(
    localNode: SimNode,
    targetNode: SimNode,
    nodes: List<SimNode> = simNodes
): List<SimNode> {
    val startIndex = nodes.indexOfFirst { it.name == localNode.name }
    val endIndex = nodes.indexOfFirst { it.name == targetNode.name }
    if (startIndex < 0 || endIndex < 0) {
        return listOf(localNode)
    }
    return if (startIndex <= endIndex) {
        nodes.subList(startIndex, endIndex + 1)
    } else {
        nodes.subList(endIndex, startIndex + 1).asReversed()
    }
}

private fun failoverRoute(
    selectedNetwork: NetworkMode,
    networkState: NetworkState,
    path: List<SimNode>
): RouteLabel {
    return failoverOrder(selectedNetwork).firstOrNull { route ->
        isRouteGloballyAvailable(route, networkState) && path.all { nodeRouteAvailable(it, route) }
    } ?: RouteLabel.None
}

private fun failoverOrder(selectedNetwork: NetworkMode): List<RouteLabel> {
    val priority = listOf(RouteLabel.Lora, RouteLabel.Wifi, RouteLabel.Gsm, RouteLabel.Satellite)
    return when (selectedNetwork) {
        NetworkMode.Auto -> priority
        NetworkMode.Lora -> priority
        NetworkMode.Wifi -> priority.dropWhile { it != RouteLabel.Wifi }
        NetworkMode.Gsm -> priority.dropWhile { it != RouteLabel.Gsm }
    }
}

private fun candidateName(route: RouteLabel, hopCount: Int): String {
    return when (route) {
        RouteLabel.Lora -> if (hopCount <= 1) "LoRa direct" else "Multi-hop LoRa"
        RouteLabel.Wifi -> "WiFi relay"
        RouteLabel.Gsm -> "GSM fallback"
        RouteLabel.Satellite -> "Satellite fallback"
        RouteLabel.None -> "No route"
    }
}

private fun preferredRoute(selectedNetwork: NetworkMode): RouteLabel {
    return when (selectedNetwork) {
        NetworkMode.Auto,
        NetworkMode.Lora -> RouteLabel.Lora
        NetworkMode.Wifi -> RouteLabel.Wifi
        NetworkMode.Gsm -> RouteLabel.Gsm
    }
}

private fun isRouteGloballyAvailable(route: RouteLabel, networkState: NetworkState): Boolean {
    return when (route) {
        RouteLabel.Lora -> networkState.loraAvailable
        RouteLabel.Wifi -> networkState.wifiAvailable
        RouteLabel.Gsm -> networkState.gsmAvailable
        RouteLabel.Satellite -> networkState.satelliteAvailable
        RouteLabel.None -> false
    }
}

private fun nodeRouteAvailable(node: SimNode, route: RouteLabel): Boolean {
    return when (route) {
        RouteLabel.Lora -> node.loraAvailable
        RouteLabel.Wifi -> node.wifiAvailable
        RouteLabel.Gsm -> node.gsmAvailable
        RouteLabel.Satellite -> node.satelliteAvailable
        RouteLabel.None -> false
    }
}

private fun nodeHasAnyAvailableRoute(node: SimNode, networkState: NetworkState): Boolean {
    return (node.loraAvailable && networkState.loraAvailable) ||
        (node.wifiAvailable && networkState.wifiAvailable) ||
        (node.gsmAvailable && networkState.gsmAvailable) ||
        (node.satelliteAvailable && networkState.satelliteAvailable)
}

private fun bluetoothLinkedToEsp32(bluetoothState: BluetoothState): Boolean {
    return bluetoothState.pairingStatus == PairingStatus.Paired && bluetoothState.connectedNode != null
}

private fun effectiveNetworkState(
    networkState: NetworkState,
    bluetoothState: BluetoothState
): NetworkState {
    return if (bluetoothLinkedToEsp32(bluetoothState)) {
        networkState.copy(loraAvailable = true)
    } else {
        networkState
    }
}

private fun nextNetworkSimulationState(
    nodes: List<SimNode>,
    networkState: NetworkState,
    speed: SimulationSpeed
): NetworkSimulationUpdate {
    val events = mutableListOf<String>()
    var nextNetwork = networkState
    val volatility = speed.volatility
    val updatedNodes = nodes.map { node ->
        val previousHealth = nodeHealth(node, networkState)
        val rssiDelta = Random.nextInt(-3 * volatility, 3 * volatility + 1)
        val snrDelta = Random.nextDouble(-0.7 * volatility, 0.7 * volatility)
        val outageChance = 4 * volatility
        val recoveryChance = 10 * volatility
        var nextNode = node.copy(
            rssi = (node.rssi + rssiDelta).coerceIn(-96, -45),
            snr = (node.snr + snrDelta).coerceIn(1.0, 19.0)
        )

        if (Random.nextInt(100) < outageChance) {
            nextNode = toggleRandomNodeRoute(nextNode, available = false)
        } else if (Random.nextInt(100) < recoveryChance) {
            nextNode = toggleRandomNodeRoute(nextNode, available = true)
        }

        val nextHealth = nodeHealth(nextNode, nextNetwork)
        if (previousHealth != nextHealth) {
            events += "${nextNode.name} changed from $previousHealth to $nextHealth"
        }
        if (node.rssi < -82 && nextNode.rssi >= -72) {
            events += "${nextNode.name} LoRa RSSI recovered"
        } else if (node.rssi >= -72 && nextNode.rssi < -82) {
            events += "${nextNode.name} signal degraded"
        }
        nextNode
    }.let { maybeMoveNode(it, events, volatility) }

    if (Random.nextInt(100) < 8 * volatility) {
        val previous = nextNetwork
        nextNetwork = toggleRandomNetworkRoute(nextNetwork)
        describeNetworkChange(previous, nextNetwork)?.let { events += it }
    }

    val offlineCount = updatedNodes.count { nodeHealth(it, nextNetwork) == "Offline" }
    val weakCount = updatedNodes.count {
        val health = nodeHealth(it, nextNetwork)
        health == "Weak signal" || health == "Critical signal"
    }
    val unavailableLinks = listOf(
        nextNetwork.loraAvailable,
        nextNetwork.wifiAvailable,
        nextNetwork.gsmAvailable,
        nextNetwork.satelliteAvailable
    ).count { !it }
    val condition = when {
        offlineCount >= 2 || unavailableLinks >= 3 -> SimulationCondition.Partitioned
        offlineCount == 1 || weakCount >= 2 || unavailableLinks >= 2 -> SimulationCondition.Congested
        events.any { it.contains("recovered") || it.contains("restored") } -> SimulationCondition.Recovering
        else -> SimulationCondition.Stable
    }

    if (events.isEmpty()) {
        events += "${condition.label} MANET tick: RSSI and link state updated"
    }

    return NetworkSimulationUpdate(
        nodes = updatedNodes,
        networkState = nextNetwork,
        condition = condition,
        events = events.takeLast(4)
    )
}

private fun toggleRandomNodeRoute(node: SimNode, available: Boolean): SimNode {
    return when (Random.nextInt(4)) {
        0 -> node.copy(loraAvailable = available)
        1 -> node.copy(wifiAvailable = available)
        2 -> node.copy(gsmAvailable = available)
        else -> node.copy(satelliteAvailable = available)
    }
}

private fun maybeMoveNode(
    nodes: List<SimNode>,
    events: MutableList<String>,
    volatility: Int
): List<SimNode> {
    if (nodes.size < 4 || Random.nextInt(100) >= 5 * volatility) {
        return nodes
    }
    val movableIndexes = nodes.indices.filter { nodes[it].name != "Gateway Node" }
    val firstIndex = movableIndexes.random()
    val secondIndex = movableIndexes.filter { it != firstIndex }.random()
    val reordered = nodes.toMutableList()
    val firstNode = reordered[firstIndex]
    reordered[firstIndex] = reordered[secondIndex]
    reordered[secondIndex] = firstNode
    events += "Node mobility changed path order"
    events += "Route rediscovered after node mobility"
    return reordered
}

private fun toggleRandomNetworkRoute(networkState: NetworkState): NetworkState {
    return when (Random.nextInt(4)) {
        0 -> networkState.copy(loraAvailable = !networkState.loraAvailable)
        1 -> networkState.copy(wifiAvailable = !networkState.wifiAvailable)
        2 -> networkState.copy(gsmAvailable = !networkState.gsmAvailable)
        else -> networkState.copy(satelliteAvailable = !networkState.satelliteAvailable)
    }
}

private fun describeNetworkChange(
    previous: NetworkState,
    next: NetworkState
): String? {
    return when {
        previous.loraAvailable != next.loraAvailable -> if (next.loraAvailable) "LoRa RSSI recovered" else "LoRa link failed"
        previous.wifiAvailable != next.wifiAvailable -> if (next.wifiAvailable) "WiFi relay recovered" else "WiFi relay degraded"
        previous.gsmAvailable != next.gsmAvailable -> if (next.gsmAvailable) "GSM failover restored" else "GSM failover unavailable"
        previous.satelliteAvailable != next.satelliteAvailable -> if (next.satelliteAvailable) "Satellite fallback restored" else "Satellite fallback interrupted"
        !next.loraAvailable && !next.wifiAvailable && next.gsmAvailable -> "GSM failover activated"
        else -> null
    }
}

private fun nodeHealth(node: SimNode): String {
    return when {
        !node.loraAvailable && !node.wifiAvailable && !node.gsmAvailable && !node.satelliteAvailable -> "Offline"
        node.rssi < -88 || node.snr < 2.5 -> "Critical signal"
        node.rssi < -75 || node.snr < 6.0 -> "Weak signal"
        else -> "Healthy"
    }
}

private fun nodeHealth(node: SimNode, networkState: NetworkState): String {
    return if (!nodeHasAnyAvailableRoute(node, networkState)) {
        "Offline"
    } else {
        nodeHealth(node)
    }
}

private fun routeAvailabilityLabel(node: SimNode): String {
    return listOf(
        "LoRa" to node.loraAvailable,
        "WiFi" to node.wifiAvailable,
        "GSM" to node.gsmAvailable,
        "Sat" to node.satelliteAvailable
    ).joinToString(" ") { (label, available) ->
        "$label:${if (available) "Up" else "Down"}"
    }
}

private fun messageStatusColors(status: MessageStatus): Pair<Color, Color> {
    return when (status) {
        MessageStatus.Queued -> Color(0xFFFFF3CD) to Color(0xFF5C4200)
        MessageStatus.Routing -> Color(0xFFFFF3CD) to Color(0xFF5C4200)
        MessageStatus.Relaying -> Color(0xFFDDEBFF) to Color(0xFF143C70)
        MessageStatus.Retrying -> Color(0xFFFFE3C2) to Color(0xFF7A4100)
        MessageStatus.Delivered -> Color(0xFFD6F1E7) to Color(0xFF145C48)
        MessageStatus.Failed -> Color(0xFFFFDAD6) to Color(0xFF8C1D18)
    }
}

private fun formatRssi(value: Int): String {
    return value.toString()
}

private fun formatSnr(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}

private fun routeQuality(metrics: SimMetrics): String {
    val score = metrics.rssi + (metrics.snr * 2.0) - (metrics.hopCount * 7.0)
    return when {
        score >= -48.0 -> "Excellent"
        score >= -64.0 -> "Good"
        score >= -82.0 -> "Weak"
        else -> "Critical"
    }
}

private fun simulatedDelayMs(route: RouteLabel, metrics: SimMetrics): Long {
    if (route == RouteLabel.None) {
        return 0L
    }

    val routeBase = when (route) {
        RouteLabel.Lora -> 420L
        RouteLabel.Wifi -> 260L
        RouteLabel.Gsm -> 620L
        RouteLabel.Satellite -> 880L
        RouteLabel.None -> 0L
    }
    val hopPenalty = metrics.hopCount * 180L
    val rssiPenalty = when {
        metrics.rssi >= -60 -> 0L
        metrics.rssi >= -72 -> 180L
        else -> 360L
    }
    return routeBase + hopPenalty + rssiPenalty
}

private fun metricsFor(
    route: RouteLabel,
    networkState: NetworkState,
    targetNode: SimNode,
    path: List<SimNode>
): SimMetrics {
    val satelliteStatus = if (networkState.satelliteAvailable) "Available" else "Unavailable"
    val averageRssi = path.map { it.rssi }.average().toInt()
    val averageSnr = path.map { it.snr }.average()
    val hopCount = (path.size - 1).coerceAtLeast(0)
    return when (route) {
        RouteLabel.Lora -> SimMetrics(averageRssi, averageSnr, hopCount, targetNode.gatewayProximity, satelliteStatus)
        RouteLabel.Wifi -> SimMetrics(averageRssi + 8, averageSnr + 4.0, hopCount, targetNode.gatewayProximity, satelliteStatus)
        RouteLabel.Gsm -> SimMetrics(averageRssi - 6, averageSnr - 2.0, hopCount, targetNode.gatewayProximity, satelliteStatus)
        RouteLabel.Satellite -> SimMetrics(averageRssi - 2, averageSnr + 1.0, hopCount, targetNode.gatewayProximity, satelliteStatus)
        RouteLabel.None -> SimMetrics(0, 0.0, 0, "Unavailable", satelliteStatus)
    }
}

private fun simulationStatus(isAvailable: Boolean): String {
    return if (isAvailable) "Simulated Up" else "Simulated Down"
}

private fun currentTimeLabel(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
}

private fun connectionUptimeLabel(session: BluetoothConnectionSession): String {
    val startedAt = session.startedAt ?: return "0s"
    val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
    return "${elapsedSeconds}s"
}

@Preview(showBackground = true)
@Composable
private fun MessengerAppPreview() {
    PUPMANETMessengerTheme {
        MessengerApp()
    }
}
