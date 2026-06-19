package com.apia.musicplayer.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.apia.musicplayer.domain.model.AuthType
import com.apia.musicplayer.domain.model.SearchSource
import com.apia.musicplayer.domain.model.SourceCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var savedSnack by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = {
            if (savedSnack) {
                Snackbar(modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { savedSnack = false }) { Text("OK") } }
                ) { Text("Settings saved") }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Сводка активных источников
            item {
                Spacer(Modifier.height(4.dp))
                val enabledCount = state.enabledSources.size
                val connectedCount = state.connectedStatus.count { it.value }
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatBadge("$enabledCount", "Enabled")
                        StatBadge("$connectedCount", "Connected")
                        StatBadge("${SearchSource.entries.size}", "Total")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Группы по категориям
            val byCategory = SearchSource.entries.groupBy { it.meta.category }
            byCategory.forEach { (category, sources) ->
                item {
                    Text(category.label, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                }
                items(sources, key = { it.name }) { source ->
                    SourceCard(
                        source = source,
                        enabled = state.enabledSources.contains(source),
                        creds = state.credentials[source] ?: SourceCredentials(),
                        connectedStatus = state.connectedStatus[source],
                        onToggle = { viewModel.toggleSource(source, it) },
                        onCredsChange = { viewModel.updateCreds(source, it) },
                        onConnect = { viewModel.connect(source) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.saveAll(); savedSnack = true },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save all settings")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun StatBadge(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
fun SourceCard(
    source: SearchSource,
    enabled: Boolean,
    creds: SourceCredentials,
    connectedStatus: Boolean?,
    onToggle: (Boolean) -> Unit,
    onCredsChange: (SourceCredentials) -> Unit,
    onConnect: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val meta = source.meta
    val needsCreds = meta.authType != AuthType.NONE

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                connectedStatus == true -> MaterialTheme.colorScheme.surfaceVariant
                enabled -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Emoji
                Text(meta.emoji, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(meta.displayName, style = MaterialTheme.typography.bodyMedium)
                        // Quality chip
                        AssistChip(
                            onClick = {},
                            label = { Text(meta.quality, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(20.dp)
                        )
                        // Connection status icon
                        when {
                            connectedStatus == true ->
                                Icon(Icons.Default.CheckCircle, "Connected",
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            meta.authType == AuthType.LOGIN_PASS || meta.authType == AuthType.TOKEN ->
                                Icon(Icons.Default.Lock, "Login required",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            meta.authType == AuthType.OPTIONAL_TOKEN ->
                                Icon(Icons.Default.LockOpen, "Optional",
                                    tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                            else -> {}
                        }
                    }
                    Text(meta.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                // Expand creds button
                if (needsCreds) {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, modifier = Modifier.size(20.dp))
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            // Credentials form
            AnimatedVisibility(visible = expanded && needsCreds) {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    when (meta.authType) {
                        AuthType.LOGIN_PASS -> {
                            OutlinedTextField(
                                value = creds.login,
                                onValueChange = { onCredsChange(creds.copy(login = it)) },
                                label = { Text("Login") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = { Icon(Icons.Default.Person, null) },
                                singleLine = true
                            )
                            PasswordTextField(
                                value = creds.password, label = "Password",
                                onValueChange = { onCredsChange(creds.copy(password = it)) }
                            )
                            Button(
                                onClick = onConnect,
                                modifier = Modifier.fillMaxWidth(),
                                colors = if (connectedStatus == true)
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                else ButtonDefaults.buttonColors()
                            ) {
                                Icon(if (connectedStatus == true) Icons.Default.CheckCircle else Icons.Default.Login, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (connectedStatus == true) "Connected ✓" else "Connect")
                            }
                        }
                        AuthType.TOKEN, AuthType.OPTIONAL_TOKEN -> {
                            val label = when (source) {
                                com.apia.musicplayer.domain.model.SearchSource.VK -> "VK Token (Kate Mobile)"
                                com.apia.musicplayer.domain.model.SearchSource.YANDEX -> "Yandex OAuth Token"
                                com.apia.musicplayer.domain.model.SearchSource.DEEZER -> "ARL Cookie (optional, for full tracks)"
                                com.apia.musicplayer.domain.model.SearchSource.SOUNDCLOUD -> "Client ID (auto-extracted if empty)"
                                com.apia.musicplayer.domain.model.SearchSource.YOUTUBE -> "YouTube API Key (optional)"
                                com.apia.musicplayer.domain.model.SearchSource.JAMENDO -> "Client ID (built-in demo key works)"
                                com.apia.musicplayer.domain.model.SearchSource.FMA -> "API Key (built-in public key works)"
                                else -> "Token / API Key"
                            }
                            PasswordTextField(
                                value = creds.token, label = label,
                                onValueChange = { onCredsChange(creds.copy(token = it)) }
                            )
                            if (meta.authType == AuthType.OPTIONAL_TOKEN) {
                                Text("Works without token. Token gives higher limits.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Check, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Apply Token")
                            }
                        }
                        AuthType.NONE -> {
                            Text("No credentials needed — ready to use!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PasswordTextField(value: String, label: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
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
