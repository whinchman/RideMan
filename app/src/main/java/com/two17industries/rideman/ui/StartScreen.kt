package com.two17industries.rideman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.BuildConfig
import com.two17industries.rideman.core.PlanGrading
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.ui.components.BlinkingCursor
import com.two17industries.rideman.ui.components.HairLine
import com.two17industries.rideman.ui.components.PromptLabel
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.components.TerminalButtonStyle
import com.two17industries.rideman.ui.components.TerminalPanel
import com.two17industries.rideman.ui.components.glow
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.Dim
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.TextPrimary

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
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        ">",
                        color = Muted,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "RIDEMAN",
                        color = Cyan,
                        style = MaterialTheme.typography.titleLarge
                            .copy(fontSize = 26.sp, letterSpacing = 0.8.sp)
                            .glow(Cyan),
                    )
                    Spacer(Modifier.width(6.dp))
                    BlinkingCursor(modifier = Modifier.padding(bottom = 2.dp))
                }

                Spacer(Modifier.height(18.dp))
                HairLine()
                Spacer(Modifier.height(18.dp))

                TerminalPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        PromptLabel("NEXT UP")
                        Spacer(Modifier.height(9.dp))
                        when {
                            !planAvailable -> Text(
                                "PLAN UNAVAILABLE",
                                color = Muted,
                                style = MaterialTheme.typography.titleLarge
                                    .copy(fontSize = 17.sp, letterSpacing = 0.7.sp),
                            )
                            nextUp != null -> {
                                Text(
                                    "WK ${nextUp.week} · RIDE ${nextUp.slot}",
                                    color = Cyan,
                                    style = MaterialTheme.typography.titleLarge
                                        .copy(fontSize = 17.sp, letterSpacing = 0.7.sp)
                                        .glow(Cyan, blurRadius = 5f),
                                )
                                Spacer(Modifier.height(5.dp))
                                Row {
                                    Text(
                                        "${formatPlanDistance(nextUp.targetMiles, units)} - ${nextUp.kind}",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                                    )
                                    Text(
                                        " · ${nextUp.pace.name.lowercase()}",
                                        color = Muted,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                                    )
                                }
                            }
                            else -> Text(
                                "PLAN COMPLETE",
                                color = Cyan,
                                style = MaterialTheme.typography.titleLarge
                                    .copy(fontSize = 17.sp, letterSpacing = 0.7.sp)
                                    .glow(Cyan, blurRadius = 5f),
                            )
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(top = 40.dp)) {
                TerminalButton(
                    text = "> PLAN RIDE",
                    onClick = onPlanRide,
                    enabled = planAvailable,
                    style = TerminalButtonStyle.PRIMARY,
                    trailing = "→",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                TerminalButton(
                    text = "> FREE RIDE",
                    onClick = onFreeRide,
                    style = TerminalButtonStyle.SECONDARY,
                    trailing = "→",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TerminalButton(
                        text = "HISTORY",
                        onClick = onHistory,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TerminalButton(
                        text = "SETTINGS",
                        onClick = onSettings,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_COMMIT}",
                    color = Dim,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 11.sp),
                )
            }
        }
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
