# Handoff: RIDEMAN — 217 Industries visual alignment (direction 1A "Terminal")

## Overview
Re-skin the existing **RideMan** Android app (`whinchman/RideMan`, Kotlin + Jetpack Compose + Material 3)
to the **217 Industries** design system: pitch-black canvas, Orbitron display + IBM Plex Mono body,
sharp square corners (no pills), hairline neon borders, and subtle neon glow. The information
architecture and navigation do **not** change — this is a visual/theme pass plus two structural
changes to the ride display (below).

## About the design files
The `.dc.html` files in this bundle are **design references** — HTML prototypes showing the intended
look and behavior. They are **not** code to ship. The task is to reproduce these designs **in the
existing Compose codebase**, editing the real composables and theme, using Compose idioms
(`Modifier`, `Brush`, `TextStyle`, `RoundedCornerShape(0.dp)`, etc). Do not introduce a WebView or
port HTML.

- `RIDEMAN-1A.dc.html` — the resolved direction. Full system: Start, Plan picker, Ride (2×2 grid),
  End summary, History, Settings, plus **portrait and landscape** ride/end.
- `RIDEMAN.dc.html` — the earlier 3-option exploration (1a/1b/1c). Reference only; **1A won**.

## Fidelity
**High-fidelity.** Colors, type, spacing, glow, and layout are final. Recreate pixel-close using the
values in Design Tokens below. Where the prototype and the current app disagree, the prototype wins.

---

## What changes vs. the current app

### 1. Typography (`app/src/main/java/com/two17industries/rideman/ui/theme/Type.kt`)
Add two font families and route them through `RidemanTypography`:
- **Orbitron** — all large ride values, the wordmark, screen titles, and button labels (display face).
- **IBM Plex Mono** — everything else: labels, body, metadata, controls.

Add the fonts to `app/src/main/res/font/` (download from Google Fonts: Orbitron 400–900,
IBM Plex Mono 400–700) and build `FontFamily`s. Then:
```
displayLarge = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold,   // was Black/140sp
                         fontSize = 54.sp, lineHeight = 46.sp, letterSpacing = 0.sp)
titleLarge   = TextStyle(fontFamily = Orbitron, fontWeight = FontWeight.Bold,
                         fontSize = 20.sp, letterSpacing = 2.sp)
labelLarge   = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.Bold,
                         fontSize = 12.sp, letterSpacing = 1.6.sp)   // small caps labels
bodyLarge    = TextStyle(fontFamily = IbmPlexMono, fontWeight = FontWeight.Medium, fontSize = 14.sp)
```
Rule: **Orbitron for headings/values only; IBM Plex Mono for all body and UI.** Never Material's
default sans for body.

### 2. Color (`ui/theme/Color.kt`, `ui/theme/Theme.kt`)
- Canvas `Background` becomes **`#0A0A0A`** (was `#050505`).
- Add surface/border/muted tokens (see Design Tokens).
- Keep the four selectable accents **exactly as-is** (they already match 217): `#FFC400`, `#39FF14`,
  `#00F0FF`, `#FF2D95`.
- **Accent scope change (per stakeholder):** the selectable accent drives the **Ride display and the
  End summary only**. Every other screen (Start, Plan picker, History, Settings) uses a fixed
  **cyan `#00FFFF`**, with the accent honored only lightly (e.g. the color-theme swatches). Implement
  by providing cyan to those screens instead of `LocalAccent`, and passing `LocalAccent.current` only
  into the ride/end composables. The Settings color-theme picker still previews live.

### 3. Shape — remove all pills (global)
Everywhere the app uses `RoundedCornerShape(50)` / rounded buttons/cards (`StartScreen`,
`SettingsScreen` `OptionPill`/`ColorPill`/`StepButton`, `PlanPickerScreen`, `HistoryScreen`,
`EndScreen`), switch to **`RoundedCornerShape(0.dp)`** (sharp). Buttons become bordered rectangles:
1px hairline neon border, optional faint fill + glow on the primary. `StepButton` circles become
sharp squares. Status/plan dots become **squares**, not circles.

### 4. Glow (subtle — sunlight legibility first)
Apply a soft neon halo to headings and large values via a layered shadow. In Compose, the cleanest
route is `Modifier.drawBehind {}` or a `style = TextStyle(shadow = Shadow(color, blurRadius))`.
Use a **single-layer, low-radius** shadow (see token `glow-sm`) — not the full 3-layer web glow —
so numbers stay readable outdoors. Large ride values get slightly more; small mono labels get none.

### 5. Ride display — structural changes (README-critical)
`ui/ride/RideScreen.kt`, `ui/ride/MetricScreens.kt`:
- **Was:** one metric per swipe page (`BigMetric` = label / huge number / unit).
- **Now:** a **2×2 readout grid** on one page, matching the bike-mounted display: **SPEED, DISTANCE,
  DURATION, HEADING**. Each cell = hairline-bordered (1px `rgba(0,255,255,0.12)` grid lines via a
  1px-gap grid on a faint-neon background), with the **unit in the cell header** (`SPEED · MPH`,
  `DISTANCE · MI`, `DURATION` has none, `HEADING · DEG`).
- **Center each cell's contents** (label + value centered as a group) — deviates from the strict 217
  left-align, chosen for glanceable readability.
- **Heading cell:** show **both** degrees and cardinal. In **portrait**, stack `042°` (cyan/accent)
  over `NE` (magenta `#FF00FF`). In **landscape**, place them side-by-side baseline-aligned.
- The other RideScreen entries (Compass, Altitude, Cadence) remain as additional swipe pages / or fold
  into the grid per product preference; the 2×2 is the default first page. Keep the existing pager,
  `PagerWrap` wrap-around, double-tap TTS, and the enable/reorder setting.
- **Remove** the `REC 24:18` element from the ride header/status area (both orientations).
- **Add a rotate toggle button** to the ride screen (both orientations): a sharp 1px-bordered square
  with a `⟳` glyph. Portrait: top-right of the ride header. Landscape: top of the side rail, above the
  paginator and END. **Behavior:** it should let the rider **manually switch portrait⇄landscape on
  demand**, and the app should otherwise **lock orientation during a ride** (do not free-rotate on the
  accelerometer). Rationale: today it locks to whatever rotation the ride started in (bad), and free
  rotation flips mid-ride (also bad); an explicit button fixes both. Implementation: drive
  `activity.requestedOrientation` from a ViewModel/state flag toggled by the button; default the ride
  to `SCREEN_ORIENTATION_LOCKED` and swap between `..._PORTRAIT` / `..._LANDSCAPE` on tap.

### 6. Landscape ride/end
- **Ride landscape:** the existing `SideRail` layout is correct — 2×2 grid fills the left, a right rail
  holds (top→bottom) the rotate button, the vertical paginator dots, then the `END` button.
- **End landscape:** stats laid out as a single 4-up row (TIME / DISTANCE / MAX SPEED / AVG SPEED).

### 7. Terminal cues & copy (217 voice)
- Section headers and the wordmark take a grey `> ` prefix; the wordmark is `> RIDEMAN▮` with a blinking
  **hot-pink** block cursor (`#FF007F`, hard 1s step blink). Back affordances use `◀`.
- The blinking cursor is the **only** animation. Hover/press = color swap only, ~120ms, no scale.
- **No emoji** anywhere (the current repo uses `🗑`, `🎉`, `⭱` — replace: trash → `✕`, arrows → `↑`/`→`,
  drop `🎉`). Unicode glyphs (`>`, `→`, `◀`, `▲▼`, `★`, `✓`, `✕`, `⟳`, `▮`) are fine.
- Rename the wordmark from **BIKEMAN → RIDEMAN** (the app currently renders "BIKEMAN" in `StartScreen`).

---

## Screens / Views
Detailed layout/component specs for each screen are best read directly off `RIDEMAN-1A.dc.html`
(open in a browser). Summary:

- **Start** — `> RIDEMAN` wordmark + cursor; hairline "NEXT UP" panel; primary `> PLAN RIDE` bordered
  button (faint cyan fill + glow, `→`); `> FREE RIDE`; a HISTORY/SETTINGS split row; dim version line.
- **Plan picker** — `◀ 14-WEEK PLAN` title; `> PHASE n · NAME` dim label; week groups; ride rows =
  square status box (`✓` filled when complete) + `Ride X · kind` + target; one expanded ride =
  hairline-bordered card with TARGET (big Orbitron) / PACE / guidance / `★ LONG RIDE` chip; bottom
  `START RIDE`.
- **Ride** — see section 5 (2×2 grid, rotate button, paginator, END).
- **End** — `RIDE COMPLETE`; plan-graded banner (cyan-filled `✓ TARGET MET · SLOT COMPLETE` when met,
  amber `#FFCF3A` tint + "SHORT" when not); `target → actual` line; stat rows TIME / MAX / AVG (+ DISTANCE
  for free rides); `DONE`.
- **History** — `◀ HISTORY` + `SELECT`; `↑ Upload past rides to Strava`; `> PLAN PROGRESS` panel
  (`10 / 42 rides`); expandable ride rows (date, Strava chip, PLAN/FREE tag, `Wk · Ride — dist`, and on
  expand: target/time/avg + red `✕ DELETE RIDE`). Selection mode: CANCEL / n selected / ALL / DELETE.
- **Settings** — `SETTINGS` + bordered `CANCEL`; `> UNITS` segmented (sharp); `> CADENCE PULSE` segmented
  + square −/+ stepper around Orbitron RPM; `> COLOR THEME` = four sharp accent swatches (selected =
  2px white ring), labeled "drives ride display"; `> HANDLEBAR DASHBOARD` (T-Display BLE on/off + state);
  `> STRAVA` (connected/auto-upload/disconnect); `> RIDE SCREENS` reorder rows (square ✓ enable + ▲▼);
  `SAVE`.

---

## Interactions & behavior
- Preserve all current navigation (`ui/Nav.kt` `Dest` graph), the `HorizontalPager` + `PagerWrap`
  wrap-around, double-tap TTS readout, `SoundPool` cadence click, Strava upload states, and the
  delete-confirm dialog. Only visuals + the two ride changes (grid, rotate/orientation) differ.
- Blinking cursor: `1s` step, hard on/off. Hover/press: color swap only.
- Orientation: ride locks; rotate button toggles (section 5).

## State management
No new domain state beyond a **ride-orientation flag** (portrait|landscape) held in `RideViewModel`
(or nav-level state), toggled by the rotate button and applied via `activity.requestedOrientation`.
Everything else uses existing `RidemanSettings` / `RideUiState` / `PlanProgress`.

## Design tokens
```
# Canvas / surfaces
bg            #0A0A0A      surface       #151515      surface-2   #1F1F1F
text          #E0E0E0      muted         #888888      dim         #4A4A4A

# Neon (menus = cyan; accent = user-selectable, ride/end only)
cyan          #00FFFF      magenta       #FF00FF      hotpink (cursor) #FF007F
accent presets: Amber #FFC400 · Acid Green #39FF14 · Electric Cyan #00F0FF · Hot Magenta #FF2D95
warn/amber (short/strava) #FFCF3A       delete/red  #FF5252

# Borders (1px hairline)
border-cyan       rgba(0,255,255,0.20)     border-cyan-dim  rgba(0,255,255,0.10)
grid-line         rgba(0,255,255,0.12)     magenta          rgba(255,0,255,0.20)

# Glow (subtle — single layer for values, none for small labels)
glow-cyan-sm      0 0 5px rgba(0,255,255,0.45)  (+ optional 0 0 16px rgba(0,255,255,0.20) on big values)
glow-magenta-sm   0 0 5px rgba(255,0,255,0.40)
glow-hotpink      0 0 8px rgba(255,0,127,0.60)
box-glow (button) 0 0 14px rgba(0,255,255,0.15) + 1px inset border

# Radius — sharp
all corners 0dp   (was RoundedCornerShape(50))

# Spacing (existing 4px base is fine): 4 8 12 16 24 32 48 64

# Type (see Type.kt section)
display Orbitron · body/ui IBM Plex Mono · body line-height 1.625
ride value sizes (portrait): speed 54 · distance 40 · duration 38 · heading 44/NE 22
```

## Assets
- **Fonts:** Orbitron + IBM Plex Mono from Google Fonts → `res/font/` (self-host). No CDN in-app.
- **Icons:** none required — all affordances are unicode glyphs (`>`, `→`, `◀`, `▲▼`, `★`, `✓`, `✕`,
  `⟳`, `▮`). If a vector is ever needed, use inline sharp-cornered SVG, 1.5–2px stroke, `currentColor`.
- No logo file exists; the wordmark is type.

## Files in this bundle
- `RIDEMAN-1A.dc.html` — resolved hi-fi design (open in a browser; portrait + landscape).
- `RIDEMAN.dc.html` — earlier 1a/1b/1c exploration (reference only).

## Source files to edit (in whinchman/RideMan)
- `ui/theme/Type.kt`, `ui/theme/Color.kt`, `ui/theme/Theme.kt` — fonts, palette, accent scope.
- `ui/StartScreen.kt` (BIKEMAN→RIDEMAN, sharp buttons, terminal cues).
- `ui/ride/RideScreen.kt`, `ui/ride/MetricScreens.kt` — 2×2 grid, centered cells, unit-in-header,
  heading stack, remove REC, rotate button + orientation lock.
- `ui/PlanPickerScreen.kt`, `ui/EndScreen.kt`, `ui/HistoryScreen.kt`, `ui/SettingsScreen.kt` — sharp
  corners, glow, mono/Orbitron, de-emoji, cyan menus.
- `MainActivity.kt` — wire ride-orientation flag to `requestedOrientation`.
