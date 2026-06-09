# Portrait + Landscape Orientation Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the app rotate freely between portrait and landscape everywhere, while freezing orientation during an active ride.

**Architecture:** Unlock the activity in the manifest (`fullUser`). Drive the during-ride orientation lock from the Activity using the existing `onRideActiveChanged` signal that already flows from `Nav` (the same hook that controls keep-screen-on). Make `RideScreen` branch its outer layout on `LocalConfiguration` orientation (stacked bottom bar in portrait, slim side rail in landscape), extracting the paginator dots and END control into reusable pieces. Add scroll safety to `StartScreen` and `EndScreen`, and give `EndScreen` a responsive 2-wide stat layout in landscape.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Android `ActivityInfo` orientation flags, Gradle.

---

## Note on Testing

This feature is UI layout + orientation policy. It introduces **no new pure/business logic**, and the project has no Compose UI-test harness (existing tests are JVM unit tests over `core/` and `data/` only). Manufacturing UI assertions here would be low-value theater. Instead, every task verifies by:

- **Build green:** `./gradlew :app:assembleDebug`
- **Existing suite green** (where behavior could regress): `./gradlew :app:testDebugUnitTest`
- **Manual checklist** (final task) on a device/emulator.

If any task's change is larger than expected or you find extractable pure logic, add a focused unit test for that logic — but do not add fake tests for layout.

---

## File Structure

| File | Responsibility | Change |
|------|----------------|--------|
| `app/src/main/AndroidManifest.xml` | App/activity config | `screenOrientation` `portrait` → `fullUser` |
| `app/src/main/java/com/two17industries/rideman/MainActivity.kt` | Hosts Compose, owns window/activity flags | Extend the ride-active hook to lock/unlock orientation |
| `app/src/main/java/com/two17industries/rideman/ui/ride/RideScreen.kt` | Ride pager + controls | Orientation-branched layout; extract `RidePager`, `PaginatorDots`, `SideRail`; refactor `BottomBar` |
| `app/src/main/java/com/two17industries/rideman/ui/StartScreen.kt` | Landing screen | Wrap content in `verticalScroll` |
| `app/src/main/java/com/two17industries/rideman/ui/EndScreen.kt` | Ride summary | `verticalScroll` + responsive stat layout (column / 2-wide rows) |

`BigMetric`, the individual metric screens, `Nav.kt`, and all `core/`/`data/` code are unchanged.

---

## Task 1: Unlock orientation in the manifest

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Change the activity's screen orientation**

In `app/src/main/AndroidManifest.xml`, find the `MainActivity` `<activity>` element and replace the orientation line.

Replace:

```xml
            android:screenOrientation="portrait"
```

With:

```xml
            android:screenOrientation="fullUser"
```

(`fullUser` permits every orientation the user's system auto-rotate setting allows, and respects their device rotation-lock toggle.)

- [ ] **Step 2: Build to verify the manifest is valid**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: unlock activity orientation (portrait -> fullUser)"
```

---

## Task 2: Lock orientation during an active ride

The Activity already receives a `Boolean` ride-active signal via `RidemanNav(onRideActiveChanged = ...)` (see `Nav.kt:22`, `LaunchedEffect(dest) { onRideActiveChanged(dest == Dest.RIDE) }`). It currently drives keep-screen-on. Extend that one method to also pin orientation while riding and release it afterward.

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/MainActivity.kt`

- [ ] **Step 1: Add the ActivityInfo import**

In `MainActivity.kt`, add this import alongside the other `android.*` imports (after `import android.os.Bundle`):

```kotlin
import android.content.pm.ActivityInfo
```

- [ ] **Step 2: Replace `keepScreenOn` with a combined ride-active handler**

Replace this method:

```kotlin
    private fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
```

With:

```kotlin
    private fun onRideActive(active: Boolean) {
        if (active) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Freeze whatever orientation the ride started in so a bump can't flip the display mid-glance.
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
```

- [ ] **Step 3: Update the callback reference in `App()`**

In the `App()` composable, replace:

```kotlin
                RidemanNav(vm = vm, onRideActiveChanged = ::keepScreenOn)
```

With:

```kotlin
                RidemanNav(vm = vm, onRideActiveChanged = ::onRideActive)
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/MainActivity.kt
git commit -m "feat: lock screen orientation while a ride is active"
```

---

## Task 3: Orientation-aware ride layout (side rail in landscape)

Branch `RideScreen`'s outer layout on orientation. Extract the pager body into `RidePager` (so it isn't duplicated), the dots into `PaginatorDots` (horizontal or vertical), and add a `SideRail` for landscape. Refactor the existing `BottomBar` to reuse `PaginatorDots`.

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/ride/RideScreen.kt`

- [ ] **Step 1: Add the new imports**

In `RideScreen.kt`, add these imports (keep the file's existing imports):

```kotlin
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
```

- [ ] **Step 2: Replace the `RideScreen` body to branch on orientation**

Replace the `Box { Column { HorizontalPager(...) { when(...) }; BottomBar(...) } }` block (the body inside `Box(modifier = Modifier.fillMaxSize().pointerInput...)`, currently the `Column` at lines ~67-86) with an orientation branch. The full replacement for the `Box` content:

```kotlin
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val currentIndex = PagerWrap.screenIndex(pagerState.currentPage, count)

        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                RidePager(pagerState, screens, count, state, settings, Modifier.weight(1f).fillMaxHeight())
                SideRail(count = count, currentIndex = currentIndex, onEndRide = onEndRide, accent = accent)
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                RidePager(pagerState, screens, count, state, settings, Modifier.weight(1f).fillMaxWidth())
                BottomBar(count = count, currentIndex = currentIndex, onEndRide = onEndRide, accent = accent)
            }
        }
```

(`screens`, `count`, `state`, `settings`, `pagerState`, `accent` are already in scope from the existing function body.)

- [ ] **Step 3: Add the `RidePager` helper**

Add this private composable to the file (e.g. just below `RideScreen`):

```kotlin
@Composable
private fun RidePager(
    pagerState: PagerState,
    screens: List<RideScreen>,
    count: Int,
    state: RideUiState,
    settings: RidemanSettings,
    modifier: Modifier,
) {
    HorizontalPager(state = pagerState, modifier = modifier) { page ->
        when (screens[PagerWrap.screenIndex(page, count)]) {
            RideScreen.SPEED -> SpeedometerScreen(state.speedMps, settings.units)
            RideScreen.ODOMETER -> OdometerScreen(state.distanceM, settings.units)
            RideScreen.COMPASS -> CompassScreen(state.headingDeg)
            RideScreen.ALTITUDE -> AltimeterScreen(state.altitudeM, settings.units)
            RideScreen.CADENCE -> CadenceScreen(settings.cadenceMode, settings.targetRpm)
        }
    }
}
```

- [ ] **Step 4: Add the `PaginatorDots` helper**

```kotlin
@Composable
private fun PaginatorDots(count: Int, currentIndex: Int, accent: Color, vertical: Boolean) {
    @Composable
    fun dots() {
        repeat(count) { i ->
            val dotPadding = if (vertical) Modifier.padding(vertical = 5.dp) else Modifier.padding(horizontal = 5.dp)
            Box(
                dotPadding
                    .size(if (i == currentIndex) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i == currentIndex) accent else accent.copy(alpha = 0.3f))
            )
        }
    }
    if (vertical) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { dots() }
    } else {
        Row(horizontalArrangement = Arrangement.Center) { dots() }
    }
}
```

- [ ] **Step 5: Add the `SideRail` helper (landscape)**

```kotlin
@Composable
private fun SideRail(count: Int, currentIndex: Int, onEndRide: () -> Unit, accent: Color) {
    Column(
        Modifier.fillMaxHeight().padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            PaginatorDots(count = count, currentIndex = currentIndex, accent = accent, vertical = true)
        }
        Button(
            onClick = onEndRide,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
        ) { Text("END", style = MaterialTheme.typography.titleLarge) }
    }
}
```

- [ ] **Step 6: Refactor `BottomBar` to reuse `PaginatorDots`**

Replace the existing `BottomBar` composable with:

```kotlin
@Composable
private fun BottomBar(
    count: Int,
    currentIndex: Int,
    onEndRide: () -> Unit,
    accent: Color,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().padding(bottom = 12.dp), contentAlignment = Alignment.Center) {
            PaginatorDots(count = count, currentIndex = currentIndex, accent = accent, vertical = false)
        }
        Button(
            onClick = onEndRide,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("END RIDE", style = MaterialTheme.typography.titleLarge) }
    }
}
```

(Note: the `accent` param type is now `Color` via the new import — you can drop the old fully-qualified `androidx.compose.ui.graphics.Color` in the signature.)

- [ ] **Step 7: Build to verify it compiles**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Run the existing unit suite to confirm no regressions**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL` (all existing tests pass)

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/ride/RideScreen.kt
git commit -m "feat: landscape side-rail layout for the ride screen"
```

---

## Task 4: Scroll-safety for the Start screen

In short landscape height, Start's centered column can clip. Wrap it in a vertical scroll; it stays centered when it fits and scrolls when it doesn't.

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/StartScreen.kt`

- [ ] **Step 1: Add scroll imports**

Add to `StartScreen.kt`:

```kotlin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
```

- [ ] **Step 2: Apply `verticalScroll` to the root column**

Replace:

```kotlin
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
```

With:

```kotlin
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
```

- [ ] **Step 3: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/StartScreen.kt
git commit -m "feat: make Start screen scrollable so it never clips in landscape"
```

---

## Task 5: Scroll-safety + responsive stats for the End screen

Wrap End in vertical scroll, and lay the summary stats out two-wide in landscape (single column in portrait). Build the stats as a list first so the layout works whether there are 3 stats (plan ride hides DISTANCE) or 4 (free ride).

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/EndScreen.kt`

- [ ] **Step 1: Add imports**

Add to `EndScreen.kt`:

```kotlin
import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
```

- [ ] **Step 2: Apply `verticalScroll` to the root column**

Replace:

```kotlin
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
```

With:

```kotlin
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
```

- [ ] **Step 3: Replace the four inline `Stat(...)` calls with a responsive list**

Replace this block:

```kotlin
        Stat("TIME", formatDuration(summary.totalTimeMs), accent)
        if (planRide == null) {
            Stat("DISTANCE",
                "${String.format(Locale.US, "%.2f", Units.distance(summary.distanceM, units))} ${Units.distanceLabel(units)}",
                accent)
        }
        Stat("MAX SPEED",
            "${Units.speed(summary.maxSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}",
            accent)
        Stat("AVG SPEED",
            "${Units.speed(summary.avgSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}",
            accent)
```

With:

```kotlin
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
        if (landscape) {
            stats.chunked(2).forEach { rowStats ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rowStats.forEach { (label, value) -> Stat(label, value, accent) }
                }
            }
        } else {
            stats.forEach { (label, value) -> Stat(label, value, accent) }
        }
```

- [ ] **Step 4: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Run the existing unit suite to confirm no regressions**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/EndScreen.kt
git commit -m "feat: scrollable End screen with two-wide stats in landscape"
```

---

## Task 6: Manual verification on device/emulator

No code changes — confirm the feature end-to-end. Run the app (`./gradlew :app:installDebug` or via Android Studio) and walk the checklist.

- [ ] **Step 1: Non-ride screens rotate freely**

Rotate the device on each of: Start, Settings, History, Plan Picker. Each rotates and stays readable; nothing clips (scroll if needed). Confirm Start's title/buttons/debug line are all reachable in landscape.

- [ ] **Step 2: Ride locks to portrait**

From Start in portrait, begin a Free Ride. Rotate the device. Expected: the ride screen stays portrait (locked), stacked layout with bottom paginator + full-width END RIDE.

- [ ] **Step 3: Ride locks to landscape + side rail**

Return to Start, rotate to landscape, begin a Free Ride. Expected: side-rail layout — big number large on the left, vertical paginator dots and a compact `END` button on the right rail. Swipe left/right pages through the metric screens. Rotating the device does not change the orientation mid-ride.

- [ ] **Step 4: End screen releases the lock + 2-wide stats**

Tap END. Expected: orientation lock releases (End screen now rotates with the device). In landscape, the summary stats lay out two-wide; in portrait they stack. Tap DONE returns to Start.

- [ ] **Step 5: Plan ride variant**

Start a Plan Ride and end it. Confirm the End screen still looks right (3 stats — DISTANCE is replaced by the plan result banner) and the two-wide landscape layout handles the odd count gracefully (row of 2 + row of 1).

- [ ] **Step 6: Final confirmation**

All checklist items pass → feature complete. No commit needed (verification only).
