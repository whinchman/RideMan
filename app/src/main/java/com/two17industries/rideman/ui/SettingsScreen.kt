package com.two17industries.rideman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.Cadence
import com.two17industries.rideman.core.CadenceMode
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.data.RidemanSettings
import com.two17industries.rideman.data.ThemeChoice
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.accentFor

@Composable
fun SettingsScreen(current: RidemanSettings, onSave: (RidemanSettings) -> Unit, onDone: () -> Unit) {
    var units by remember { mutableStateOf(current.units) }
    var cadenceMode by remember { mutableStateOf(current.cadenceMode) }
    var targetRpm by remember { mutableIntStateOf(current.targetRpm) }
    var theme by remember { mutableStateOf(current.theme) }

    // Preview the picked theme live so the whole screen recolors as you choose.
    val accent = accentFor(theme)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Text(
            "SETTINGS",
            color = accent,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )

        Section("UNITS", accent) {
            PillRow {
                UnitSystem.entries.forEach { opt ->
                    OptionPill(
                        text = if (opt == UnitSystem.AMERICAN) "American" else "Metric",
                        selected = opt == units,
                        accent = accent,
                    ) { units = opt }
                }
            }
        }

        Section("CADENCE PULSE", accent) {
            PillRow {
                CadenceMode.entries.forEach { opt ->
                    OptionPill(
                        text = if (opt == CadenceMode.FULL) "Same foot" else "Alternating",
                        selected = opt == cadenceMode,
                        accent = accent,
                    ) { cadenceMode = opt }
                }
            }
            Stepper(
                value = targetRpm,
                accent = accent,
                onDec = { targetRpm = Cadence.clampRpm(targetRpm - 5) },
                onInc = { targetRpm = Cadence.clampRpm(targetRpm + 5) },
            )
        }

        Section("COLOR THEME", accent) {
            PillRow {
                ThemeChoice.entries.forEach { opt ->
                    ColorPill(
                        label = themeName(opt),
                        color = accentFor(opt),
                        selected = opt == theme,
                    ) { theme = opt }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                onSave(current.copy(units = units, cadenceMode = cadenceMode, targetRpm = targetRpm, theme = theme))
                onDone()
            },
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Background),
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) { Text("SAVE", fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp) }
    }
}

@Composable
private fun Section(title: String, accent: Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PillRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun OptionPill(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent else Color.Transparent)
            .border(2.dp, accent, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Background else accent,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ColorPill(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Stepper(value: Int, accent: Color, onDec: () -> Unit, onInc: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepButton("−", accent, onDec) // minus sign
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$value", color = accent, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text("TARGET RPM", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        StepButton("+", accent, onInc)
    }
}

@Composable
private fun StepButton(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Background, fontSize = 32.sp, fontWeight = FontWeight.Black)
    }
}

private fun themeName(choice: ThemeChoice): String = when (choice) {
    ThemeChoice.AMBER -> "Amber"
    ThemeChoice.ACID_GREEN -> "Acid Green"
    ThemeChoice.ELECTRIC_CYAN -> "Electric Cyan"
    ThemeChoice.HOT_MAGENTA -> "Hot Magenta"
}
