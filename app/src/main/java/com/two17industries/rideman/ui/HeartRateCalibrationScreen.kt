package com.two17industries.rideman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.ble.BleConnectionState
import com.two17industries.rideman.core.BaselineCalibration
import com.two17industries.rideman.core.CalibrationResult
import com.two17industries.rideman.core.CalibrationSample
import com.two17industries.rideman.hrm.HrmBus
import com.two17industries.rideman.hrm.HrmStatus
import com.two17industries.rideman.ui.components.BackLabel
import com.two17industries.rideman.ui.components.HairLine
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.components.TerminalButtonStyle
import com.two17industries.rideman.ui.ride.hrmStatusLabel
import com.two17industries.rideman.ui.theme.BorderCyanDim
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.TextPrimary
import com.two17industries.rideman.ui.theme.bigMetric
import kotlinx.coroutines.delay

/**
 * Five minutes seated, reduced to a baseline heart rate by [BaselineCalibration].
 *
 * Note the honest framing in the copy: this is a *seated baseline*, not a clinical resting
 * heart rate, which is measured on waking and reads meaningfully lower.
 */
@Composable
fun HeartRateCalibrationScreen(
    onSave: (baselineBpm: Int, atMillis: Long) -> Unit,
    onDone: () -> Unit,
) {
    val hrmState by HrmStatus.state.collectAsState()
    val liveBpm by HrmBus.latest.collectAsState()

    var running by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var result by remember { mutableStateOf<CalibrationResult?>(null) }
    val samples = remember { mutableListOf<CalibrationSample>() }

    // Collect every reading while running, and stop at the full duration.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        while (running && elapsedMs < BaselineCalibration.DURATION_MS) {
            HrmBus.latest.value?.let { hr ->
                if (samples.lastOrNull()?.epochMillis != hr.epochMillis) {
                    samples.add(CalibrationSample(hr.epochMillis, hr.bpm, hr.contactOk))
                }
            }
            delay(250)
            elapsedMs = System.currentTimeMillis() - startedAt
        }
        if (running) {
            running = false
            result = BaselineCalibration.reduce(samples.toList())
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BackLabel(
            "CALIBRATE",
            modifier = Modifier.fillMaxWidth().clickable { onDone() }.padding(bottom = 24.dp),
        )
        HairLine(color = BorderCyanDim)

        val connected = hrmState == BleConnectionState.CONNECTED
        if (!connected && !running && result == null) {
            Text(
                hrmStatusLabel(hrmState),
                color = Muted,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }

        when {
            result != null -> {
                val r = result
                Text(
                    when (r) {
                        is CalibrationResult.Ok -> "${r.baselineBpm} bpm"
                        is CalibrationResult.Failed -> when (r.reason) {
                            CalibrationResult.Failure.TOO_SHORT ->
                                "Stopped early — the full five minutes is needed."
                            CalibrationResult.Failure.POOR_CONTACT ->
                                "Strap contact was poor. Moisten the electrodes and try again."
                            CalibrationResult.Failure.TOO_VARIABLE ->
                                "Heart rate never settled. Sit still and try again."
                            CalibrationResult.Failure.INSUFFICIENT_DATA ->
                                "Not enough readings came through. Reseat the strap and try again."
                        }
                        null -> ""
                    },
                    color = if (r is CalibrationResult.Ok) Cyan else TextPrimary,
                    style = if (r is CalibrationResult.Ok) {
                        MaterialTheme.typography.titleLarge.copy(fontSize = 34.sp)
                    } else {
                        MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp)
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                if (r is CalibrationResult.Ok) {
                    TerminalButton(
                        text = "SAVE BASELINE",
                        onClick = { onSave(r.baselineBpm, System.currentTimeMillis()); onDone() },
                        style = TerminalButtonStyle.PRIMARY,
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TerminalButton(
                    text = "REDO",
                    onClick = { samples.clear(); elapsedMs = 0L; result = null },
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }

            running -> {
                val remaining = ((BaselineCalibration.DURATION_MS - elapsedMs) / 1000).coerceAtLeast(0)
                Text(
                    liveBpm?.bpm?.toString() ?: "--",
                    color = Cyan,
                    style = bigMetric.copy(fontSize = 72.sp),
                    textAlign = TextAlign.Center,
                )
                Text("BPM", color = Cyan, style = MaterialTheme.typography.titleLarge)
                Text(
                    "%d:%02d remaining".format(remaining / 60, remaining % 60),
                    color = Muted,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                Text(
                    "Sit still and breathe normally.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    textAlign = TextAlign.Center,
                )
                TerminalButton(
                    text = "STOP",
                    onClick = { running = false; result = BaselineCalibration.reduce(samples.toList()) },
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
            }

            else -> {
                Text(
                    "Sit down, wear the strap, and stay still for five minutes.\n\n" +
                        "This measures a seated baseline, not a clinical resting heart rate — " +
                        "measure it the same way each time so the trend means something.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                TerminalButton(
                    text = "START",
                    onClick = { samples.clear(); elapsedMs = 0L; running = true },
                    enabled = connected,
                    style = TerminalButtonStyle.PRIMARY,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
