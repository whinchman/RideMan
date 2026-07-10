package com.two17industries.rideman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.data.StravaUploadState
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.LocalAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackfillScreen(
    rides: List<RideEntity>,
    units: UnitSystem,
    onUpload: (List<Long>) -> Unit,
    onDone: () -> Unit,
) {
    val accent = LocalAccent.current
    // Only rides not already uploaded are eligible.
    val eligible = remember(rides) { rides.filter { it.stravaState != StravaUploadState.UPLOADED } }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val fmt = remember { SimpleDateFormat("MMM d · h:mm a", Locale.US) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text(
            "◀ UPLOAD PAST RIDES",
            color = accent,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clickable(onClick = onDone).padding(bottom = 12.dp),
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(eligible, key = { it.id }) { ride ->
                val isSel = ride.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                        .clickable {
                            selected = if (isSel) selected - ride.id else selected + ride.id
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(fmt.format(Date(ride.startedAt)), color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        String.format(Locale.US, "%.1f %s", Units.distance(ride.distanceM, units), Units.distanceLabel(units).lowercase()),
                        color = if (isSel) accent else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Button(
            onClick = { onUpload(selected.toList()); onDone() },
            enabled = selected.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Background),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("UPLOAD ${selected.size} RIDE(S)", fontSize = 18.sp, fontWeight = FontWeight.Black) }
    }
}
