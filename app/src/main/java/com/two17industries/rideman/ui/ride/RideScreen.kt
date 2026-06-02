package com.two17industries.rideman.ui.ride

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.core.PagerWrap
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.data.RideScreen
import com.two17industries.rideman.data.RidemanSettings
import com.two17industries.rideman.ui.RideUiState
import com.two17industries.rideman.ui.theme.LocalAccent
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RideScreen(state: RideUiState, settings: RidemanSettings, onEndRide: () -> Unit) {
    val accent = LocalAccent.current
    val context = LocalContext.current
    val screens = settings.screenOrder.ifEmpty { RideScreen.entries.toList() }
    val count = screens.size

    val tts = remember { TextToSpeech(context, null) }
    DisposableEffect(Unit) { onDispose { tts.stop(); tts.shutdown() } }

    val pagerState = rememberPagerState(
        initialPage = PagerWrap.startPage(count),
        pageCount = { PagerWrap.VIRTUAL_PAGES },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(state, settings.units) {
                detectTapGestures(onDoubleTap = { speak(tts, state, settings.units) })
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                when (screens[PagerWrap.screenIndex(page, count)]) {
                    RideScreen.SPEED -> SpeedometerScreen(state.speedMps, settings.units)
                    RideScreen.ODOMETER -> OdometerScreen(state.distanceM, settings.units)
                    RideScreen.COMPASS -> CompassScreen(state.headingDeg)
                    RideScreen.ALTITUDE -> AltimeterScreen(state.altitudeM, settings.units)
                    RideScreen.CADENCE -> CadenceScreen(settings.cadenceMode, settings.targetRpm)
                }
            }
            BottomBar(
                count = count,
                currentIndex = PagerWrap.screenIndex(pagerState.currentPage, count),
                onEndRide = onEndRide,
                accent = accent,
            )
        }
    }
}

@Composable
private fun BottomBar(
    count: Int,
    currentIndex: Int,
    onEndRide: () -> Unit,
    accent: androidx.compose.ui.graphics.Color,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .padding(horizontal = 5.dp)
                        .size(if (i == currentIndex) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i == currentIndex) accent else accent.copy(alpha = 0.3f))
                )
            }
        }
        Button(
            onClick = onEndRide,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("END RIDE", style = MaterialTheme.typography.titleLarge) }
    }
}

private fun speak(tts: TextToSpeech, state: RideUiState, units: UnitSystem) {
    val speed = Units.speed(state.speedMps, units).roundToInt()
    val dist = String.format(Locale.US, "%.2f", Units.distance(state.distanceM, units))
    val heading = state.headingDeg.roundToInt() % 360
    val alt = Units.altitude(state.altitudeM, units).roundToInt()
    val text = "Speed $speed ${Units.speedLabel(units)}. " +
        "Distance $dist ${Units.distanceLabel(units)}. " +
        "Heading $heading degrees. " +
        "Altitude $alt ${Units.altitudeLabel(units)}."
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ride-readout")
}
