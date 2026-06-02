# Bike Helper — Design Spec

**Date:** 2026-06-02
**Author:** Will Hinchman (with Claude)
**Status:** Approved for planning

## Purpose

A K.I.S.S. Android app to help Will with casual cycling. Every existing app is too
complicated, hard to read, or ad-riddled. This one shows one piece of information per
screen, in big bright neon on a dark background, readable at a glance in direct sun.
It is a personal, sideloaded app — not a Play Store release.

## Target device

- **Pixel 10 Pro XL**, Android 16 (API 36).
- Confirmed hardware: barometer (pressure sensor), magnetometer + gyro (rotation
  vector), dual-band GNSS.
- Because it targets a single known device, we do not optimize for broad
  compatibility.

## Tech & Architecture

- **Language/UI:** Kotlin + Jetpack Compose, single `Activity`, no fragments.
  Material3 theme reduced to the custom dark/neon look.
- **SDK levels:** `minSdk 34`, `targetSdk 36`, `compileSdk 36`.
- **State:** a single `RideViewModel` holds live ride state; Compose screens observe it.
- **Sensors layer (each independently testable behind an interface):**
  - `LocationRepository` — FusedLocationProvider → speed, cumulative distance,
    heading, lat/lng, GPS altitude.
  - `SensorRepository` — barometer (altitude) + rotation-vector sensor (compass heading).
- **Storage:**
  - **Room (SQLite)** with two tables: `rides` (summary) and `track_points` (GPS
    breadcrumb, FK to ride). Pulled off-device with `adb pull` and queryable with any
    SQLite tool / DuckDB. Not exposed in-app to the user.
  - **DataStore (Preferences)** for settings: units, screen display order, cadence
    mode, target RPM, color theme.
- **Build/deploy:** Gradle wrapper. `./gradlew assembleDebug`, then
  `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Developed in VS Code;
  Android Studio not required.
- **Package / app name:** `com.two17industries.rideman` (applicationId + namespace),
  app label "Rideman". The `two17industries` segment is Will's 217industries namespace,
  spelled out because a package segment cannot start with a digit.

## Data model

### `rides`
- `id` (PK), `started_at`, `ended_at`, `total_time_ms`, `distance_m`,
  `max_speed_mps`, `avg_speed_mps`.
- Stored in SI units (meters, m/s, ms); converted for display per the units setting.

### `track_points`
- `id` (PK), `ride_id` (FK), `timestamp`, `lat`, `lng`, `altitude_m`, `speed_mps`,
  `heading_deg`.
- Recorded at a steady interval during the ride. Enables future map/route features
  and recomputing any stat later.

## Screens & Navigation

### Start
- Buttons: `START RIDE`, `Settings`.
- Below: debug data (version, build, commit).

### Settings
- Screen display order (which ride sub-screens are enabled and in what order).
- Units: Metric or American (**American default**).
- Cadence mode: 1 RPM (right foot, same position each pulse) or ½ RPM (alternating feet).
- Cadence target RPM.
- Color theme: Acid Green / Electric Cyan / Hot Magenta / **Amber (default)**.
- Save button.

### Ride
- `HorizontalPager` over the enabled ride sub-screens, **circular** — swiping left on
  the first screen wraps to the last; swiping right on the last wraps to the first.
- Persistent bottom bar on every sub-screen: paginator dots + `END RIDE`.
- **Double-tap anywhere** → TTS reads current Speed, Distance Traveled, Heading,
  Altitude.
- Behavior: screen kept on (`FLAG_KEEP_SCREEN_ON`), locked to portrait.

### End
- Shows Total Time, Distance Traveled, Max Speed, Average Speed.
- Persists the ride (summary + track points) to the DB, then returns to Start.

## Ride sub-screens

1. **Speedometer** — current speed in MPH or km/h.
2. **Odometer** — distance traveled this ride in miles or km.
3. **Compass** — current heading in degrees.
4. **Altimeter** — current altitude (barometer, GPS-anchored).
5. **Cadence Pulse** — target cadence display; Speed Up / Play-Pause / Slow Down
   buttons; plays a click/pulse on a steady timer when not paused.

## Visual design

- Near-black background (`#050505`) — OLED-friendly, best contrast in sun.
- Single neon accent color applied app-wide, chosen in Settings. Palettes:
  - Acid Green `#39FF14`
  - Electric Cyan `#00F0FF`
  - Hot Magenta `#FF2D95`
  - **Amber `#FFC400` (default)**
- One value per screen, as large as legibly fits. Small uppercase label for the metric
  name; small unit label. Paginator dots + END RIDE pinned to the bottom.
- No gauges, no decoration. Glance-down legibility is the only goal.

## Key behaviors / decisions

- **Keep screen on + portrait lock during a ride** (mounted-phone glance use).
- **Foreground location service** with a small persistent notification while riding, so
  GPS stays accurate and is not throttled when the screen dims or the app backgrounds.
- **Permissions:** request `ACCESS_FINE_LOCATION` (foreground only) when START RIDE is
  pressed. No background-location permission.
- **Altimeter:** barometer for responsive altitude, calibrated/anchored by GPS
  altitude; falls back to GPS-only if the barometer is unavailable.
- **Cadence:** short click sample via `SoundPool`, scheduled on a steady timer; ½-RPM
  mode doubles the click rate; buttons adjust target RPM live.

## Testing approach

TDD on the logic worth testing:
- Unit conversions (mph↔km/h, mi↔km, m↔ft).
- Distance accumulation from a sequence of GPS points.
- Cadence interval math (RPM → click period; ½-RPM doubling).
- Circular pager wrap logic.

Sensor and UI glue sit behind thin wrappers/interfaces so the math is unit-testable
without a device.

## Out of scope (future features)

Listed for architecture awareness only; not in this build:
1. Tidal music integration.
2. Gear-selection helper.
3. Route planner / map (the stored GPS track is the seed for this).
4. External sensor integration (heart rate, cadence sensor).
5. Personal records / "time to beat".
