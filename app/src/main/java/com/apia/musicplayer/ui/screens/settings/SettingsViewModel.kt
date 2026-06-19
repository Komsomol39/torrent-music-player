package com.apia.musicplayer.ui.screens.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
    val enabledSources: Set<SearchSource> = SearchSource.entries.filter { it.meta.defaultEnabled }.toSet(),
    val credentials: Map<SearchSource, SourceCredentials> = emptyMap(),
    val connectedStatus: Map<SearchSource, Boolean> = emptyMap()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val aggregator: SearchAggregator
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    companion object {
        val KEY_ENABLED = stringSetPreferencesKey("enabled_sources")
        fun credKey(src: SearchSource, field: String) = stringPreferencesKey("cred_${src.name}_$field")
    }

    init { loadSettings() }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            val enabled = prefs[KEY_ENABLED]
                ?.mapNotNull { runCatching { SearchSource.valueOf(it) }.getOrNull() }
                ?.toSet() ?: _state.value.enabledSources
            val creds = SearchSource.entries.associateWith { src ->
                SourceCredentials(
                    login    = prefs[credKey(src, "login")] ?: "",
                    password = prefs[credKey(src, "pass")] ?: "",
                    token    = prefs[credKey(src, "token")] ?: ""
                )
            }
            _state.update { it.copy(enabledSources = enabled, credentials = creds) }
            aggregator.enabledSources.clear()
            aggregator.enabledSources.addAll(enabled)
            applyCredentials(creds)
        }
    }

    fun toggleSource(source: SearchSource, on: Boolean) {
        _state.update { s ->
            val newSrc = if (on) s.enabledSources + source else s.enabledSources - source
            aggregator.enabledSources.clear(); aggregator.enabledSources.addAll(newSrc)
            s.copy(enabledSources = newSrc)
        }
    }

    fun updateCreds(source: SearchSource, creds: SourceCredentials) {
        _state.update { it.copy(credentials = it.credentials + (source to creds)) }
    }

    fun connect(source: SearchSource) {
        viewModelScope.launch {
            val creds = _state.value.credentials[source] ?: return@launch
            val ok = when (source) {
                SearchSource.RUTRACKER -> aggregator.rutracker.login(creds.login, creds.password)
                SearchSource.KINOZAL   -> aggregator.kinozal.login(creds.login, creds.password)
                SearchSource.NNMCLUB   -> aggregator.nnmclub.login(creds.login, creds.password)
                else -> { applyToken(source, creds.token); true }
            }
            _state.update { it.copy(connectedStatus = it.connectedStatus + (source to ok)) }
        }
    }

    fun saveAll() {
        viewModelScope.launch {
            val s = _state.value
            dataStore.edit { prefs ->
                prefs[KEY_ENABLED] = s.enabledSources.map { it.name }.toSet()
                s.credentials.forEach { (src, creds) ->
                    prefs[credKey(src, "login")] = creds.login
                    prefs[credKey(src, "pass")]  = creds.password
                    prefs[credKey(src, "token")] = creds.token
                }
            }
            applyCredentials(s.credentials)
        }
    }

    private fun applyCredentials(creds: Map<SearchSource, SourceCredentials>) {
        creds.forEach { (src, c) -> applyToken(src, c.token) }
    }

    private fun applyToken(src: SearchSource, token: String) {
        when (src) {
            SearchSource.VK         -> aggregator.vk.token = token
            SearchSource.YOUTUBE    -> aggregator.youtube.apiKey = token
            SearchSource.SOUNDCLOUD -> aggregator.soundcloud.clientId = token
            SearchSource.DEEZER     -> aggregator.deezer.arlCookie = token
            SearchSource.YANDEX     -> aggregator.yandex.token = token
            SearchSource.JAMENDO    -> { if (token.isNotBlank()) aggregator.jamendo.clientId = token }
            SearchSource.FMA        -> {} // встроенный ключ
            else -> {}
        }
    }
}
