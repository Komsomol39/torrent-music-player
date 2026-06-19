package com.apia.musicplayer.ui.screens.equalizer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(viewModel: EqualizerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Equalizer") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            // Enable toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Equalizer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(checked = state.enabled, onCheckedChange = { viewModel.setEnabled(it) })
            }

            Spacer(Modifier.height(16.dp))

            // Presets
            Text("Preset", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                EqualizerPreset.entries.forEachIndexed { i, preset ->
                    SegmentedButton(
                        selected = state.preset == preset,
                        onClick = { viewModel.setPreset(preset) },
                        shape = SegmentedButtonDefaults.itemShape(i, EqualizerPreset.entries.size),
                        label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // EQ bands
            Text("Bands", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))

            state.bands.forEachIndexed { index, band ->
                EqBand(
                    band = band,
                    enabled = state.enabled,
                    onGainChange = { viewModel.setBandGain(index, it) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // Bass boost
            Text("Bass Boost", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = state.bassBoost,
                    onValueChange = { viewModel.setBassBoost(it) },
                    valueRange = 0f..1000f,
                    enabled = state.enabled,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("${state.bassBoost.toInt()}", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(36.dp))
            }

            // Virtualizer
            Text("Virtualizer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = state.virtualizer,
                    onValueChange = { viewModel.setVirtualizer(it) },
                    valueRange = 0f..1000f,
                    enabled = state.enabled,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text("${state.virtualizer.toInt()}", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(36.dp))
            }
        }
    }
}

@Composable
fun EqBand(band: EqBandState, enabled: Boolean, onGainChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(band.label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (band.gain >= 0) "+${band.gain.toInt()}dB" else "${band.gain.toInt()}dB",
                style = MaterialTheme.typography.bodySmall,
                color = if (band.gain != 0f) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = band.gain,
            onValueChange = onGainChange,
            valueRange = -15f..15f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
