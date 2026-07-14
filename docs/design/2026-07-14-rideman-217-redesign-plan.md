# RIDEMAN 217 Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-skin RideMan to the 217 Industries design system and add two ride features — a 2×2 "Dash" readout screen and an explicit portrait⇄landscape rotate button.

**Architecture:** Three phases on one branch. Phase 1 lays a theme foundation (fonts, color tokens, accent scope) with no screen changes. Phase 2 adds the two features — the only phase with behavior change, and where all the tests live. Phase 3 re-skins the five non-ride screens with zero behavior change. New logic is extracted into pure functions in `core/` and `data/` so it can be tested in the existing fast JVM suite.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, DataStore Preferences, JUnit 4.

**Spec:** `docs/design/2026-07-14-rideman-217-redesign-design.md`
**Design reference:** `design_handoff_rideman_217/RIDEMAN-1A.dc.html` (open in a browser)
**Worktree:** `/home/whinchman/experiments/RIDEMAN/rideman/.claude/worktrees/217-redesign` (branch `217-redesign`)

## Global Constraints

- **Corners are square everywhere.** `RoundedCornerShape(0.dp)`. No `CircleShape`, no `RoundedCornerShape(50)`, anywhere in the app.
- **Orbitron for headings and large values only. IBM Plex Mono for all body and UI.** Material's default sans appears nowhere.
- **No emoji.** The repo ships `🗑`, `🎉`, `⭱`. Replace: trash → `✕`, arrows → `↑` / `→`, drop `🎉`. Unicode glyphs (`>`, `→`, `◀`, `▲▼`, `★`, `✓`, `✕`, `⟳`, `▮`) are fine.
- **Accent scope:** the user-selectable accent (`LocalAccent`) drives the **ride and end screens only**. Start, Plan picker, History, and Settings use the fixed `Cyan` (`#00FFFF`) constant directly. The Settings color swatches are the one exception — they render their own accent colors and preview live.
- **The blinking cursor is the only animation in the app.** Press states are a color swap only, ~120ms, no scale.
- **Glow is a single low-radius layer.** Sunlight legibility first. Small mono labels get no glow at all.
- **No test infrastructure beyond JUnit.** There is no Robolectric and no `androidTest` source set. Every test in this plan is a pure JVM unit test on a pure function. Do not add a test framework.
- **Phases 1 and 3 must not change behavior.** If a flow changes or an existing test breaks in those phases, something is wrong.

**Build/test commands (run from the worktree root):**
- Tests: `./gradlew :app:testDebugUnitTest`
- Compile: `./gradlew :app:assembleDebug`

---

## File Structure

**Created:**
- `app/src/main/res/font/*.ttf` — Orbitron + IBM Plex Mono
- `app/src/main/java/com/two17industries/rideman/ui/ride/DashGridScreen.kt` — the 2×2 dash grid
- `app/src/main/java/com/two17industries/rideman/ui/components/TerminalUi.kt` — shared 217 primitives (bordered button, blinking cursor)
- `app/src/main/java/com/two17industries/rideman/data/ScreenOrder.kt` — pure ride-screen order + migration logic
- `app/src/test/java/com/two17industries/rideman/data/ScreenOrderTest.kt`

**Modified:**
- `ui/theme/Type.kt`, `ui/theme/Color.kt`, `ui/theme/Theme.kt` — fonts, tokens, accent scope
- `core/Units.kt` — duration formatter (+ `core/UnitsTest.kt`)
- `data/Settings.kt` — `RideScreen.GRID`, `RideOrientation`, migration flag
- `ui/RideViewModel.kt` — 1 Hz elapsed ticker, orientation toggle
- `ui/ride/RideScreen.kt` — chrome, rotate button, square dots, GRID page
- `ui/ride/MetricScreens.kt` — `BigMetric` keeps large type
- `MainActivity.kt`, `ui/Nav.kt` — orientation wiring
- `ui/StartScreen.kt`, `ui/PlanPickerScreen.kt`, `ui/EndScreen.kt`, `ui/HistoryScreen.kt`, `ui/SettingsScreen.kt`, `ui/BackfillScreen.kt` — re-skin

`BackfillScreen.kt` is **not** in the spec's re-skin list — that's a gap in the spec, found while
auditing the codebase for rounded corners. It's reachable (History → "Upload past rides to
Strava"), it has a `RoundedCornerShape(10.dp)`, and it reads `LocalAccent` despite being a menu
screen. Left out it would be the one screen still wearing the old look. Task 14 covers it.

---

# Phase 1 — Theme Foundation

No screen changes. At the end of this phase the app still looks broadly like itself, but is drawing on the new type and color system.

### Task 1: Fonts and typography

**Files:**
- Create: `app/src/main/res/font/orbitron_regular.ttf`, `orbitron_medium.ttf`, `orbitron_semibold.ttf`, `orbitron_bold.ttf`, `orbitron_extrabold.ttf`, `orbitron_black.ttf`
- Create: `app/src/main/res/font/ibm_plex_mono_regular.ttf`, `ibm_plex_mono_medium.ttf`, `ibm_plex_mono_bold.ttf`
- Modify: `app/src/main/java/com/two17industries/rideman/ui/theme/Type.kt`

**Interfaces:**
- Produces: `Orbitron: FontFamily`, `IbmPlexMono: FontFamily`, `RidemanTypography: Typography`, and `bigMetric: TextStyle` — all in package `com.two17industries.rideman.ui.theme`.

- [ ] **Step 1: Copy the font files**

The fonts are already on disk (OFL-licensed). Copy only the weights `Type.kt` names — the unused weights and every italic are dead APK weight. Android resource filenames must be lowercase with underscores.

```bash
cd /home/whinchman/experiments/RIDEMAN/rideman/.claude/worktrees/217-redesign
mkdir -p app/src/main/res/font
SRC=/home/whinchman/experiments/RIDEMAN/Fonts

cp "$SRC/Orbitron/static/Orbitron-Regular.ttf"    app/src/main/res/font/orbitron_regular.ttf
cp "$SRC/Orbitron/static/Orbitron-Medium.ttf"     app/src/main/res/font/orbitron_medium.ttf
cp "$SRC/Orbitron/static/Orbitron-SemiBold.ttf"   app/src/main/res/font/orbitron_semibold.ttf
cp "$SRC/Orbitron/static/Orbitron-Bold.ttf"       app/src/main/res/font/orbitron_bold.ttf
cp "$SRC/Orbitron/static/Orbitron-ExtraBold.ttf"  app/src/main/res/font/orbitron_extrabold.ttf
cp "$SRC/Orbitron/static/Orbitron-Black.ttf"      app/src/main/res/font/orbitron_black.ttf

cp "$SRC/IBM_Plex_Mono/IBMPlexMono-Regular.ttf"   app/src/main/res/font/ibm_plex_mono_regular.ttf
cp "$SRC/IBM_Plex_Mono/IBMPlexMono-Medium.ttf"    app/src/main/res/font/ibm_plex_mono_medium.ttf
cp "$SRC/IBM_Plex_Mono/IBMPlexMono-Bold.ttf"      app/src/main/res/font/ibm_plex_mono_bold.ttf

ls app/src/main/res/font/
```

Expected: nine `.ttf` files listed. Use the **static** Orbitron instances, not `Orbitron-VariableFont_wght.ttf` — Compose's variable-font support is not worth the risk here.

- [ ] **Step 2: Rewrite `Type.kt`**

Replace the entire contents of `app/src/main/java/com/two17industries/rideman/ui/theme/Type.kt`:

```kotlin
package com.two17industries.rideman.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.R

val Orbitron = FontFamily(
    Font(R.font.orbitron_regular, FontWeight.Normal),
    Font(R.font.orbitron_medium, FontWeight.Medium),
    Font(R.font.orbitron_semibold, FontWeight.SemiBold),
    Font(R.font.orbitron_bold, FontWeight.Bold),
    Font(R.font.orbitron_extrabold, FontWeight.ExtraBold),
    Font(R.font.orbitron_black, FontWeight.Black),
)

val IbmPlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
)

/**
 * The full-screen single-metric value (Speed, Odometer, Compass, Altitude, Cadence).
 *
 * Deliberately NOT displayLarge. displayLarge's 54sp is a *dash grid cell* size, where four
 * values share a screen; these five screens give one value the whole display and need to stay
 * legible on a handlebar at arm's length.
 */
val bigMetric = TextStyle(
    fontFamily = Orbitron,
    fontWeight = FontWeight.Bold,
    fontSize = 120.sp,
    letterSpacing = 0.sp,
)

val RidemanTypography = Typography(
    // Dash grid value.
    displayLarge = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Bold,
        fontSize = 54.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Orbitron,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 2.sp,
    ),
    // Small-caps mono labels.
    labelLarge = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.6.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
)
```

- [ ] **Step 3: Keep `BigMetric` large**

`displayLarge` just dropped from 140sp to 54sp. `ui/ride/MetricScreens.kt:27` currently uses `MaterialTheme.typography.displayLarge` for the huge number — left alone, the five single-metric screens would shrink to under half their current size.

In `app/src/main/java/com/two17industries/rideman/ui/ride/MetricScreens.kt`, change the value `Text` inside `BigMetric` (line 26-31) from `style = MaterialTheme.typography.displayLarge` to `style = bigMetric`, and add the import:

```kotlin
import com.two17industries.rideman.ui.theme.bigMetric
```

The `BigMetric` composable body becomes:

```kotlin
@Composable
fun BigMetric(label: String, value: String, unit: String) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = accent, style = MaterialTheme.typography.labelLarge)
        Text(
            value,
            color = accent,
            style = bigMetric,
            textAlign = TextAlign.Center,
        )
        Text(unit, color = accent, style = MaterialTheme.typography.titleLarge)
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. If `R.font.*` is unresolved, a filename is wrong — Android font resource names must be lowercase, digits, and underscores only.

- [ ] **Step 5: Verify on device**

Install and open the app. Expected: all text is now Orbitron (headings/values) or IBM Plex Mono (labels/body) — no default sans anywhere. The five ride metric screens still show a very large number, roughly the size it was before. **If those numbers look small, Step 3 didn't take.**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/font app/src/main/java/com/two17industries/rideman/ui/theme/Type.kt app/src/main/java/com/two17industries/rideman/ui/ride/MetricScreens.kt
git commit -m "feat(theme): add Orbitron + IBM Plex Mono, route through RidemanTypography

displayLarge becomes the 54sp dash-grid value size, so BigMetric takes its
own 120sp style rather than shrinking the five single-metric screens by half."
```

---

### Task 2: Color tokens and accent scope

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/ui/theme/Theme.kt`

**Interfaces:**
- Consumes: nothing.
- Produces (package `com.two17industries.rideman.ui.theme`): `Background`, `Surface1`, `Surface2`, `TextPrimary`, `Muted`, `Dim`, `Cyan`, `Magenta`, `HotPink`, `Warn`, `Delete`, `BorderCyan`, `BorderCyanDim`, `GridLine` — all `Color`. Plus the existing `Amber`, `AcidGreen`, `ElectricCyan`, `HotMagenta`, `accentFor(ThemeChoice)`, `LocalAccent`, and `RidemanTheme(theme, content)`, all unchanged in signature.

- [ ] **Step 1: Rewrite `Color.kt`**

Replace the entire contents:

```kotlin
package com.two17industries.rideman.ui.theme

import androidx.compose.ui.graphics.Color
import com.two17industries.rideman.data.ThemeChoice

// Canvas / surfaces
val Background = Color(0xFF0A0A0A)
val Surface1 = Color(0xFF151515)
val Surface2 = Color(0xFF1F1F1F)
val TextPrimary = Color(0xFFE0E0E0)
val Muted = Color(0xFF888888)
val Dim = Color(0xFF4A4A4A)

// Fixed neon. Cyan drives every menu screen; the ride/end screens use LocalAccent instead.
val Cyan = Color(0xFF00FFFF)
val Magenta = Color(0xFFFF00FF)
val HotPink = Color(0xFFFF007F)  // wordmark cursor
val Warn = Color(0xFFFFCF3A)     // "SHORT" / Strava pending
val Delete = Color(0xFFFF5252)

// Hairline borders (1px).
val BorderCyan = Color(0x3300FFFF)     // rgba(0,255,255,0.20)
val BorderCyanDim = Color(0x1A00FFFF)  // rgba(0,255,255,0.10)
val GridLine = Color(0x1F00FFFF)       // rgba(0,255,255,0.12)

// User-selectable accents — unchanged; these already match 217.
val AcidGreen = Color(0xFF39FF14)
val ElectricCyan = Color(0xFF00F0FF)
val HotMagenta = Color(0xFFFF2D95)
val Amber = Color(0xFFFFC400)

fun accentFor(choice: ThemeChoice): Color = when (choice) {
    ThemeChoice.AMBER -> Amber
    ThemeChoice.ACID_GREEN -> AcidGreen
    ThemeChoice.ELECTRIC_CYAN -> ElectricCyan
    ThemeChoice.HOT_MAGENTA -> HotMagenta
}
```

Note the ARGB alpha bytes: 20% = `0x33`, 12% = `0x1F`, 10% = `0x1A`.

- [ ] **Step 2: Update `Theme.kt` for the new canvas**

`Background` changed from `#050505` to `#0A0A0A`; `Theme.kt` already references the `Background` constant, so it picks that up for free. The one change is that `onBackground`/`onSurface` should be readable text, not accent-colored. Replace the `scheme` block in `app/src/main/java/com/two17industries/rideman/ui/theme/Theme.kt`:

```kotlin
@Composable
fun RidemanTheme(theme: ThemeChoice, content: @Composable () -> Unit) {
    val accent = accentFor(theme)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = Background,
        background = Background,
        onBackground = TextPrimary,
        surface = Background,
        onSurface = TextPrimary,
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, typography = RidemanTypography, content = content)
    }
}
```

**Do not** introduce a second composition local for cyan. `LocalAccent` keeps meaning exactly one thing — "the user's chosen accent" — and only the ride/end composables read it. Menu screens import and use the `Cyan` constant directly. A composition local whose value depends on where you read it is a trap.

- [ ] **Step 3: Verify it compiles and nothing regressed**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all existing tests pass. This phase changes no behavior.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/theme/Color.kt app/src/main/java/com/two17industries/rideman/ui/theme/Theme.kt
git commit -m "feat(theme): add 217 color tokens, move canvas to #0A0A0A"
```

---

# Phase 2 — Ride Changes

The only phase with behavior change. All tests live here.

### Task 3: Duration formatter

`elapsedMs` has been tracked in `RideUiState` since the beginning but has never been rendered, so **no duration formatter exists anywhere in the codebase.** The dash's DURATION cell is its first consumer. This is the only genuinely new logic in the dash, and it is a pure function, so it gets real TDD.

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/core/Units.kt`
- Test: `app/src/test/java/com/two17industries/rideman/core/UnitsTest.kt`

**Interfaces:**
- Produces: `Units.duration(millis: Long): String` — `MM:SS` under an hour, `H:MM:SS` at an hour and over.

- [ ] **Step 1: Write the failing tests**

Append to `UnitsTest.kt`, inside the `UnitsTest` class:

```kotlin
    @Test fun duration_zero() {
        assertEquals("0:00", Units.duration(0L))
    }
    @Test fun duration_seconds_only() {
        assertEquals("0:07", Units.duration(7_000L))
    }
    @Test fun duration_pads_seconds() {
        assertEquals("1:05", Units.duration(65_000L))
    }
    @Test fun duration_minutes() {
        assertEquals("24:18", Units.duration(24 * 60_000L + 18_000L))
    }
    @Test fun duration_rolls_over_to_hours() {
        assertEquals("1:00:00", Units.duration(3_600_000L))
    }
    @Test fun duration_just_under_an_hour() {
        assertEquals("59:59", Units.duration(3_599_000L))
    }
    @Test fun duration_hours_pad_minutes_and_seconds() {
        assertEquals("1:04:08", Units.duration(3_600_000L + 4 * 60_000L + 8_000L))
    }
    @Test fun duration_truncates_partial_seconds() {
        assertEquals("0:01", Units.duration(1_999L))
    }
    @Test fun duration_clamps_negative_to_zero() {
        assertEquals("0:00", Units.duration(-5_000L))
    }
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*UnitsTest*"`
Expected: FAIL — compilation error, `Unresolved reference: duration`.

- [ ] **Step 3: Implement it**

Add to the `Units` object in `core/Units.kt`, and add `import java.util.Locale` at the top of the file:

```kotlin
    /**
     * Elapsed ride time. MM:SS under an hour, H:MM:SS at an hour and over.
     * Partial seconds truncate; negatives clamp to zero.
     */
    fun duration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
```

`Locale.US` is deliberate — the existing code already uses it for the TTS readout, and a locale-dependent format here would produce different digits on some devices.

- [ ] **Step 4: Run the tests and watch them pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*UnitsTest*"`
Expected: PASS, all tests green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/core/Units.kt app/src/test/java/com/two17industries/rideman/core/UnitsTest.kt
git commit -m "feat(core): add Units.duration formatter for the dash DURATION cell"
```

---

### Task 4: `RideScreen.GRID` and the one-shot migration

`screenOrder` is persisted as a CSV of enum names and **doubles as the enable list** (absent = disabled). Existing installs have a saved CSV that predates `GRID`, so without a migration the new dash silently arrives **disabled**.

The naive fix — "if `GRID` is absent from the CSV, add it" — is **wrong**: it resurrects the dash every single time a user deliberately turns it off. It has to be a one-shot migration guarded by its own flag.

`SettingsStore` needs a `Context` and DataStore, so it can't be unit-tested in this project (no Robolectric). The migration therefore lives in a **pure function** that `SettingsStore` calls, and the pure function is what gets tested.

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/data/Settings.kt`
- Create: `app/src/main/java/com/two17industries/rideman/data/ScreenOrder.kt`
- Create: `app/src/test/java/com/two17industries/rideman/data/ScreenOrderTest.kt`
- Modify: `app/src/test/java/com/two17industries/rideman/core/PagerWrapTest.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/ui/SettingsScreen.kt` (the `screenName` label only)

**Interfaces:**
- Produces: `RideScreen.GRID` (first in the enum); `ScreenOrder.migrate(saved: List<RideScreen>?, alreadyMigrated: Boolean): List<RideScreen>`.

- [ ] **Step 1: Write the failing migration tests**

Create `app/src/test/java/com/two17industries/rideman/data/ScreenOrderTest.kt`:

```kotlin
package com.two17industries.rideman.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenOrderTest {

    @Test fun fresh_install_gets_the_full_default_order_with_grid_first() {
        val result = ScreenOrder.migrate(saved = null, alreadyMigrated = false)
        assertEquals(RideScreen.entries.toList(), result)
        assertEquals(RideScreen.GRID, result.first())
    }

    @Test fun legacy_order_gets_grid_prepended() {
        val legacy = listOf(RideScreen.SPEED, RideScreen.ODOMETER, RideScreen.COMPASS)
        val result = ScreenOrder.migrate(saved = legacy, alreadyMigrated = false)
        assertEquals(
            listOf(RideScreen.GRID, RideScreen.SPEED, RideScreen.ODOMETER, RideScreen.COMPASS),
            result,
        )
    }

    @Test fun legacy_migration_preserves_a_users_custom_order() {
        val legacy = listOf(RideScreen.CADENCE, RideScreen.SPEED)
        val result = ScreenOrder.migrate(saved = legacy, alreadyMigrated = false)
        assertEquals(listOf(RideScreen.GRID, RideScreen.CADENCE, RideScreen.SPEED), result)
    }

    /** The bug this whole design exists to prevent. */
    @Test fun a_user_who_disabled_grid_keeps_it_disabled() {
        val disabled = listOf(RideScreen.SPEED, RideScreen.ODOMETER)
        val result = ScreenOrder.migrate(saved = disabled, alreadyMigrated = true)
        assertEquals(disabled, result)
    }

    @Test fun migration_does_not_duplicate_grid_if_it_is_somehow_already_there() {
        val saved = listOf(RideScreen.GRID, RideScreen.SPEED)
        val result = ScreenOrder.migrate(saved = saved, alreadyMigrated = false)
        assertEquals(listOf(RideScreen.GRID, RideScreen.SPEED), result)
    }

    @Test fun an_empty_saved_order_falls_back_to_the_default() {
        val result = ScreenOrder.migrate(saved = emptyList(), alreadyMigrated = true)
        assertEquals(RideScreen.entries.toList(), result)
    }

    @Test fun a_post_migration_order_is_returned_untouched() {
        val saved = listOf(RideScreen.SPEED, RideScreen.GRID, RideScreen.CADENCE)
        val result = ScreenOrder.migrate(saved = saved, alreadyMigrated = true)
        assertEquals(saved, result)
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*ScreenOrderTest*"`
Expected: FAIL — compilation error, `Unresolved reference: ScreenOrder` (and `GRID`).

- [ ] **Step 3: Add `GRID` to the enum**

In `app/src/main/java/com/two17industries/rideman/data/Settings.kt`, replace the enum on line 14-15:

```kotlin
/**
 * The ride sub-screens, in their default order.
 *
 * GRID is the 2x2 "Dash" readout (Speed/Distance/Duration/Heading). It is named GRID, not DASH,
 * because `dash/` is already the T-Display BLE handlebar dashboard — different feature entirely.
 *
 * Stored as a CSV of names; presence in the CSV also means "enabled".
 */
enum class RideScreen { GRID, SPEED, ODOMETER, COMPASS, ALTITUDE, CADENCE }
```

- [ ] **Step 4: Write the pure migration**

Create `app/src/main/java/com/two17industries/rideman/data/ScreenOrder.kt`:

```kotlin
package com.two17industries.rideman.data

/**
 * Ride-screen order, and the one-shot migration that introduces GRID.
 *
 * Kept pure and separate from SettingsStore so it can be unit-tested — SettingsStore needs a
 * Context and DataStore, and this project has no Robolectric.
 */
object ScreenOrder {

    /**
     * Resolves the stored screen order.
     *
     * [saved] is what DataStore holds (null on a fresh install). [alreadyMigrated] is the
     * one-shot `grid_migrated` flag.
     *
     * GRID is prepended exactly once, to installs that predate it. After the flag is set the
     * user's order is authoritative forever — including a user who deliberately disabled GRID.
     * Inferring the migration from "GRID is missing" instead of a flag would resurrect the dash
     * every time somebody turned it off.
     */
    fun migrate(saved: List<RideScreen>?, alreadyMigrated: Boolean): List<RideScreen> {
        val default = RideScreen.entries.toList()
        if (saved.isNullOrEmpty()) return default
        if (alreadyMigrated) return saved
        if (saved.contains(RideScreen.GRID)) return saved
        return listOf(RideScreen.GRID) + saved
    }
}
```

- [ ] **Step 5: Run the tests and watch them pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*ScreenOrderTest*"`
Expected: PASS, seven tests green.

- [ ] **Step 6: Wire the migration into `SettingsStore`**

In `data/Settings.kt`, add the flag key to `Keys`:

```kotlin
        val GRID_MIGRATED = booleanPreferencesKey("grid_migrated")
```

Replace the `screenOrder = ...` block inside the `settings` flow's `map` with a call to the pure function:

```kotlin
            screenOrder = ScreenOrder.migrate(
                saved = p[Keys.ORDER]?.split(",")
                    ?.mapNotNull { runCatching { RideScreen.valueOf(it) }.getOrNull() },
                alreadyMigrated = p[Keys.GRID_MIGRATED] ?: false,
            ),
```

Then set the flag in `save`, so the migration runs at most once. Add to the `edit` block:

```kotlin
            p[Keys.GRID_MIGRATED] = true
```

Writing the flag on **save** (rather than on read) keeps the read path free of side effects. A user who never opens Settings keeps getting the same migrated order computed on every read, which is idempotent and harmless; the moment they save anything, their choice is locked in.

- [ ] **Step 7: Label it "Dash" in Settings**

In `app/src/main/java/com/two17industries/rideman/ui/SettingsScreen.kt`, add the new case to `screenName` (currently line 416-421):

```kotlin
private fun screenName(screen: RideScreen): String = when (screen) {
    RideScreen.GRID -> "Dash"
    RideScreen.SPEED -> "Speed"
    RideScreen.ODOMETER -> "Odometer"
    RideScreen.COMPASS -> "Compass"
    RideScreen.ALTITUDE -> "Altitude"
    RideScreen.CADENCE -> "Cadence"
}
```

The existing reorder UI (lines 71-76) already lists any enum value missing from `screenOrder` as a disabled row, so it needs no other change.

- [ ] **Step 8: Assert the pager handles six screens**

The pager is the dash's entire delivery mechanism, and every existing `PagerWrapTest` case is hardcoded to `count = 5`. Add to `app/src/test/java/com/two17industries/rideman/core/PagerWrapTest.kt`:

```kotlin
    @Test fun wraps_with_six_screens() {
        assertEquals(0, PagerWrap.screenIndex(0, 6))
        assertEquals(5, PagerWrap.screenIndex(5, 6))
        assertEquals(0, PagerWrap.screenIndex(6, 6))
        assertEquals(5, PagerWrap.screenIndex(-1, 6))
    }
    @Test fun start_page_lands_on_index_zero_with_six_screens() {
        assertEquals(0, PagerWrap.screenIndex(PagerWrap.startPage(count = 6), 6))
    }
```

- [ ] **Step 9: Run the whole suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS. Everything green, including the pre-existing tests.

The app will not build yet if `RideScreen.kt`'s `when` is now non-exhaustive — that's expected and Task 7 fixes it. If `./gradlew :app:assembleDebug` fails with "when expression must be exhaustive", carry on.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/data app/src/test/java/com/two17industries/rideman/data app/src/test/java/com/two17industries/rideman/core/PagerWrapTest.kt app/src/main/java/com/two17industries/rideman/ui/SettingsScreen.kt
git commit -m "feat(settings): add RideScreen.GRID with a one-shot migration

GRID arrives enabled and first for existing users. The migration is guarded by
a grid_migrated flag rather than inferred from GRID's absence — inference would
resurrect the dash every time a user deliberately disabled it."
```

---

### Task 5: Sticky ride orientation

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/data/Settings.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/ui/RideViewModel.kt`
- Test: `app/src/test/java/com/two17industries/rideman/data/ScreenOrderTest.kt` — no; create a new file (below)
- Create: `app/src/test/java/com/two17industries/rideman/data/RideOrientationTest.kt`

**Interfaces:**
- Produces: `enum class RideOrientation { PORTRAIT, LANDSCAPE }` with `fun flipped(): RideOrientation`; `RidemanSettings.rideOrientation: RideOrientation`; `RideViewModel.toggleRideOrientation()`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/two17industries/rideman/data/RideOrientationTest.kt`:

```kotlin
package com.two17industries.rideman.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RideOrientationTest {
    @Test fun portrait_flips_to_landscape() {
        assertEquals(RideOrientation.LANDSCAPE, RideOrientation.PORTRAIT.flipped())
    }
    @Test fun landscape_flips_to_portrait() {
        assertEquals(RideOrientation.PORTRAIT, RideOrientation.LANDSCAPE.flipped())
    }
    @Test fun flipping_twice_is_identity() {
        assertEquals(RideOrientation.PORTRAIT, RideOrientation.PORTRAIT.flipped().flipped())
    }
    @Test fun default_settings_are_portrait() {
        assertEquals(RideOrientation.PORTRAIT, RidemanSettings().rideOrientation)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*RideOrientationTest*"`
Expected: FAIL — `Unresolved reference: RideOrientation`.

- [ ] **Step 3: Add the enum and the setting**

In `data/Settings.kt`, add next to `ThemeChoice`:

```kotlin
/** Ride display orientation. Sticky across rides; toggled only by the ride screen's rotate button. */
enum class RideOrientation {
    PORTRAIT,
    LANDSCAPE;

    fun flipped(): RideOrientation = if (this == PORTRAIT) LANDSCAPE else PORTRAIT
}
```

Add the field to `RidemanSettings`:

```kotlin
    val rideOrientation: RideOrientation = RideOrientation.PORTRAIT,
```

Add the key:

```kotlin
        val RIDE_ORIENTATION = stringPreferencesKey("ride_orientation")
```

Read it in the `settings` flow's `map`:

```kotlin
            rideOrientation = p[Keys.RIDE_ORIENTATION]
                ?.let { runCatching { RideOrientation.valueOf(it) }.getOrNull() }
                ?: RideOrientation.PORTRAIT,
```

And persist it in `save`:

```kotlin
            p[Keys.RIDE_ORIENTATION] = s.rideOrientation.name
```

- [ ] **Step 4: Run the test and watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*RideOrientationTest*"`
Expected: PASS, four tests green.

- [ ] **Step 5: Add the toggle to the ViewModel**

In `ui/RideViewModel.kt`, add next to `saveSettings` (which is at the bottom of the class), plus the import `com.two17industries.rideman.data.RideOrientation`:

```kotlin
    /**
     * Flips the ride display between portrait and landscape and persists the choice.
     *
     * Sticky by design: the next ride opens in whatever orientation you last rode in, so a
     * bar-mounted rider never has to correct it. The rotate button is the only control — this
     * is deliberately not surfaced in Settings.
     */
    fun toggleRideOrientation() {
        val current = settings.value
        saveSettings(current.copy(rideOrientation = current.rideOrientation.flipped()))
    }
```

- [ ] **Step 6: Verify**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/data/Settings.kt app/src/main/java/com/two17industries/rideman/ui/RideViewModel.kt app/src/test/java/com/two17industries/rideman/data/RideOrientationTest.kt
git commit -m "feat(settings): persist a sticky ride orientation"
```

---

### Task 6: Make elapsed time tick independently of GPS

**`RideViewModel.collectLocation()` recomputes `elapsedMs` inside the location collector.** Nothing renders it today, so nobody has noticed — but the moment the dash shows a clock, it will visibly **stall** whenever GPS fixes stop arriving (under trees, in a garage, at a light under an overpass). A frozen clock reads as a frozen app.

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/RideViewModel.kt`

**Interfaces:**
- Consumes: `RideUiState.elapsedMs` (already exists).
- Produces: nothing new — `elapsedMs` simply becomes reliable.

- [ ] **Step 1: Drop `elapsedMs` from the location collector**

In `collectLocation()`, remove the `elapsedMs` line from the `_ui.value.copy(...)` call. It becomes:

```kotlin
            _ui.value = _ui.value.copy(
                speedMps = sample.speedMps ?: 0f,
                distanceM = t.distanceM,
                headingDeg = sample.headingDeg,
                altitudeM = displayedAltitude(),
            )
```

- [ ] **Step 2: Add a 1 Hz ticker**

Add the imports `kotlinx.coroutines.delay` and `kotlinx.coroutines.isActive`, then add this private method next to `collectLocation()`:

```kotlin
    /**
     * Drives elapsedMs at 1 Hz, independent of the location stream.
     *
     * This used to be computed inside collectLocation(), which meant the ride clock froze
     * whenever GPS fixes stopped — under trees, in a garage, in a tunnel. Nothing rendered it
     * then; the dash does now.
     */
    private fun collectElapsed() = viewModelScope.launch {
        while (isActive) {
            _ui.value = _ui.value.copy(elapsedMs = System.currentTimeMillis() - startMillis)
            delay(1_000L)
        }
    }
```

- [ ] **Step 3: Start it with the ride**

In `startRide()`, add it alongside the other collectors:

```kotlin
        collectorJobs += collectLocation()
        collectorJobs += collectSensors()
        collectorJobs += collectElapsed()
```

`collectSensors()` returns a `List<Job>` and `collectLocation()` returns a single `Job` — `+=` handles both against a `MutableList<Job>`, and `collectElapsed()` returns a single `Job` like `collectLocation()`. `endRide()` already cancels everything in `collectorJobs`, so the ticker stops with the ride and needs no separate teardown.

- [ ] **Step 4: Verify**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: PASS / `BUILD SUCCESSFUL`.

This is the one piece of phase 2 that resists a cheap unit test — testing it properly would mean abstracting the clock, which isn't worth it for a `while (isActive)` loop. It gets verified on-device in Task 8 instead.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/RideViewModel.kt
git commit -m "fix(ride): tick elapsed time at 1Hz instead of on GPS fix

elapsedMs was recomputed inside the location collector, so the ride clock froze
whenever fixes stopped arriving. Invisible until now — the dash renders it."
```

---

### Task 7: The Dash grid screen

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/ui/ride/DashGridScreen.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/ui/ride/MetricScreens.kt` (expose `cardinal`)

**Interfaces:**
- Consumes: `RideUiState`, `RidemanSettings`, `Units.duration`, `Units.speed/distance/speedLabel/distanceLabel`, `LocalAccent`, `Magenta`, `Muted`, `Dim`, `Background`, `RidemanTypography`.
- Produces: `@Composable fun DashGridScreen(state: RideUiState, units: UnitSystem, landscape: Boolean)`.

- [ ] **Step 1: Share the `cardinal` helper**

`cardinal(deg: Float)` is currently `private` at the bottom of `MetricScreens.kt`. The dash needs it too. Change its declaration from `private fun cardinal` to `internal fun cardinal` — same file, same package, no other change.

- [ ] **Step 2: Write the grid**

Create `app/src/main/java/com/two17industries/rideman/ui/ride/DashGridScreen.kt`:

```kotlin
package com.two17industries.rideman.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.ui.RideUiState
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.Dim
import com.two17industries.rideman.ui.theme.LocalAccent
import com.two17industries.rideman.ui.theme.Magenta
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.Orbitron
import com.two17industries.rideman.ui.theme.IbmPlexMono
import kotlin.math.roundToInt

/**
 * The "Dash" screen: a 2x2 readout grid matching the bike-mounted display.
 *
 * Row-major: SPEED, DISTANCE / DURATION, HEADING.
 *
 * The hairline grid lines are drawn as a 1dp gap over an accent-tinted background — the cells
 * paint themselves Background and the gutters show through. That gives the interior cross with
 * no outer border, which is what the design calls for and what Modifier.border cannot do.
 */
@Composable
fun DashGridScreen(state: RideUiState, units: UnitSystem, landscape: Boolean) {
    val accent = LocalAccent.current
    val gridLine = accent.copy(alpha = 0.12f)

    Column(
        modifier = Modifier.fillMaxSize().background(gridLine),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Cell(
                label = "SPEED",
                unit = Units.speedLabel(units),
                landscape = landscape,
                modifier = Modifier.weight(1f),
            ) {
                Value(
                    text = Units.speed(state.speedMps, units).roundToInt().toString(),
                    sizeSp = if (landscape) 56 else 54,
                    color = accent,
                )
            }
            Cell(
                label = "DISTANCE",
                unit = Units.distanceLabel(units),
                landscape = landscape,
                modifier = Modifier.weight(1f),
            ) {
                Value(
                    text = String.format(java.util.Locale.US, "%.2f", Units.distance(state.distanceM, units)),
                    sizeSp = if (landscape) 48 else 40,
                    color = accent,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Cell(
                label = "DURATION",
                unit = null,  // duration carries no unit suffix
                landscape = landscape,
                modifier = Modifier.weight(1f),
            ) {
                Value(
                    text = Units.duration(state.elapsedMs),
                    sizeSp = if (landscape) 44 else 38,
                    color = accent,
                )
            }
            Cell(
                label = "HEADING",
                unit = "DEG",
                landscape = landscape,
                modifier = Modifier.weight(1f),
            ) {
                Heading(headingDeg = state.headingDeg, landscape = landscape, accent = accent)
            }
        }
    }
}

/** One grid cell: a mono header line above a centered value. */
@Composable
private fun Cell(
    label: String,
    unit: String?,
    landscape: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(
                horizontal = if (landscape) 18.dp else 16.dp,
                vertical = if (landscape) 14.dp else 16.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Header(label = label, unit = unit)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
        content()
    }
}

/** `SPEED · MPH` — label in muted grey, the unit suffix dimmer still. No glow on small labels. */
@Composable
private fun Header(label: String, unit: String?) {
    val style = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
    )
    Row {
        Text(label, color = Muted, style = style)
        if (unit != null) {
            Text(" · $unit", color = Dim, style = style)
        }
    }
}

/** An Orbitron grid value with a single low-radius glow — sunlight legibility first. */
@Composable
private fun Value(text: String, sizeSp: Int, color: Color) {
    Text(
        text = text,
        color = color,
        textAlign = TextAlign.Center,
        style = TextStyle(
            fontFamily = Orbitron,
            fontWeight = FontWeight.Bold,
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp * 0.82f).sp,
            letterSpacing = 0.sp,
            shadow = Shadow(color = color.copy(alpha = 0.45f), offset = Offset.Zero, blurRadius = 5f),
        ),
    )
}

/**
 * Degrees over cardinal in portrait; side-by-side, baseline-aligned in landscape.
 *
 * The cardinal is the ONLY fixed color on the ride screen — magenta, never the accent — so it
 * stays distinguishable from the degrees at a glance.
 */
@Composable
private fun Heading(headingDeg: Float, landscape: Boolean, accent: Color) {
    val degrees = String.format(java.util.Locale.US, "%03d°", headingDeg.roundToInt().mod(360))
    val dir = cardinal(headingDeg)

    if (landscape) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Value(text = degrees, sizeSp = 48, color = accent)
            Value(text = dir, sizeSp = 24, color = Magenta)
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Value(text = degrees, sizeSp = 44, color = accent)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
            Value(text = dir, sizeSp = 22, color = Magenta)
        }
    }
}
```

Note `headingDeg.roundToInt().mod(360)` — Kotlin's `mod` (unlike `%`) always returns non-negative, so a heading of `-1f` renders `359°`, not `-1°`.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (It may still fail on the non-exhaustive `when` in `RideScreen.kt` — Task 8 wires the page in. If that's the only error, proceed.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/ride/DashGridScreen.kt app/src/main/java/com/two17industries/rideman/ui/ride/MetricScreens.kt
git commit -m "feat(ride): add the Dash 2x2 readout grid"
```

---

### Task 8: Ride chrome, rotate button, and orientation wiring

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/ride/RideScreen.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/ui/Nav.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/MainActivity.kt`

**Interfaces:**
- Consumes: `DashGridScreen`, `RideOrientation`, `RideViewModel.toggleRideOrientation()`, `RidemanSettings.rideOrientation`.
- Produces: `RideScreen(state, settings, onEndRide, onToggleOrientation)` — note the **new fourth parameter**.

- [ ] **Step 1: Rewrite `RideScreen.kt`**

Replace the whole file. This wires in the GRID page, swaps circular dots for squares, adds the portrait header with the rotate button, and rebuilds the side rail (rotate → dots → END).

```kotlin
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
        Box(Modifier.fillMaxWidth().padding(bottom = 1.dp).background(accent.copy(alpha = 0.10f)))
    }
}

/** Sharp bordered square with the rotate glyph. The only control for ride orientation. */
@Composable
private fun RotateButton(onClick: () -> Unit, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, accent.copy(alpha = 0.35f), Sharp)
            .clickable(onClick = onClick),
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
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
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
```

The TTS readout is unchanged — it already reads speed/distance/heading/altitude regardless of which page is showing.

- [ ] **Step 2: Pass the toggle through Nav**

In `ui/Nav.kt`, the `Dest.RIDE` branch (line 107-114) gains the callback:

```kotlin
        Dest.RIDE -> {
            BackHandler { lastSummary = vm.endRide(); dest = Dest.END }
            com.two17industries.rideman.ui.ride.RideScreen(
                state = ui,
                settings = settings,
                onEndRide = { lastSummary = vm.endRide(); dest = Dest.END },
                onToggleOrientation = { vm.toggleRideOrientation() },
            )
        }
```

- [ ] **Step 3: Apply the orientation in `MainActivity`**

`onRideActive` currently slams `SCREEN_ORIENTATION_LOCKED` — which freezes the ride into however the phone happened to be held at the start. That's the bug.

Replace the `App()` composable and `onRideActive` in `MainActivity.kt` with:

```kotlin
    @Composable
    private fun App() {
        val vm: RideViewModel = viewModel()
        rideViewModel = vm
        val settings by vm.settings.collectAsState()
        var rideActive by remember { mutableStateOf(false) }

        // Orientation is applied from the persisted setting, not from how the phone is held, and
        // re-applied whenever the rotate button flips it. The manifest declares
        // configChanges="orientation|..." so this recomposes without recreating the activity.
        LaunchedEffect(rideActive, settings.rideOrientation) {
            requestedOrientation = if (!rideActive) {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else when (settings.rideOrientation) {
                RideOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                RideOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }

        RidemanTheme(theme = settings.theme) {
            Surface(modifier = Modifier.fillMaxSize()) {
                RidemanNav(
                    vm = vm,
                    onRideActiveChanged = { active ->
                        rideActive = active
                        if (active) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    },
                )
            }
        }
    }
```

Delete the old `private fun onRideActive(active: Boolean)` method entirely. Add the imports:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.two17industries.rideman.data.RideOrientation
```

The app never free-rotates on the accelerometer during a ride: outside a ride the orientation is `UNSPECIFIED`, and during one it is pinned to the persisted value.

- [ ] **Step 4: Verify the build and the suite**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: PASS / `BUILD SUCCESSFUL`.

- [ ] **Step 5: Verify on device — this is the important one**

Install and run through this checklist. These are the behaviors that no unit test in this plan covers:

1. Start a ride. **The first page is the Dash** — a 2×2 grid, SPEED / DISTANCE over DURATION / HEADING, hairline lines between the cells and none around the outside.
2. **The DURATION cell counts up once a second.** Now walk into a garage or otherwise kill GPS — **it must keep counting.** (This is Task 6. If it freezes, the ticker didn't take.)
3. Swipe: six pages, six square dots, wrap-around still works in both directions.
4. Tap `⟳` in the header. The display flips to landscape; the header disappears and the rotate button moves to the top of the right rail, above the dots and END.
5. Tap `⟳` again — back to portrait.
6. **Physically rotate the phone without touching the button. Nothing should happen.**
7. End the ride. Start a new one. **It opens in whichever orientation you last chose** — this is the whole point of the sticky setting.
8. Double-tap anywhere: the TTS readout still speaks.
9. Nothing anywhere on the ride screen has a rounded corner.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/ride/RideScreen.kt app/src/main/java/com/two17industries/rideman/ui/Nav.kt app/src/main/java/com/two17industries/rideman/MainActivity.kt
git commit -m "feat(ride): add the rotate button and wire the Dash into the pager

Orientation is now driven by a persisted setting applied on ride start and
flipped by the rotate button, replacing SCREEN_ORIENTATION_LOCKED — which
froze the ride into however the phone happened to be held."
```

---

# Phase 3 — Re-skin

Zero behavior change. No logic, navigation, or state changes. These screens are pure Compose styling with no unit-testable surface, so each task verifies by compiling plus a concrete on-device visual checklist.

### Task 9: Re-skin Start screen (and add the shared terminal component kit)

> **Note on the ordering:** Task 8 defines a private `EndButton` inside `RideScreen.kt` that is the
> same 217 primary-button treatment `TerminalButton` provides here. That duplication is deliberate
> and temporary — phase 2 ships before this kit exists, and the ride screen must not block on the
> re-skin. Once `TerminalUi.kt` lands, **replace `RideScreen.kt`'s private `EndButton` with
> `TerminalButton(style = PRIMARY, accent = accent)`** and delete it. Do this as part of this task's
> commit so the duplication never outlives the phase boundary.

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/ui/components/TerminalUi.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/ui/StartScreen.kt`

**Interfaces:**
- Consumes: `Background`, `Surface1`, `TextPrimary`, `Muted`, `Dim`, `Cyan`, `HotPink`, `BorderCyan`, `BorderCyanDim` (Task 2, `ui/theme/Color.kt`); `RidemanTypography` `titleLarge` / `labelLarge` / `bodyLarge` (Task 1, `ui/theme/Type.kt`); `BuildConfig`, `PlanRide`, `UnitSystem`, `formatPlanDistance`.
- Produces: `com.two17industries.rideman.ui.components.TerminalButton`, `TerminalButtonStyle`, `TerminalPanel`, `BlinkingCursor`, `PromptLabel`, `BackLabel`, `HairLine`, `CheckSquare`, `TextStyle.glow()` — consumed by Tasks 10, 11, 12, 13, 14. `StartScreen` keeps its exact signature (`nextUp`, `planAvailable`, `units`, `onPlanRide`, `onFreeRide`, `onHistory`, `onSettings`), and `internal fun formatPlanDistance` stays in this file (PlanPicker and History both call it).

**Emoji / shape audit for this file:** `StartScreen.kt` line 76 `"Plan complete 🎉"` — the only emoji; drop the glyph. No `RoundedCornerShape` / `CircleShape` occurrences: the rounding comes from the Material `Button` / `OutlinedButton` defaults, which are removed wholesale.

- [ ] **Step 1: Create the shared terminal component kit**

Create `app/src/main/java/com/two17industries/rideman/ui/components/TerminalUi.kt`:

```kotlin
package com.two17industries.rideman.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.BorderCyan
import com.two17industries.rideman.ui.theme.BorderCyanDim
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.HotPink
import com.two17industries.rideman.ui.theme.Muted

/**
 * The 217 glow: a single low-radius shadow layer. Deliberately weaker than the three-layer web
 * glow — sunlight legibility comes first. Never apply this to small mono labels.
 */
fun TextStyle.glow(color: Color, blurRadius: Float = 8f): TextStyle =
    copy(shadow = Shadow(color = color.copy(alpha = 0.55f), offset = Offset.Zero, blurRadius = blurRadius))

/** The wordmark's block cursor. The only animation in the app: a hard 1s step blink. */
@Composable
fun BlinkingCursor(
    modifier: Modifier = Modifier,
    color: Color = HotPink,
    width: Dp = 10.dp,
    height: Dp = 21.dp,
) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0 using LinearEasing
                1f at 499 using LinearEasing
                0f at 500 using LinearEasing
                0f at 999 using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "cursor-alpha",
    )
    Box(
        modifier
            .size(width = width, height = height)
            .alpha(alpha)
            .background(color, RectangleShape)
    )
}

enum class TerminalButtonStyle { PRIMARY, SECONDARY }

/**
 * Sharp, hairline-bordered button. PRIMARY carries a faint accent fill and a glow; SECONDARY is a
 * bare outline. Text is centred when [trailing] is null, otherwise pushed apart from it.
 */
@Composable
fun TerminalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TerminalButtonStyle = TerminalButtonStyle.SECONDARY,
    accent: Color = Cyan,
    trailing: String? = null,
    fontSize: TextUnit = 15.sp,
) {
    val primary = style == TerminalButtonStyle.PRIMARY
    val borderColor = when {
        !enabled -> accent.copy(alpha = 0.15f)
        primary -> accent
        else -> accent.copy(alpha = 0.25f)
    }
    val fill = if (primary && enabled) accent.copy(alpha = 0.12f) else Color.Transparent
    val contentColor = if (enabled) accent else accent.copy(alpha = 0.35f)
    val textStyle = MaterialTheme.typography.titleLarge
        .copy(fontSize = fontSize, letterSpacing = 1.2.sp)
        .let { if (enabled && primary) it.glow(accent, blurRadius = 6f) else it }

    Row(
        modifier = modifier
            .border(1.dp, borderColor, RectangleShape)
            .background(fill, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (trailing == null) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        Text(text, color = contentColor, style = textStyle, maxLines = 1)
        if (trailing != null) {
            Text(
                trailing,
                color = if (primary) contentColor else Muted,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            )
        }
    }
}

/** A sharp hairline panel. Zero radius, 1px border, optional faint fill. */
@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = BorderCyan,
    fill: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .border(1.dp, borderColor, RectangleShape)
            .background(fill, RectangleShape),
        content = content,
    )
}

/** A 1px full-width rule. */
@Composable
fun HairLine(modifier: Modifier = Modifier, color: Color = BorderCyanDim) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color, RectangleShape))
}

/** `> LABEL` — grey prompt prefix, accent label, optional dim trailing hint. Small mono, no glow. */
@Composable
fun PromptLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Muted,
    fontSize: TextUnit = 11.sp,
    letterSpacing: TextUnit = 1.7.sp,
    trailing: String? = null,
    trailingColor: Color = Color.Unspecified,
) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Muted, fontWeight = FontWeight.Normal)) { append("> ") }
            withStyle(SpanStyle(color = color)) { append(text) }
            if (trailing != null) {
                withStyle(
                    SpanStyle(
                        color = trailingColor,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.sp,
                    )
                ) { append(" $trailing") }
            }
        },
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize, letterSpacing = letterSpacing),
    )
}

/** `◀ TITLE` — grey back chevron, accent Orbitron title. */
@Composable
fun BackLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Cyan,
    fontSize: TextUnit = 18.sp,
) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Muted, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)) {
                append("◀ ")
            }
            withStyle(SpanStyle(color = color)) { append(text) }
        },
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge
            .copy(fontSize = fontSize, letterSpacing = 0.8.sp)
            .glow(color, blurRadius = 6f),
    )
}

/** Square status / toggle box. Filled accent with a black ✓ when checked, hairline outline when not. */
@Composable
fun CheckSquare(
    checked: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = Cyan,
    size: Dp = 18.dp,
    glyphSize: TextUnit = 12.sp,
) {
    Box(
        modifier
            .size(size)
            .then(
                if (checked) Modifier.background(accent, RectangleShape)
                else Modifier.border(1.5.dp, accent.copy(alpha = 0.5f), RectangleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text(
                "✓",
                color = Background,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = glyphSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp,
                ),
            )
        }
    }
}
```

- [ ] **Step 2: Rewrite `StartScreen.kt` (replace lines 1-105; keep `formatPlanDistance`, lines 107-116, verbatim)**

This drops `LocalAccent` (menu screens take the fixed `Cyan` per the accent scope in Task 2), drops the Material `Button` / `OutlinedButton` (their default `RoundedCornerShape` is the entire reason the screen looks rounded), and drops the 🎉 on line 76. Callbacks, the `planAvailable` gate, and the three subtitle states are unchanged.

```kotlin
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
```

- [ ] **Step 3: Verify — compile**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verify — on-device visual checklist (Start)**

1. The wordmark reads `> RIDEMAN` — the `>` is grey `#888` mono, `RIDEMAN` is cyan Orbitron with a soft glow.
2. A hot-pink (`#FF007F`) solid block cursor sits right of the `N` and blinks on/off on a hard 1-second step — no fade.
3. **No rounded corners anywhere on this screen:** the NEXT UP panel, PLAN RIDE, FREE RIDE, HISTORY and SETTINGS are all sharp rectangles with 1px borders.
4. PLAN RIDE has a faint cyan fill plus a glow and a `→` on its right edge; FREE RIDE has no fill and a grey `→`.
5. With no plan available, PLAN RIDE is dimmed and untappable. With the plan complete, the panel reads `PLAN COMPLETE` — **no 🎉**.
6. The version line is `#4A4A4A` dim grey mono at 11sp.
7. Change the accent theme in Settings and come back: **this screen stays cyan.**

- [ ] **Final step: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/components/TerminalUi.kt \
        app/src/main/java/com/two17industries/rideman/ui/StartScreen.kt
git commit -m "feat(ui): re-skin Start screen; add shared terminal component kit

Adds ui/components/TerminalUi.kt (TerminalButton, TerminalPanel, BlinkingCursor,
PromptLabel, BackLabel, HairLine, CheckSquare, TextStyle.glow) and rebuilds Start
on it: > RIDEMAN wordmark with a blinking hot-pink block cursor, hairline NEXT UP
panel, sharp bordered buttons, fixed cyan. Drops the 🎉. No behavior change."
```

---

### Task 10: Re-skin Plan picker screen

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/PlanPickerScreen.kt`

**Interfaces:**
- Consumes: `TerminalButton`, `TerminalButtonStyle`, `BackLabel`, `PromptLabel`, `CheckSquare`, `HairLine`, `glow` (Task 9); `Background`, `Cyan`, `Muted`, `Dim`, `TextPrimary`, `BorderCyanDim` (Task 2); `RidemanTypography` (Task 1); `formatPlanDistance` (StartScreen.kt).
- Produces: nothing new. `PlanPickerScreen(plan, progress, units, onStart, onBack)`, `PlanListItem` and `buildPlanListItems` are unchanged.

**Shape audit for this file — every occurrence:**

| Line | Current | Replacement |
|---|---|---|
| 20 | `import androidx.compose.foundation.shape.CircleShape` | delete the import |
| 21 | `import androidx.compose.foundation.shape.RoundedCornerShape` | replace with `RectangleShape` |
| 122 | `CollapsedRow`: `.clip(RoundedCornerShape(10.dp))` | delete — the row needs no clip |
| 150 | `StatusDot`: `.clip(CircleShape)` | replaced by `CheckSquare` (a square) |
| 153 | `StatusDot`: `.border(1.5.dp, accent.copy(alpha = 0.5f), CircleShape)` | replaced by `CheckSquare` |
| 173 | `ExpandedRow`: `.clip(RoundedCornerShape(14.dp))` | delete |
| 174 | `ExpandedRow`: `.border(1.5.dp, accent, RoundedCornerShape(14.dp))` | `.border(1.dp, Cyan, RectangleShape)` |
| 207 | LONG RIDE badge: `.clip(RoundedCornerShape(6.dp))` | delete — sharp badge |

No emoji in this file: `◀`, `✓` and `★` are plain Unicode glyphs and stay.

- [ ] **Step 1: Replace the import block (lines 1-42) and the `PlanPickerScreen` body (lines 44-89)**

Drops `LocalAccent` for the fixed `Cyan`, removes both shape imports, swaps the Material `Button` for `TerminalButton`. The `expandedId` state, the `defaultExpanded` derivation, the `buildPlanListItems` memo and the `enabled = expandedId != null` gate are untouched.

```kotlin
package com.two17industries.rideman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanProgress
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.ui.components.BackLabel
import com.two17industries.rideman.ui.components.CheckSquare
import com.two17industries.rideman.ui.components.HairLine
import com.two17industries.rideman.ui.components.PromptLabel
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.components.TerminalButtonStyle
import com.two17industries.rideman.ui.components.glow
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.BorderCyanDim
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.Dim
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.TextPrimary

@Composable
fun PlanPickerScreen(
    plan: Plan,
    progress: PlanProgress?,
    units: UnitSystem,
    onStart: (PlanRide) -> Unit,
    onBack: () -> Unit,
) {
    val defaultExpanded = progress?.nextIncomplete()?.id ?: plan.rides.firstOrNull()?.id
    var expandedId by rememberSaveable { mutableStateOf(defaultExpanded) }

    // Build the ordered display list: a phase header appears before its first ride,
    // a week header before that week's first ride.
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp)) {
        BackLabel(
            "14-WEEK PLAN",
            modifier = Modifier.clickable(onClick = onBack).padding(bottom = 12.dp),
        )

        val listItems = remember(plan) { buildPlanListItems(plan) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listItems, key = { it.key }) { item ->
                when (item) {
                    is PlanListItem.PhaseHeaderItem -> PhaseHeader(item.number, item.name)
                    is PlanListItem.WeekHeaderItem -> WeekHeader(item.week, item.recovery)
                    is PlanListItem.RideItem -> {
                        val ride = item.ride
                        val complete = progress?.isComplete(ride.id) == true
                        if (ride.id == expandedId) ExpandedRow(ride, complete, units)
                        else CollapsedRow(ride, complete, units) { expandedId = ride.id }
                    }
                }
            }
        }

        TerminalButton(
            text = "START RIDE",
            onClick = { plan.byId[expandedId]?.let(onStart) },
            enabled = expandedId != null,
            style = TerminalButtonStyle.PRIMARY,
            fontSize = 16.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}
```

- [ ] **Step 2: Replace `PhaseHeader` and `WeekHeader` (lines 91-109)**

```kotlin
@Composable
private fun PhaseHeader(number: Int, name: String) {
    PromptLabel(
        "PHASE $number · ${name.uppercase()}",
        color = Muted,
        fontSize = 10.sp,
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun WeekHeader(week: Int, recovery: Boolean) {
    Text(
        "Week $week" + if (recovery) " (recovery)" else "",
        color = Dim,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}
```

- [ ] **Step 3: Replace `CollapsedRow` and delete `StatusDot` (lines 111-161)**

`StatusDot` — the circle — is deleted outright; `CheckSquare` from Task 9 replaces it.

```kotlin
@Composable
private fun CollapsedRow(
    ride: PlanRide,
    complete: Boolean,
    units: UnitSystem,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckSquare(checked = complete, accent = Cyan, size = 18.dp)
        Spacer(Modifier.width(11.dp))
        Text(
            "Ride ${ride.slot} · ${ride.kind}",
            modifier = Modifier.weight(1f),
            color = if (complete) Dim else TextPrimary,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
        )
        Text(
            formatPlanDistance(ride.targetMiles, units),
            color = if (complete) Cyan.copy(alpha = 0.5f) else Cyan,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
        )
    }
}
```

- [ ] **Step 4: Replace `ExpandedRow` and delete `DetailRow` (lines 163-232)**

The old `private fun DetailRow` (lines 217-232) is deleted — its two callers are inlined below with the design's exact `TARGET` / `PACE` treatment.

```kotlin
@Composable
private fun ExpandedRow(
    ride: PlanRide,
    complete: Boolean,
    units: UnitSystem,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Cyan, RectangleShape)
            .background(Cyan.copy(alpha = 0.06f), RectangleShape)
            .padding(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "WK ${ride.week} · RIDE ${ride.slot} - ${ride.kind}",
                color = Cyan,
                style = MaterialTheme.typography.titleLarge
                    .copy(fontSize = 13.sp, letterSpacing = 0.5.sp)
                    .glow(Cyan, blurRadius = 5f),
            )
            if (complete) {
                Text(
                    "✓ DONE",
                    color = Cyan,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        HairLine(color = BorderCyanDim)
        Spacer(Modifier.height(9.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "TARGET",
                color = Muted,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
            )
            Text(
                formatPlanDistance(ride.targetMiles, units),
                color = Cyan,
                style = MaterialTheme.typography.titleLarge
                    .copy(fontSize = 26.sp, letterSpacing = 0.sp)
                    .glow(Cyan, blurRadius = 6f),
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "PACE",
                color = Muted,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
            )
            Text(
                ride.pace.name.lowercase(),
                color = Cyan,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
            )
        }

        if (ride.guidance.isNotBlank()) {
            Text(
                ride.guidance,
                color = Muted,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp, lineHeight = 18.sp),
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (ride.longRide) {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .background(Cyan, RectangleShape)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    "★ LONG RIDE",
                    color = Background,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 0.6.sp),
                )
            }
        }
    }
}
```

- [ ] **Step 5: Verify — compile**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Verify — on-device visual checklist (Plan picker)**

1. The header reads `◀ 14-WEEK PLAN` — grey chevron, cyan Orbitron title — and tapping it still goes back.
2. **Zero rounded corners:** status boxes are squares, the expanded card is a sharp 1px cyan rectangle, `★ LONG RIDE` is a sharp filled cyan chip, `START RIDE` is a sharp bordered rectangle.
3. Completed rides show a filled cyan square with a black `✓`; incomplete rides show an empty 1.5px outlined square.
4. Phase headers read `> PHASE 1 · BUILD THE BASE` in grey mono; week headers are dim `#4A4A4A`.
5. The expanded card shows a hairline rule above `TARGET`, and the target value is large Orbitron with a glow.
6. Tapping a collapsed row still expands it and collapses the previous one; `START RIDE` still starts the expanded ride.
7. The screen stays cyan regardless of the accent theme.

- [ ] **Final step: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/PlanPickerScreen.kt
git commit -m "feat(ui): re-skin Plan picker screen

Square status boxes, sharp hairline expanded card, > PHASE headers, TerminalButton
START RIDE, fixed cyan. Removes every RoundedCornerShape and CircleShape.
No behavior change."
```

---

### Task 11: Re-skin End screen

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/EndScreen.kt`

**Interfaces:**
- Consumes: `TerminalButton`, `TerminalButtonStyle`, `glow` (Task 9); `Background`, `Muted`, `Warn`, and `LocalAccent` (Task 2 — End is a ride-family screen and **keeps** the user's accent); `RidemanTypography` (Task 1).
- Produces: nothing new. `EndScreen(summary, units, planRide, tolerancePercent, onDone)` is unchanged, and the private `formatDuration` (lines 155-162) stays verbatim.

**Shape / color audit for this file — every occurrence:**

| Line | Current | Replacement |
|---|---|---|
| 15 | `import androidx.compose.foundation.shape.RoundedCornerShape` | replace with `RectangleShape` |
| 125 | banner: `.clip(RoundedCornerShape(12.dp))` | delete the clip — the banner is a sharp `Box` |
| 107 | `val amber = Color(0xFFFFCF3A)` | delete — use the `Warn` token from Task 2 |
| 89 | Material `Button` (rounded pill by default) | `TerminalButton` |

No emoji in this file: `✓` and `→` are plain glyphs and stay.

- [ ] **Step 1: Replace the import block (lines 1-37)**

```kotlin
package com.two17industries.rideman.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.PlanGrading
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.core.RideSummary
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.components.TerminalButtonStyle
import com.two17industries.rideman.ui.components.glow
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.LocalAccent
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.Warn
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
```

- [ ] **Step 2: Replace the `EndScreen` body (lines 39-96)**

The `stats` `buildList` (lines 63-73), the free-ride `DISTANCE` branch, and the `landscape` detection keep their exact current logic. Only the layout changes: landscape becomes a **single 4-up row** (it was `stats.chunked(2)`), and portrait becomes a hairline-gridded stack.

```kotlin
@Composable
fun EndScreen(
    summary: RideSummary,
    units: UnitSystem,
    planRide: PlanRide?,
    tolerancePercent: Int,
    onDone: () -> Unit,
) {
    val accent = LocalAccent.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                "RIDE COMPLETE",
                color = accent,
                style = MaterialTheme.typography.titleLarge
                    .copy(fontSize = 20.sp, letterSpacing = 1.2.sp)
                    .glow(accent, blurRadius = 6f),
            )

            if (planRide != null) {
                PlanResult(summary, planRide, tolerancePercent, units, accent)
            }

            val stats = buildList {
                add("TIME" to formatDuration(summary.totalTimeMs))
                if (planRide == null) {
                    add("DISTANCE" to
                        "${String.format(Locale.US, "%.2f", Units.distance(summary.distanceM, units))} ${Units.distanceLabel(units)}")
                }
                add("MAX SPEED" to
                    "${Units.speed(summary.maxSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}")
                add("AVG SPEED" to
                    "${Units.speed(summary.avgSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}")
            }

            val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val gridLine = accent.copy(alpha = 0.12f)
            if (landscape) {
                // Single 4-up row. The 1dp gaps sit on a grid-line-coloured background, so the
                // dividers are the background showing through — no per-cell borders.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, gridLine, RectangleShape)
                        .background(gridLine, RectangleShape),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    stats.forEach { (label, value) -> StatCell(label, value, accent, Modifier.weight(1f)) }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(1.dp, gridLine, RectangleShape)
                        .background(gridLine, RectangleShape),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    stats.forEach { (label, value) -> StatRow(label, value, accent) }
                }
            }

            TerminalButton(
                text = "DONE",
                onClick = onDone,
                style = TerminalButtonStyle.PRIMARY,
                accent = accent,
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            )
        }
    }
}
```

- [ ] **Step 3: Replace `PlanResult`, and replace `Stat` with `StatRow` + `StatCell` (lines 98-153)**

`PlanGrading.isMet(...)` and the `shortBy` arithmetic are untouched.

```kotlin
@Composable
private fun PlanResult(
    summary: RideSummary,
    planRide: PlanRide,
    tolerancePercent: Int,
    units: UnitSystem,
    accent: Color,
) {
    val met = PlanGrading.isMet(planRide, summary.distanceM, tolerancePercent)
    val label = Units.distanceLabel(units)
    val targetDisplay = Units.distance(planRide.targetMiles * PlanGrading.METERS_PER_MILE, units)
    val actualDisplay = Units.distance(summary.distanceM, units)
    val shortBy = targetDisplay - actualDisplay

    Text(
        "Week ${planRide.week} · Ride ${planRide.slot} — ${planRide.kind}",
        color = Muted,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
    )

    val bannerText = if (met) "✓ TARGET MET · SLOT COMPLETE"
        else "LOGGED · ${String.format(Locale.US, "%.2f", abs(shortBy))} $label SHORT — SLOT STAYS OPEN"

    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (met) Modifier.background(accent, RectangleShape)
                else Modifier
                    .border(1.dp, Warn, RectangleShape)
                    .background(Warn.copy(alpha = 0.18f), RectangleShape)
            )
            .padding(13.dp),
    ) {
        Text(
            bannerText,
            color = if (met) Background else Warn,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp, letterSpacing = 0.7.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "DISTANCE",
            color = Muted,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 1.4.sp),
        )
        Text(
            "target ${String.format(Locale.US, "%.1f", targetDisplay)} $label  →  " +
                "${String.format(Locale.US, "%.2f", actualDisplay)} $label",
            color = if (met) accent else Warn,
            style = MaterialTheme.typography.titleLarge
                .copy(fontSize = 17.sp, letterSpacing = 0.4.sp)
                .glow(if (met) accent else Warn, blurRadius = 5f),
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
private fun StatRow(label: String, value: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Background, RectangleShape)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Muted,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 1.3.sp),
            maxLines = 1,
        )
        Text(
            value,
            color = accent,
            style = MaterialTheme.typography.titleLarge
                .copy(fontSize = 20.sp, letterSpacing = 0.sp)
                .glow(accent, blurRadius = 5f),
            maxLines = 1,
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Background, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            color = Muted,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
            maxLines = 1,
        )
        Text(
            value,
            color = accent,
            style = MaterialTheme.typography.titleLarge
                .copy(fontSize = 24.sp, letterSpacing = 0.sp)
                .glow(accent, blurRadius = 5f),
            maxLines = 1,
        )
    }
}
```

- [ ] **Step 4: Verify — compile**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Verify — on-device visual checklist (End)**

1. Finish a **plan** ride that meets its target: the banner is a solid accent-filled sharp rectangle with near-black text reading `✓ TARGET MET · SLOT COMPLETE`. No rounded corners.
2. Finish a plan ride that comes up short: the banner is amber `#FFCF3A` — 1px border, 18% fill, amber text — and the `target x.x → y.yy` line is amber too.
3. Finish a **free** ride: no banner, and the stats stack shows four rows (TIME / DISTANCE / MAX SPEED / AVG SPEED).
4. Portrait stats are a hairline-gridded stack — 1px accent-tinted lines between rows, grey mono labels on the left, glowing Orbitron values on the right.
5. Rotate to landscape: the stats become a **single 4-up row** of centred cells separated by 1px lines, not two rows of two.
6. `DONE` is a sharp bordered rectangle with a faint accent fill, and still returns to Start.
7. Change the accent theme, then finish a ride: **this screen recolors to the chosen accent** — it is one of the two screens that honor `LocalAccent`.

- [ ] **Final step: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/EndScreen.kt
git commit -m "feat(ui): re-skin End screen

Sharp graded banner (accent fill when met, amber outline when short), hairline stat
grid, a single 4-up row in landscape, TerminalButton DONE. Keeps LocalAccent.
Removes RoundedCornerShape and the local amber literal. No behavior change."
```

---

### Task 12: Re-skin History screen

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/HistoryScreen.kt`

**Interfaces:**
- Consumes: `BackLabel`, `PromptLabel`, `CheckSquare`, `HairLine`, `glow` (Task 9); `Background`, `Surface1`, `Cyan`, `Muted`, `Dim`, `TextPrimary`, `Warn`, `Delete`, `BorderCyanDim` (Task 2); `RidemanTypography` (Task 1); `formatPlanDistance` (StartScreen.kt).
- Produces: nothing new. `HistoryScreen(rides, plan, progress, units, onBack, onRetryUpload, onOpenActivity, stravaConnected, onBackfill, onDeleteRides)` is unchanged, and `HISTORY_DATE_FMT`, `formatDate` and `formatHistoryDuration` (lines 325-336) stay verbatim.

**Emoji audit for this file — every occurrence:**

| Line | Current | Replacement | Why |
|---|---|---|---|
| 129 | `"🗑 DELETE"` | `"✕ DELETE"` | 🗑 U+1F5D1 — spec: trash becomes `✕` |
| 142 | `"⭱ Upload past rides to Strava"` | `"↑ Upload past rides to Strava"` | ⭱ U+2B71 — spec: arrows become `↑` |
| 265 | `"🗑 DELETE RIDE"` | `"✕ DELETE RIDE"` | 🗑 U+1F5D1 |
| 289 | `"⏳ Queued"` | `"QUEUED"` | **Extra find, not in the spec's list.** U+23F3 has `Emoji_Presentation=Yes`, so Android renders it as a full-colour emoji, and it has no text-presentation form — so the glyph is dropped rather than substituted. |
| 231 | `if (checked) "☑" else "☐"` | `CheckSquare(checked = checked, ...)` | Ballot-box glyphs replaced by the design's square status box |
| 292 | `"⚠ Retry"` | `"⚠ RETRY"` — kept | U+26A0 is `Emoji_Presentation=No`: it renders as a monochrome text glyph that takes its `color`, so it is not an emoji render. Recoloured to `Warn`. |

**Shape audit for this file — every occurrence:**

| Line | Current | Replacement |
|---|---|---|
| 15 | `import androidx.compose.foundation.shape.RoundedCornerShape` | replace with `RectangleShape` |
| 184 | `ProgressHeader`: `.clip(RoundedCornerShape(10.dp))` | delete — sharp bordered panel |
| 221 | `RideRow`: `.clip(RoundedCornerShape(if (expanded) 12.dp else 10.dp))` | delete — sharp rectangle |
| 363 | `AlertDialog` has **no explicit shape**, so it inherits Material's rounded 28dp container | add `shape = RectangleShape` |

- [ ] **Step 1: Replace the import block (lines 1-42)**

```kotlin
package com.two17industries.rideman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.AlertDialog
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
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanAttempt
import com.two17industries.rideman.core.PlanGrading
import com.two17industries.rideman.core.PlanProgress
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.core.slotsUncompletedBy
import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.data.StravaUploadState
import com.two17industries.rideman.ui.components.BackLabel
import com.two17industries.rideman.ui.components.CheckSquare
import com.two17industries.rideman.ui.components.HairLine
import com.two17industries.rideman.ui.components.PromptLabel
import com.two17industries.rideman.ui.components.glow
import com.two17industries.rideman.ui.theme.BorderCyanDim
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.Delete
import com.two17industries.rideman.ui.theme.Dim
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.Surface1
import com.two17industries.rideman.ui.theme.TextPrimary
import com.two17industries.rideman.ui.theme.Warn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
```

- [ ] **Step 2: Replace the `HistoryScreen` body (lines 44-177)**

Every piece of state (`expandedId`, `pendingDelete`, `selectionMode`, `selected`), `exitSelection()`, the select-all toggle and the `onToggle` branch keep byte-identical logic. Only colours, styles, glyphs and the accent source change.

```kotlin
@Composable
fun HistoryScreen(
    rides: List<RideEntity>,
    plan: Plan?,
    progress: PlanProgress?,
    units: UnitSystem,
    onBack: () -> Unit,
    onRetryUpload: (Long) -> Unit,
    onOpenActivity: (Long) -> Unit,
    stravaConnected: Boolean,
    onBackfill: () -> Unit,
    onDeleteRides: (List<Long>) -> Unit,
) {
    var expandedId by remember { mutableStateOf<Long?>(null) }

    // Rides staged for deletion; non-empty means the confirm dialog is showing.
    var pendingDelete by remember { mutableStateOf<List<RideEntity>>(emptyList()) }

    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    if (pendingDelete.isNotEmpty()) {
        ConfirmDeleteDialog(
            rides = pendingDelete,
            allRides = rides,
            plan = plan,
            units = units,
            onDismiss = { pendingDelete = emptyList() },
            onConfirm = {
                onDeleteRides(pendingDelete.map { it.id })
                pendingDelete = emptyList()
                exitSelection()
            },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp)) {
        if (!selectionMode) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackLabel("HISTORY", modifier = Modifier.clickable(onClick = onBack))
                if (rides.isNotEmpty()) {
                    Text(
                        "SELECT",
                        color = Cyan,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
                        modifier = Modifier.clickable { selectionMode = true },
                    )
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackLabel("CANCEL", modifier = Modifier.clickable { exitSelection() }, fontSize = 15.sp)
                Text(
                    "${selected.size} selected",
                    color = Muted,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                )
                Text(
                    if (selected.size == rides.size) "NONE" else "ALL",
                    color = Cyan,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
                    modifier = Modifier.clickable {
                        selected = if (selected.size == rides.size) emptySet() else rides.map { it.id }.toSet()
                    },
                )
                Text(
                    "✕ DELETE",
                    color = if (selected.isEmpty()) Delete.copy(alpha = 0.35f) else Delete,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 1.2.sp),
                    modifier = Modifier.clickable(enabled = selected.isNotEmpty()) {
                        pendingDelete = rides.filter { it.id in selected }
                    },
                )
            }
        }

        if (stravaConnected) {
            Text(
                "↑ Upload past rides to Strava",
                color = Cyan.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp),
                modifier = Modifier.clickable(onClick = onBackfill).padding(bottom = 10.dp),
            )
        }

        if (progress != null) {
            ProgressHeader(progress)
            Spacer(Modifier.height(12.dp))
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(rides, key = { it.id }) { ride ->
                RideRow(
                    ride = ride,
                    plan = plan,
                    units = units,
                    selectionMode = selectionMode,
                    checked = ride.id in selected,
                    expanded = !selectionMode && ride.id == expandedId,
                    onToggle = {
                        if (selectionMode) {
                            selected = if (ride.id in selected) selected - ride.id else selected + ride.id
                        } else {
                            expandedId = if (expandedId == ride.id) null else ride.id
                        }
                    },
                    onDelete = { pendingDelete = listOf(ride) },
                    onRetryUpload = onRetryUpload,
                    onOpenActivity = onOpenActivity,
                )
            }
        }
    }
}
```

- [ ] **Step 3: Replace `ProgressHeader` (lines 179-197)**

```kotlin
@Composable
private fun ProgressHeader(progress: PlanProgress) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, BorderCyanDim, RectangleShape)
            .background(Cyan.copy(alpha = 0.05f), RectangleShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        PromptLabel("PLAN PROGRESS", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(
            "${progress.completedCount()} / ${progress.total} rides",
            color = Cyan,
            style = MaterialTheme.typography.titleLarge
                .copy(fontSize = 24.sp, letterSpacing = 0.sp)
                .glow(Cyan, blurRadius = 6f),
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}
```

- [ ] **Step 4: Replace `RideRow`, `StravaChip` and `DetailLine` (lines 199-323)**

Collapsed rows sit on `Surface1` (`#151515`) with sharp corners; the expanded row becomes the design's hairline cyan card. The `planRide` lookup, the `PlanGrading.isMet` call and the free-ride-only `max speed` line are unchanged.

```kotlin
@Composable
private fun RideRow(
    ride: RideEntity,
    plan: Plan?,
    units: UnitSystem,
    selectionMode: Boolean,
    checked: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onRetryUpload: (Long) -> Unit,
    onOpenActivity: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    val planRide = ride.planRideId?.let { plan?.byId?.get(it) }
    val dist = String.format(Locale.US, "%.1f", Units.distance(ride.distanceM, units))
    val distLabel = Units.distanceLabel(units).lowercase()

    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (expanded) Modifier
                    .border(1.dp, Cyan.copy(alpha = 0.35f), RectangleShape)
                    .background(Cyan.copy(alpha = 0.06f), RectangleShape)
                else Modifier.background(Surface1, RectangleShape)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectionMode) {
                        CheckSquare(checked = checked, accent = Cyan, size = 16.dp, glyphSize = 11.sp)
                    }
                    Text(
                        formatDate(ride.startedAt),
                        color = Muted,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 11.sp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StravaChip(
                        state = ride.stravaState,
                        activityId = ride.stravaActivityId,
                        onRetry = { onRetryUpload(ride.id) },
                        onOpenActivity = onOpenActivity,
                    )
                    Text(
                        if (planRide != null) "PLAN" else "FREE",
                        color = if (planRide != null) Cyan else Dim,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.7.sp),
                    )
                }
            }

            val main = if (planRide != null)
                "Wk ${planRide.week} · Ride ${planRide.slot} - $dist $distLabel"
            else "$dist $distLabel"
            Text(
                main,
                color = if (expanded) Cyan else TextPrimary,
                style = MaterialTheme.typography.titleLarge
                    .copy(fontSize = 15.sp, letterSpacing = 0.3.sp)
                    .let { if (expanded) it.glow(Cyan, blurRadius = 5f) else it },
                modifier = Modifier.padding(top = 8.dp),
            )

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HairLine(color = BorderCyanDim)
                if (planRide != null) {
                    val met = PlanGrading.isMet(planRide, ride.distanceM, plan!!.tolerancePercent)
                    DetailLine(
                        "target",
                        "${formatPlanDistance(planRide.targetMiles, units)}  " + if (met) "✓ met" else "✕ short",
                        met,
                    )
                }
                DetailLine("time", formatHistoryDuration(ride.totalTimeMs), null)
                DetailLine("avg speed", "${Units.speed(ride.avgSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}", null)
                if (planRide == null) {
                    DetailLine("max speed", "${Units.speed(ride.maxSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}", null)
                }
                Text(
                    "✕ DELETE RIDE",
                    color = Delete,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 0.8.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clickable(onClick = onDelete),
                )
            }
        }
    }
}

@Composable
private fun StravaChip(
    state: StravaUploadState,
    activityId: Long?,
    onRetry: () -> Unit,
    onOpenActivity: (Long) -> Unit,
) {
    // No emoji: the hourglass (U+23F3) renders in colour on Android and has no text-presentation
    // form, so it is dropped outright rather than substituted.
    val label = when (state) {
        StravaUploadState.NONE -> return
        StravaUploadState.QUEUED -> "QUEUED"
        StravaUploadState.UPLOADING -> "↑ UPLOADING"
        StravaUploadState.UPLOADED -> "✓ Strava"
        StravaUploadState.FAILED -> "⚠ RETRY"
    }
    val clickModifier = when {
        state == StravaUploadState.FAILED -> Modifier.clickable(onClick = onRetry)
        state == StravaUploadState.UPLOADED && activityId != null ->
            Modifier.clickable { onOpenActivity(activityId) }
        else -> Modifier
    }
    Text(
        label,
        color = when (state) {
            StravaUploadState.FAILED -> Warn
            StravaUploadState.QUEUED -> Muted
            else -> Cyan
        },
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 0.6.sp),
        modifier = clickModifier,
    )
}

@Composable
private fun DetailLine(label: String, value: String, met: Boolean?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp))
        Text(
            value,
            color = when (met) { true -> Cyan; false -> Warn; null -> TextPrimary },
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, letterSpacing = 0.sp),
        )
    }
}
```

- [ ] **Step 5: Replace `ConfirmDeleteDialog` (lines 338-406)**

Drops the `accent` parameter (fixed cyan), forces `RectangleShape` on the Material dialog container, and moves the two amber literals onto the `Warn` token. The `slotsUncompletedBy` / `onStrava` computation is unchanged.

```kotlin
@Composable
private fun ConfirmDeleteDialog(
    rides: List<RideEntity>,
    allRides: List<RideEntity>,
    plan: Plan?,
    units: UnitSystem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val ids = rides.map { it.id }.toSet()

    // Strava keeps its copy — their v3 API has no delete endpoint, so we cannot remove it.
    val onStrava = rides.count { it.stravaState == StravaUploadState.UPLOADED }

    // Only warn when a slot actually regresses; a duplicate attempt changes nothing.
    val uncompleted = plan?.let { p ->
        val attempts = allRides.mapNotNull { r ->
            r.planRideId?.let { PlanAttempt(r.id, it, r.distanceM) }
        }
        slotsUncompletedBy(p, attempts, ids)
    }.orEmpty()

    val title = if (rides.size == 1) "Delete this ride?" else "Delete ${rides.size} rides?"

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        containerColor = Surface1,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, letterSpacing = 0.5.sp))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (rides.size == 1) {
                    val r = rides.first()
                    val dist = String.format(Locale.US, "%.1f", Units.distance(r.distanceM, units))
                    Text(
                        "${formatDate(r.startedAt)} · $dist ${Units.distanceLabel(units).lowercase()}",
                        color = Muted,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    )
                }
                Text(
                    "This also deletes the GPS track. It cannot be undone.",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                )
                if (onStrava > 0) {
                    Text(
                        if (rides.size == 1) "This ride is on Strava. Deleting here will NOT remove it from Strava."
                        else "$onStrava of these are on Strava. Deleting here will NOT remove them from Strava.",
                        color = Warn,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    )
                }
                if (uncompleted.isNotEmpty()) {
                    val slots = uncompleted.joinToString(", ") { "Wk ${it.week} · Ride ${it.slot}" }
                    Text(
                        "This will mark $slots incomplete again.",
                        color = Warn,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                    )
                }
            }
        },
        confirmButton = {
            Text(
                "✕ DELETE",
                color = Delete,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, letterSpacing = 1.sp),
                modifier = Modifier.clickable(onClick = onConfirm).padding(12.dp),
            )
        },
        dismissButton = {
            Text(
                "CANCEL",
                color = Cyan,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, letterSpacing = 1.sp),
                modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp),
            )
        },
    )
}
```

- [ ] **Step 6: Verify — compile**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Verify — on-device visual checklist (History)**

1. The header reads `◀ HISTORY` with `SELECT` on the right. **No rounded corners anywhere on this screen, including the confirm dialog** — its container is a sharp `#151515` rectangle, not a 28dp-rounded one.
2. With Strava connected, the backfill line reads `↑ Upload past rides to Strava` — **no ⭱**.
3. The `> PLAN PROGRESS` panel is a sharp 1px-bordered box with a big glowing Orbitron `n / m rides`.
4. Tap a ride to expand it: a sharp cyan hairline card, a hairline rule above the detail lines, and a red `#FF5252` `✕ DELETE RIDE` — **no 🗑**.
5. Tap `SELECT`: each row shows a small **square** checkbox (filled cyan with a black `✓` when selected) — no `☑` / `☐` glyphs — and the top bar's delete reads `✕ DELETE` in red, dimmed until something is selected.
6. Strava chips render monochrome: `QUEUED` in grey (**no ⏳**), `↑ UPLOADING`, `✓ Strava`, `⚠ RETRY` in amber. **No colour emoji anywhere on the screen.**
7. Deleting still works end to end: single-row delete, multi-select delete, the Strava and plan-regression warnings still appear in amber, and Cancel dismisses.
8. The screen stays cyan regardless of the accent theme.

- [ ] **Final step: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/HistoryScreen.kt
git commit -m "feat(ui): re-skin History screen

Sharp rows and dialog, > PLAN PROGRESS panel, square selection boxes, fixed cyan.
Removes every emoji: 🗑 -> ✕ (x2), ⭱ -> ↑, ⏳ dropped, ☑/☐ -> square CheckSquare.
No behavior change."
```

---

### Task 13: Re-skin Settings screen

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/SettingsScreen.kt`

**Interfaces:**
- Consumes: `TerminalButton`, `TerminalButtonStyle`, `PromptLabel`, `CheckSquare`, `glow` (Task 9); `Background`, `Cyan`, `Muted`, `Dim`, `TextPrimary`, and `accentFor` (Task 2 — used **only** for the four swatch fills); `RidemanTypography` (Task 1).
- Produces: nothing new. `SettingsScreen(current, stravaConnected, stravaAthleteName, onConnectStrava, onDisconnectStrava, onSave, onDone, onCancel)` is unchanged.

**Boundary with Task 4:** `private fun screenName(screen: RideScreen)` (lines 416-422) is **owned by Task 4**, which adds the `RideScreen.GRID -> "Dash"` arm. **Do not touch it in this task** — just call it.

**Accent scope:** line 79 currently sets `val accent = accentFor(theme)`, so the whole screen previews the picked accent live. Per the accent scope from Task 2, all chrome becomes fixed `Cyan`; only the four colour swatches keep their own `accentFor(opt)` fills. The `theme` state and the `onSave` payload are untouched.

**Shape audit for this file — every occurrence:**

| Line | Current | Replacement |
|---|---|---|
| 21 | `import androidx.compose.foundation.shape.CircleShape` | delete the import |
| 22 | `import androidx.compose.foundation.shape.RoundedCornerShape` | replace with `RectangleShape` |
| 104-105 | CANCEL: `.clip(RoundedCornerShape(50))` + `.border(2.dp, accent, RoundedCornerShape(50))` | sharp 1px `Box` |
| 200-201 | DISCONNECT pill | `TerminalButton` (SECONDARY) |
| 208 | CONNECT TO STRAVA pill: `.clip(RoundedCornerShape(50))` | `TerminalButton` (PRIMARY) |
| 292 / 294 | `OptionPill`: `.clip(RoundedCornerShape(50))` + `.border(2.dp, accent, RoundedCornerShape(50))` | sharp segmented cell |
| 314 / 319 | `ColorPill`: `.clip(RoundedCornerShape(50))` + `shape = RoundedCornerShape(50)` | sharp swatch, 2dp white ring when selected |
| 353 | `StepButton`: `.clip(CircleShape)` plus `.background(accent)` | sharp 52dp outlined square, no fill |
| 381 / 383 | `OrderRow` toggle: `.clip(RoundedCornerShape(10.dp))` + `.border(2.dp, accent, RoundedCornerShape(10.dp))` | `CheckSquare` at 34dp |
| 407 / 408 | `ArrowButton`: `.clip(RoundedCornerShape(10.dp))` + `.border(2.dp, tint, RoundedCornerShape(10.dp))` | sharp 38dp outlined square |
| 249 | `Button` SAVE — Material default pill | `TerminalButton` (PRIMARY) |

No emoji in this file: `✓`, `▲`, `▼`, `−` and `+` are plain glyphs and stay.

- [ ] **Step 1: Replace the import block (lines 1-50) and the top of the composable (lines 52-111)**

```kotlin
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
import androidx.compose.foundation.shape.RectangleShape
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
import com.two17industries.rideman.core.Cadence
import com.two17industries.rideman.core.CadenceMode
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.dash.DashConnectionState
import com.two17industries.rideman.dash.DashStatus
import com.two17industries.rideman.data.RideScreen
import com.two17industries.rideman.data.RidemanSettings
import com.two17industries.rideman.data.ThemeChoice
import com.two17industries.rideman.ui.components.CheckSquare
import com.two17industries.rideman.ui.components.PromptLabel
import com.two17industries.rideman.ui.components.TerminalButton
import com.two17industries.rideman.ui.components.TerminalButtonStyle
import com.two17industries.rideman.ui.components.glow
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.Cyan
import com.two17industries.rideman.ui.theme.Dim
import com.two17industries.rideman.ui.theme.Muted
import com.two17industries.rideman.ui.theme.TextPrimary
import com.two17industries.rideman.ui.theme.accentFor

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
```

- [ ] **Step 2: Replace the section bodies (lines 113-268)**

Same state writes, same `onSave` payload — including the `order.ifEmpty { RideScreen.entries.toList() }` guard. Only chrome changes. The `COLOR THEME` and `RIDE SCREENS` hints move into their section headers, per the design.

```kotlin
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
                    DashConnectionState.CONNECTED -> "Connected"
                    DashConnectionState.SCANNING -> "Searching…"
                    DashConnectionState.DISCONNECTED -> "Disconnected"
                    DashConnectionState.DISABLED -> "Idle (starts with your ride)"
                }
                Text(label, color = Muted, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp))
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
                val order = screenItems.filter { it.second }.map { it.first }
                onSave(
                    current.copy(
                        units = units,
                        cadenceMode = cadenceMode,
                        targetRpm = targetRpm,
                        theme = theme,
                        screenOrder = order.ifEmpty { RideScreen.entries.toList() },
                        stravaUploadEnabled = stravaUploadEnabled,
                        dashEnabled = dashEnabled,
                    )
                )
                onDone()
            },
            style = TerminalButtonStyle.PRIMARY,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
```

- [ ] **Step 3: Replace the private helpers (lines 271-414) — `Section`, `PillRow`, `OptionPill`, `ColorPill`, `Stepper`, `StepButton`, `OrderRow`, `ArrowButton`**

`screenName` (lines 416-422, owned by Task 4) and `themeName` (lines 424-429) are left exactly as they are.

```kotlin
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
private fun Stepper(value: Int, onDec: () -> Unit, onInc: () -> Unit) {
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
                "TARGET RPM",
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
            size = 34.dp,
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
            .size(38.dp)
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
```

- [ ] **Step 4: Verify — compile**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Verify — on-device visual checklist (Settings)**

1. **Zero rounded corners anywhere on this screen** — specifically: CANCEL, the UNITS / CADENCE PULSE / On-Off segmented cells, the `−` and `+` stepper buttons (**now outlined squares with no fill**, not filled circles), the four colour swatches, the RIDE SCREENS toggle boxes, the `▲` / `▼` arrow buttons, DISCONNECT / CONNECT TO STRAVA, and SAVE.
2. Section headers read `> UNITS`, `> CADENCE PULSE`, `> COLOR THEME · drives ride display`, `> HANDLEBAR DASHBOARD`, `> STRAVA`, `> RIDE SCREENS · tap to enable · arrows reorder` — grey `>` prefix, cyan label, dim `#4A4A4A` hint.
3. All chrome is cyan and **stays cyan when you tap a different colour swatch** — only the white 2dp ring moves. Then confirm the accent still takes effect: save, start a ride, and see the ride and end screens in the chosen colour.
4. The selected swatch has a crisp 2px white ring; the other three have none.
5. RIDE SCREENS rows still toggle on and off and still reorder with the arrows; the first row's `▲` and the last row's `▼` are dimmed and inert.
6. `SAVE` still persists everything (units, cadence mode, target RPM, theme, screen order, Strava auto-upload, dash) and returns to Start; `CANCEL` still discards.

- [ ] **Final step: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/SettingsScreen.kt
git commit -m "feat(ui): re-skin Settings screen

Sharp segmented controls, square -/+ stepper, sharp accent swatches with a 2px
white selected ring, > section headers, TerminalButton for Strava and SAVE.
Chrome is fixed cyan; the accent previews only in the swatches. Removes every
RoundedCornerShape and CircleShape. No behavior change; screenName() untouched."
```

---

### Task 14: Re-skin Backfill screen

The design spec's re-skin list names five screens, but `ui/BackfillScreen.kt` is a sixth — reached from History's "Upload past rides to Strava" line (`Dest.BACKFILL` in `Nav.kt`). Left alone it would be the one screen still wearing the old look: a rounded card list, a rounded Material button, and `LocalAccent` on a menu screen.

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/BackfillScreen.kt`

**Interfaces:**
- Consumes: `TerminalButton`, `TerminalButtonStyle`, `BackLabel`, `CheckSquare` (Task 9); `Surface1`, `Cyan`, `Muted`, `TextPrimary` (Task 2); `RidemanTypography` (Task 1).
- Produces: nothing new. `BackfillScreen(rides, units, onUpload, onDone)` is unchanged.

**Shape / accent audit for this file — every occurrence:**

| Line | Current | Replacement |
|---|---|---|
| 16 | `import androidx.compose.foundation.shape.RoundedCornerShape` | replace with `RectangleShape` |
| 48 | `val accent = LocalAccent.current` | delete — this is a menu screen, so it uses the fixed `Cyan` |
| 67 | row: `.clip(RoundedCornerShape(10.dp))` | delete — sharp rectangle |
| 84 | Material `Button` (rounded pill by default) | `TerminalButton` (PRIMARY) |

No emoji in this file — `◀` is a plain glyph and stays.

**Behavior that must not change:** the `eligible` filter (`stravaState != UPLOADED`), the `selected` set toggle, `enabled = selected.isNotEmpty()`, and `onUpload(selected.toList()); onDone()` on tap.

- [ ] **Step 1: Rewrite `BackfillScreen.kt` (replace the whole file, lines 1-92)**

```kotlin
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
import androidx.compose.foundation.shape.RectangleShape
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
```

The `↑` on the upload button matches History's `↑ Upload past rides to Strava` entry point — the same arrow the spec substitutes for `⭱`. The button label still carries the live `selected.size`, so the count still updates as you tap rows.

- [ ] **Step 2: Verify — compile**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Verify — on-device visual checklist (Backfill)**

1. From History (with Strava connected), tap `↑ Upload past rides to Strava` — the Backfill screen opens with the header `◀ UPLOAD PAST RIDES` (grey chevron, cyan Orbitron title), and tapping it still returns to History.
2. **No rounded corners anywhere:** the ride rows are sharp `#151515` rectangles and the upload button is a sharp bordered rectangle with a faint cyan fill.
3. Each row carries a **square** checkbox on the left — filled cyan with a black `✓` when selected; the row's background lifts to a faint cyan tint when selected.
4. **Only rides that are not already on Strava are listed** — upload one, come back, and it is gone from the list.
5. The button reads `↑ UPLOAD 0 RIDE(S)` and is dimmed/untappable with nothing selected; the count updates live as rows are tapped.
6. Tapping the button with rides selected still uploads them and returns to History.
7. The screen stays cyan regardless of the accent theme.

- [ ] **Final step: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/BackfillScreen.kt
git commit -m "feat(ui): re-skin Backfill screen

The sixth menu screen, missing from the spec's re-skin list. Sharp rows and
button, square selection boxes, ◀ back label, fixed cyan instead of LocalAccent.
Removes the last RoundedCornerShape in the app. No behavior change."
```
