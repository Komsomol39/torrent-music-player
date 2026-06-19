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
    val enabledSources: Set<SearchSource> = SearchSource.DEFAULT_ENABLED,
    val credentials: Map<String, String> = emptyMap(),
    val loginStatus: Map<String, Boolean> = emptyMap(),
    val isSaving: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val aggregator: SearchAggregator,
    private val rutracker: RuTrackerProvider,
    private val kinozal: KinozalProvider,
    private val nnmclub: NnmClubProvider,
    private val vk: VkMusicProvider,
    private val youtube: YouTubeProvider,
    private val soundcloud: SoundCloudProvider,
    private val deezer: DeezerProvider,
    private val yandex: YandexMusicProvider,
    private val jamendo: JamendoProvider
) : ViewModel() {

    companion object {
        val KEY_SOURCES = stringSetPreferencesKey("enabled_sources")
    }

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.first().let { prefs ->
                // Загружаем включённые источники
                val sources = prefs[KEY_SOURCES]
                    ?.mapNotNull { runCatching { SearchSource.valueOf(it) }.getOrNull() }
                    ?.toSet() ?: SearchSource.DEFAULT_ENABLED

                // Загружаем все credentials
                val creds = mutableMapOf<String, String>()
                SearchSource.ALL_SOURCES.forEach { info ->
                    val s = info.source
                    listOf("${s.name}_login", "${s.name}_pass", "${s.name}_token").forEach { key ->
                        prefs[stringPreferencesKey(key)]?.let { creds[key] = it }
                    }
                }

                _state.update { it.copy(enabledSources = sources, credentials = creds) }
                aggregator.enabledSources.clear()
                aggregator.enabledSources.addAll(sources)
                applyCredentials(creds)
            }
        }
    }

    fun toggleSource(source: SearchSource, enabled: Boolean) {
        _state.update { s ->
            val new = if (enabled) s.enabledSources + source else s.enabledSources - source
            aggregator.enabledSources.clear()
            aggregator.enabledSources.addAll(new)
            s.copy(enabledSources = new)
        }
    }

    fun setCredential(key: String, value: String) {
        _state.update { it.copy(credentials = it.credentials + (key to value)) }
    }

    fun login(source: SearchSource) {
        viewModelScope.launch {
            val creds = state.value.credentials
            val ok = when (source) {
                SearchSource.RUTRACKER -> rutracker.login(
                    creds["${source.name}_login"] ?: "",
                    creds["${source.name}_pass"] ?: ""
                )
                SearchSource.KINOZAL -> kinozal.login(
                    creds["${source.name}_login"] ?: "",
                    creds["${source.name}_pass"] ?: ""
                )
                SearchSource.NNMCLUB -> nnmclub.login(
                    creds["${source.name}_login"] ?: "",
                    creds["${source.name}_pass"] ?: ""
                )
                else -> false
            }
            _state.update { it.copy(loginStatus = it.loginStatus + (source.name to ok)) }
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val s = state.value
            dataStore.edit { prefs ->
                prefs[KEY_SOURCES] = s.enabledSources.map { it.name }.toSet()
                s.credentials.forEach { (key, value) ->
                    prefs[stringPreferencesKey(key)] = value
                }
            }
            applyCredentials(s.credentials)
            _state.update { it.copy(isSaving = false) }
        }
    }

    private fun applyCredentials(creds: Map<String, String>) {
        vk.token         = creds["${SearchSource.VK.name}_token"] ?: ""
        youtube.apiKey   = creds["${SearchSource.YOUTUBE.name}_token"] ?: ""
        soundcloud.clientId = creds["${SearchSource.SOUNDCLOUD.name}_token"] ?: ""
        deezer.arlToken  = creds["${SearchSource.DEEZER.name}_token"] ?: ""
        yandex.token     = creds["${SearchSource.YANDEX.name}_token"] ?: ""
        jamendo.apiKey   = creds["${SearchSource.JAMENDO.name}_token"] ?: ""
    }
}
