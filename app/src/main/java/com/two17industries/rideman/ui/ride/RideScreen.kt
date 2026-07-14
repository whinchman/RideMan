package com.two17industries.rideman.ui.ride

import android.content.res.Configuration
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.PagerWrap
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.data.RideScreen
import com.two17industries.rideman.data.RidemanSettings
import com.two17industries.rideman.ui.RideUiState
import com.two17industries.rideman.ui.theme.LocalAccent
import com.two17industries.rideman.ui.theme.Muted
import java.util.Locale
import kotlin.math.roundToInt

private val Sharp = RoundedCornerShape(0.dp)

@Composable
fun RideScreen(
    state: RideUiState,
    settings: RidemanSettings,
    onEndRide: () -> Unit,
    onToggleOrientation: () -> Unit,
) {
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

    val currentState = rememberUpdatedState(state)
    val currentUnits = rememberUpdatedState(settings.units)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { speak(tts, currentState.value, currentUnits.value) })
            }
    ) {
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val currentIndex = PagerWrap.screenIndex(pagerState.currentPage, count)

        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                RidePager(
                    pagerState, screens, count, state, settings, landscape = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                SideRail(
                    count = count,
                    currentIndex = currentIndex,
                    onEndRide = onEndRide,
                    onToggleOrientation = onToggleOrientation,
                    accent = accent,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                RideHeader(onToggleOrientation = onToggleOrientation, accent = accent)
                RidePager(
                    pagerState, screens, count, state, settings, landscape = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                BottomBar(
                    count = count,
                    currentIndex = currentIndex,
                    onEndRide = onEndRide,
                    accent = accent,
                )
            }
        }
    }
}

@Composable
private fun RidePager(
    pagerState: PagerState,
    screens: List<RideScreen>,
    count: Int,
    state: RideUiState,
    settings: RidemanSettings,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(state = pagerState, modifier = modifier) { page ->
        when (screens[PagerWrap.screenIndex(page, count)]) {
            RideScreen.GRID -> DashGridScreen(state, settings.units, landscape)
            RideScreen.SPEED -> SpeedometerScreen(state.speedMps, settings.units)
            RideScreen.ODOMETER -> OdometerScreen(state.distanceM, settings.units)
            RideScreen.COMPASS -> CompassScreen(state.headingDeg)
            RideScreen.ALTITUDE -> AltimeterScreen(state.altitudeM, settings.units)
            RideScreen.CADENCE -> CadenceScreen(settings.cadenceMode, settings.targetRpm)
        }
    }
}

/** Portrait only: `> RIDING` on the left, rotate button on the right. Landscape has no header. */
@Composable
private fun RideHeader(onToggleOrientation: () -> Unit, accent: Color) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "> RIDING",
                color = Muted,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 1.5.sp),
            )
            RotateButton(
                onClick = onToggleOrientation,
                accent = accent,
                modifier = Modifier.size(30.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(accent.copy(alpha = 0.10f)))
    }
}

/**
 * Sharp bordered square with the rotate glyph. The only control for ride orientation, so it
 * must always offer at least a 48dp touch target even when the visible border is smaller
 * (portrait's 30dp square). `minimumInteractiveComponentSize()` expands the touch target
 * without inflating the visible border; `contentPadding` is applied inside the border/clickable
 * (matching `EndButton`'s ordering below) so it grows the visible box rather than becoming
 * outer margin.
 */
@Composable
private fun RotateButton(
    onClick: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .then(modifier)
            .border(1.dp, accent.copy(alpha = 0.35f), Sharp)
            .clickable(onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text("⟳", color = accent, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PaginatorDots(count: Int, currentIndex: Int, accent: Color, vertical: Boolean) {
    if (vertical) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            repeat(count) { i -> Dot(active = i == currentIndex, accent = accent) }
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(count) { i -> Dot(active = i == currentIndex, accent = accent) }
        }
    }
}

/** Squares, not circles — 217 has no round corners anywhere. */
@Composable
private fun Dot(active: Boolean, accent: Color) {
    Box(
        Modifier
            .size(if (active) 9.dp else 7.dp)
            .background(if (active) accent else accent.copy(alpha = 0.3f), Sharp)
    )
}

/** Landscape: rotate (top), dots (centered, absorbing slack), END (bottom). */
@Composable
private fun SideRail(
    count: Int,
    currentIndex: Int,
    onEndRide: () -> Unit,
    onToggleOrientation: () -> Unit,
    accent: Color,
) {
    Column(
        Modifier
            .fillMaxHeight()
            .width(96.dp)
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RotateButton(
            onClick = onToggleOrientation,
            accent = accent,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 9.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            PaginatorDots(count = count, currentIndex = currentIndex, accent = accent, vertical = true)
        }
        EndButton(label = "END", onClick = onEndRide, accent = accent, fontSize = 15)
    }
}

@Composable
private fun BottomBar(count: Int, currentIndex: Int, onEndRide: () -> Unit, accent: Color) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PaginatorDots(count = count, currentIndex = currentIndex, accent = accent, vertical = false)
        EndButton(label = "END RIDE", onClick = onEndRide, accent = accent, fontSize = 16)
    }
}

/** The 217 primary button: 1px border, faint fill, sharp corners. Same treatment as START RIDE. */
@Composable
private fun EndButton(label: String, onClick: () -> Unit, accent: Color, fontSize: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.12f), Sharp)
            .border(1.dp, accent, Sharp)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = accent,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = fontSize.sp,
                letterSpacing = 1.6.sp,
            ),
        )
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
