# Strava Upload — Design

**Date:** 2026-07-10
**Status:** Approved, ready for planning

## Goal

Let rideman push each recorded ride to Strava so Strava produces its map, route,
segment matching, and social features — without running the Strava app live during the
ride. rideman becomes the **sole GPS tracker**; Strava becomes a **post-ride sink**.

This collapses the current two-apps-at-once workflow (rideman tracking for its own dash +
the Strava app tracking independently on the handlebars) into one tracker with two
downstream consumers: the on-phone/on-display dash (existing), and Strava (this feature).

### Why post-ride upload (research verdict)

Strava exposes **no live interface** to third parties — no live-out (the v3 API and
webhooks are post-activity only; Beacon and iOS Live Activities are walled off) and **no
live-in** (the Strava app always records from the phone's own GPS; it pairs only with BLE
heart-rate straps, and there is no external-GPS-source input). The only supported,
non-hacky path to hand Strava our track is the **upload API** (`POST /api/v3/uploads`),
which accepts a completed activity file. Since the user only cares about Strava's
*post-ride* features, nothing they want is lost. (The Android mock-location route was
rejected: ToS-violating, detectable, fragile, and it still requires running the Strava app
alongside rideman — defeating the one-app goal.)

## Scope & Guiding Constraints

- **Personal, single-user app.** Only the owner's phone and Strava account. This permits
  on-device OAuth with an embedded client secret — no backend/token-exchange server.
- **Purely additive.** No change to GPS tracking, the `LocationForegroundService`, the
  ride model's core fields, `RideTracker`, or the live dash. We add a `strava/` package,
  an exporter, a few `rides` columns, and small UI touchpoints.
- **Exact-match distance.** Strava's distance/speed must equal rideman's haversine number
  (see Exporter), so the dash and Strava agree.
- **Robust to offline ride ends.** Rides frequently end with no signal; uploading must
  survive that transparently.
- **Encoder is swappable.** Export goes behind an interface so a richer **FIT** encoder
  can be dropped in later (when rideman gains sensor/lap data) with no change to auth or
  upload. See Out of Scope.

## Architecture & Data Flow

```
endRide() saves RideEntity  ──▶  if Strava connected: repo.enqueueUpload(rideId)
                                          │
                              WorkManager unique work "upload-<rideId>"
                              (constraint: CONNECTED; backoff: exponential)
                                          │
                                   StravaUploadWorker
        ┌─────────────────────────────────┼─────────────────────────────────┐
        ▼                                  ▼                                  ▼
  StravaAuth.freshToken()          TcxExporter(rideId)              StravaUploader
  (refresh if near expiry)   reads track_points + ride →      POST /uploads (multipart, gz)
                             TCX bytes + distance stream       → poll GET /uploads/{id}
                                                               → activity_id | error
                                          │
                    update RideEntity.stravaState = UPLOADED(+activityId) | FAILED(+error)
                                          │
                              History screen observes state (Room Flow)
```

**Dedup / idempotency:** each ride carries a stable
`external_id = "rideman-<rideId>-<startedAtEpoch>"`. Retries and accidental re-enqueues
collapse to one Strava activity; a `duplicate` response is resolved to `UPLOADED` (with the
referenced activity id), not treated as an error.

## Components

Each unit has one job and is testable in isolation.

| Component | Responsibility | Depends on |
|-----------|----------------|-----------|
| `export/RideExporter` (interface) | `export(rideId) -> (bytes, dataType)` | — |
| `export/TcxExporter` (impl) | ride + track points → gzipped TCX with a distance stream | Room read, `core/Geo` |
| `strava/StravaAuth` | OAuth connect, token refresh, connection state, `GET /athlete` name | `StravaTokenStore`, Custom Tab |
| `strava/StravaTokenStore` | persist refresh/access token + `expires_at` | EncryptedSharedPreferences |
| `strava/StravaUploader` | `POST /uploads` (multipart) + poll `GET /uploads/{id}` → activityId/error | HTTP client, `StravaAuth` |
| `strava/StravaUploadWorker` | per-ride orchestration: token → export → upload → poll → status; retry policy | the above + `RideRepository` |
| `strava/StravaCallbackActivity` | catches `rideman://strava-callback?code=…`, hands code to `StravaAuth` | `StravaAuth` |
| Room migration (v+1) | add `stravaState, stravaActivityId, stravaExternalId, stravaError` to `rides` | — |
| UI touchpoints | Settings connect/disconnect + toggle; History status chip + retry; backfill picker | ViewModel/repo |

**HTTP client:** the app currently has no networking dependency. Add a lightweight client
(OkHttp) for multipart upload + JSON; or `HttpURLConnection` to avoid the dependency. Pick
during planning — isolated inside `StravaUploader`/`StravaAuth`, so either is a local
choice.

## OAuth (one-time connect)

1. Owner creates a Strava API application once at `strava.com/settings/api` → **Client ID**
   + **Client Secret**. These are injected via `local.properties` → `BuildConfig` and are
   **never committed** to git.
2. Settings → **Connect to Strava** opens a **Chrome Custom Tab** (androidx.browser) to
   Strava's authorize URL: `response_type=code`, `scope=activity:write,read`,
   `redirect_uri=rideman://strava-callback`, `approval_prompt=auto`.
3. Strava redirects to `rideman://strava-callback?code=…`; `StravaCallbackActivity`
   (intent-filter on that scheme) extracts `code` and calls `StravaAuth.exchangeCode()` →
   `POST /oauth/token` (client_id + secret + code) → **access token** (~6h) + **refresh
   token** (long-lived) + `expires_at`.
4. `StravaTokenStore` persists them in **EncryptedSharedPreferences** (the refresh token is
   a long-lived credential and is encrypted at rest).
5. `StravaAuth.freshToken()` returns a valid access token, transparently refreshing via
   `grant_type=refresh_token` when within ~60s of `expires_at`.
6. **Disconnect** wipes the token store; **Reconnect** re-runs the flow (surfaced when a
   refresh fails because access was revoked).

## TCX Exporter

Reads the ride's `track_points` (already time-ordered by insert) and the `RideEntity`
summary; emits a TCX `Activity` with `Sport="Biking"`.

- **Per trackpoint:** `<Time>` as UTC ISO-8601 with `Z`; `<Position>` lat/lng;
  `<AltitudeMeters>` when present; and the load-bearing field `<DistanceMeters>` =
  **cumulative haversine** distance recomputed with the existing `core/Geo.haversineMeters`
  — identical math to `RideTracker`, so Strava's total equals rideman's. Instantaneous
  speed goes in the Garmin `TPX` extension (`<Speed>` m/s).
- **Lap-level:** `TotalTimeSeconds`, `DistanceMeters`, `MaximumSpeed` from `RideEntity`.
- **Output:** gzipped → `data_type = "tcx.gz"`.

This is the one component with substantial pure-logic surface and gets real unit tests.

## Upload Worker & States

`RideEntity.stravaState` is the single source of truth and is observable by History.

```
NONE ─enqueue─▶ QUEUED ─worker start─▶ UPLOADING ─┬─ success   ─▶ UPLOADED (+activityId)
                  ▲                                ├─ duplicate ─▶ UPLOADED (referenced id)
                  │                                └─ error     ─▶ FAILED   (+message)
                  └───────────── retry (WorkManager backoff, or manual) ──────┘
```

- **Retryable** (no network, 5xx, transient token refresh failure) → `Worker.retry()` with
  WorkManager exponential backoff; state stays `QUEUED`.
- **Terminal** (400 malformed file, 401 after a failed refresh) → `FAILED` + message;
  History shows a **Retry** action.
- **Duplicate** → `UPLOADED` with the referenced activity id (idempotent; never an error).

Work is enqueued as **unique work** keyed `"upload-<rideId>"` with `KEEP` policy, so
duplicate enqueues are no-ops.

## UI Touchpoints

Follows the existing Compose Settings/History patterns; no new navigation graph beyond the
backfill picker.

- **Settings — Strava row:**
  - Disconnected: **Connect to Strava**.
  - Connected: **Connected as ‹firstname›** (from a one-time `GET /athlete`) + **Disconnect**.
  - **"Upload rides to Strava" toggle** — pause auto-upload without disconnecting.
- **History — per-ride status chip:** `⏳ Queued` / `↑ Uploading` /
  `✓ Strava` (tap → open the Strava activity URL) / `⚠ Failed` (tap → **Retry**).
- **Backfill picker:** after a successful connect, a one-time prompt → a checkbox list of
  local rides ("Upload past rides to Strava?"). Selected rides enqueue through the same
  worker + dedup path. Already-uploaded rides are excluded from the list.

## Error Handling

| Situation | Behavior |
|-----------|----------|
| Offline at ride end | WorkManager `CONNECTED` constraint holds the job until connectivity; no user action. |
| Access revoked (refresh fails) | Ride → `FAILED`; Settings surfaces **Reconnect Strava**. |
| Malformed file (400) | Ride → `FAILED` + message. Should not occur once the exporter is tested; caught in dev. |
| Strava 5xx / outage | Retryable; exponential backoff. |
| Duplicate | Resolved to `UPLOADED`; never surfaced as an error. |

## Testing

**Unit (JVM, alongside existing `core/` and `data/` tests):**
- `TcxExporter`: well-formed TCX; monotonically non-decreasing `<DistanceMeters>` whose
  total equals `RideEntity.distanceM`; UTC timestamps; `Sport="Biking"`; empty/one-point
  rides handled.
- `StravaAuth`: refresh triggers within the expiry window and not before; refresh failure
  surfaces as reconnect-needed — with a faked HTTP client.
- `StravaUploadWorker`: state transitions for success / retryable / terminal / duplicate —
  with a fake `StravaUploader`.
- `external_id` is stable across retries for the same ride.

**Manual / instrumented E2E (owner's real account — the near-term test target):**
1. Connect Strava in Settings → shows connected name.
2. Record a short real ride → it appears on Strava with **distance matching rideman**.
3. Force a retry (airplane-mode the upload, then restore) → exactly one activity, no dupe.
4. Backfill: select a past ride → uploads once; re-selecting is a no-op.
5. Disconnect → wipes tokens; new rides no longer upload.

## Out of Scope

- **FIT export.** Deferred until rideman records data TCX can't already represent well
  (HR/cadence/power from a BLE sensor, laps/intervals, barometric ascent, device
  attribution). The `RideExporter` interface makes this a self-contained future swap.
- **Any live / real-time integration with Strava** — confirmed infeasible (see research
  verdict). No Beacon, Live Segments, or live-activity equivalents.
- **The T-Display BLE dash** — the separate second build track; not part of this spec.
- **Multi-user / distribution, backend token exchange** — personal app only.
- **Editing activity metadata on Strava** beyond sport type and the name Strava
  auto-assigns (renaming/description sync could come later via `PUT /activities/{id}`).
