package com.two17industries.rideman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.core.CadenceMode
import com.two17industries.rideman.core.Cadence
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.data.RidemanSettings
import com.two17industries.rideman.data.ThemeChoice
import com.two17industries.rideman.ui.theme.LocalAccent

@Composable
fun SettingsScreen(current: RidemanSettings, onSave: (RidemanSettings) -> Unit, onDone: () -> Unit) {
    val accent = LocalAccent.current
    var units by remember { mutableStateOf(current.units) }
    var cadenceMode by remember { mutableStateOf(current.cadenceMode) }
    var targetRpm by remember { mutableStateOf(current.targetRpm) }
    var theme by remember { mutableStateOf(current.theme) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("SETTINGS", color = accent, style = MaterialTheme.typography.titleLarge)

        Section("UNITS", accent) {
            ChipRow(
                options = UnitSystem.entries,
                selected = units,
                label = { if (it == UnitSystem.AMERICAN) "American" else "Metric" },
                onSelect = { units = it },
                accent = accent,
            )
        }

        Section("CADENCE PULSE", accent) {
            ChipRow(
                options = CadenceMode.entries,
                selected = cadenceMode,
                label = { if (it == CadenceMode.FULL) "1 RPM (same foot)" else "1/2 RPM (alternating)" },
                onSelect = { cadenceMode = it },
                accent = accent,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { targetRpm = Cadence.clampRpm(targetRpm - 5) },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("-5") }
                Text("Target $targetRpm RPM", color = accent, style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { targetRpm = Cadence.clampRpm(targetRpm + 5) },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("+5") }
            }
        }

        Section("COLOR THEME", accent) {
            ChipRow(
                options = ThemeChoice.entries,
                selected = theme,
                label = { it.name.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() } },
                onSelect = { theme = it },
                accent = accent,
            )
        }

        Button(
            onClick = {
                onSave(current.copy(units = units, cadenceMode = cadenceMode, targetRpm = targetRpm, theme = theme))
                onDone()
            },
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("SAVE", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun Section(title: String, accent: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = accent, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    accent: androidx.compose.ui.graphics.Color,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(label(opt)) },
            )
        }
    }
}
