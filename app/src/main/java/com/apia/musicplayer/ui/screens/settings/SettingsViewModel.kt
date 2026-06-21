package com.apia.musicplayer.ui.screens.settings

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apia.musicplayer.data.search.*
import com.apia.musicplayer.domain.model.SearchSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val torapiInstance: String = "",
    val enabledSources: Set<SearchSource> = SearchSource.entries.filter { it.meta.defaultEnabled }.toSet(),
    val credentials: Map<SearchSource, SourceCredentials> = emptyMap(),
    val connectedStatus: Map<SearchSource, Boolean> = emptyMap(),
    val connectingSource: SearchSource? = null,
    val lastError: Map<SearchSource, String> = emptyMap(),
    val saveMessage: String? = null,
    val isLoaded: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val aggregator: SearchAggregator
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    companion object {
        val KEY_TORAPI_INSTANCE = stringPreferencesKey("torapi_instance")
        val KEY_ENABLED = stringSetPreferencesKey("enabled_sources")
        fun credKey(src: SearchSource, field: String) = stringPreferencesKey("cred_${src.name}_$field")
        fun connKey(src: SearchSource) = booleanPreferencesKey("conn_${src.name}")
    }

    init { loadSettings() }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val enabled = prefs[KEY_ENABLED]
                ?.mapNotNull { runCatching { SearchSource.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: SearchSource.entries.filter { it.meta.defaultEnabled }.toSet()

            val creds = SearchSource.entries.associateWith { src ->
                SourceCredentials(
                    login    = prefs[credKey(src, "login")] ?: "",
                    password = prefs[credKey(src, "pass")]  ?: "",
                    token    = prefs[credKey(src, "token")] ?: ""
                )
            }
            val connected = SearchSource.entries.associateWith { prefs[connKey(it)] ?: false }

            _state.update { it.copy(
                enabledSources = enabled,
                credentials = creds,
                connectedStatus = connected,
                isLoaded = true
            )}
            // Применяем кастомный TorAPI URL
            val torapiUrl = prefs[KEY_TORAPI_INSTANCE] ?: ""
            aggregator.torapi.customInstance = torapiUrl
            aggregator.enabledSources.clear()
            aggregator.enabledSources.addAll(enabled)
            applyCredentials(creds)

            // Автологин для источников с кредами
            creds.forEach { (src, c) ->
                if (connected[src] == true) return@forEach  // уже подключён
                when (src) {
                    SearchSource.RUTRACKER -> if (c.login.isNotBlank() && c.password.isNotBlank()) {
                        runCatching { aggregator.rutracker.login(c.login, c.password) }
                            .onSuccess { ok -> if (ok) markConnected(src, true) }
                    }
                    SearchSource.KINOZAL -> if (c.login.isNotBlank() && c.password.isNotBlank()) {
                        runCatching { aggregator.kinozal.login(c.login, c.password) }
                            .onSuccess { ok -> if (ok) markConnected(src, true) }
                    }
                    SearchSource.NNMCLUB -> if (c.login.isNotBlank() && c.password.isNotBlank()) {
                        runCatching { aggregator.nnmclub.login(c.login, c.password) }
                            .onSuccess { ok -> if (ok) markConnected(src, true) }
                    }
                    else -> {}
                }
            }
        }
    }

    fun toggleSource(source: SearchSource, on: Boolean) {
        _state.update { s ->
            val newSrc = if (on) s.enabledSources + source else s.enabledSources - source
            val torapiUrl = prefs[KEY_TORAPI_INSTANCE] ?: ""
            aggregator.torapi.customInstance = torapiUrl
            aggregator.enabledSources.clear()
            aggregator.enabledSources.addAll(newSrc)
            s.copy(enabledSources = newSrc)
        }
    }

    fun updateCreds(source: SearchSource, creds: SourceCredentials) {
        _state.update { it.copy(credentials = it.credentials + (source to creds)) }
    }

    fun connect(source: SearchSource) {
        _state.update { it.copy(connectingSource = source, lastError = it.lastError - source) }
        viewModelScope.launch {
            val creds = _state.value.credentials[source] ?: return@launch
            try {
                val ok = when (source) {
                    SearchSource.RUTRACKER -> aggregator.rutracker.login(creds.login, creds.password)
                    SearchSource.KINOZAL   -> aggregator.kinozal.login(creds.login, creds.password)
                    SearchSource.NNMCLUB   -> aggregator.nnmclub.login(creds.login, creds.password)
                    else -> { applyToken(source, creds.token); true }
                }
                markConnected(source, ok)
                if (!ok) {
                    _state.update { it.copy(lastError = it.lastError + (source to "Wrong login or password")) }
                } else {
                    dataStore.edit { prefs -> prefs[connKey(source)] = true }
                }
                Log.d("Settings", "connect $source: $ok")
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                Log.e("Settings", "connect $source failed: $msg")
                markConnected(source, false)
                _state.update { it.copy(lastError = it.lastError + (source to msg)) }
            } finally {
                _state.update { it.copy(connectingSource = null) }
            }
        }
    }

    fun saveAll() {
        viewModelScope.launch {
            val s = _state.value
            dataStore.edit { prefs ->
                prefs[KEY_ENABLED] = s.enabledSources.map { it.name }.toSet()
                // TorAPI URL сохраняется отдельно через updateTorapiUrl
                prefs[KEY_TORAPI_INSTANCE] = s.torapiInstance
                s.credentials.forEach { (src, creds) ->
                    prefs[credKey(src, "login")] = creds.login
                    prefs[credKey(src, "pass")]  = creds.password
                    prefs[credKey(src, "token")] = creds.token
                }
            }
            aggregator.torapi.customInstance = s.torapiInstance
            applyCredentials(s.credentials)
            _state.update { it.copy(saveMessage = "Saved ${s.enabledSources.size} sources") }
        }
    }

    fun clearSaveMessage() = _state.update { it.copy(saveMessage = null) }

    private fun markConnected(source: SearchSource, ok: Boolean) {
        _state.update { it.copy(connectedStatus = it.connectedStatus + (source to ok)) }
    }

    private fun applyCredentials(creds: Map<SearchSource, SourceCredentials>) {
        creds.forEach { (src, c) -> if (c.token.isNotBlank()) applyToken(src, c.token) }
    }

    private fun applyToken(src: SearchSource, token: String) {
        when (src) {
            SearchSource.VK         -> aggregator.vk.token = token
            SearchSource.YOUTUBE    -> aggregator.youtube.apiKey = token
            SearchSource.SOUNDCLOUD -> if (token.isNotBlank()) aggregator.soundcloud.clientId = token
            SearchSource.DEEZER     -> aggregator.deezer.arlCookie = token
            SearchSource.YANDEX     -> aggregator.yandex.token = token
            SearchSource.JAMENDO    -> if (token.isNotBlank()) aggregator.jamendo.clientId = token
            else -> {}
        }
    }
}
