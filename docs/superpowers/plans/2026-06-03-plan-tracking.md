# 14-Week Plan Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user ride against the 14-week Couch-to-10-Miles plan — pick a Plan Ride or Free Ride, see real results against each plan ride's target, and browse all rides in a History screen.

**Architecture:** The plan is a read-only `assets/plan.json` parsed at startup into pure-Kotlin `core` domain types. Each saved ride carries a nullable `planRideId`; slot completion is *derived* by querying rides (no status table), using a 5% distance tolerance. UI adds a Plan Picker, a History screen, and a plan-aware End screen, wired through the existing `when(dest)` navigation.

**Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore, kotlinx.serialization (new), JUnit4.

**Spec:** `docs/superpowers/specs/2026-06-03-plan-tracking-design.md`

**Conventions to follow:**
- Tests: JUnit4, `org.junit.Assert.assertEquals`, `@Test fun snake_case_name()`. Pure logic lives in `core/` and is JVM-unit-tested (see `app/src/test/java/com/two17industries/rideman/core/`).
- Unit-test working directory is the `app/` module dir, so `File("src/main/assets/plan.json")` resolves in tests.
- Compose screens use `LocalAccent.current` for the themed accent color, `MaterialTheme.typography`, dark background. Match `ui/StartScreen.kt` / `ui/EndScreen.kt`.
- Run unit tests with: `./gradlew :app:testDebugUnitTest`
- Build the app with: `./gradlew :app:assembleDebug`

---

## File Structure

**Create:**
- `app/src/main/java/com/two17industries/rideman/core/Plan.kt` — `Pace`, `PlanRide`, `Plan` domain types.
- `app/src/main/java/com/two17industries/rideman/core/PlanProgress.kt` — `PlanAttempt`, `PlanGrading`, `PlanProgress` (pure completion logic).
- `app/src/main/java/com/two17industries/rideman/data/PlanLoader.kt` — JSON DTOs + `parsePlanJson()` + `PlanLoader.load(context)`.
- `app/src/main/assets/plan.json` — the 42-ride plan.
- `app/src/main/java/com/two17industries/rideman/ui/PlanPickerScreen.kt` — accordion picker.
- `app/src/main/java/com/two17industries/rideman/ui/HistoryScreen.kt` — history list.
- `app/src/test/java/com/two17industries/rideman/core/PlanProgressTest.kt`
- `app/src/test/java/com/two17industries/rideman/data/PlanLoaderTest.kt`

**Modify:**
- `gradle/libs.versions.toml` — add serialization plugin + lib.
- `app/build.gradle.kts` — apply plugin + dependency.
- `app/src/main/java/com/two17industries/rideman/data/RideEntity.kt` — add `planRideId`.
- `app/src/main/java/com/two17industries/rideman/data/RideDao.kt` — add `getAllRides`, `getPlanTaggedRides`, update `insertRideWithTrack` callers.
- `app/src/main/java/com/two17industries/rideman/data/RidemanDatabase.kt` — version 2 + `MIGRATION_1_2`.
- `app/src/main/java/com/two17industries/rideman/data/RideRepository.kt` — `saveRide(..., planRideId)` + expose flows.
- `app/src/main/java/com/two17industries/rideman/ui/RideViewModel.kt` — load plan, expose progress/history flows, carry active `planRideId`.
- `app/src/main/java/com/two17industries/rideman/ui/StartScreen.kt` — Plan/Free/History buttons + next-up subtitle.
- `app/src/main/java/com/two17industries/rideman/ui/EndScreen.kt` — plan target-vs-actual.
- `app/src/main/java/com/two17industries/rideman/ui/Nav.kt` — `PLAN_PICKER` + `HISTORY` destinations.

---

## Task 1: Add kotlinx.serialization to the build

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the version, library, and plugin to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` add (after the `junit` line):

```toml
kotlinxSerialization = "1.7.3"
```

Under `[libraries]` add (after the `kotlinx-coroutines-test` line):

```toml
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
```

Under `[plugins]` add (after the `ksp` line):

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Apply the plugin and dependency in the app module**

In `app/build.gradle.kts`, in the `plugins { }` block add after `alias(libs.plugins.ksp)`:

```kotlin
    alias(libs.plugins.kotlin.serialization)
```

In the `dependencies { }` block, add after `implementation(libs.kotlinx.coroutines.android)`:

```kotlin
    implementation(libs.kotlinx.serialization.json)
```

- [ ] **Step 3: Verify the project still configures and builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (no new code yet, just confirming the plugin/dependency resolve).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add kotlinx.serialization for plan.json parsing"
```

---

## Task 2: Plan domain types (`core/Plan.kt`)

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/core/Plan.kt`

These are pure data types with no Android or serialization dependencies.

- [ ] **Step 1: Create the file**

```kotlin
package com.two17industries.rideman.core

/** Pace descriptor from the plan's legend. */
enum class Pace { EASY, STEADY, BRISK }

/**
 * One ride slot in the plan. [id] is stable ("w{week}{slot}", e.g. "w3B") and is the
 * value stored as RideEntity.planRideId. [targetMiles] is the distance target used for
 * completion grading; pace and guidance are descriptive only.
 */
data class PlanRide(
    val id: String,
    val phaseNumber: Int,
    val phaseName: String,
    val week: Int,
    val slot: String,            // "A" | "B" | "C"
    val kind: String,            // "easy" | "endurance" | "quality"
    val targetMiles: Double,
    val pace: Pace,
    val longRide: Boolean,
    val guidance: String,
    val recoveryWeek: Boolean,
)

/** The whole plan, in ride order. */
class Plan(
    val title: String,
    val rides: List<PlanRide>,
    val tolerancePercent: Int,
) {
    /** Lookup by [PlanRide.id]. */
    val byId: Map<String, PlanRide> = rides.associateBy { it.id }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/core/Plan.kt
git commit -m "feat(core): add Plan domain types"
```

---

## Task 3: Completion logic (`core/PlanProgress.kt`) — TDD

**Files:**
- Create: `app/src/test/java/com/two17industries/rideman/core/PlanProgressTest.kt`
- Create: `app/src/main/java/com/two17industries/rideman/core/PlanProgress.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/two17industries/rideman/core/PlanProgressTest.kt`:

```kotlin
package com.two17industries.rideman.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanProgressTest {

    private fun ride(id: String, miles: Double) = PlanRide(
        id = id, phaseNumber = 1, phaseName = "P", week = 1, slot = "A",
        kind = "easy", targetMiles = miles, pace = Pace.EASY,
        longRide = false, guidance = "", recoveryWeek = false,
    )

    private val plan = Plan(
        title = "T",
        rides = listOf(ride("w1A", 7.0), ride("w1B", 3.0), ride("w1C", 10.0)),
        tolerancePercent = 5,
    )

    private fun attempt(id: String, miles: Double) =
        PlanAttempt(id, miles * PlanGrading.METERS_PER_MILE)

    @Test fun ride_at_target_is_met() {
        assertTrue(PlanGrading.isMet(plan.rides[0], 7.0 * PlanGrading.METERS_PER_MILE, 5))
    }

    @Test fun ride_within_tolerance_is_met() {
        // 7 mi target, 5% tolerance -> 6.65 mi clears.
        assertTrue(PlanGrading.isMet(plan.rides[0], 6.65 * PlanGrading.METERS_PER_MILE, 5))
    }

    @Test fun ride_below_tolerance_is_not_met() {
        assertFalse(PlanGrading.isMet(plan.rides[0], 6.64 * PlanGrading.METERS_PER_MILE, 5))
    }

    @Test fun slot_complete_when_any_attempt_clears() {
        val p = PlanProgress(plan, listOf(attempt("w1A", 5.0), attempt("w1A", 7.1)))
        assertTrue(p.isComplete("w1A"))
    }

    @Test fun short_only_attempts_leave_slot_open() {
        val p = PlanProgress(plan, listOf(attempt("w1A", 5.0), attempt("w1A", 6.0)))
        assertFalse(p.isComplete("w1A"))
    }

    @Test fun next_incomplete_skips_completed_slots() {
        val p = PlanProgress(plan, listOf(attempt("w1A", 7.0)))
        assertEquals("w1B", p.nextIncomplete()?.id)
    }

    @Test fun next_incomplete_is_null_when_all_done() {
        val p = PlanProgress(plan, listOf(attempt("w1A", 7.0), attempt("w1B", 3.0), attempt("w1C", 10.0)))
        assertNull(p.nextIncomplete())
    }

    @Test fun completed_count_and_total() {
        val p = PlanProgress(plan, listOf(attempt("w1A", 7.0), attempt("w1C", 10.0)))
        assertEquals(2, p.completedCount())
        assertEquals(3, p.total)
    }

    @Test fun unknown_plan_ride_id_is_not_complete() {
        val p = PlanProgress(plan, listOf(attempt("w9Z", 99.0)))
        assertFalse(p.isComplete("w9Z"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlanProgressTest"`
Expected: FAIL — unresolved references `PlanAttempt`, `PlanGrading`, `PlanProgress`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/two17industries/rideman/core/PlanProgress.kt`:

```kotlin
package com.two17industries.rideman.core

/** One ride that was tagged to a plan slot. distanceM is the ride's total distance in meters. */
data class PlanAttempt(val planRideId: String, val distanceM: Double)

/** Distance-target grading for a plan ride. */
object PlanGrading {
    const val METERS_PER_MILE = 1609.344

    fun targetMeters(ride: PlanRide): Double = ride.targetMiles * METERS_PER_MILE

    /** Minimum distance (meters) that completes the slot, given the tolerance. */
    fun threshold(ride: PlanRide, tolerancePercent: Int): Double =
        targetMeters(ride) * (1.0 - tolerancePercent / 100.0)

    /** True if a ride of [distanceM] meters clears [ride]'s target within tolerance. */
    fun isMet(ride: PlanRide, distanceM: Double, tolerancePercent: Int): Boolean =
        distanceM >= threshold(ride, tolerancePercent)
}

/**
 * Derived progress over a [Plan] given the rides tagged to it. A slot is complete when
 * its single best attempt clears the target distance within the plan's tolerance.
 */
class PlanProgress(private val plan: Plan, attempts: List<PlanAttempt>) {

    private val bestMetersBySlot: Map<String, Double> =
        attempts.groupBy { it.planRideId }
            .mapValues { (_, list) -> list.maxOf { it.distanceM } }

    fun isComplete(rideId: String): Boolean {
        val ride = plan.byId[rideId] ?: return false
        val best = bestMetersBySlot[rideId] ?: return false
        return PlanGrading.isMet(ride, best, plan.tolerancePercent)
    }

    /** First ride in plan order that is not complete, or null if the whole plan is done. */
    fun nextIncomplete(): PlanRide? = plan.rides.firstOrNull { !isComplete(it.id) }

    fun completedCount(): Int = plan.rides.count { isComplete(it.id) }

    val total: Int get() = plan.rides.size
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlanProgressTest"`
Expected: PASS (all 9 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/core/PlanProgress.kt app/src/test/java/com/two17industries/rideman/core/PlanProgressTest.kt
git commit -m "feat(core): add derived plan completion logic with 5% tolerance"
```

---

## Task 4: The plan asset (`assets/plan.json`)

**Files:**
- Create: `app/src/main/assets/plan.json`

This is the read-only transcription of `couch-to-10-miles-cycling-plan.md`. "Long ride" in the doc is the long-ride flag on whichever of A/B/C is longest that week (B in weeks 1–13, C in week 14).

- [ ] **Step 1: Create the asset file**

Create `app/src/main/assets/plan.json`:

```json
{
  "title": "Couch-to-10-Miles Cycling Plan",
  "tolerancePercent": 5,
  "phases": [
    {
      "number": 1,
      "name": "Build the base",
      "weeks": [
        { "week": 1, "recovery": false, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 2.0, "pace": "easy", "longRide": false, "guidance": "Easy spin, high cadence." },
          { "slot": "B", "kind": "endurance", "targetMiles": 3.0, "pace": "easy", "longRide": true, "guidance": "Your longer endurance ride. Pace doesn't matter yet." },
          { "slot": "C", "kind": "quality", "targetMiles": 2.5, "pace": "easy", "longRide": false, "guidance": "Easy spin." }
        ]},
        { "week": 2, "recovery": false, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 2.5, "pace": "easy", "longRide": false, "guidance": "Easy spin, high cadence." },
          { "slot": "B", "kind": "endurance", "targetMiles": 4.0, "pace": "easy", "longRide": true, "guidance": "Your longer endurance ride." },
          { "slot": "C", "kind": "quality", "targetMiles": 3.0, "pace": "easy", "longRide": false, "guidance": "Easy spin." }
        ]},
        { "week": 3, "recovery": false, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 3.0, "pace": "easy", "longRide": false, "guidance": "Easy spin, high cadence." },
          { "slot": "B", "kind": "endurance", "targetMiles": 5.0, "pace": "easy", "longRide": true, "guidance": "Your longer endurance ride." },
          { "slot": "C", "kind": "quality", "targetMiles": 3.0, "pace": "easy", "longRide": false, "guidance": "Incl. 3 x 20-sec light pickups (spin faster in an easy gear)." }
        ]},
        { "week": 4, "recovery": true, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 2.0, "pace": "easy", "longRide": false, "guidance": "Recovery week. Very easy." },
          { "slot": "B", "kind": "endurance", "targetMiles": 4.0, "pace": "easy", "longRide": true, "guidance": "Recovery week. Easy." },
          { "slot": "C", "kind": "quality", "targetMiles": 3.0, "pace": "easy", "longRide": false, "guidance": "Recovery week. Easy." }
        ]}
      ]
    },
    {
      "number": 2,
      "name": "Build the distance",
      "weeks": [
        { "week": 5, "recovery": false, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 3.0, "pace": "easy", "longRide": false, "guidance": "Easy spin, high cadence." },
          { "slot": "B", "kind": "endurance", "targetMiles": 6.0, "pace": "easy", "longRide": true, "guidance": "Your longer endurance ride." },
          { "slot": "C", "kind": "quality", "targetMiles": 3.5, "pace": "easy", "longRide": false, "guidance": "Incl. 4 x 20-sec pickups." }
        ]},
        { "week": 6, "recovery": false, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 3.0, "pace": "easy", "longRide": false, "guidance": "Easy spin, high cadence." },
          { "slot": "B", "kind": "endurance", "targetMiles": 7.0, "pace": "easy", "longRide": true, "guidance": "Your longer endurance ride." },
          { "slot": "C", "kind": "quality", "targetMiles": 4.0, "pace": "steady", "longRide": false, "guidance": "Incl. 2 x 3-min steady efforts." }
        ]},
        { "week": 7, "recovery": false, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 3.0, "pace": "easy", "longRide": false, "guidance": "Easy spin, high cadence." },
          { "slot": "B", "kind": "endurance", "targetMiles": 8.0, "pace": "easy", "longRide": true, "guidance": "Your longer endurance ride." },
          { "slot": "C", "kind": "quality", "targetMiles": 4.0, "pace": "steady", "longRide": false, "guidance": "Incl. 3 x 3-min steady efforts." }
        ]},
        { "week": 8, "recovery": true, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 3.0, "pace": "easy", "longRide": false, "guidance": "Recovery week. Easy." },
          { "slot": "B", "kind": "endurance", "targetMiles": 5.0, "pace": "easy", "longRide": true, "guidance": "Recovery week. Easy." },
          { "slot": "C", "kind": "quality", "targetMiles": 4.0, "pace": "easy", "longRide": false, "guidance": "Recovery week. Easy." }
        ]}
      ]
    },
    {
      "number": 3,
      "name": "Reach 10 miles",
      "weeks": [
        { "week": 9, "recovery": false, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 3.0, "pace": "easy", "longRide": false, "guidance": "Easy spin, high cadence." },
          { "slot": "B", "kind": "endurance", "targetMiles": 9.0, "pace": "easy", "longRide": true, "guidance": "Your longer endurance ride. Just finish comfortably." },
          { "slot": "C", "kind": "quality", "targetMiles": 5.0, "pace": "steady", "longRide": false, "guidance": "Incl. 3 x 4-min steady efforts." }
        ]},
        { "week": 10, "recovery": false, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 4.0, "pace": "easy", "longRide": false, "guidance": "Easy spin, high cadence." },
          { "slot": "B", "kind": "endurance", "targetMiles": 10.0, "pace": "easy", "longRide": true, "guidance": "First 10-miler! Pace doesn't matter, just finish." },
          { "slot": "C", "kind": "quality", "targetMiles": 5.0, "pace": "steady", "longRide": false, "guidance": "Incl. 4 x 4-min steady efforts." }
        ]}
      ]
    },
    {
      "number": 4,
      "name": "Build the pace",
      "weeks": [
        { "week": 11, "recovery": false, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 4.0, "pace": "easy", "longRide": false, "guidance": "Easy spin, high cadence." },
          { "slot": "B", "kind": "endurance", "targetMiles": 8.0, "pace": "steady", "longRide": true, "guidance": "Middle 4 mi at steady." },
          { "slot": "C", "kind": "quality", "targetMiles": 5.0, "pace": "brisk", "longRide": false, "guidance": "5 x 3-min brisk (15+ mph)." }
        ]},
        { "week": 12, "recovery": false, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 4.0, "pace": "easy", "longRide": false, "guidance": "Easy spin, high cadence." },
          { "slot": "B", "kind": "endurance", "targetMiles": 10.0, "pace": "steady", "longRide": true, "guidance": "Hold ~14 mph avg." },
          { "slot": "C", "kind": "quality", "targetMiles": 5.0, "pace": "brisk", "longRide": false, "guidance": "4 x 4-min brisk." }
        ]},
        { "week": 13, "recovery": false, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 4.0, "pace": "easy", "longRide": false, "guidance": "Easy spin, high cadence." },
          { "slot": "B", "kind": "endurance", "targetMiles": 10.0, "pace": "steady", "longRide": true, "guidance": "Push avg toward 15 mph." },
          { "slot": "C", "kind": "quality", "targetMiles": 5.0, "pace": "brisk", "longRide": false, "guidance": "Short sharp intervals." }
        ]},
        { "week": 14, "recovery": false, "rides": [
          { "slot": "A", "kind": "easy", "targetMiles": 4.0, "pace": "easy", "longRide": false, "guidance": "Very easy." },
          { "slot": "B", "kind": "endurance", "targetMiles": 3.0, "pace": "easy", "longRide": false, "guidance": "Rest day, or an easy 3 mi spin." },
          { "slot": "C", "kind": "quality", "targetMiles": 10.0, "pace": "brisk", "longRide": true, "guidance": "GOAL RIDE: 10 mi at 15 mph (~40 min)." }
        ]}
      ]
    }
  ]
}
```

- [ ] **Step 2: Validate the JSON syntax**

Run: `python3 -m json.tool app/src/main/assets/plan.json > /dev/null && echo OK`
Expected: `OK`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/plan.json
git commit -m "feat: add 14-week plan as a bundled JSON asset"
```

---

## Task 5: Plan loader & parser (`data/PlanLoader.kt`) — TDD

**Files:**
- Create: `app/src/test/java/com/two17industries/rideman/data/PlanLoaderTest.kt`
- Create: `app/src/main/java/com/two17industries/rideman/data/PlanLoader.kt`

`parsePlanJson` is pure (string in, `Plan` out) so it is JVM-unit-tested. `PlanLoader.load` is the thin Android wrapper that reads the asset.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/two17industries/rideman/data/PlanLoaderTest.kt`:

```kotlin
package com.two17industries.rideman.data

import com.two17industries.rideman.core.Pace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlanLoaderTest {

    private val sampleJson = """
        {
          "title": "Test Plan",
          "tolerancePercent": 5,
          "phases": [
            { "number": 1, "name": "Base", "weeks": [
              { "week": 1, "recovery": false, "rides": [
                { "slot": "A", "kind": "easy", "targetMiles": 2.0, "pace": "easy", "longRide": false, "guidance": "x" },
                { "slot": "B", "kind": "endurance", "targetMiles": 3.0, "pace": "steady", "longRide": true, "guidance": "y" }
              ]}
            ]}
          ]
        }
    """.trimIndent()

    @Test fun parses_title_and_tolerance() {
        val plan = parsePlanJson(sampleJson)
        assertEquals("Test Plan", plan.title)
        assertEquals(5, plan.tolerancePercent)
    }

    @Test fun flattens_rides_in_order_with_stable_ids() {
        val plan = parsePlanJson(sampleJson)
        assertEquals(listOf("w1A", "w1B"), plan.rides.map { it.id })
    }

    @Test fun maps_fields_and_pace() {
        val plan = parsePlanJson(sampleJson)
        val b = plan.byId.getValue("w1B")
        assertEquals(1, b.phaseNumber)
        assertEquals("Base", b.phaseName)
        assertEquals(1, b.week)
        assertEquals("B", b.slot)
        assertEquals(3.0, b.targetMiles, 0.0)
        assertEquals(Pace.STEADY, b.pace)
        assertTrue(b.longRide)
    }

    @Test fun real_asset_has_42_rides_with_unique_ids() {
        val json = File("src/main/assets/plan.json").readText()
        val plan = parsePlanJson(json)
        assertEquals(42, plan.rides.size)
        assertEquals(42, plan.rides.map { it.id }.toSet().size)
    }

    @Test fun real_asset_week_14_C_is_the_goal_ride() {
        val json = File("src/main/assets/plan.json").readText()
        val plan = parsePlanJson(json)
        val goal = plan.byId.getValue("w14C")
        assertEquals(10.0, goal.targetMiles, 0.0)
        assertTrue(goal.longRide)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlanLoaderTest"`
Expected: FAIL — unresolved reference `parsePlanJson`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/two17industries/rideman/data/PlanLoader.kt`:

```kotlin
package com.two17industries.rideman.data

import android.content.Context
import com.two17industries.rideman.core.Pace
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanRide
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class PlanDto(
    val title: String,
    val tolerancePercent: Int,
    val phases: List<PhaseDto>,
)

@Serializable
private data class PhaseDto(
    val number: Int,
    val name: String,
    val weeks: List<WeekDto>,
)

@Serializable
private data class WeekDto(
    val week: Int,
    val recovery: Boolean = false,
    val rides: List<RideDto>,
)

@Serializable
private data class RideDto(
    val slot: String,
    val kind: String,
    val targetMiles: Double,
    val pace: String,
    val longRide: Boolean = false,
    val guidance: String = "",
)

private val json = Json { ignoreUnknownKeys = true }

/** Parse plan JSON text into the domain [Plan]. Throws on malformed input. */
fun parsePlanJson(text: String): Plan {
    val dto = json.decodeFromString(PlanDto.serializer(), text)
    val rides = dto.phases.flatMap { phase ->
        phase.weeks.flatMap { week ->
            week.rides.map { ride ->
                PlanRide(
                    id = "w${week.week}${ride.slot}",
                    phaseNumber = phase.number,
                    phaseName = phase.name,
                    week = week.week,
                    slot = ride.slot,
                    kind = ride.kind,
                    targetMiles = ride.targetMiles,
                    pace = Pace.valueOf(ride.pace.uppercase()),
                    longRide = ride.longRide,
                    guidance = ride.guidance,
                    recoveryWeek = week.recovery,
                )
            }
        }
    }
    return Plan(title = dto.title, rides = rides, tolerancePercent = dto.tolerancePercent)
}

object PlanLoader {
    /** Read and parse the bundled plan asset. Throws if the asset is missing/malformed. */
    fun load(context: Context): Plan {
        val text = context.assets.open("plan.json").bufferedReader().use { it.readText() }
        return parsePlanJson(text)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*PlanLoaderTest"`
Expected: PASS (all 5 tests). If `real_asset_*` tests fail to find the file, confirm the working dir is the `app/` module (it is for `:app:testDebugUnitTest`).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/data/PlanLoader.kt app/src/test/java/com/two17industries/rideman/data/PlanLoaderTest.kt
git commit -m "feat(data): parse plan.json into Plan domain model"
```

---

## Task 6: Add `planRideId` to `RideEntity`

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/data/RideEntity.kt`

- [ ] **Step 1: Add the column**

Replace the contents of `RideEntity.kt` with:

```kotlin
package com.two17industries.rideman.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val totalTimeMs: Long,
    val distanceM: Double,
    val maxSpeedMps: Float,
    val avgSpeedMps: Float,
    /** Plan slot this ride was tagged to (e.g. "w3B"), or null for a free ride. */
    val planRideId: String? = null,
)
```

- [ ] **Step 2: Verify it compiles** (Room will flag the schema change at compile time; the migration is added in Task 8.)

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/data/RideEntity.kt
git commit -m "feat(data): add nullable planRideId to RideEntity"
```

---

## Task 7: History/progress queries on `RideDao`

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/data/RideDao.kt`

- [ ] **Step 1: Add the query methods**

Replace the contents of `RideDao.kt` with:

```kotlin
package com.two17industries.rideman.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert
    suspend fun insertRide(ride: RideEntity): Long

    @Insert
    suspend fun insertTrackPoints(points: List<TrackPointEntity>)

    @Transaction
    suspend fun insertRideWithTrack(ride: RideEntity, points: List<TrackPointEntity>): Long {
        val rideId = insertRide(ride)
        insertTrackPoints(points.map { it.copy(rideId = rideId) })
        return rideId
    }

    /** All rides, newest first — for the History screen. */
    @Query("SELECT * FROM rides ORDER BY startedAt DESC")
    fun getAllRides(): Flow<List<RideEntity>>

    /** Rides tagged to a plan slot — for deriving plan progress. */
    @Query("SELECT * FROM rides WHERE planRideId IS NOT NULL")
    fun getPlanTaggedRides(): Flow<List<RideEntity>>
}
```

- [ ] **Step 2: Verify it compiles** (Room generates the DAO implementation.)

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/data/RideDao.kt
git commit -m "feat(data): add getAllRides and getPlanTaggedRides queries"
```

---

## Task 8: Room migration v1 → v2

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/data/RidemanDatabase.kt`

The new `planRideId` column requires a schema migration so existing ride data survives an app update. (Automated migration testing via `MigrationTestHelper` requires androidTest + schema export infra; per the KISS scope this is verified manually — see Step 3. This deviation from the spec's "migration test" bullet is intentional to avoid heavy androidTest setup.)

- [ ] **Step 1: Bump the version and add the migration**

Replace the contents of `RidemanDatabase.kt` with:

```kotlin
package com.two17industries.rideman.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [RideEntity::class, TrackPointEntity::class], version = 2, exportSchema = false)
abstract class RidemanDatabase : RoomDatabase() {
    abstract fun rideDao(): RideDao

    companion object {
        @Volatile private var instance: RidemanDatabase? = null

        /** Adds the nullable planRideId column introduced for plan tracking. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN planRideId TEXT")
            }
        }

        fun get(context: Context): RidemanDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RidemanDatabase::class.java,
                    "rideman.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Manual migration verification** (do this once the app is runnable, e.g. after Task 15)

If you have an existing install with ride data: install the new build over it (`./gradlew :app:installDebug`), open the app, go to History, and confirm previously recorded rides still appear (they will show as FREE rides since they predate plan tagging). No crash on launch = migration succeeded.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/data/RidemanDatabase.kt
git commit -m "feat(data): migrate Room schema to v2 for planRideId"
```

---

## Task 9: Repository — tag rides and expose flows

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/data/RideRepository.kt`

- [ ] **Step 1: Add the planRideId parameter and flow accessors**

Replace the contents of `RideRepository.kt` with:

```kotlin
package com.two17industries.rideman.data

import com.two17industries.rideman.core.LocationSample
import com.two17industries.rideman.core.RideSummary
import kotlinx.coroutines.flow.Flow

class RideRepository(private val dao: RideDao) {

    fun allRides(): Flow<List<RideEntity>> = dao.getAllRides()

    fun planTaggedRides(): Flow<List<RideEntity>> = dao.getPlanTaggedRides()

    suspend fun saveRide(
        summary: RideSummary,
        track: List<LocationSample>,
        planRideId: String? = null,
    ): Long {
        val ride = RideEntity(
            startedAt = summary.startedAtMillis,
            endedAt = summary.endedAtMillis,
            totalTimeMs = summary.totalTimeMs,
            distanceM = summary.distanceM,
            maxSpeedMps = summary.maxSpeedMps,
            avgSpeedMps = summary.avgSpeedMps,
            planRideId = planRideId,
        )
        val points = track.map {
            TrackPointEntity(
                rideId = 0,
                timestamp = it.epochMillis,
                lat = it.lat,
                lng = it.lng,
                altitudeM = it.gpsAltitudeM,
                speedMps = it.speedMps,
                headingDeg = it.headingDeg,
            )
        }
        return dao.insertRideWithTrack(ride, points)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/data/RideRepository.kt
git commit -m "feat(data): tag saved rides with planRideId and expose ride flows"
```

---

## Task 10: ViewModel — load plan, carry active slot, expose progress & history

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/RideViewModel.kt`

- [ ] **Step 1: Add plan state, active planRideId, and derived flows**

Make these specific changes to `RideViewModel.kt`:

a) Add imports (with the other `com.two17industries.rideman` imports near the top):

```kotlin
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanAttempt
import com.two17industries.rideman.core.PlanProgress
import com.two17industries.rideman.data.PlanLoader
import com.two17industries.rideman.data.RideEntity
import kotlinx.coroutines.flow.map
```

b) After the `repo` property declaration (`private val repo = RideRepository(...)`), add:

```kotlin
    /** Parsed once at startup; null if the asset is missing/malformed (plan features disable). */
    val plan: Plan? = runCatching { PlanLoader.load(app) }.getOrNull()

    /** All rides, newest first, for the History screen. */
    val allRides: StateFlow<List<RideEntity>> =
        repo.allRides().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Derived plan progress, or null if there is no loaded plan. */
    val progress: StateFlow<PlanProgress?> =
        repo.planTaggedRides()
            .map { rides ->
                plan?.let { p ->
                    PlanProgress(p, rides.mapNotNull { r ->
                        r.planRideId?.let { PlanAttempt(it, r.distanceM) }
                    })
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Plan slot for the in-progress ride, or null for a free ride. */
    private var activePlanRideId: String? = null
```

c) Change the `startRide()` signature to accept the slot. Replace the line:

```kotlin
    fun startRide() {
```

with:

```kotlin
    fun startRide(planRideId: String? = null) {
        activePlanRideId = planRideId
```

d) In `persistLastRide()`, pass the active slot. Replace:

```kotlin
        viewModelScope.launch { repo.saveRide(summary, snapshot) }
```

with:

```kotlin
        viewModelScope.launch { repo.saveRide(summary, snapshot, activePlanRideId) }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/RideViewModel.kt
git commit -m "feat(ui): load plan, expose progress/history, carry active plan slot"
```

---

## Task 11: Start screen — Plan / Free / History + next-up subtitle

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/StartScreen.kt`

- [ ] **Step 1: Rewrite the Start screen**

Replace the contents of `StartScreen.kt` with:

```kotlin
package com.two17industries.rideman.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.BuildConfig
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.ui.theme.LocalAccent

@Composable
fun StartScreen(
    nextUp: PlanRide?,
    planAvailable: Boolean,
    onPlanRide: () -> Unit,
    onFreeRide: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "BIKEMAN",
            color = accent,
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onPlanRide,
            enabled = planAvailable,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth().height(96.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PLAN RIDE", style = MaterialTheme.typography.titleLarge)
                val subtitle = when {
                    !planAvailable -> "plan unavailable"
                    nextUp != null -> "Next: Wk ${nextUp.week} · Ride ${nextUp.slot} — " +
                        "${formatMiles(nextUp.targetMiles)}mi ${nextUp.pace.name.lowercase()}"
                    else -> "Plan complete 🎉"
                }
                Text(subtitle, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedButton(onClick = onFreeRide, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("FREE RIDE", color = accent, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f).height(56.dp)) {
                Text("HISTORY", color = accent, style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f).height(56.dp)) {
                Text("SETTINGS", color = accent, style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(48.dp))
        Text(
            "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · ${BuildConfig.GIT_COMMIT}",
            color = accent.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** Trim trailing ".0" so 7.0 -> "7" but 3.5 -> "3.5". */
internal fun formatMiles(mi: Double): String =
    if (mi % 1.0 == 0.0) mi.toInt().toString() else mi.toString()
```

- [ ] **Step 2: Verify it compiles** (Nav still calls the old signature — that's fixed in Task 15. Compile may fail on `Nav.kt`; that is expected until Task 15. To verify just this file in isolation, proceed and rely on the Task 15 build.)

Run: `./gradlew :app:compileDebugKotlin`
Expected: May FAIL only in `Nav.kt` (old `StartScreen` call). `StartScreen.kt` itself must have no errors. If other files error, fix `StartScreen.kt`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/StartScreen.kt
git commit -m "feat(ui): plan-first Start screen with next-up subtitle"
```

---

## Task 12: Plan Picker screen (`ui/PlanPickerScreen.kt`)

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/ui/PlanPickerScreen.kt`

Accordion list grouped Phase → Week → ride rows. The next incomplete ride is auto-expanded on entry; a sticky bottom `START RIDE` starts the expanded slot.

- [ ] **Step 1: Create the screen**

```kotlin
package com.two17industries.rideman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanProgress
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.ui.theme.LocalAccent

@Composable
fun PlanPickerScreen(
    plan: Plan,
    progress: PlanProgress?,
    onStart: (PlanRide) -> Unit,
    onBack: () -> Unit,
) {
    val accent = LocalAccent.current
    val defaultExpanded = progress?.nextIncomplete()?.id ?: plan.rides.firstOrNull()?.id
    var expandedId by remember { mutableStateOf(defaultExpanded) }

    // Build the ordered display list: a phase header appears before its first ride,
    // a week header before that week's first ride.
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "◀ 14-WEEK PLAN",
            color = accent,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clickable(onClick = onBack).padding(bottom = 12.dp),
        )

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            var lastPhase = -1
            var lastWeek = -1
            items(plan.rides, key = { it.id }) { ride ->
                if (ride.phaseNumber != lastPhase) {
                    lastPhase = ride.phaseNumber
                    PhaseHeader(ride.phaseNumber, ride.phaseName, accent)
                }
                if (ride.week != lastWeek) {
                    lastWeek = ride.week
                    WeekHeader(ride.week, ride.recoveryWeek)
                }
                val complete = progress?.isComplete(ride.id) == true
                if (ride.id == expandedId) {
                    ExpandedRow(ride, complete, accent)
                } else {
                    CollapsedRow(ride, complete, accent) { expandedId = ride.id }
                }
            }
        }

        Button(
            onClick = { plan.byId[expandedId]?.let(onStart) },
            enabled = expandedId != null,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("START RIDE", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun PhaseHeader(number: Int, name: String, accent: androidx.compose.ui.graphics.Color) {
    Text(
        "PHASE $number · ${name.uppercase()}",
        color = accent.copy(alpha = 0.6f),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun WeekHeader(week: Int, recovery: Boolean) {
    Text(
        "Week $week" + if (recovery) " (recovery)" else "",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun CollapsedRow(
    ride: PlanRide,
    complete: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val labelColor = if (complete) accent.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Ride ${ride.slot} · ${ride.kind}", color = labelColor, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${formatMiles(ride.targetMiles)} mi  " + if (complete) "✓" else "○",
            color = if (complete) accent else accent.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ExpandedRow(
    ride: PlanRide,
    complete: Boolean,
    accent: androidx.compose.ui.graphics.Color,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(14.dp),
    ) {
        Column {
            Text(
                "Week ${ride.week} · Ride ${ride.slot} — ${ride.kind}",
                color = accent, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "${formatMiles(ride.targetMiles)} mi",
                color = accent,
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                "${ride.pace.name.lowercase()} pace" + if (complete) "  ·  ✓ done" else "",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (ride.guidance.isNotBlank()) {
                Text(
                    ride.guidance,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (ride.longRide) {
                Text(
                    "★ LONG RIDE",
                    color = accent, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL for this file (Nav wiring in Task 15). `formatMiles` is reused from `StartScreen.kt` (same `ui` package).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/PlanPickerScreen.kt
git commit -m "feat(ui): add Plan Picker accordion screen"
```

---

## Task 13: History screen (`ui/HistoryScreen.kt`)

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/ui/HistoryScreen.kt`

A plan-progress header plus a single chronological accordion list of all rides. Plan rows resolve their target via the `Plan`; free rows show plain stats.

- [ ] **Step 1: Create the screen**

```kotlin
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanGrading
import com.two17industries.rideman.core.PlanProgress
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.ui.theme.LocalAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    rides: List<RideEntity>,
    plan: Plan?,
    progress: PlanProgress?,
    units: UnitSystem,
    onBack: () -> Unit,
) {
    val accent = LocalAccent.current
    var expandedId by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "◀ HISTORY",
            color = accent,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clickable(onClick = onBack).padding(bottom = 12.dp),
        )

        if (progress != null) {
            ProgressHeader(progress, accent)
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(rides, key = { it.id }) { ride ->
                RideRow(
                    ride = ride,
                    plan = plan,
                    units = units,
                    accent = accent,
                    expanded = ride.id == expandedId,
                    onToggle = { expandedId = if (expandedId == ride.id) null else ride.id },
                )
            }
        }
    }
}

@Composable
private fun ProgressHeader(progress: PlanProgress, accent: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.08f))
            .padding(12.dp),
    ) {
        Column {
            Text("PLAN PROGRESS", color = accent.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge)
            Text(
                "${progress.completedCount()} / ${progress.total} rides",
                color = accent, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun RideRow(
    ride: RideEntity,
    plan: Plan?,
    units: UnitSystem,
    accent: androidx.compose.ui.graphics.Color,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val planRide = ride.planRideId?.let { plan?.byId?.get(it) }
    val dist = String.format(Locale.US, "%.1f", Units.distance(ride.distanceM, units))
    val distLabel = Units.distanceLabel(units).lowercase()

    val bg = if (expanded) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (expanded) 12.dp else 10.dp))
            .background(bg)
            .clickable(onClick = onToggle)
            .padding(12.dp),
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDate(ride.startedAt), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge)
                Text(if (planRide != null) "PLAN" else "FREE", color = if (planRide != null) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            val main = if (planRide != null)
                "Wk ${planRide.week} · Ride ${planRide.slot} — $dist $distLabel"
            else "$dist $distLabel"
            Text(main, color = if (expanded) accent else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)

            if (expanded) {
                if (planRide != null) {
                    val met = PlanGrading.isMet(planRide, ride.distanceM, plan!!.tolerancePercent)
                    DetailLine("target", "${formatMiles(planRide.targetMiles)} mi  " + if (met) "✓ met" else "✗ short", accent, met)
                }
                DetailLine("time", formatHistoryDuration(ride.totalTimeMs), accent, null)
                DetailLine("avg speed", "${Units.speed(ride.avgSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}", accent, null)
                if (planRide == null) {
                    DetailLine("max speed", "${Units.speed(ride.maxSpeedMps, units).roundToInt()} ${Units.speedLabel(units)}", accent, null)
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, accent: androidx.compose.ui.graphics.Color, met: Boolean?) {
    Row(
        Modifier.fillMaxWidth().padding(top = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            color = when (met) { true -> accent; false -> androidx.compose.ui.graphics.Color(0xFFFFCF3A); null -> MaterialTheme.colorScheme.onSurface },
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("MMM d · h:mm a", Locale.US).format(Date(epochMillis))

private fun formatHistoryDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL for this file (Nav wiring in Task 15).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/HistoryScreen.kt
git commit -m "feat(ui): add History screen with plan progress and accordion rows"
```

---

## Task 14: Plan-aware End screen

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/EndScreen.kt`

- [ ] **Step 1: Add the plan-ride banner and target line**

Replace the contents of `EndScreen.kt` with:

```kotlin
package com.two17industries.rideman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.two17industries.rideman.core.PlanGrading
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.core.RideSummary
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.ui.theme.LocalAccent
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EndScreen(
    summary: RideSummary,
    units: UnitSystem,
    planRide: PlanRide?,
    tolerancePercent: Int,
    onDone: () -> Unit,
) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("RIDE COMPLETE", color = accent, style = MaterialTheme.typography.titleLarge)

        if (planRide != null) {
            PlanResult(summary, planRide, tolerancePercent, units, accent)
        }

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

        Button(
            onClick = onDone,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) { Text("DONE", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun PlanResult(
    summary: RideSummary,
    planRide: PlanRide,
    tolerancePercent: Int,
    units: UnitSystem,
    accent: Color,
) {
    val met = PlanGrading.isMet(planRide, summary.distanceM, tolerancePercent)
    val amber = Color(0xFFFFCF3A)
    val actualMi = Units.distance(summary.distanceM, UnitSystem.AMERICAN)
    val shortByMi = planRide.targetMiles - actualMi

    Text(
        "Week ${planRide.week} · Ride ${planRide.slot} — ${planRide.kind}",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodyLarge,
    )

    val (bannerText, bannerColor) = if (met)
        "✓ TARGET MET · SLOT COMPLETE" to accent
    else
        "LOGGED · ${String.format(Locale.US, "%.1f", abs(shortByMi))} mi SHORT — SLOT STAYS OPEN" to amber

    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bannerColor.copy(alpha = if (met) 1f else 0.18f)).padding(12.dp),
    ) {
        Text(
            bannerText,
            color = if (met) Color.Black else amber,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("DISTANCE", color = accent.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge)
        Text(
            "target ${formatMiles(planRide.targetMiles)} mi  →  " +
                "${String.format(Locale.US, "%.2f", Units.distance(summary.distanceM, units))} ${Units.distanceLabel(units)}",
            color = if (met) accent else amber,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = accent.copy(alpha = 0.6f), style = MaterialTheme.typography.labelLarge)
        Text(value, color = accent, style = MaterialTheme.typography.titleLarge)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
```

Note: `shortBy` is computed in miles (the plan's authored unit) so the banner reads naturally regardless of the display unit; the comparison itself (`isMet`) is in meters.

- [ ] **Step 2: Verify it compiles** (Nav still calls the old `EndScreen` signature — fixed in Task 15.)

Run: `./gradlew :app:compileDebugKotlin`
Expected: May FAIL only in `Nav.kt`. `EndScreen.kt` itself must be error-free.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/EndScreen.kt
git commit -m "feat(ui): plan-aware End screen with target vs actual"
```

---

## Task 15: Wire up navigation

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/Nav.kt`

This connects all the new screens and fixes the call-site changes from Tasks 11 and 14. After this task the app compiles and runs end to end.

- [ ] **Step 1: Rewrite Nav**

Replace the contents of `Nav.kt` with:

```kotlin
package com.two17industries.rideman.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.two17industries.rideman.core.PlanRide
import com.two17industries.rideman.core.RideSummary

private enum class Dest { START, SETTINGS, RIDE, END, PLAN_PICKER, HISTORY }

@Composable
fun RidemanNav(vm: RideViewModel, onRideActiveChanged: (Boolean) -> Unit) {
    var dest by remember { mutableStateOf(Dest.START) }
    var lastSummary by remember { mutableStateOf<RideSummary?>(null) }
    var activePlanRide by remember { mutableStateOf<PlanRide?>(null) }

    LaunchedEffect(dest) { onRideActiveChanged(dest == Dest.RIDE) }

    val settings by vm.settings.collectAsState()
    val ui by vm.ui.collectAsState()
    val progress by vm.progress.collectAsState()
    val allRides by vm.allRides.collectAsState()

    when (dest) {
        Dest.START -> {
            StartScreen(
                nextUp = progress?.nextIncomplete(),
                planAvailable = vm.plan != null,
                onPlanRide = { dest = Dest.PLAN_PICKER },
                onFreeRide = { activePlanRide = null; vm.startRide(null); dest = Dest.RIDE },
                onHistory = { dest = Dest.HISTORY },
                onSettings = { dest = Dest.SETTINGS },
            )
        }
        Dest.PLAN_PICKER -> {
            BackHandler { dest = Dest.START }
            val plan = vm.plan
            if (plan == null) {
                dest = Dest.START
            } else {
                PlanPickerScreen(
                    plan = plan,
                    progress = progress,
                    onStart = { ride ->
                        activePlanRide = ride
                        vm.startRide(ride.id)
                        dest = Dest.RIDE
                    },
                    onBack = { dest = Dest.START },
                )
            }
        }
        Dest.SETTINGS -> {
            BackHandler { dest = Dest.START }
            SettingsScreen(
                current = settings,
                onSave = { vm.saveSettings(it) },
                onDone = { dest = Dest.START },
                onCancel = { dest = Dest.START },
            )
        }
        Dest.HISTORY -> {
            BackHandler { dest = Dest.START }
            HistoryScreen(
                rides = allRides,
                plan = vm.plan,
                progress = progress,
                units = settings.units,
                onBack = { dest = Dest.START },
            )
        }
        Dest.RIDE -> {
            BackHandler { lastSummary = vm.endRide(); dest = Dest.END }
            com.two17industries.rideman.ui.ride.RideScreen(
                state = ui,
                settings = settings,
                onEndRide = { lastSummary = vm.endRide(); dest = Dest.END },
            )
        }
        Dest.END -> {
            BackHandler { vm.persistLastRide(); dest = Dest.START }
            EndScreen(
                summary = lastSummary ?: vm.endRide(),
                units = settings.units,
                planRide = activePlanRide,
                tolerancePercent = vm.plan?.tolerancePercent ?: 0,
                onDone = { vm.persistLastRide(); dest = Dest.START },
            )
        }
    }
}
```

- [ ] **Step 2: Build the whole app**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full unit-test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (existing tests + PlanProgressTest + PlanLoaderTest).

- [ ] **Step 4: Update the project XML if present** (per global instruction for project files)

This is a Gradle/Android project (no `.xcodeproj`), so no project-file update is needed. Confirm new Kotlin files live under `app/src/main/java/com/two17industries/rideman/` and the asset under `app/src/main/assets/` — Gradle picks these up automatically.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/Nav.kt
git commit -m "feat(ui): wire Plan Picker, History, and plan-aware End into navigation"
```

---

## Task 16: Manual end-to-end verification

**Files:** none (verification only)

- [ ] **Step 1: Install and exercise the app**

Run: `./gradlew :app:installDebug` (with a device/emulator connected), then:
1. Start screen shows `PLAN RIDE` with a "Next: Wk 1 · Ride A — 2mi easy" subtitle, plus `FREE RIDE`, `HISTORY`, `SETTINGS`.
2. Tap `PLAN RIDE` → picker opens with Week 1 · Ride A auto-expanded. Tap `START RIDE` → ride screens appear.
3. End the ride → End screen shows the plan banner (met/short) + target → actual distance.
4. `DONE` → back to Start. Open `HISTORY` → the ride appears, badged `PLAN`; tap it to expand target/time/avg.
5. Do a `FREE RIDE` → End screen is the plain layout; it appears in History badged `FREE`.

- [ ] **Step 2: Verify the plan fail-safe (optional)**

Temporarily rename the asset (e.g. break the JSON), rebuild: `PLAN RIDE` should be disabled with "plan unavailable", while `FREE RIDE` and `HISTORY` still work. Restore the asset afterward.

---

## Self-Review

**Spec coverage:**
- Self-paced checklist (42 rides) → Tasks 2, 4, 5 (Plan model + asset + parser).
- Soft completion + 5% tolerance, short rides logged + slot open → Task 3 (logic), Task 14 (End banner).
- Suggest next, allow override → Task 12 (auto-expand `nextIncomplete`, browse to pick any slot).
- Intervals = guidance text only → Task 4 (guidance strings), Task 12 (shown in expanded row).
- History = all rides (plan + free) → Tasks 7, 13.
- Plan in `assets/plan.json`, parsed at startup → Tasks 4, 5, 10.
- Nullable `planRideId`, derived completion, migration → Tasks 6, 7, 8, 9.
- Grading in meters, display respects units → Task 3 (`METERS_PER_MILE`), Tasks 13/14 (`Units`).
- Fail-safe on parse failure → Task 10 (`runCatching`), Task 11 (`planAvailable`), Task 15 (null guards).
- Start/Picker/Ride/End/History screens → Tasks 11–15.

**Deviation:** Spec's "Room migration test" bullet is implemented as a careful migration + *manual* verification (Task 8 Step 3 / Task 16), not an automated `MigrationTestHelper` test, to avoid adding androidTest + schema-export infrastructure to this KISS app. Flagged here intentionally.

**Type consistency:** `PlanRide`, `Plan.byId`, `PlanAttempt`, `PlanGrading.isMet/threshold/METERS_PER_MILE`, `PlanProgress.isComplete/nextIncomplete/completedCount/total`, `startRide(planRideId)`, `saveRide(summary, track, planRideId)`, `parsePlanJson`, `PlanLoader.load`, and `formatMiles` (defined in `StartScreen.kt`, reused in `PlanPickerScreen.kt`/`EndScreen.kt`, same `ui` package) are used consistently across tasks. The `EndScreen`/`StartScreen` signature changes are both resolved in Task 15.

**Placeholder scan:** No TBD/TODO; every code step contains complete code.
