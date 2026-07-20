# RIDEMAN — 217 Industries redesign

**Date:** 2026-07-14
**Branch:** `217-redesign`
**Source:** `design_handoff_rideman_217/` (README + `RIDEMAN-1A.dc.html`, direction 1A "Terminal")

## Summary

Re-skin RideMan to the 217 Industries design system — pitch-black canvas, Orbitron display
face, IBM Plex Mono body, zero-radius corners, hairline neon borders, subtle glow — and add
two features to the ride display:

1. **Dash screen** — a 2×2 readout grid (SPEED / DISTANCE / DURATION / HEADING) on a single
   page, selectable and reorderable like the existing ride screens.
2. **Rotate button** — an explicit portrait⇄landscape toggle during a ride, replacing today's
   "lock to whatever orientation the ride started in" behavior.

Navigation and information architecture do not change.

## Deviations from the handoff

The handoff README is the design authority, with three deliberate exceptions. Each is recorded
here so nobody "fixes" them back later.

### 1. The dash is additive, not a replacement

README §5 frames the 2×2 grid as *replacing* the one-metric-per-page model ("**Was:** one metric
per swipe page … **Now:** a 2×2 readout grid"). **This is superseded by stakeholder direction:**
all five existing ride screens (Speed, Odometer, Compass, Altitude, Cadence) remain, and the dash
is a **sixth** screen — selectable, reorderable, and enabled by default in first position.

Consequence: the ride pager has **six** pages and six paginator dots, not five.

### 2. `BigMetric` keeps its large type

README §1 sets `displayLarge` to 54sp (down from 140sp). That size is correct *for a dash grid
cell*, where four values share a screen — but the prototype never drew the five single-metric
screens, which depend on large type to be legible on a handlebar at arm's length.

`displayLarge` = 54sp (the grid value). `BigMetric` gets its own Orbitron style at ~120sp.
Applying `displayLarge` to `BigMetric` would shrink those five screens to under half their
current size and make the app worse.

### 3. Elapsed time is dash-only

README §5 removes the `REC 24:18` element from the ride header. Since no `DURATION` metric
screen exists, the dash's DURATION cell becomes the **only** place elapsed time appears during
a ride. This is accepted: a rider who disables or reorders the dash loses time-at-a-glance, but
that is their own reversible choice. No standalone duration screen, no persistent header clock.

## Phasing

One branch, three reviewable phases.

**Phase 1 — Theme foundation.** Fonts, color, shape, glow, accent scope. No screen changes.
**Phase 2 — Ride changes.** Dash screen + rotate button. The only phase with behavior change.
**Phase 3 — Re-skin.** Start, Plan picker, End, History, Settings.

Phases 1 and 3 are a pure re-skin with **zero behavior change** — a useful review invariant. If a
flow changes or a test breaks in those phases, something is wrong. All meaningful testing lives
in phase 2.

---

## Phase 1 — Theme foundation

### Fonts (`app/src/main/res/font/`)

Already on disk at `/home/whinchman/experiments/RIDEMAN/Fonts/` (OFL-licensed; nothing to
download). Copy **only** the weights `Type.kt` names — the unused weights and every italic are
dead APK weight:

- **Orbitron** (static instances, not the variable font — Compose variable-font support is not
  worth the risk here): Regular, Medium, SemiBold, Bold, ExtraBold, Black.
- **IBM Plex Mono**: Regular, Medium, Bold.

Android resource names must be lowercase with underscores (`orbitron_bold.ttf`,
`ibm_plex_mono_regular.ttf`).

### `ui/theme/Type.kt`

Build two `FontFamily`s and route them through `RidemanTypography`:

```
displayLarge = Orbitron  Bold    54sp / 46sp line / 0sp tracking   // dash grid value
titleLarge   = Orbitron  Bold    20sp / 2sp tracking
labelLarge   = IbmPlexMono Bold  12sp / 1.6sp tracking             // small-caps labels
bodyLarge    = IbmPlexMono Medium 14sp
```

Plus a `bigMetric` style (Orbitron Bold ~120sp) for the five single-metric screens — see
Deviation 2.

**Rule:** Orbitron for headings and values only. IBM Plex Mono for all body and UI. Material's
default sans appears nowhere.

### `ui/theme/Color.kt`

```
bg        #0A0A0A   surface  #151515   surface-2 #1F1F1F
text      #E0E0E0   muted    #888888   dim       #4A4A4A
cyan      #00FFFF   magenta  #FF00FF   hotpink   #FF007F   (cursor)
warn      #FFCF3A   delete   #FF5252
accents (unchanged): Amber #FFC400 · AcidGreen #39FF14 · ElectricCyan #00F0FF · HotMagenta #FF2D95
borders: border-cyan rgba(0,255,255,0.20) · border-cyan-dim rgba(0,255,255,0.10)
         grid-line   rgba(0,255,255,0.12)
```

`Background` moves from `#050505` to `#0A0A0A`.

### `ui/theme/Theme.kt` — accent scope

The user-selectable accent drives the **ride and end screens only**. Every other screen (Start,
Plan picker, History, Settings) uses fixed cyan `#00FFFF`, with the accent honored only in the
Settings color swatches (which still preview live).

Implement by having the menu screens consume a fixed cyan constant directly. Do **not** change
what `LocalAccent` *means* depending on location — a composition local whose value depends on
where you read it is a trap. `LocalAccent` continues to mean "the user's chosen accent," and
only the ride/end composables read it.

### Shape and glow

All corners `RoundedCornerShape(0.dp)`. Every `RoundedCornerShape(50)` pill, rounded card, and
circular `StepButton` becomes a sharp rectangle. Status and plan dots become **squares**.

Glow is a **single low-radius layer** — `Shadow(color, blurRadius)` in a `TextStyle`, or
`Modifier.drawBehind`. Sunlight legibility comes first, so this is deliberately weaker than the
three-layer web glow. Big values may take an optional second wider layer. **Small mono labels get
no glow at all.**

---

## Phase 2 — Ride changes

### 2a. Dash screen (`RideScreen.GRID`)

**Naming:** the enum value is `GRID` and the composable is `ui/ride/DashGridScreen.kt`; the UI
label is **"Dash"**. The name `dash` is already taken by the `dash/` package (the T-Display BLE
handlebar dashboard — `DashBleClient`, `DashBroadcaster`, the `dashEnabled` setting, Settings'
"HANDLEBAR DASHBOARD" section). `GRID` keeps the two unambiguous in code and in search.

Add to the enum in `data/Settings.kt`, **first** in the default order:

```kotlin
enum class RideScreen { GRID, SPEED, ODOMETER, COMPASS, ALTITUDE, CADENCE }
```

Rendered as one page in the existing `HorizontalPager`, so it inherits pager wrap-around,
double-tap TTS, and the enable/reorder setting for free.

**Layout.** A 2×2 grid, row-major: **SPEED, DISTANCE / DURATION, HEADING**. Cells are centered
(label + value centered as a group) — a deliberate departure from 217's left-align, chosen for
glanceable readability.

The grid lines are drawn with a **1px gap over a grid-line-colored background**, not with
`Modifier.border` — the cells sit on `#0A0A0A` and the 1px gutters let the background show
through, producing only the interior cross with no outer border. In landscape the grid's right
edge comes from the side rail's left border.

Cell header — IBM Plex Mono 11sp, 0.11em tracking, on one line: the label in `muted` (`#888`)
followed by the unit suffix in `dim` (`#4A4A4A`):

| Cell | Header | Portrait | Landscape |
|---|---|---|---|
| SPEED | `SPEED · MPH` | 54sp | 56sp |
| DISTANCE | `DISTANCE · MI` | 40sp | 48sp |
| DURATION | `DURATION` (no unit) | 38sp | 44sp |
| HEADING | `HEADING · DEG` | 44sp / 22sp | 48sp / 24sp |

Unit labels come from the existing `core/Units.kt` and follow the unit-system setting — `MPH`/`MI`
in American, `KM/H`/`KM` in metric.

Values are Orbitron Bold, `lineHeight` ≈ 0.82em, in **`LocalAccent`** (the prototype renders them
cyan because cyan is one of the accent choices; on the ride screen every cyan is the accent). The
hairline grid lines likewise take the accent at ~12% alpha.

**Heading cell** shows degrees and cardinal. Portrait stacks `042°` over `NE` (6dp gap);
landscape sets them side-by-side, baseline-aligned (10dp gap). The cardinal is the **only fixed
color on the ride screen** — magenta `#FF00FF`, never the accent — so it stays distinguishable
from the degrees at a glance.

**Chrome.** Portrait has a header row (`> RIDING` in muted mono on the left, rotate button on the
right, 1px bottom border) and a bottom bar (paginator dots, then a full-width `END RIDE` button).
Landscape has **no header at all** — the 96dp side rail owns the rotate button (top), the vertical
dots (centered, absorbing slack), and `END` (bottom).

Paginator dots become **squares**: active 9dp filled accent with a soft glow, inactive 7dp at 30%
alpha with no glow.

No GPS indicator, no plan-target line, no `REC` element, no cadence readout on the dash.

### 2b. Duration formatting (`core/Units.kt`)

`elapsedMs` exists in `RideUiState` but has never been rendered, so **no duration formatter exists
anywhere in the codebase.** Add one next to the existing converters:

- under an hour → `MM:SS` (`24:18`)
- an hour and over → `H:MM:SS` (`1:04:18`)

This is the only genuinely new logic in the dash screen, and it is a pure function — it gets unit
tests.

### 2c. Elapsed time must tick independently of GPS

**`RideViewModel.collectLocation()` recomputes `elapsedMs` inside the location collector.** Today
nothing renders it, so nobody notices — but on the dash, the clock will visibly **stall** whenever
GPS fixes stop arriving (under trees, in a garage, in a tunnel). A frozen clock reads as a frozen
app.

Drive `elapsedMs` from a **1 Hz ticker** in the ViewModel, independent of the location stream.
`collectLocation()` stops touching `elapsedMs` entirely.

### 2d. Rotate button and orientation

**State.** A `rideOrientation` (`PORTRAIT` | `LANDSCAPE`) persisted in `RidemanSettings` /
DataStore — **sticky across rides**, so a bar-mounted rider who rides in landscape gets landscape
every ride without touching anything. This is the actual fix for "locked into the wrong
orientation at the start of a ride."

The rotate button is the **only** control for it. It is deliberately *not* surfaced in the Settings
screen: a preference you set by using the thing beats a preference you set in a menu.

**Behavior.** `MainActivity.onRideActive` currently sets `SCREEN_ORIENTATION_LOCKED`, which
freezes the ride into however the phone happened to be held. Replace with:

- **ride start** → apply the stored orientation explicitly (`SCREEN_ORIENTATION_PORTRAIT` or
  `..._LANDSCAPE`).
- **button tap** → flip the flag, persist it, apply it.
- **ride end** → `SCREEN_ORIENTATION_UNSPECIFIED` (unchanged).

The app never free-rotates on the accelerometer during a ride. `AndroidManifest.xml` already
declares `configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden"`,
so the flip recomposes without recreating the activity — no mid-ride state loss.

**Appearance.** A sharp 1px-bordered square with the glyph `⟳` (U+27F3), accent-colored, faint box
glow, no fill. Portrait: 30×30dp, top-right of the ride header. Landscape: full rail width at the
top of the side rail, above the dots and END.

### 2e. Settings migration (`data/Settings.kt`)

`screenOrder` is persisted as a CSV of enum names and **doubles as the enable list** (absent =
disabled). Existing installs have a saved CSV that predates `GRID`, so without a migration the new
dash would silently appear **disabled**.

The naive fix — "if `GRID` is absent from the CSV, add it" — is **wrong**: it would resurrect the
dash every time a user deliberately disables it. It must be a **one-shot migration**:

- Add a `grid_migrated` boolean key to DataStore.
- On read, if the flag is unset **and** a saved `screenOrder` exists, prepend `GRID` (enabled,
  first) and set the flag.
- After that, the user's choice is authoritative forever.
- Fresh installs take `GRID` from the enum default and skip the migration path entirely.

Also add the `"Dash"` label for `RideScreen.GRID` to `screenName()` in `SettingsScreen.kt`. The
existing reorder UI already lists any enum value missing from `screenOrder` as a disabled row, so
it needs no other change.

---

## Phase 3 — Re-skin

Mechanical, high-volume, low-risk. Zero behavior change.

- **Start** — `BIKEMAN` → `> RIDEMAN` wordmark with a blinking hot-pink block cursor (`▮`,
  `#FF007F`, hard 1s step blink); hairline "NEXT UP" panel; primary `> PLAN RIDE` bordered button
  (faint cyan fill + glow, `→`); `> FREE RIDE`; HISTORY/SETTINGS split row; dim version line.
- **Plan picker** — `◀ 14-WEEK PLAN`; `> PHASE n · NAME`; week groups; ride rows with square status
  boxes (`✓` when complete); expanded ride as a hairline card with TARGET (Orbitron) / PACE /
  guidance / `★ LONG RIDE`; bottom `START RIDE`.
- **End** — `RIDE COMPLETE`; graded banner (cyan `✓ TARGET MET · SLOT COMPLETE`, or amber `#FFCF3A`
  + "SHORT"); `target → actual`; TIME / MAX / AVG (+ DISTANCE for free rides); `DONE`. Landscape
  lays the stats out as a single 4-up row. Uses `LocalAccent`, like the ride screen.
- **History** — `◀ HISTORY` + `SELECT`; `↑ Upload past rides to Strava`; `> PLAN PROGRESS` panel;
  expandable ride rows with a red `✕ DELETE RIDE`; selection mode.
- **Settings** — sharp segmented controls; square `−`/`+` stepper; four sharp accent swatches
  (selected = 2px white ring) labeled "drives ride display"; `> HANDLEBAR DASHBOARD`; `> STRAVA`;
  `> RIDE SCREENS` reorder rows.

**Copy and cue rules (global):**

- Section headers and the wordmark take a grey `> ` prefix. Back affordances use `◀`.
- **No emoji.** The repo currently ships `🗑`, `🎉`, `⭱` — replace: trash → `✕`, arrows → `↑` / `→`,
  drop `🎉`. Unicode glyphs (`>`, `→`, `◀`, `▲▼`, `★`, `✓`, `✕`, `⟳`, `▮`) are fine.
- The blinking cursor is the **only** animation in the app. Press/hover states are a color swap
  only, ~120ms, no scale.

**Note:** `StartScreen.kt` has an uncommitted `BIKEMAN` → `> RIDEMAN` edit on `main` that
overlaps this phase.

---

## Testing

The re-skin phases are verified on-device by eye — they are pure Compose styling and unit tests
would only assert that constants equal themselves. The meaningful tests are all in phase 2, and
all of them are pure functions or plain state (no Compose needed):

1. **Duration formatting** — zero; sub-minute; minute rollover; the `59:59` → `1:00:00` hour
   rollover. New logic, and the most likely thing to be quietly wrong.
2. **Settings migration** — a fresh install has `GRID` first and enabled; a legacy CSV gets `GRID`
   prepended exactly once; **a user who disables `GRID` and reloads still has it disabled.** The
   third case is the one that catches the resurrection bug.
3. **Orientation flag** — toggling persists; the next ride reads it back.
4. **`PagerWrapTest` with `count = 6`** — the pager is the dash's entire delivery mechanism, so
   assert the new count explicitly rather than assuming the existing tests cover it.

The 1 Hz elapsed ticker resists a cheap unit test (it would require abstracting the clock).
Verify it on-device: start a ride, kill GPS, confirm the DURATION cell keeps counting.

## State

No new domain state beyond two persisted settings: **`rideOrientation`** and the one-shot
**`grid_migrated`** flag. Everything else uses the existing `RidemanSettings`, `RideUiState`, and
`PlanProgress`.

## Files

**Phase 1:** `ui/theme/Type.kt`, `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `res/font/*`
**Phase 2:** `data/Settings.kt`, `core/Units.kt`, `ui/RideViewModel.kt`, `ui/ride/RideScreen.kt`,
`ui/ride/DashGridScreen.kt` (new), `ui/ride/MetricScreens.kt`, `ui/SettingsScreen.kt`,
`MainActivity.kt`
**Phase 3:** `ui/StartScreen.kt`, `ui/PlanPickerScreen.kt`, `ui/EndScreen.kt`,
`ui/HistoryScreen.kt`, `ui/SettingsScreen.kt`
