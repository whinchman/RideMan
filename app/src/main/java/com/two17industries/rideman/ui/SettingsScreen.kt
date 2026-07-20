package com.two17industries.rideman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.ble.BleConnectionState
import com.two17industries.rideman.core.Cadence
import com.two17industries.rideman.core.CadenceMode
import com.two17industries.rideman.core.MaxHeartRate
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.dash.DashStatus
import com.two17industries.rideman.data.RideScreen
import com.two17industries.rideman.data.RidemanSettings
import com.two17industries.rideman.data.ThemeChoice
import com.two17industries.rideman.hrm.HrmStatus
import com.two17industries.rideman.ui.components.CheckSquare
import com.two17industries.rideman.ui.components.HairLine
import com.two17industries.rideman.ui.components.PromptLabel
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.components.TerminalButtonStyle
import com.two17industries.rideman.ui.components.glow
import com.two17industries.rideman.ui.ride.hrmStatusLabel
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.BorderCyanDim
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.Dim
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.TextPrimary
import com.two17industries.rideman.ui.theme.accentFor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    current: RidemanSettings,
    stravaConnected: Boolean,
    stravaAthleteName: String?,
    onConnectStrava: () -> Unit,
    onDisconnectStrava: () -> Unit,
    onSave: (RidemanSettings) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    onOpenCalibration: () -> Unit,
    onRequestBlePermissions: () -> Unit,
) {
    var units by remember { mutableStateOf(current.units) }
    var cadenceMode by remember { mutableStateOf(current.cadenceMode) }
    var targetRpm by remember { mutableIntStateOf(current.targetRpm) }
    var theme by remember { mutableStateOf(current.theme) }
    var stravaUploadEnabled by remember { mutableStateOf(current.stravaUploadEnabled) }
    var dashEnabled by remember { mutableStateOf(current.dashEnabled) }
    // Ordered list of ALL ride screens; the Boolean is "enabled". Enabled ones keep
    // their saved order first, then the rest (disabled) in their default order.
    var screenItems by remember {
        mutableStateOf(
            current.screenOrder.map { it to true } +
                (RideScreen.entries - current.screenOrder.toSet()).map { it to false }
        )
    }
    var hrmEnabled by remember { mutableStateOf(current.hrmEnabled) }
    var hrmAddress by remember { mutableStateOf(current.hrmAddress) }
    var birthYear by remember { mutableIntStateOf(current.birthYear ?: DEFAULT_BIRTH_YEAR) }
    var birthYearSet by remember { mutableStateOf(current.birthYear != null) }
    var maxHr by remember { mutableStateOf(current.maxHeartRateBpm) }
    var showPicker by remember { mutableStateOf(false) }

    fun buildSettings(): RidemanSettings {
        val order = screenItems.filter { it.second }.map { it.first }
        return current.copy(
            units = units,
            cadenceMode = cadenceMode,
            targetRpm = targetRpm,
            theme = theme,
            screenOrder = order.ifEmpty { RideScreen.entries.toList() },
            stravaUploadEnabled = stravaUploadEnabled,
            dashEnabled = dashEnabled,
            hrmEnabled = hrmEnabled,
            hrmAddress = hrmAddress,
            birthYear = if (birthYearSet) birthYear else null,
            maxHeartRateBpm = maxHr,
        )
        // baselineHeartRateBpm and baselineCalibratedAtMillis are deliberately absent — the
        // calibration screen writes them directly, and including them here would let a stale
        // local clobber a fresh calibration on the next SAVE.
    }

    if (showPicker) {
        HrmPickerDialog(
            onPick = { addr -> hrmAddress = addr; showPicker = false },
            onDismiss = { showPicker = false },
        )
    }

    // Menu chrome is always cyan: the user's accent drives the ride and end screens only.
    // The four swatches below still render in their own colours — that is the live preview.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "SETTINGS",
                color = Cyan,
                style = MaterialTheme.typography.titleLarge
                    .copy(fontSize = 24.sp, letterSpacing = 0.7.sp)
                    .glow(Cyan, blurRadius = 7f),
            )
            Box(
                modifier = Modifier
                    .border(1.dp, Cyan.copy(alpha = 0.4f), RectangleShape)
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    "CANCEL",
                    color = Cyan,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 0.7.sp),
                )
            }
        }

        Section("UNITS") {
            SegmentRow {
                UnitSystem.entries.forEach { opt ->
                    SegmentCell(
                        text = if (opt == UnitSystem.AMERICAN) "American" else "Metric",
                        selected = opt == units,
                        modifier = Modifier.weight(1f),
                    ) { units = opt }
                }
            }
        }

        Section("CADENCE PULSE") {
            SegmentRow {
                CadenceMode.entries.forEach { opt ->
                    SegmentCell(
                        text = if (opt == CadenceMode.FULL) "Same foot" else "Alternating",
                        selected = opt == cadenceMode,
                        modifier = Modifier.weight(1f),
                    ) { cadenceMode = opt }
                }
            }
            Stepper(
                value = targetRpm,
                label = "TARGET RPM",
                onDec = { targetRpm = Cadence.clampRpm(targetRpm - 5) },
                onInc = { targetRpm = Cadence.clampRpm(targetRpm + 5) },
            )
        }

        Section("COLOR THEME", hint = "· drives ride display") {
            SwatchRow {
                ThemeChoice.entries.forEach { opt ->
                    ColorSwatch(
                        label = themeName(opt),
                        color = accentFor(opt),
                        selected = opt == theme,
                    ) { theme = opt }
                }
            }
        }

        Section("HANDLEBAR DASHBOARD") {
            val dashState by DashStatus.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "T-Display over BLE",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                )
                SegmentCell(
                    text = if (dashEnabled) "On" else "Off",
                    selected = dashEnabled,
                ) { dashEnabled = !dashEnabled }
            }
            if (dashEnabled) {
                val label = when (dashState) {
                    BleConnectionState.DISABLED -> "Idle (starts with your ride)"
                    BleConnectionState.NO_PERMISSION -> "Bluetooth permission needed"
                    BleConnectionState.BLUETOOTH_OFF -> "Bluetooth is off"
                    BleConnectionState.SCANNING -> "Searching…"
                    BleConnectionState.CONNECTED -> "Connected"
                    BleConnectionState.DISCONNECTED -> "Reconnecting…"
                }
                Text(label, color = Muted, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp))
            }
        }

        Section("HEART RATE", hint = "· BLE chest strap") {
            val hrmState by HrmStatus.state.collectAsState()
            val year = remember { Calendar.getInstance().get(Calendar.YEAR) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Strap over BLE",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                )
                SegmentCell(
                    text = if (hrmEnabled) "On" else "Off",
                    selected = hrmEnabled,
                ) { hrmEnabled = !hrmEnabled }
            }

            if (hrmEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        hrmStatusLabel(hrmState),
                        color = Muted,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                        modifier = Modifier.weight(1f),
                    )
                    if (hrmState == BleConnectionState.NO_PERMISSION) {
                        SegmentCell(text = "GRANT", selected = false) { onRequestBlePermissions() }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        hrmAddress ?: "Any strap",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                        modifier = Modifier.weight(1f),
                    )
                    SegmentCell(text = "CHOOSE", selected = false) { showPicker = true }
                }

                val estimated = if (birthYearSet) MaxHeartRate.estimateFromAge(birthYear, year) else null

                Stepper(
                    value = birthYear,
                    label = if (birthYearSet) "BIRTH YEAR" else "BIRTH YEAR · NOT SET",
                    onDec = { birthYear -= 1; birthYearSet = true },
                    onInc = { birthYear += 1; birthYearSet = true },
                )

                Stepper(
                    value = maxHr ?: estimated ?: 180,
                    label = if (maxHr == null) "MAX HR · ESTIMATED" else "MAX HR",
                    onDec = { maxHr = (maxHr ?: estimated ?: 180) - 1 },
                    onInc = { maxHr = (maxHr ?: estimated ?: 180) + 1 },
                )

                HairLine(color = BorderCyanDim)

                Text(
                    current.baselineHeartRateBpm?.let { bpm ->
                        val on = current.baselineCalibratedAtMillis
                            ?.let { SETTINGS_DATE_FMT.format(Date(it)) }
                            ?: "unknown date"
                        "Baseline $bpm bpm · calibrated $on"
                    } ?: "Baseline not calibrated",
                    color = Muted,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                )

                TerminalButton(
                    text = "CALIBRATE BASELINE",
                    onClick = { onSave(buildSettings()); onOpenCalibration() },
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Section("STRAVA") {
            if (stravaConnected) {
                Text(
                    "Connected" + (stravaAthleteName?.let { " as $it" } ?: ""),
                    color = Cyan,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Auto-upload rides",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    )
                    SegmentCell(
                        text = if (stravaUploadEnabled) "On" else "Off",
                        selected = stravaUploadEnabled,
                    ) { stravaUploadEnabled = !stravaUploadEnabled }
                }
                TerminalButton(
                    text = "DISCONNECT",
                    onClick = onDisconnectStrava,
                    style = TerminalButtonStyle.SECONDARY,
                    fontSize = 13.sp,
                )
            } else {
                TerminalButton(
                    text = "> CONNECT TO STRAVA",
                    onClick = onConnectStrava,
                    style = TerminalButtonStyle.PRIMARY,
                    fontSize = 14.sp,
                )
            }
        }

        Section("RIDE SCREENS", hint = "· tap to enable · arrows reorder") {
            screenItems.forEachIndexed { index, (screen, enabled) ->
                OrderRow(
                    name = screenName(screen),
                    enabled = enabled,
                    isFirst = index == 0,
                    isLast = index == screenItems.lastIndex,
                    onToggle = {
                        screenItems = screenItems.toMutableList().also { it[index] = screen to !enabled }
                    },
                    onUp = {
                        if (index > 0) screenItems = screenItems.toMutableList().also {
                            val tmp = it[index - 1]; it[index - 1] = it[index]; it[index] = tmp
                        }
                    },
                    onDown = {
                        if (index < screenItems.lastIndex) screenItems = screenItems.toMutableList().also {
                            val tmp = it[index + 1]; it[index + 1] = it[index]; it[index] = tmp
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        TerminalButton(
            text = "SAVE",
            onClick = {
                onSave(buildSettings())
                onDone()
            },
            style = TerminalButtonStyle.PRIMARY,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Section(title: String, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        PromptLabel(
            title,
            color = Cyan,
            fontSize = 11.sp,
            letterSpacing = 1.8.sp,
            trailing = hint,
            trailingColor = Dim,
        )
        content()
    }
}

@Composable
private fun SegmentRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Sharp segmented-control cell: selected is a solid cyan fill with near-black text; else a hairline outline. */
@Composable
private fun SegmentCell(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .then(
                if (selected) Modifier.background(Cyan, RectangleShape)
                else Modifier.border(1.dp, Cyan.copy(alpha = 0.3f), RectangleShape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Background else Cyan,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) { content() }
}

/** Sharp accent swatch. Selected takes a 2dp white ring. These are the live accent preview. */
@Composable
private fun ColorSwatch(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(color, RectangleShape)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) Color.White else Color.Transparent,
                shape = RectangleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.Black,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 0.sp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun Stepper(value: Int, label: String, onDec: () -> Unit, onInc: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StepButton("−", onDec) // minus sign
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$value",
                color = Cyan,
                style = MaterialTheme.typography.titleLarge
                    .copy(fontSize = 34.sp, letterSpacing = 0.sp)
                    .glow(Cyan, blurRadius = 6f),
            )
            Text(
                label,
                color = Muted,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 1.5.sp),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        StepButton("+", onInc)
    }
}

/** Square, outlined, no fill — the old circular filled StepButton is gone. */
@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .border(1.dp, Cyan, RectangleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Cyan,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, letterSpacing = 0.sp),
        )
    }
}

@Composable
private fun OrderRow(
    name: String,
    enabled: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CheckSquare(
            checked = enabled,
            modifier = Modifier.clickable(onClick = onToggle),
            accent = Cyan,
            size = 40.dp,
            glyphSize = 16.sp,
        )
        Text(
            name,
            modifier = Modifier.weight(1f),
            color = if (enabled) Cyan else Cyan.copy(alpha = 0.4f),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, letterSpacing = 0.sp),
        )
        ArrowButton("▲", enabled = !isFirst, onClick = onUp)
        ArrowButton("▼", enabled = !isLast, onClick = onDown)
    }
}

@Composable
private fun ArrowButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Cyan.copy(alpha = 0.9f) else Cyan.copy(alpha = 0.25f)
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(
                1.dp,
                if (enabled) Cyan.copy(alpha = 0.35f) else Cyan.copy(alpha = 0.15f),
                RectangleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = tint, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp))
    }
}

private fun screenName(screen: RideScreen): String = when (screen) {
    RideScreen.GRID -> "Dash"
    RideScreen.SPEED -> "Speed"
    RideScreen.ODOMETER -> "Odometer"
    RideScreen.COMPASS -> "Compass"
    RideScreen.ALTITUDE -> "Altitude"
    RideScreen.CADENCE -> "Cadence"
    RideScreen.HEART_RATE -> "HEART RATE"
}

private fun themeName(choice: ThemeChoice): String = when (choice) {
    ThemeChoice.AMBER -> "Amber"
    ThemeChoice.ACID_GREEN -> "Acid Green"
    ThemeChoice.ELECTRIC_CYAN -> "Electric Cyan"
    ThemeChoice.HOT_MAGENTA -> "Hot Magenta"
}

/** Seed for the birth-year stepper when the rider has never set one. */
private const val DEFAULT_BIRTH_YEAR = 1990

private val SETTINGS_DATE_FMT = SimpleDateFormat("d MMM yyyy", Locale.US)
