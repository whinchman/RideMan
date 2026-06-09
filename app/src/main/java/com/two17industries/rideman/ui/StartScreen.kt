package com.two17industries.rideman.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.BuildConfig
import com.two17industries.rideman.core.PlanGrading
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.ui.theme.LocalAccent

@Composable
fun StartScreen(
    nextUp: PlanRide?,
    planAvailable: Boolean,
    units: UnitSystem,
    onPlanRide: () -> Unit,
    onFreeRide: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "BIKEMAN",
            color = accent,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onPlanRide,
            enabled = planAvailable,
            colors = ButtonDefaults.buttonColors(
                containerColor = accent,
                disabledContainerColor = accent.copy(alpha = 0.20f),
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PLAN RIDE", style = MaterialTheme.typography.titleLarge)
                val subtitle = when {
                    !planAvailable -> "plan unavailable"
                    nextUp != null -> "Next: Wk ${nextUp.week} · Ride ${nextUp.slot} — " +
                        "${formatPlanDistance(nextUp.targetMiles, units)} ${nextUp.pace.name.lowercase()}"
                    else -> "Plan complete 🎉"
                }
                Text(subtitle, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onFreeRide, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("FREE RIDE", color = accent, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f).height(56.dp)) {
                Text("HISTORY", color = accent, style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f).height(56.dp)) {
                Text("SETTINGS", color = accent, style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(48.dp))
        Text(
            "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_COMMIT}",
            color = accent.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * Format a plan target (stored in miles) into the user's unit system, e.g. "7 MI" or "11.3 KM".
 * Whole numbers drop the decimal; otherwise one decimal place.
 */
internal fun formatPlanDistance(targetMiles: Double, units: UnitSystem): String {
    val value = Units.distance(targetMiles * PlanGrading.METERS_PER_MILE, units)
    val num = if (value % 1.0 == 0.0) value.toInt().toString()
        else String.format(java.util.Locale.US, "%.1f", value)
    return "$num ${Units.distanceLabel(units)}"
}
