package com.apia.musicplayer.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.apia.musicplayer.domain.model.SearchSource
import com.apia.musicplayer.domain.model.SourceCategory
import com.apia.musicplayer.domain.model.SourceInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Sources") },
                actions = {
                    TextButton(onClick = { viewModel.saveSettings() }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Группируем по категориям
            val byCategory = SearchSource.ALL_SOURCES.groupBy { it.category }
            SourceCategory.entries.forEach { category ->
                val sources = byCategory[category] ?: return@forEach
                item {
                    CategorySection(
                        category = category,
                        sources = sources,
                        enabledSources = state.enabledSources,
                        credentials = state.credentials,
                        onToggle = { source, on -> viewModel.toggleSource(source, on) },
                        onCredentialChange = { key, value -> viewModel.setCredential(key, value) },
                        onLogin = { source -> viewModel.login(source) },
                        loginStatus = state.loginStatus
                    )
                }
            }

            // Кнопка сохранить
            item {
                Button(
                    onClick = { viewModel.saveSettings() },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save All Settings", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun CategorySection(
    category: SourceCategory,
    sources: List<SourceInfo>,
    enabledSources: Set<SearchSource>,
    credentials: Map<String, String>,
    onToggle: (SearchSource, Boolean) -> Unit,
    onCredentialChange: (String, String) -> Unit,
    onLogin: (SearchSource) -> Unit,
    loginStatus: Map<String, Boolean>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Заголовок категории
        Text(
            category.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        sources.forEach { info ->
            SourceCard(
                info = info,
                enabled = enabledSources.contains(info.source),
                credentials = credentials,
                onToggle = { onToggle(info.source, it) },
                onCredentialChange = onCredentialChange,
                onLogin = { onLogin(info.source) },
                isLoggedIn = loginStatus[info.source.name] == true
            )
        }
    }
}

@Composable
fun SourceCard(
    info: SourceInfo,
    enabled: Boolean,
    credentials: Map<String, String>,
    onToggle: (Boolean) -> Unit,
    onCredentialChange: (String, String) -> Unit,
    onLogin: () -> Unit,
    isLoggedIn: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val needsCredentials = info.requiresLogin || info.requiresToken

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .clickable { if (needsCredentials) expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Заголовок карточки
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${info.source.emoji}  ${info.source.displayName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // Индикатор логина
                if (needsCredentials) {
                    Icon(
                        if (isLoggedIn) Icons.Default.CheckCircle else Icons.Default.Lock,
                        null,
                        tint = if (isLoggedIn) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Описание
            Text(
                info.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Качество + требования
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (info.quality.isNotBlank()) {
                    QualityChip(info.quality)
                }
                if (info.requiresLogin) StatusChip("Login required", MaterialTheme.colorScheme.secondary)
                if (info.requiresToken) StatusChip("Token required", MaterialTheme.colorScheme.tertiary)
            }

            // Форма с логином/токеном (разворачивается)
            if (expanded && needsCredentials) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                CredentialsForm(
                    source = info.source,
                    requiresLogin = info.requiresLogin,
                    credentials = credentials,
                    onCredentialChange = onCredentialChange,
                    onLogin = onLogin,
                    isLoggedIn = isLoggedIn
                )
            }

            // Стрелка если есть что развернуть
            if (needsCredentials) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CredentialsForm(
    source: SearchSource,
    requiresLogin: Boolean,
    credentials: Map<String, String>,
    onCredentialChange: (String, String) -> Unit,
    onLogin: () -> Unit,
    isLoggedIn: Boolean
) {
    val loginKey = "${source.name}_login"
    val passKey  = "${source.name}_pass"
    val tokenKey = "${source.name}_token"

    if (requiresLogin) {
        OutlinedTextField(
            value = credentials[loginKey] ?: "",
            onValueChange = { onCredentialChange(loginKey, it) },
            label = { Text("Login") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        PasswordField(
            value = credentials[passKey] ?: "",
            label = "Password",
            onValueChange = { onCredentialChange(passKey, it) }
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isLoggedIn) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.secondary
            )
        ) {
            Icon(
                if (isLoggedIn) Icons.Default.CheckCircle else Icons.Default.Login,
                null
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isLoggedIn) "Connected ✓" else "Connect")
        }
    } else {
        // Только токен
        val hint = when (source) {
            SearchSource.VK -> "Kate Mobile token (vk.com → settings → devices)"
            SearchSource.DEEZER -> "ARL cookie from deezer.com"
            SearchSource.YANDEX -> "Token from yandex.ru (see docs)"
            SearchSource.SOUNDCLOUD -> "Client ID from soundcloud.com/developers"
            SearchSource.JAMENDO -> "API key from developer.jamendo.com"
            SearchSource.YOUTUBE -> "API key from console.cloud.google.com (optional)"
            else -> "Token"
        }
        Text(hint, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        PasswordField(
            value = credentials[tokenKey] ?: "",
            label = "Token / API Key",
            onValueChange = { onCredentialChange(tokenKey, it) }
        )
    }
}

@Composable
fun QualityChip(quality: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            "🎵 $quality",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun StatusChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
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
