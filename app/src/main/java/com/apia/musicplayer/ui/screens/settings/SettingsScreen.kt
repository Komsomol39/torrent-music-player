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
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { savedSnack = false }) { Text("OK") } }
                ) { Text("Settings saved") }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Группируем источники по категориям
            val byCategory = SearchSource.entries.groupBy { it.meta.category }
            byCategory.forEach { (category, sources) ->
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(category.label, style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
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
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.saveAll(); savedSnack = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save all settings") }
                Spacer(Modifier.height(32.dp))
            }
        }
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meta.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(meta.displayName, style = MaterialTheme.typography.bodyLarge)
                        // Quality chip
                        SuggestionChip(
                            onClick = {},
                            label = { Text(meta.quality, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(22.dp)
                        )
                        // Auth badge
                        if (meta.authType != AuthType.NONE) {
                            val (icon, color) = when {
                                connectedStatus == true -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                                meta.authType == AuthType.OPTIONAL_TOKEN -> Icons.Default.LockOpen to MaterialTheme.colorScheme.onSurfaceVariant
                                else -> Icons.Default.Lock to MaterialTheme.colorScheme.secondary
                            }
                            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
                        }
                    }
                    Text(meta.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Expand credentials button
                if (meta.authType != AuthType.NONE) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            // Credentials form
            AnimatedVisibility(visible = expanded) {
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
                                value = creds.password,
                                label = "Password",
                                onValueChange = { onCredsChange(creds.copy(password = it)) }
                            )
                            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                                if (connectedStatus == true) {
                                    Icon(Icons.Default.CheckCircle, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Connected")
                                } else {
                                    Text("Connect")
                                }
                            }
                        }
                        AuthType.TOKEN, AuthType.OPTIONAL_TOKEN -> {
                            val label = when (source) {
                                SearchSource.VK -> "VK Token (Kate Mobile)"
                                SearchSource.YANDEX -> "Yandex OAuth Token"
                                SearchSource.DEEZER -> "ARL Cookie (optional)"
                                SearchSource.SOUNDCLOUD -> "Client ID (auto-extracted if empty)"
                                SearchSource.YOUTUBE -> "YouTube API Key (optional)"
                                SearchSource.JAMENDO -> "Client ID (has built-in demo key)"
                                SearchSource.FMA -> "API Key (has built-in public key)"
                                else -> "Token / API Key"
                            }
                            PasswordTextField(
                                value = creds.token,
                                label = label,
                                onValueChange = { onCredsChange(creds.copy(token = it)) }
                            )
                        }
                        AuthType.NONE -> {}
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
