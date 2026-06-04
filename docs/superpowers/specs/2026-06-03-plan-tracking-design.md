# BIKEMAN — 14-Week Plan Tracking

**Date:** 2026-06-03
**Status:** Approved design, ready for implementation planning
**App:** RIDEMAN (package `com.two17industries.rideman`)

## Summary

Add the ability to ride against the *Couch-to-10-Miles* 14-week plan. When starting a
ride, the user chooses a **Plan Ride** or a **Free Ride**. Plan rides are tagged to a
specific plan slot; after the ride, real results are shown against that slot's target.
A new **History** screen browses all rides (plan and free), and a **Plan Picker** lets the
user pick which plan ride to do. The plan itself is read-only in the app.

The plan reference document lives at `couch-to-10-miles-cycling-plan.md` in the repo root.

## Decisions (locked)

| Topic | Decision |
|-------|----------|
| Pacing | **Self-paced checklist** — 42 ordered rides (14 weeks × 3). No calendar dates. Any ride can be done or repeated at any time. |
| Completion | **Soft completion** — a slot is complete when any tagged ride's distance ≥ target − 5% tolerance. |
| Short rides | Still saved and still tagged to the slot (as an attempt). The slot stays open. |
| Selection | **Suggest next, allow override** — picker auto-expands the next incomplete ride; user can browse and pick any slot. |
| Intervals | **Guidance text only** — interval structure shown as read-only coaching text. No timers, no per-interval tracking. |
| History scope | **All rides** — plan + free, one chronological list. |
| Plan storage | **`assets/plan.json`**, parsed at startup. Editable without a code change. |
| Ride↔slot link | **Nullable `planRideId` column** on `RideEntity`. `null` = free ride. |
| Completion state | **Derived by query** — no progress/status table. |
| Grading unit | Compared in **meters** (target miles → m). Display respects the user's unit setting. |

## Architecture

### Plan data (`assets/plan.json`)

The plan is a bundled asset, parsed once at startup with **kotlinx.serialization**
(JVM-testable, unlike `org.json` which is stubbed in unit tests).

Build changes required:
- Add the `org.jetbrains.kotlin.plugin.serialization` plugin (version catalog + `app/build.gradle.kts`).
- Add `org.jetbrains.kotlinx:kotlinx-serialization-json` dependency.

JSON shape:

```json
{
  "title": "Couch-to-10-Miles Cycling Plan",
  "tolerancePercent": 5,
  "phases": [
    {
      "number": 1,
      "name": "Build the base",
      "weeks": [
        {
          "week": 1,
          "recovery": false,
          "rides": [
            { "slot": "A", "kind": "easy",      "targetMiles": 2.0, "pace": "easy", "longRide": false, "guidance": "" },
            { "slot": "B", "kind": "endurance", "targetMiles": 3.0, "pace": "easy", "longRide": true,  "guidance": "Your longer endurance ride." },
            { "slot": "C", "kind": "quality",   "targetMiles": 2.5, "pace": "easy", "longRide": false, "guidance": "" }
          ]
        }
      ]
    }
  ]
}
```

Notes:
- `pace` ∈ `easy | steady | brisk` (mirrors the doc's pace legend).
- The doc's "Long ride" column is metadata, not a fourth ride. It maps to `longRide: true`
  on whichever of A/B/C is the long ride that week.
- Week 14 Ride B ("rest or 3 mi spin") is modeled as a normal ride with a guidance note.

### Domain model (`core/`, pure Kotlin)

```
data class PlanRide(
    val id: String,            // "w{week}{slot}", e.g. "w3B" — stable, used as planRideId
    val phaseNumber: Int,
    val phaseName: String,
    val week: Int,
    val slot: String,          // "A" | "B" | "C"
    val kind: String,          // "easy" | "endurance" | "quality"
    val targetMiles: Double,
    val pace: Pace,            // EASY | STEADY | BRISK
    val longRide: Boolean,
    val guidance: String,
    val recoveryWeek: Boolean,
)

class Plan(val title: String, val rides: List<PlanRide>, val tolerancePercent: Int)
```

- `PlanLoader` reads `assets/plan.json` → `Plan`. Parse failure throws; caller handles.
- `PlanProgress` (pure logic) takes the `Plan` + the list of plan-tagged rides and computes:
  - `isComplete(planRideId)` — any attempt's distance ≥ `targetMeters × (1 − tolerance)`.
  - `nextIncomplete()` — first ride in plan order that is not complete.
  - `completedCount()` / total (42).

### Persistence (`data/`)

- `RideEntity` gains `val planRideId: String? = null`.
- Room DB version `1 → 2` with a migration: `ALTER TABLE rides ADD COLUMN planRideId TEXT`.
  (`RidemanDatabase` currently builds with no migrations; add the migration so existing
  ride data survives.)
- `RideDao` additions:
  - `getAllRides(): Flow<List<RideEntity>>` (ordered by `startedAt` desc) — for History.
  - `getPlanTaggedRides(): Flow<List<RideEntity>>` (where `planRideId IS NOT NULL`) — for progress.
- `RideRepository.saveRide(...)` gains a `planRideId: String?` parameter, written to the entity.

### ViewModel / nav

- `RideViewModel.startRide(planRideId: String? = null)` — stores the active `planRideId`.
- `persistLastRide()` writes the ride with its `planRideId`.
- `endRide()` returns the existing `RideSummary`; the End screen resolves the `PlanRide`
  target from the `Plan` to render target-vs-actual.
- `Nav.kt` `Dest` enum gains `PLAN_PICKER` and `HISTORY`. Flows:
  - Start → `PLAN_PICKER` → (select slot) → `RIDE(planRideId)` → `END(plan)` → Start
  - Start → `RIDE(null)` → `END(free)` → Start  *(unchanged)*
  - Start → `HISTORY` → back to Start

### Fail-safe

If `plan.json` fails to load/parse:
- `PLAN RIDE` on the Start screen is disabled (with a short message/toast).
- Free Ride and History remain fully functional.
- The next-up subtitle on the Start screen is omitted.

## Screens

All screens follow the existing KISS aesthetic: dark background, large bright themed
accent text (`LocalAccent`), big tap targets.

### Start (modified)
Plan-first layout:
- `PLAN RIDE` — primary button; subtitle shows the next incomplete ride
  (e.g. "Next: Wk 3 · Ride B — 7mi easy"). Disabled if plan failed to load.
- `FREE RIDE` — secondary (outlined) button → current ride flow with `planRideId = null`.
- Row: `HISTORY` · `SETTINGS` (subtle).
- Version/build/commit footer (unchanged).

### Plan Picker (new)
Accordion list, no next-up card:
- Grouped **Phase → Week → 3 ride rows**.
- Completed slots: dimmed + ✓. Incomplete: ○, tappable.
- Tapping a row expands it inline (one open at a time) showing: big target distance,
  pace description, guidance text, and a ★ LONG RIDE flag when applicable.
- On open, the **next incomplete ride is auto-expanded**.
- Sticky bottom button reads **`START RIDE`** and starts the currently expanded slot.

### Ride (unchanged)
Existing ride sub-screens. No changes beyond carrying `planRideId` through the session.

### End screen
- **Free ride:** unchanged (TIME, DISTANCE, MAX, AVG).
- **Plan ride:** adds, above the stats —
  - Slot label ("Week 3 · Ride B — endurance").
  - A completion banner: green `✓ TARGET MET · SLOT COMPLETE`, or
    amber `LOGGED · X mi SHORT — SLOT STAYS OPEN`.
  - A target-vs-actual **distance** line (the only graded line).
  - Time / avg / max shown as plain stats (no judgment), consistent with the plan's
    "pace doesn't matter" ethos.

### History (new)
- Plan-progress header: `N / 42 rides · Week W` + progress bar.
- Single chronological list (plan + free interleaved), **no filter tabs**.
- Each row badged `PLAN`/`FREE`. Accordion expanding rows (same pattern as the picker):
  - Collapsed: date · badge · summary line (slot + distance, or just distance for free).
  - Expanded plan ride: target + ✓/✗, time, avg speed.
  - Expanded free ride: time, avg, max (no target line).

## Data flow

1. Startup → `PlanLoader` parses `assets/plan.json` → `Plan` (held by ViewModel/repo).
2. Start screen / Plan Picker → `PlanProgress` combines `Plan` + plan-tagged rides
   (from `getPlanTaggedRides()`) → completion set + next-incomplete.
3. User picks a slot → `startRide(planRideId)`.
4. Ride runs (existing tracking).
5. `endRide()` → `RideSummary`; End screen resolves the target via `Plan` and renders
   target-vs-actual using the 5% tolerance.
6. `persistLastRide()` → `RideRepository.saveRide(summary, track, planRideId)`.
7. History → `getAllRides()`; plan rows resolve their target via `Plan`.

## Error handling

- **Plan parse failure:** disable plan features gracefully (see Fail-safe); free ride + history unaffected.
- **Unknown `planRideId`** on a stored ride (e.g. plan.json edited and a slot removed):
  treat the ride as a free ride in History (show its stats, no target line); ignore it for progress.
- **Room migration:** explicit `MIGRATION_1_2`; no destructive fallback, so existing rides persist.

## Testing

Core logic is pure Kotlin in `core/` and unit-tested on the JVM (matching existing
`core/*Test.kt` style):

- **Plan parsing:** valid JSON → 42 rides, ids unique and well-formed (`w{week}{slot}`),
  rides in plan order, phase/week/long-ride flags correct.
- **Completion logic:** at/above target completes; 5% tolerance boundary (e.g. 6.65mi
  clears a 7mi target, 6.64mi does not); short ride does not complete; multiple attempts
  on one slot — any clearing attempt completes it.
- **Next-incomplete selection:** returns the first incomplete ride in plan order; returns
  null/"plan done" when all 42 complete.
- **Units in grading:** target-miles → meters comparison correct regardless of the user's
  display unit setting.
- **Migration:** `MIGRATION_1_2` adds the column and preserves existing rows (Room
  migration test).

## Out of scope (future)

- Structured interval tracking / interval trainer.
- Editing the plan in-app.
- Calendar/date anchoring, streaks, "you're behind" nudges.
- Per-ride GPS map / route review in History.
