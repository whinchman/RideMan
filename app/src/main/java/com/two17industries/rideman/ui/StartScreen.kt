package com.two17industries.rideman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.ui.theme.LocalAccent

@Composable
fun StartScreen(
    nextUp: PlanRide?,
    planAvailable: Boolean,
    onPlanRide: () -> Unit,
    onFreeRide: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth().height(96.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PLAN RIDE", style = MaterialTheme.typography.titleLarge)
                val subtitle = when {
                    !planAvailable -> "plan unavailable"
                    nextUp != null -> "Next: Wk ${nextUp.week} · Ride ${nextUp.slot} — " +
                        "${formatMiles(nextUp.targetMiles)}mi ${nextUp.pace.name.lowercase()}"
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

/** Trim trailing ".0" so 7.0 -> "7" but 3.5 -> "3.5". */
internal fun formatMiles(mi: Double): String =
    if (mi % 1.0 == 0.0) mi.toInt().toString() else mi.toString()
