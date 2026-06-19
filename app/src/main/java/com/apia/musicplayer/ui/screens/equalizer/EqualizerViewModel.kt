package com.apia.musicplayer.ui.screens.equalizer

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EqBandState(val label: String, val gain: Float)

enum class EqualizerPreset(val label: String, val gains: List<Float>) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    BASS("Bass", listOf(6f, 4f, 0f, -2f, -3f)),
    ROCK("Rock", listOf(4f, 2f, -1f, 2f, 4f)),
    POP("Pop", listOf(-1f, 3f, 4f, 3f, -1f)),
    JAZZ("Jazz", listOf(3f, 0f, 2f, 3f, 4f)),
    CLASSICAL("Classic", listOf(5f, 3f, -2f, 3f, 4f))
}

data class EqualizerState(
    val enabled: Boolean = false,
    val bands: List<EqBandState> = listOf(
        EqBandState("60Hz", 0f),
        EqBandState("230Hz", 0f),
        EqBandState("910Hz", 0f),
        EqBandState("3.6kHz", 0f),
        EqBandState("14kHz", 0f)
    ),
    val preset: EqualizerPreset = EqualizerPreset.FLAT,
    val bassBoost: Float = 0f,
    val virtualizer: Float = 0f
)

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val player: ExoPlayer
) : ViewModel() {

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bassBoostFx: BassBoost? = null
    private var virtualizerFx: Virtualizer? = null

    private val audioSessionId: Int
        get() = player.audioSessionId

    fun setEnabled(enabled: Boolean) {
        _state.update { it.copy(enabled = enabled) }
        if (enabled) initEffects() else releaseEffects()
    }

    fun setBandGain(bandIndex: Int, gain: Float) {
        _state.update { state ->
            val bands = state.bands.toMutableList()
            bands[bandIndex] = bands[bandIndex].copy(gain = gain)
            state.copy(bands = bands, preset = EqualizerPreset.FLAT)
        }
        equalizer?.setBandLevel(bandIndex.toShort(), (gain * 100).toInt().toShort())
    }

    fun setPreset(preset: EqualizerPreset) {
        _state.update { state ->
            state.copy(
                preset = preset,
                bands = state.bands.mapIndexed { i, b ->
                    b.copy(gain = preset.gains.getOrElse(i) { 0f })
                }
            )
        }
        preset.gains.forEachIndexed { i, gain ->
            equalizer?.setBandLevel(i.toShort(), (gain * 100).toInt().toShort())
        }
    }

    fun setBassBoost(strength: Float) {
        _state.update { it.copy(bassBoost = strength) }
        bassBoostFx?.setStrength(strength.toInt().toShort())
    }

    fun setVirtualizer(strength: Float) {
        _state.update { it.copy(virtualizer = strength) }
        virtualizerFx?.setStrength(strength.toInt().toShort())
    }

    private fun initEffects() {
        try {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            bassBoostFx = BassBoost(0, audioSessionId).apply { enabled = true }
            virtualizerFx = Virtualizer(0, audioSessionId).apply { enabled = true }
            // Применяем текущие настройки
            _state.value.bands.forEachIndexed { i, band ->
                equalizer?.setBandLevel(i.toShort(), (band.gain * 100).toInt().toShort())
            }
        } catch (e: Exception) { /* device may not support effects */ }
    }

    private fun releaseEffects() {
        equalizer?.release(); equalizer = null
        bassBoostFx?.release(); bassBoostFx = null
        virtualizerFx?.release(); virtualizerFx = null
    }

    override fun onCleared() {
        releaseEffects()
        super.onCleared()
    }
}
