import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val obsHost by viewModel.obsHost.collectAsState()
    val obsPort by viewModel.obsPort.collectAsState()
    val obsPassword by viewModel.obsPassword.collectAsState()

    var hostState by remember { mutableStateOf("") }
    var portState by remember { mutableStateOf("") }
    var passwordState by remember { mutableStateOf("") }

    LaunchedEffect(obsHost, obsPort, obsPassword) {
        obsHost?.let { hostState = it }
        obsPort?.let { portState = it }
        obsPassword?.let { passwordState = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = hostState,
            onValueChange = { hostState = it },
            label = { Text("OBS Host") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = portState,
            onValueChange = { portState = it },
            label = { Text("OBS Port") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = passwordState,
            onValueChange = { passwordState = it },
            label = { Text("OBS Password") },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                viewModel.saveObsSettings(hostState, portState, passwordState)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save")
        }
    }
}
