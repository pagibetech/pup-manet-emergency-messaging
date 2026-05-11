package ph.edu.pup.manetmessenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ph.edu.pup.manetmessenger.ui.theme.PUPMANETMessengerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    Relayed("Relayed"),
    Delivered("Delivered"),
    Failed("Failed")
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

enum class TransportOption(val label: String) {
    Simulation("Simulation"),
    BluetoothPlaceholder("Bluetooth Placeholder"),
    WiFiPlaceholder("WiFi Placeholder"),
    UsbSerialPlaceholder("USB Serial Placeholder")
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
    Messaging("Messaging"),
    Network("Network"),
    Routing("Routing"),
    Simulation("Simulation"),
    Diagnostics("Diagnostics")
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
    val deliveryStatus: String
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
    val packet: LoraManetPacket
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

private class UsbSerialTransportPlaceholder : BaseTransport(
    implementationName = "UsbSerialTransportPlaceholder",
    connectedLabel = TransportConnectionState.PlaceholderReady.label
)

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
    var activeTransport by remember {
        mutableStateOf<ManetTransportInterface>(transportFor(TransportOption.Simulation))
    }
    var transportStatus by remember { mutableStateOf(activeTransport.connect()) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val packetLog = remember { mutableStateListOf<LoraManetPacket>() }
    val eventLog = remember { mutableStateListOf<String>() }
    val queueScope = rememberCoroutineScope()
    val routingEngine = remember { AdaptiveRoutingEngine() }
    val routedNetworkState = effectiveNetworkState(networkState, bluetoothState)
    val adaptiveRoutingDecision = routingEngine.decide(
        selectedNetwork = selectedNetwork,
        networkState = routedNetworkState,
        localNode = localNode,
        targetNode = targetNode,
        nodes = simulatedNodes
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
                            val transportForMessage = activeTransport
                            val sentPacket = transportForMessage.sendPacket(packet)
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
                                    delay(300L)
                                    val queuedIndex = messages.indexOfFirst { it.id == messageId }
                                    if (queuedIndex >= 0) {
                                        val relayedPacket = messages[queuedIndex].packet.copy(
                                            deliveryStatus = MessageStatus.Relayed.label
                                        )
                                        messages[queuedIndex] = messages[queuedIndex].copy(
                                            status = MessageStatus.Relayed,
                                            progressStep = if (decision.path.size > 1) 1 else 0,
                                            packet = relayedPacket
                                        )
                                        updatePacketLog(packetLog, relayedPacket)
                                    }

                                    delay(delayMs)
                                    val relayIndex = messages.indexOfFirst { it.id == messageId }
                                    if (relayIndex >= 0) {
                                        val deliveredPacket = (transportForMessage.receivePacket() ?: messages[relayIndex].packet).copy(
                                            deliveryStatus = MessageStatus.Delivered.label
                                        )
                                        messages[relayIndex] = messages[relayIndex].copy(
                                            status = MessageStatus.Delivered,
                                            progressStep = (decision.path.size - 1).coerceAtLeast(0),
                                            packet = deliveredPacket
                                        )
                                        updatePacketLog(packetLog, deliveredPacket)
                                        if (activeTransport === transportForMessage) {
                                            transportStatus = transportForMessage.getTransportStatus()
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
                    }
                    AppTab.Simulation -> {
                        item {
                            BluetoothPanel(
                                bluetoothState = bluetoothState,
                                onBluetoothStateChange = { bluetoothState = it }
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
                text = { Text(tab.label) }
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
        StatusRow(label = "Message states", value = "Queued, Relayed, Delivered, Failed")
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
    onBluetoothStateChange: (BluetoothState) -> Unit
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
            text = "Bluetooth Pairing",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Bluetooth", value = bluetoothState.bluetoothStatus.label)
        StatusRow(label = "Connected ESP32 Node", value = bluetoothState.connectedNode ?: "None")
        StatusRow(label = "Pairing Status", value = bluetoothState.pairingStatus.label)
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
                onClick = {
                    onBluetoothStateChange(
                        bluetoothState.copy(
                            discoveredNodes = fakeEsp32Nodes,
                            selectedNode = bluetoothState.selectedNode ?: fakeEsp32Nodes.first(),
                            connectedNode = null,
                            pairingStatus = PairingStatus.Scanning
                        )
                    )
                }
            ) {
                Text("Scan")
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = bluetoothState.discoveredNodes.isNotEmpty(),
                onClick = {
                    val node = bluetoothState.selectedNode ?: bluetoothState.discoveredNodes.firstOrNull()
                    onBluetoothStateChange(
                        if (node == null) {
                            bluetoothState.copy(pairingStatus = PairingStatus.ConnectionFailed)
                        } else {
                            bluetoothState.copy(
                                selectedNode = node,
                                connectedNode = node,
                                pairingStatus = PairingStatus.Paired
                            )
                        }
                    )
                }
            ) {
                Text("Pair")
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = bluetoothState.connectedNode != null || bluetoothState.pairingStatus != PairingStatus.NotPaired,
            onClick = {
                onBluetoothStateChange(
                    bluetoothState.copy(
                        connectedNode = null,
                        pairingStatus = PairingStatus.NotPaired
                    )
                )
            }
        ) {
            Text("Disconnect")
        }
        if (bluetoothState.discoveredNodes.isEmpty()) {
            Text(
                text = "No simulated ESP32 nodes scanned yet.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "Simulated ESP32 Nodes",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            bluetoothState.discoveredNodes.chunked(2).forEach { rowNodes ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowNodes.forEach { nodeName ->
                        FilterChip(
                            selected = bluetoothState.selectedNode == nodeName,
                            onClick = {
                                onBluetoothStateChange(
                                    bluetoothState.copy(
                                        selectedNode = nodeName,
                                        pairingStatus = if (bluetoothState.connectedNode == nodeName) {
                                            PairingStatus.Paired
                                        } else {
                                            PairingStatus.Scanning
                                        }
                                    )
                                )
                            },
                            label = { Text(nodeName) }
                        )
                    }
                }
            }
        }
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
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Current Route",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Selected", value = routingDecision.selectedRoute.label)
        StatusRow(label = "Score", value = routingDecision.routeScore.toString())
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${packet.packetId} | ${packet.selectedTransport} | ${packet.deliveryStatus}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = packet.hopPath.joinToString(" -> "),
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
            text = "${message.sourceNode} -> ${message.targetNode}",
            style = MaterialTheme.typography.labelSmall,
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
        Text(
            text = "Quality ${message.routeQuality} | Delay ${message.delayMs} ms",
            style = MaterialTheme.typography.labelSmall,
            color = statusColors.second
        )
        Text(
            text = "Gateway ${message.metrics.gatewayProximity} | Satellite ${message.metrics.satelliteStatus}",
            style = MaterialTheme.typography.labelSmall,
            color = statusColors.second
        )
        PacketPreview(packet = message.packet, textColor = statusColors.second)
        Text(
            text = message.note,
            style = MaterialTheme.typography.labelSmall,
            color = statusColors.second
        )
    }
}

@Composable
private fun PacketPreview(packet: LoraManetPacket, textColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.White,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "Packet ${packet.packetId}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Text(
            text = "Source ${packet.sourceNodeId} | Destination ${packet.destinationNodeId}",
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
        Text(
            text = "Transport ${packet.selectedTransport} | Hop ${packet.hopCount} | Status ${packet.deliveryStatus}",
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
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
        deliveryStatus = decision.status.label
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
        packet = packet
    )
}

private fun updatePacketLog(packetLog: MutableList<LoraManetPacket>, packet: LoraManetPacket) {
    val packetIndex = packetLog.indexOfFirst { it.packetId == packet.packetId }
    if (packetIndex >= 0) {
        packetLog[packetIndex] = packet
    }
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
        TransportOption.UsbSerialPlaceholder -> UsbSerialTransportPlaceholder()
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
        MessageStatus.Relayed -> Color(0xFFDDEBFF) to Color(0xFF143C70)
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

@Preview(showBackground = true)
@Composable
private fun MessengerAppPreview() {
    PUPMANETMessengerTheme {
        MessengerApp()
    }
}
