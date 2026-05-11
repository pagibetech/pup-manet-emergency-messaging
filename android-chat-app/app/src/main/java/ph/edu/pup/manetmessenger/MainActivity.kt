package ph.edu.pup.manetmessenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

data class NetworkState(
    val loraAvailable: Boolean = true,
    val wifiAvailable: Boolean = true,
    val gsmAvailable: Boolean = true,
    val satelliteAvailable: Boolean = true
)

data class SimMetrics(
    val rssi: Int,
    val snr: Double,
    val hopCount: Int,
    val batteryLevel: Int,
    val gatewayProximity: String,
    val satelliteStatus: String
)

data class RouteDecision(
    val route: RouteLabel,
    val status: MessageStatus,
    val metrics: SimMetrics,
    val path: List<String>,
    val note: String
)

data class SimNode(
    val name: String,
    val batteryLevel: Int,
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
    val delayMs: Long
)

private val simNodes = listOf(
    SimNode("Node Alpha", 91, -58, 11.0, 0, "Near gateway", true, true, false, true),
    SimNode("Node Bravo", 84, -64, 9.2, 1, "One relay", true, true, true, true),
    SimNode("Node Charlie", 76, -71, 7.5, 2, "Two relays", true, false, true, true),
    SimNode("Node Delta", 69, -78, 5.8, 3, "Edge relay", true, false, true, true),
    SimNode("Gateway Node", 97, -48, 17.4, 0, "Gateway", true, true, true, true)
)

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
    var localNode by remember { mutableStateOf(simNodes.first()) }
    var targetNode by remember { mutableStateOf(simNodes.last()) }
    var draftMessage by remember { mutableStateOf("") }
    var nextMessageId by remember { mutableStateOf(1L) }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val queueScope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            MessageComposer(
                draftMessage = draftMessage,
                onDraftChange = { draftMessage = it },
                onSend = {
                    val trimmedMessage = draftMessage.trim()
                    if (trimmedMessage.isNotEmpty()) {
                        val decision = decideRoute(
                            selectedNetwork = selectedNetwork,
                            networkState = networkState,
                            localNode = localNode,
                            targetNode = targetNode
                        )
                        val messageId = nextMessageId++
                        val delayMs = simulatedDelayMs(decision.route, decision.metrics)
                        messages.add(
                            ChatMessage(
                                id = messageId,
                                text = trimmedMessage,
                                sourceNode = localNode.name,
                                targetNode = targetNode.name,
                                route = decision.route,
                                status = decision.status,
                                metrics = decision.metrics,
                                path = decision.path,
                                note = decision.note,
                                sentAt = currentTimeLabel(),
                                progressStep = 0,
                                routeQuality = routeQuality(decision.metrics),
                                delayMs = delayMs
                            )
                        )
                        draftMessage = ""
                        if (decision.route != RouteLabel.None) {
                            queueScope.launch {
                                delay(300L)
                                val queuedIndex = messages.indexOfFirst { it.id == messageId }
                                if (queuedIndex >= 0) {
                                    messages[queuedIndex] = messages[queuedIndex].copy(
                                        status = MessageStatus.Relayed,
                                        progressStep = if (decision.path.size > 1) 1 else 0
                                    )
                                }

                                delay(delayMs)
                                val relayIndex = messages.indexOfFirst { it.id == messageId }
                                if (relayIndex >= 0) {
                                    messages[relayIndex] = messages[relayIndex].copy(
                                        status = MessageStatus.Delivered,
                                        progressStep = (decision.path.size - 1).coerceAtLeast(0)
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { AppHeader() }
            item {
                NetworkSelector(
                    selectedNetwork = selectedNetwork,
                    onNetworkSelected = { selectedNetwork = it }
                )
            }
            item {
                NodeSelector(
                    localNode = localNode,
                    targetNode = targetNode,
                    onLocalNodeSelected = { selected ->
                        localNode = selected
                        if (targetNode == selected) {
                            targetNode = simNodes.first { it != selected }
                        }
                    },
                    onTargetNodeSelected = { targetNode = it }
                )
            }
            item {
                NodeSummaryPanel(
                    localNode = localNode,
                    targetNode = targetNode,
                    networkState = networkState
                )
            }
            item {
                TopologyOverviewPanel(
                    localNode = localNode,
                    targetNode = targetNode,
                    networkState = networkState
                )
            }
            item {
                StatusPanel(
                    selectedNetwork = selectedNetwork,
                    networkState = networkState
                )
            }
            item {
                SimulationControls(
                    networkState = networkState,
                    onNetworkStateChange = { networkState = it }
                )
            }
            item {
                MetricsPanel(
                    selectedNetwork = selectedNetwork,
                    networkState = networkState,
                    localNode = localNode,
                    targetNode = targetNode
                )
            }
            item {
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (messages.isEmpty()) {
                item { EmptyMessageState() }
            } else {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }
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
            availableNodes = simNodes,
            onNodeSelected = onLocalNodeSelected
        )
        NodeChipGroup(
            title = "Target destination node",
            selectedNode = targetNode,
            availableNodes = simNodes.filter { it != localNode },
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
                        selected = selectedNode == node,
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
            text = "Battery ${node.batteryLevel}% | RSSI ${node.rssi} dBm | SNR ${node.snr} dB | Hop ${node.hopCount}",
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
    networkState: NetworkState
) {
    val path = routePath(localNode, targetNode)
    val offlineNodes = simNodes.filter { !nodeHasAnyAvailableRoute(it, networkState) }
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
        StatusRow(label = "Connected nodes", value = "${simNodes.size - offlineNodes.size}/${simNodes.size}")
        StatusRow(label = "Offline nodes", value = if (offlineNodes.isEmpty()) "None" else offlineNodes.joinToString { it.name })
        StatusRow(label = "Gateway node", value = "Gateway Node")
        Text(
            text = "Path: ${path.joinToString(" -> ") { it.name }}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = simNodes.joinToString(" | ") { "${it.name.removePrefix("Node ")}: ${nodeHealth(it, networkState)}" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusPanel(
    selectedNetwork: NetworkMode,
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
            text = "Status",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        StatusRow(label = "Current selected network", value = selectedNetwork.label)
        StatusRow(label = "LoRa status", value = simulationStatus(networkState.loraAvailable))
        StatusRow(label = "WiFi status", value = simulationStatus(networkState.wifiAvailable))
        StatusRow(label = "GSM status", value = simulationStatus(networkState.gsmAvailable))
        StatusRow(label = "Satellite link", value = simulationStatus(networkState.satelliteAvailable))
        StatusRow(label = "Bluetooth status", value = "Not connected")
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
private fun SimulationControls(
    networkState: NetworkState,
    onNetworkStateChange: (NetworkState) -> Unit
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
    localNode: SimNode,
    targetNode: SimNode
) {
    val decision = decideRoute(selectedNetwork, networkState, localNode, targetNode)
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
        StatusRow(label = "Battery", value = "${formatBattery(decision.metrics.batteryLevel)}%")
        StatusRow(label = "Gateway", value = decision.metrics.gatewayProximity)
        StatusRow(label = "Satellite", value = decision.metrics.satelliteStatus)
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
            text = "RSSI ${formatRssi(message.metrics.rssi)} dBm | SNR ${formatSnr(message.metrics.snr)} dB | Hop ${message.metrics.hopCount} | Battery ${formatBattery(message.metrics.batteryLevel)}%",
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
        Text(
            text = message.note,
            style = MaterialTheme.typography.labelSmall,
            color = statusColors.second
        )
    }
}

private fun decideRoute(
    selectedNetwork: NetworkMode,
    networkState: NetworkState,
    localNode: SimNode,
    targetNode: SimNode
): RouteDecision {
    val path = routePath(localNode, targetNode)
    if (path.size < 2) {
        return RouteDecision(
            route = RouteLabel.None,
            status = MessageStatus.Failed,
            metrics = metricsFor(RouteLabel.None, networkState, localNode, path),
            path = path.map { it.name },
            note = "Source and destination are the same."
        )
    }
    val route = failoverRoute(selectedNetwork, networkState, path)

    if (route != RouteLabel.None) {
        val preferred = preferredRoute(selectedNetwork)
        return RouteDecision(
            route = route,
            status = MessageStatus.Queued,
            metrics = metricsFor(route, networkState, targetNode, path),
            path = path.map { it.name },
            note = if (route == preferred) {
                "Preferred simulated route available end-to-end."
            } else {
                "Adaptive failover selected ${route.label} after preferred route became unavailable."
            }
        )
    }

    return RouteDecision(
        route = RouteLabel.None,
        status = MessageStatus.Failed,
        metrics = metricsFor(RouteLabel.None, networkState, targetNode, path),
        path = path.map { it.name },
        note = "No route type is available across the simulated MANET path."
    )
}

private fun routePath(localNode: SimNode, targetNode: SimNode): List<SimNode> {
    val startIndex = simNodes.indexOf(localNode)
    val endIndex = simNodes.indexOf(targetNode)
    if (startIndex < 0 || endIndex < 0) {
        return listOf(localNode)
    }
    return if (startIndex <= endIndex) {
        simNodes.subList(startIndex, endIndex + 1)
    } else {
        simNodes.subList(endIndex, startIndex + 1).asReversed()
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

private fun nodeHealth(node: SimNode): String {
    return when {
        !node.loraAvailable && !node.wifiAvailable && !node.gsmAvailable && !node.satelliteAvailable -> "Offline"
        node.batteryLevel < 25 -> "Low battery"
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

private fun formatBattery(value: Int): String {
    return value.toString()
}

private fun routeQuality(metrics: SimMetrics): String {
    val score = metrics.rssi + (metrics.snr * 2.0) - (metrics.hopCount * 7.0) + (metrics.batteryLevel * 0.08)
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
    val weakestBattery = path.minOfOrNull { it.batteryLevel } ?: targetNode.batteryLevel
    val hopCount = (path.size - 1).coerceAtLeast(0)
    return when (route) {
        RouteLabel.Lora -> SimMetrics(averageRssi, averageSnr, hopCount, weakestBattery, targetNode.gatewayProximity, satelliteStatus)
        RouteLabel.Wifi -> SimMetrics(averageRssi + 8, averageSnr + 4.0, hopCount, weakestBattery, targetNode.gatewayProximity, satelliteStatus)
        RouteLabel.Gsm -> SimMetrics(averageRssi - 6, averageSnr - 2.0, hopCount, weakestBattery, targetNode.gatewayProximity, satelliteStatus)
        RouteLabel.Satellite -> SimMetrics(averageRssi - 2, averageSnr + 1.0, hopCount, weakestBattery, targetNode.gatewayProximity, satelliteStatus)
        RouteLabel.None -> SimMetrics(0, 0.0, 0, 0, "Unavailable", satelliteStatus)
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
