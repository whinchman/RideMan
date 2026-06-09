# Portrait + Landscape Orientation Support — Design

**Date:** 2026-06-09
**Status:** Approved, ready for planning

## Goal

Make the app usable in both vertical (portrait) and horizontal (landscape) orientations.
Today the activity is hard-locked to portrait via `android:screenOrientation="portrait"`
in `AndroidManifest.xml`. We want the app to rotate, while keeping the KISS, glance-and-go
character intact — especially during a ride, where an accidental flip would be jarring.

## Orientation Policy

- **Manifest:** remove `android:screenOrientation="portrait"` from the `MainActivity`
  entry and replace it with `android:screenOrientation="fullUser"`. `fullUser` allows
  every orientation the user's system auto-rotate setting permits, and respects the
  device's rotation-lock toggle (polite default rather than forcing rotation).
- **Lock during the ride:** `RideScreen` gains a `DisposableEffect` that, on enter, sets
  the hosting activity's `requestedOrientation` to
  `ActivityInfo.SCREEN_ORIENTATION_LOCKED` (pins the *current* orientation without forcing
  a specific one — no flip on entry), and on dispose restores
  `ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED`.
  - Effect: Start, Settings, History, Plan Picker, and End all auto-rotate freely. The
    instant the rider is on the ride screen, orientation is frozen to whatever it was;
    it releases when they tap END RIDE and land on the End screen.
  - Side benefit: freezing orientation during the ride avoids Activity recreation (and any
    associated transient state loss) while location tracking is active.

## Ride Screen — Orientation-Aware Layout

`RideScreen` reads the current orientation from `LocalConfiguration.current.orientation`
and branches its **outer** arrangement only. The per-metric `BigMetric` composable does
**not** change — it is already a centered column (label / huge number / unit) that reflows.

- **Portrait (unchanged):** a `Column` — `HorizontalPager` with `weight(1f)` fills the
  top, `BottomBar` (centered paginator dots + full-width END RIDE button) pinned below.
- **Landscape:** a `Row` —
  - The `HorizontalPager` fills the left/center region at full height (`weight(1f)`), so
    the big number stays as large as possible.
  - A slim right-edge rail holds the paginator dots stacked **vertically** and a compact
    `END` button at the bottom.
- Swipe-to-page (horizontal swipe through the circular queue) works unchanged in both
  orientations.

Sketch (landscape):

```
                    ●
                    ○
     18             ○
     mph            ○
                    ○
                 [END]
```

The existing `BottomBar` composable is refactored: the dots-rendering and the END button
become small reusable pieces so both the portrait bottom bar and the landscape side rail
can compose them without duplicating logic.

## Start + End Screens — Landscape Safety

Both are vertically-centered `Column`s with no scroll today, so in short landscape height
their content can clip.

- **Start screen:** wrap its centered column in
  `verticalScroll(rememberScrollState())`. Content stays centered when it fits and scrolls
  when it does not. No layout restructuring.
- **End screen:** wrap in `verticalScroll(rememberScrollState())` as well, **and** make the
  four summary stats (Total Time, Distance, Max Speed, Average Speed) an orientation-aware
  layout:
  - **Portrait:** single centered column (unchanged).
  - **Landscape:** a 2×2 grid (two `Row`s of two stats) to use the extra width.

Settings, History, and Plan Picker already scroll (`verticalScroll` / `LazyColumn`) and
need no changes.

## Components Touched

| File | Change |
|------|--------|
| `app/src/main/AndroidManifest.xml` | `portrait` → `fullUser` on `MainActivity` |
| `ui/ride/RideScreen.kt` | Orientation branch (Column vs Row + side rail); ride-lock `DisposableEffect`; refactor `BottomBar` into reusable dots + END button |
| `ui/StartScreen.kt` | Add `verticalScroll` |
| `ui/EndScreen.kt` | Add `verticalScroll`; 2×2 stat grid in landscape |

`BigMetric` and the individual metric screens are unchanged.

## Testing

This is layout/orientation work with no new pure logic, so existing unit tests
(`core/`, `data/`) remain the coverage for behavior and should continue to pass.

Manual verification on device/emulator:
1. Rotate on Start, Settings, History, Plan Picker, End → each rotates and remains
   readable / scrollable with no clipped content.
2. Start a ride in portrait, then rotate the device → screen stays portrait (locked).
3. Start a ride in landscape → side rail layout; big number large; dots + END reachable;
   swipe paging works.
4. End the ride → orientation lock releases; End screen rotates freely and shows the 2×2
   grid in landscape.

## Out of Scope

- A user-facing setting to choose/force orientation (the chosen policy is auto-everywhere,
  lock-during-ride, with no new UI).
- Tablet / large-screen specific layouts beyond what the above reflow provides.
