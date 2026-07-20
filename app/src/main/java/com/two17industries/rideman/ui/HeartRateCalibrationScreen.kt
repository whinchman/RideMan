package com.two17industries.rideman.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.ble.BleConnectionState
import com.two17industries.rideman.core.BaselineCalibration
import com.two17industries.rideman.core.CalibrationResult
import com.two17industries.rideman.core.CalibrationSample
import com.two17industries.rideman.hrm.HrmBleClient
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Five minutes seated, reduced to a baseline heart rate by [BaselineCalibration].
 *
 * Note the honest framing in the copy: this is a *seated baseline*, not a clinical resting
 * heart rate, which is measured on waking and reads meaningfully lower.
 *
 * This screen owns its own strap connection for the length of its composition. It has to: the
 * only other [HrmBleClient] in the app is built by LocationForegroundService, which runs only
 * during a ride, and this screen is reachable only from Settings — that is, only when no ride is
 * active. Without a client of its own, HrmStatus would read DISABLED on arrival, HrmBus would be
 * empty, and START would be permanently disabled over a "Heart rate off" label. A five-minute
 * *seated* calibration is by definition not performed mid-ride, so the feature would never be
 * usable at all.
 *
 * The two clients therefore can never be live at the same time, and
 * LocationForegroundService.clientsRequested is not a concern here: reaching this screen
 * requires passing through Settings, which requires no ride in progress, and starting a ride
 * requires leaving this screen. Recorded so the next reader does not have to re-derive it.
 */
@Composable
fun HeartRateCalibrationScreen(
    hrmAddress: String?,
    onSave: (baselineBpm: Int, atMillis: Long) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val hrmState by HrmStatus.state.collectAsState()
    val liveBpm by HrmBus.latest.collectAsState()

    var running by remember { mutableStateOf(false) }
    // STOP sets this rather than clearing `running`, so there is exactly one place that reduces
    // and `running` stays true until the result exists — otherwise the moment between clearing
    // `running` and the reduction landing would flash the start screen.
    var stopRequested by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var result by remember { mutableStateOf<CalibrationResult?>(null) }
    val samples = remember { mutableListOf<CalibrationSample>() }

    // Deliberately NOT rememberCoroutineScope(): BleCentral documents that it runs on a
    // SupervisorJob scope with no exception handler, and every framework call in it is
    // runCatching-wrapped on that premise. rememberCoroutineScope() hands back a plain Job
    // parented to the Recomposer, so an escaping throw would cancel siblings and propagate into
    // the Recomposer — a frozen or blank UI, not the failure mode the client was hardened for.
    // LocationForegroundService builds its client's scope the same way this does.
    //
    // Keyed on hrmAddress so it is rebuilt in step with the client below: the effect cancels
    // this scope on dispose, and a cancelled scope must never be handed to the next client.
    val scope = remember(hrmAddress) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    // The strap connects on entry and disconnects on exit — including via back, the BackLabel,
    // and SAVE BASELINE, all of which leave this composition.
    DisposableEffect(hrmAddress) {
        val client = HrmBleClient(context.applicationContext, scope)
        client.start(hrmAddress)
        onDispose {
            // stop() is fully synchronous, so the disconnect completes before the scope dies.
            client.stop()
            scope.cancel()
        }
    }

    // The rider watches a countdown for five minutes without touching the screen, so the display
    // would otherwise sleep at the system timeout — often 30 seconds — taking the countdown, the
    // live BPM and the STOP button behind a lock screen. Held only while a session is running;
    // rides hold the same flag independently from MainActivity, and the two never overlap.
    DisposableEffect(running) {
        val window = context.findActivity()?.window
        if (running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Collect every reading while running, and stop once the *data* covers the full duration.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        stopRequested = false
        // elapsedRealtime, not currentTimeMillis: the latter is not monotonic, and an NTP
        // correction mid-session would stall the loop (backward jump) or end it early (forward
        // jump). Sample stamps stay on epochMillis — the reducer compares against those.
        val tappedAt = SystemClock.elapsedRealtime()

        // Span accounting is anchored to the arrival of the *first sample*, not to the START
        // tap. The gap between the two scales with the strap's notification interval, and it
        // used to be charged against the collection window: a 2 s strap left the data roughly
        // 4 s short of the reducer's gate, past the overshoot cap, so a rider who sat perfectly
        // still for five minutes was told they stopped early. Anchoring here removes that gap
        // entirely, leaving only the trailing one, which the cap covers.
        //
        // Null until the first sample lands; until then the wall clock from the tap stands in,
        // so a strap that never reports still ends the session instead of hanging it.
        var collectionAnchor: Long? = null

        // Collected, not polled: a 250 ms poll of HrmBus.latest can miss a reading that is
        // superseded between ticks. The epochMillis dedupe still guards against a conflated
        // StateFlow re-emitting the value that is already the newest sample.
        val collector = launch {
            HrmBus.latest.collect { hr ->
                if (hr != null && samples.lastOrNull()?.epochMillis != hr.epochMillis) {
                    if (collectionAnchor == null) collectionAnchor = SystemClock.elapsedRealtime()
                    samples.add(CalibrationSample(hr.epochMillis, hr.bpm, hr.contactOk))
                }
            }
        }

        // Distinct from elapsedMs: that one drives the countdown and must still start at 5:00
        // the instant the rider taps START. This one sizes the collection window.
        var collectedMs = 0L

        while (
            !stopRequested &&
            CalibrationCollection.shouldKeepCollecting(
                elapsedMs = collectedMs,
                sampleSpanMs = CalibrationCollection.spanMs(samples),
            )
        ) {
            delay(250)
            val now = SystemClock.elapsedRealtime()
            elapsedMs = now - tappedAt
            collectedMs = CalibrationCollection.collectedMs(now, collectionAnchor, tappedAt)
        }
        collector.cancel()

        // reduce() is O(n^2) over ~300 samples; off the main thread so the last frame of the
        // countdown does not stutter.
        val captured = samples.toList()
        val reduced = withContext(Dispatchers.Default) { BaselineCalibration.reduce(captured) }
        result = reduced
        running = false
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
                // Clamped at zero, so the overshoot past the nominal end still reads a clean 0:00.
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
                    onClick = { stopRequested = true },
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

/** Walks the ContextWrapper chain to the hosting Activity, for the keep-screen-on flag. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
