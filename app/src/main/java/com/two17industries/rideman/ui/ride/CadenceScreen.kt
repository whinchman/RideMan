package com.two17industries.rideman.ui.ride

import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.R
import com.two17industries.rideman.core.Cadence
import com.two17industries.rideman.core.CadenceMode
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.components.TerminalButtonStyle
import com.two17industries.rideman.ui.theme.LocalAccent
import com.two17industries.rideman.ui.theme.bigMetric
import kotlinx.coroutines.delay

@Composable
fun CadenceScreen(mode: CadenceMode, initialRpm: Int) {
    val accent = LocalAccent.current
    val context = LocalContext.current

    var rpm by remember { mutableIntStateOf(Cadence.clampRpm(initialRpm)) }
    var playing by remember { mutableStateOf(false) }
    var soundId by remember { mutableIntStateOf(0) }

    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    DisposableEffect(Unit) {
        soundId = soundPool.load(context, R.raw.click, 1)
        onDispose { soundPool.release() }
    }

    LaunchedEffect(playing, rpm, mode, soundId) {
        if (!playing || soundId == 0) return@LaunchedEffect
        val period = Cadence.clickPeriodMs(rpm, mode)
        while (true) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
            delay(period)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("CADENCE", color = accent, style = MaterialTheme.typography.labelLarge)
        Text("$rpm", color = accent, style = bigMetric)
        Text("TARGET RPM", color = accent, style = MaterialTheme.typography.titleLarge)
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CadenceButton("SLOW", accent) { rpm = Cadence.clampRpm(rpm - 1) }
            CadenceButton(if (playing) "PAUSE" else "PLAY", accent) { playing = !playing }
            CadenceButton("FAST", accent) { rpm = Cadence.clampRpm(rpm + 1) }
        }
    }
}

@Composable
private fun CadenceButton(label: String, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    TerminalButton(
        text = label,
        onClick = onClick,
        style = TerminalButtonStyle.PRIMARY,
        accent = accent,
        fontSize = 16.sp,
        modifier = Modifier.height(96.dp),
    )
}
