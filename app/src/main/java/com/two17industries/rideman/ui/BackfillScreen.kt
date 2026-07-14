package com.two17industries.rideman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.data.StravaUploadState
import com.two17industries.rideman.ui.components.BackLabel
import com.two17industries.rideman.ui.components.CheckSquare
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.components.TerminalButtonStyle
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.Surface1
import com.two17industries.rideman.ui.theme.TextPrimary
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
    // Only rides not already uploaded are eligible.
    val eligible = remember(rides) { rides.filter { it.stravaState != StravaUploadState.UPLOADED } }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val fmt = remember { SimpleDateFormat("MMM d · h:mm a", Locale.US) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp)) {
        BackLabel(
            "UPLOAD PAST RIDES",
            modifier = Modifier.clickable(onClick = onDone).padding(bottom = 12.dp),
        )

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(eligible, key = { it.id }) { ride ->
                val isSel = ride.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSel) Modifier.background(Cyan.copy(alpha = 0.10f), RectangleShape)
                            else Modifier.background(Surface1, RectangleShape)
                        )
                        .clickable {
                            selected = if (isSel) selected - ride.id else selected + ride.id
                        }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CheckSquare(checked = isSel, accent = Cyan, size = 16.dp, glyphSize = 11.sp)
                        Spacer(Modifier.width(11.dp))
                        Text(
                            fmt.format(Date(ride.startedAt)),
                            color = if (isSel) TextPrimary else Muted,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                        )
                    }
                    Text(
                        String.format(
                            Locale.US,
                            "%.1f %s",
                            Units.distance(ride.distanceM, units),
                            Units.distanceLabel(units).lowercase(),
                        ),
                        color = if (isSel) Cyan else TextPrimary,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, letterSpacing = 0.sp),
                    )
                }
            }
        }

        TerminalButton(
            text = "↑ UPLOAD ${selected.size} RIDE(S)",
            onClick = { onUpload(selected.toList()); onDone() },
            enabled = selected.isNotEmpty(),
            style = TerminalButtonStyle.PRIMARY,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}
