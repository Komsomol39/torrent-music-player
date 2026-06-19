package com.apia.musicplayer.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apia.musicplayer.domain.model.SearchSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Активные источники
            Text("Search sources", style = MaterialTheme.typography.titleMedium)
            SearchSource.entries.forEach { source ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${source.flag}  ${source.displayName}", style = MaterialTheme.typography.bodyLarge)
                        if (source.requiresLogin) {
                            Text("Requires login / token", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = state.enabledSources.contains(source),
                        onCheckedChange = { viewModel.toggleSource(source, it) }
                    )
                }
            }

            HorizontalDivider()

            // RuTracker логин
            Text("RuTracker.org", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.rutrackerLogin,
                onValueChange = { viewModel.setRutrackerLogin(it) },
                label = { Text("Login") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, null) },
                singleLine = true
            )
            PasswordField(
                value = state.rutrackerPassword,
                label = "Password",
                onValueChange = { viewModel.setRutrackerPassword(it) }
            )
            Button(
                onClick = { viewModel.loginRutracker() },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.rutrackerLogin.isNotBlank() && state.rutrackerPassword.isNotBlank()
            ) {
                if (state.rutrackerLoggedIn) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connected")
                } else {
                    Text("Connect")
                }
            }

            HorizontalDivider()

            // VK токен
            Text("VK Music", style = MaterialTheme.typography.titleMedium)
            Text(
                "Kate Mobile token required. Get via kate.cs@itmailboxes.com login + VK account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PasswordField(
                value = state.vkToken,
                label = "VK Token",
                onValueChange = { viewModel.setVkToken(it) }
            )

            HorizontalDivider()

            // YouTube API Key
            Text("YouTube", style = MaterialTheme.typography.titleMedium)
            Text(
                "Optional: YouTube Data API v3 key from console.cloud.google.com. Without key uses web scraping.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PasswordField(
                value = state.youtubeApiKey,
                label = "YouTube API Key (optional)",
                onValueChange = { viewModel.setYoutubeApiKey(it) }
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun PasswordField(value: String, label: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
            }
        },
        singleLine = true
    )
}
