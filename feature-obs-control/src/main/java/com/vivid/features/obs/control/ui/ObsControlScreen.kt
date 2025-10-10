package com.vivid.features.obs.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vivid.core.network.obs.OBSWebSocketClient
import com.vivid.features.obs.control.ObsControlUiState
import com.vivid.features.obs.control.ObsControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObsControlScreen(
    navController: NavHostController? = null,
    viewModel: ObsControlViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val streamState by viewModel.streamState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OBS Control") },
                navigationIcon = {
                    navController?.let {
                        IconButton(onClick = { it.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, "Zurück")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status Card
            ConnectionStatusCard(connectionState, streamState)

            // Main Content based on state
            when (val state = uiState) {
                is ObsControlUiState.Idle -> {
                    IdleContent(
                        onConnect = viewModel::connectToObs
                    )
                }

                is ObsControlUiState.Connecting -> {
                    ConnectingContent()
                }

                is ObsControlUiState.Connected -> {
                    ConnectedContent(
                        streamState = streamState,
                        onStartStream = viewModel::startStream,
                        onStopStream = viewModel::stopStream,
                        onDisconnect = viewModel::disconnect
                    )
                }

                is ObsControlUiState.Error -> {
                    ErrorContent(
                        errorMessage = state.message,
                        onRetry = viewModel::connectToObs,
                        onDismiss = viewModel::dismissError
                    )
                }
            }
        }
    }
}

// ============================================================================
// Connecting Content
// ============================================================================

@Composable
fun ConnectingContent() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Verbinde mit OBS...",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

// ============================================================================
// Connected Content (Stream Controls)
// ============================================================================

@Composable
fun ConnectedContent(
    streamState: OBSWebSocketClient.StreamState,
    onStartStream: () -> Unit,
    onStopStream: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Stream Steuerung",
                style = MaterialTheme.typography.titleLarge
            )

            // Stream Control Buttons
            when (streamState) {
                OBSWebSocketClient.StreamState.STOPPED -> {
                    Button(
                        onClick = onStartStream,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stream starten")
                    }
                }

                OBSWebSocketClient.StreamState.STARTING -> {
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Startet...")
                    }
                }

                OBSWebSocketClient.StreamState.STREAMING -> {
                    Button(
                        onClick = onStopStream,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stream stoppen")
                    }
                }

                OBSWebSocketClient.StreamState.STOPPING -> {
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Stoppt...")
                    }
                }
            }

            HorizontalDivider()

            // Disconnect Button
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.LinkOff, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Verbindung trennen")
            }
        }
    }
}

// ============================================================================
// Error Content
// ============================================================================

@Composable
fun ErrorContent(
    errorMessage: String,
    onRetry: (String, Int, String?) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Verbindungsfehler",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Zurück")
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Erneut")
                }
            }
        }
    }
}

// ============================================================================
// Connection Status Card
// ============================================================================

@Composable
fun ConnectionStatusCard(
    connectionState: OBSWebSocketClient.ConnectionState,
    streamState: OBSWebSocketClient.StreamState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (connectionState) {
                is OBSWebSocketClient.ConnectionState.Connected ->
                    MaterialTheme.colorScheme.primaryContainer
                is OBSWebSocketClient.ConnectionState.Connecting ->
                    MaterialTheme.colorScheme.secondaryContainer
                is OBSWebSocketClient.ConnectionState.Error ->
                    MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Connection Status
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (connectionState) {
                        is OBSWebSocketClient.ConnectionState.Connected -> Icons.Default.CheckCircle
                        is OBSWebSocketClient.ConnectionState.Connecting -> Icons.Default.Refresh
                        is OBSWebSocketClient.ConnectionState.Error -> Icons.Default.Error
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = when (connectionState) {
                        is OBSWebSocketClient.ConnectionState.Connected ->
                            MaterialTheme.colorScheme.primary
                        is OBSWebSocketClient.ConnectionState.Error ->
                            MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Text(
                    text = when (connectionState) {
                        is OBSWebSocketClient.ConnectionState.Connected -> "Verbunden"
                        is OBSWebSocketClient.ConnectionState.Connecting -> "Verbinde..."
                        is OBSWebSocketClient.ConnectionState.Disconnected -> "Getrennt"
                        is OBSWebSocketClient.ConnectionState.Error -> "Fehler"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // Stream Status (only when connected)
            if (connectionState is OBSWebSocketClient.ConnectionState.Connected) {
                HorizontalDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when (streamState) {
                            OBSWebSocketClient.StreamState.STREAMING -> Icons.Default.PlayArrow
                            OBSWebSocketClient.StreamState.STARTING -> Icons.Default.Refresh
                            OBSWebSocketClient.StreamState.STOPPING -> Icons.Default.Refresh
                            OBSWebSocketClient.StreamState.STOPPED -> Icons.Default.Stop
                        },
                        contentDescription = null,
                        tint = if (streamState == OBSWebSocketClient.StreamState.STREAMING) {
                            MaterialTheme.colorScheme.error // Red for live
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Text(
                        text = when (streamState) {
                            OBSWebSocketClient.StreamState.STREAMING -> "🔴 LIVE"
                            OBSWebSocketClient.StreamState.STARTING -> "Stream startet..."
                            OBSWebSocketClient.StreamState.STOPPING -> "Stream stoppt..."
                            OBSWebSocketClient.StreamState.STOPPED -> "Stream gestoppt"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

// ============================================================================
// Idle Content (Not Connected)
// ============================================================================

@Composable
fun IdleContent(
    onConnect: (String, Int, String?) -> Unit
) {
    var host by remember { mutableStateOf("192.168.1.100") }
    var port by remember { mutableStateOf("4455") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "OBS WebSocket Einstellungen",
                style = MaterialTheme.typography.titleLarge
            )

            // Host Input
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host / IP-Adresse") },
                placeholder = { Text("z.B. 192.168.1.100") },
                leadingIcon = {
                    Icon(Icons.Default.Computer, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Port Input
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { char -> char.isDigit() } },
                label = { Text("Port") },
                placeholder = { Text("4455") },
                leadingIcon = {
                    Icon(Icons.Default.Settings, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            // Password Input
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Passwort (optional)") },
                placeholder = { Text("Leer lassen wenn kein Passwort") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible)
                                Icons.Default.Visibility
                            else
                                Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible)
                                "Passwort verbergen"
                            else
                                "Passwort anzeigen"
                        )
                    }
                },
                visualTransformation = if (passwordVisible)
                    VisualTransformation.None
                else
                    PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Connect Button
            Button(
                onClick = {
                    val portInt = port.toIntOrNull() ?: 4455
                    val pwd = password.ifBlank { null }
                    onConnect(host, portInt, pwd)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = host.isNotBlank()
            ) {
                Icon(Icons.Default.Link, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Verbinden")
            }
        }
    }
}

// ============================================================================