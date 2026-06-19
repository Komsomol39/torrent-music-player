package com.apia.musicplayer.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.apia.musicplayer.data.search.RuTrackerProvider
import com.apia.musicplayer.data.search.SearchAggregator
import com.apia.musicplayer.data.search.VkMusicProvider
import com.apia.musicplayer.data.search.YouTubeProvider
import com.apia.musicplayer.domain.model.SearchSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val enabledSources: Set<SearchSource> = setOf(SearchSource.RUTOR, SearchSource.TPB, SearchSource.NYAA),
    val rutrackerLogin: String = "",
    val rutrackerPassword: String = "",
    val rutrackerLoggedIn: Boolean = false,
    val vkToken: String = "",
    val youtubeApiKey: String = "",
    val isSaving: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val aggregator: SearchAggregator,
    private val rutracker: RuTrackerProvider,
    private val vk: VkMusicProvider,
    private val youtube: YouTubeProvider
) : ViewModel() {

    companion object {
        val KEY_ENABLED_SOURCES = stringSetPreferencesKey("enabled_sources")
        val KEY_RUTRACKER_LOGIN = stringPreferencesKey("rutracker_login")
        val KEY_RUTRACKER_PASS  = stringPreferencesKey("rutracker_pass")
        val KEY_VK_TOKEN        = stringPreferencesKey("vk_token")
        val KEY_YT_KEY          = stringPreferencesKey("yt_api_key")
    }

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.data.first().let { prefs ->
                val sources = prefs[KEY_ENABLED_SOURCES]
                    ?.mapNotNull { runCatching { SearchSource.valueOf(it) }.getOrNull() }
                    ?.toSet() ?: setOf(SearchSource.RUTOR, SearchSource.TPB, SearchSource.NYAA)
                _state.update { it.copy(
                    enabledSources = sources,
                    rutrackerLogin = prefs[KEY_RUTRACKER_LOGIN] ?: "",
                    vkToken = prefs[KEY_VK_TOKEN] ?: "",
                    youtubeApiKey = prefs[KEY_YT_KEY] ?: ""
                )}
                // Применяем сохранённые токены
                vk.token = prefs[KEY_VK_TOKEN] ?: ""
                youtube.apiKey = prefs[KEY_YT_KEY] ?: ""
                aggregator.enabledSources.clear()
                aggregator.enabledSources.addAll(sources)
            }
        }
    }

    fun toggleSource(source: SearchSource, enabled: Boolean) {
        _state.update { s ->
            val newSources = if (enabled) s.enabledSources + source else s.enabledSources - source
            aggregator.enabledSources.clear()
            aggregator.enabledSources.addAll(newSources)
            s.copy(enabledSources = newSources)
        }
    }

    fun setRutrackerLogin(v: String) = _state.update { it.copy(rutrackerLogin = v) }
    fun setRutrackerPassword(v: String) = _state.update { it.copy(rutrackerPassword = v) }
    fun setVkToken(v: String) = _state.update { it.copy(vkToken = v) }
    fun setYoutubeApiKey(v: String) = _state.update { it.copy(youtubeApiKey = v) }

    fun loginRutracker() {
        viewModelScope.launch {
            val ok = rutracker.login(state.value.rutrackerLogin, state.value.rutrackerPassword)
            _state.update { it.copy(rutrackerLoggedIn = ok) }
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val s = state.value
            dataStore.edit { prefs ->
                prefs[KEY_ENABLED_SOURCES] = s.enabledSources.map { it.name }.toSet()
                prefs[KEY_RUTRACKER_LOGIN] = s.rutrackerLogin
                prefs[KEY_RUTRACKER_PASS]  = s.rutrackerPassword
                prefs[KEY_VK_TOKEN]        = s.vkToken
                prefs[KEY_YT_KEY]          = s.youtubeApiKey
            }
            vk.token = s.vkToken
            youtube.apiKey = s.youtubeApiKey
        }
    }
}
