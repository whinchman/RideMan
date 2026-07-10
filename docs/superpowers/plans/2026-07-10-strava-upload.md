# Strava Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make rideman the sole GPS tracker and auto-upload each recorded ride to Strava as a TCX file, so Strava produces the map/route/segments while the Strava app never runs live during the ride.

**Architecture:** On "End Ride", the persisted ride is enqueued to a WorkManager background job that (1) ensures a fresh OAuth access token, (2) renders the ride's stored track points into a gzipped TCX file with a cumulative-distance stream so Strava honors rideman's haversine distance, and (3) uploads via `POST /api/v3/uploads` and polls to completion. Per-ride upload state lives on the `rides` row and is observed by the History screen. OAuth is entirely on-device (personal app): a Chrome Custom Tab authorizes, a callback Activity exchanges the code, tokens are stored in EncryptedSharedPreferences.

**Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore, WorkManager (new), OkHttp (new), androidx.browser Custom Tabs (new), androidx.security EncryptedSharedPreferences (new), kotlinx.serialization (existing, for JSON), JUnit4 + kotlinx-coroutines-test.

## Global Constraints

- Package root: `com.two17industries.rideman`. Source: `app/src/main/java/com/two17industries/rideman/`. Tests: `app/src/test/java/com/two17industries/rideman/`.
- `minSdk = 34`, `compileSdk = 36`, `targetSdk = 36`, Java 17, Kotlin 2.1.0.
- Dependencies are declared through the version catalog `gradle/libs.versions.toml` and referenced as `libs.*` in `app/build.gradle.kts`. Follow that pattern; do not hardcode coordinates in the module file.
- Additive only: do NOT modify GPS tracking (`location/`), `RideTracker`, `Geo`, the live dash (`ui/ride/`), or the `LocationForegroundService`.
- Units are SI end-to-end (m, m/s, degrees, epoch millis). Convert only where a format requires it.
- Strava distance MUST equal rideman's: the TCX distance stream is the cumulative sum of `Geo.haversineMeters` between consecutive points — identical math to `RideTracker.add`.
- Tests use JUnit4 (`org.junit.Test`, `org.junit.Assert.*`) and `kotlinx.coroutines.test.runTest`, matching existing `core/` tests.
- Strava OAuth: `redirect_uri = rideman://strava-callback`, `scope = activity:write,read`. Client ID/secret come from `local.properties` via `BuildConfig`; they are NEVER committed.
- `external_id` for every upload is `"rideman-<rideId>-<startedAtEpochMillis>"` (stable across retries → Strava dedups).
- All work happens on branch `strava-upload` (already created).

---

### Task 1: Project setup — dependencies and Strava credentials

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `local.properties` (developer machine only — not committed)
- Modify: `.gitignore` (verify `local.properties` is ignored)

**Interfaces:**
- Produces: `BuildConfig.STRAVA_CLIENT_ID`, `BuildConfig.STRAVA_CLIENT_SECRET` (String); library refs `libs.androidx.work.runtime.ktx`, `libs.okhttp`, `libs.androidx.browser`, `libs.androidx.security.crypto`, and test ref `libs.robolectric` is NOT used (JVM-only tests).

- [ ] **Step 1: Add versions and libraries to the catalog**

In `gradle/libs.versions.toml`, under `[versions]` add:

```toml
work = "2.9.1"
okhttp = "4.12.0"
browser = "1.8.0"
securityCrypto = "1.1.0-alpha06"
```

Under `[libraries]` add:

```toml
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
androidx-browser = { group = "androidx.browser", name = "browser", version.ref = "browser" }
androidx-security-crypto = { group = "androidx.security", name = "security-crypto", version.ref = "securityCrypto" }
```

> Note on `security-crypto`: `1.1.0-alpha06` is the commonly-used release (the `1.0.0` stable ships a Tink bug). It is fine for a personal app.

- [ ] **Step 2: Wire dependencies into the module**

In `app/build.gradle.kts`, in the `dependencies { }` block, after `implementation(libs.kotlinx.serialization.json)` add:

```kotlin
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.security.crypto)
```

- [ ] **Step 3: Read Strava credentials from local.properties into BuildConfig**

In `app/build.gradle.kts`, at the top add the import and load (below the existing `val gitCommit` block):

```kotlin
import java.util.Properties

val stravaProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val stravaClientId: String = (stravaProps.getProperty("strava.clientId") ?: "").trim()
val stravaClientSecret: String = (stravaProps.getProperty("strava.clientSecret") ?: "").trim()
```

In `android { defaultConfig { } }`, add two fields (alongside the existing config):

```kotlin
        buildConfigField("String", "STRAVA_CLIENT_ID", "\"$stravaClientId\"")
        buildConfigField("String", "STRAVA_CLIENT_SECRET", "\"$stravaClientSecret\"")
```

- [ ] **Step 4: Put real credentials in local.properties**

Append to `local.properties` (create the Strava API app at https://www.strava.com/settings/api first; Authorization Callback Domain must be `rideman`):

```properties
strava.clientId=YOUR_CLIENT_ID
strava.clientSecret=YOUR_CLIENT_SECRET
```

Confirm `.gitignore` contains `local.properties` (Android's default template does). If missing, add it.

- [ ] **Step 5: Verify the project builds and BuildConfig fields exist**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. If it fails on the alpha `security-crypto`, confirm the version string matches the catalog.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts .gitignore
git commit -m "build: add WorkManager, OkHttp, Custom Tabs, security-crypto; Strava credentials via BuildConfig"
```

---

### Task 2: Persistence — Strava columns, enum, converter, migration, DAO queries

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/data/StravaUploadState.kt`
- Create: `app/src/main/java/com/two17industries/rideman/data/Converters.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/data/RideEntity.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/data/RidemanDatabase.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/data/RideDao.kt`

**Interfaces:**
- Produces: `enum class StravaUploadState { NONE, QUEUED, UPLOADING, UPLOADED, FAILED }`; `RideEntity` fields `stravaState: StravaUploadState = NONE`, `stravaActivityId: Long? = null`, `stravaExternalId: String? = null`, `stravaError: String? = null`; DAO methods `getRide(rideId): RideEntity?`, `getTrackPoints(rideId): List<TrackPointEntity>`, `updateStravaStatus(rideId, state, activityId, externalId, error)`. DB version → 3, `MIGRATION_2_3`.

- [ ] **Step 1: Add the upload-state enum**

Create `data/StravaUploadState.kt`:

```kotlin
package com.two17industries.rideman.data

/** Lifecycle of a ride's upload to Strava. Persisted as its name in the rides table. */
enum class StravaUploadState { NONE, QUEUED, UPLOADING, UPLOADED, FAILED }
```

- [ ] **Step 2: Add the Room type converter**

Create `data/Converters.kt`:

```kotlin
package com.two17industries.rideman.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromState(state: StravaUploadState): String = state.name

    @TypeConverter
    fun toState(value: String): StravaUploadState =
        runCatching { StravaUploadState.valueOf(value) }.getOrDefault(StravaUploadState.NONE)
}
```

- [ ] **Step 3: Add columns to RideEntity**

In `data/RideEntity.kt`, add four fields at the end of the constructor (after `planRideId`):

```kotlin
    val stravaState: StravaUploadState = StravaUploadState.NONE,
    val stravaActivityId: Long? = null,
    val stravaExternalId: String? = null,
    val stravaError: String? = null,
```

- [ ] **Step 4: Bump DB version, register converter, add migration**

In `data/RidemanDatabase.kt`:
- Change the annotation to `@Database(entities = [RideEntity::class, TrackPointEntity::class], version = 3, exportSchema = false)` and add `@TypeConverters(Converters::class)` above the class (import `androidx.room.TypeConverters`).
- Add the migration in the companion object next to `MIGRATION_1_2`:

```kotlin
        /** Adds Strava upload tracking columns. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE rides ADD COLUMN stravaState TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE rides ADD COLUMN stravaActivityId INTEGER")
                db.execSQL("ALTER TABLE rides ADD COLUMN stravaExternalId TEXT")
                db.execSQL("ALTER TABLE rides ADD COLUMN stravaError TEXT")
            }
        }
```

- Register it: `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`.

- [ ] **Step 5: Add DAO queries**

In `data/RideDao.kt`, add:

```kotlin
    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun getRide(rideId: Long): RideEntity?

    @Query("SELECT * FROM track_points WHERE rideId = :rideId ORDER BY timestamp ASC")
    suspend fun getTrackPoints(rideId: Long): List<TrackPointEntity>

    @Query(
        "UPDATE rides SET stravaState = :state, stravaActivityId = :activityId, " +
            "stravaExternalId = :externalId, stravaError = :error WHERE id = :rideId"
    )
    suspend fun updateStravaStatus(
        rideId: Long,
        state: StravaUploadState,
        activityId: Long?,
        externalId: String?,
        error: String?,
    )
```

- [ ] **Step 6: Build to verify Room codegen accepts the schema**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (Room KSP generates without "you must add a migration" errors, since version=3 and the migration is registered).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/data/
git commit -m "data: add Strava upload columns, enum, converter, migration 2->3, DAO queries"
```

---

### Task 3: Stable external_id (TDD)

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/strava/StravaExternalId.kt`
- Test: `app/src/test/java/com/two17industries/rideman/strava/StravaExternalIdTest.kt`

**Interfaces:**
- Produces: `object StravaExternalId { fun forRide(rideId: Long, startedAtEpochMillis: Long): String }`.

- [ ] **Step 1: Write the failing test**

Create `strava/StravaExternalIdTest.kt`:

```kotlin
package com.two17industries.rideman.strava

import org.junit.Assert.assertEquals
import org.junit.Test

class StravaExternalIdTest {
    @Test fun encodes_ride_id_and_start_time() {
        assertEquals("rideman-42-1720600000000", StravaExternalId.forRide(42, 1720600000000))
    }

    @Test fun is_stable_for_same_inputs() {
        assertEquals(
            StravaExternalId.forRide(7, 111),
            StravaExternalId.forRide(7, 111),
        )
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.strava.StravaExternalIdTest"`
Expected: FAIL — unresolved reference `StravaExternalId`.

- [ ] **Step 3: Implement**

Create `strava/StravaExternalId.kt`:

```kotlin
package com.two17industries.rideman.strava

object StravaExternalId {
    fun forRide(rideId: Long, startedAtEpochMillis: Long): String =
        "rideman-$rideId-$startedAtEpochMillis"
}
```

- [ ] **Step 4: Run it and watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.strava.StravaExternalIdTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/strava/StravaExternalId.kt app/src/test/java/com/two17industries/rideman/strava/StravaExternalIdTest.kt
git commit -m "strava: stable external_id helper"
```

---

### Task 4: TCX rendering (TDD) and exporter glue

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/export/RideExporter.kt`
- Create: `app/src/main/java/com/two17industries/rideman/export/TcxWriter.kt`
- Create: `app/src/main/java/com/two17industries/rideman/export/TcxExporter.kt`
- Test: `app/src/test/java/com/two17industries/rideman/export/TcxWriterTest.kt`

**Interfaces:**
- Consumes: `RideEntity`, `TrackPointEntity` (data classes), `Geo.haversineMeters`, `RideDao.getRide/getTrackPoints`.
- Produces: `data class ExportedFile(val bytes: ByteArray, val dataType: String)`; `interface RideExporter { suspend fun export(rideId: Long): ExportedFile? }`; `object TcxWriter { fun write(ride: RideEntity, points: List<TrackPointEntity>): String }`; `class TcxExporter(dao: RideDao) : RideExporter`.

- [ ] **Step 1: Write the failing test for TCX rendering**

Create `export/TcxWriterTest.kt`:

```kotlin
package com.two17industries.rideman.export

import com.two17industries.rideman.core.Geo
import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.data.StravaUploadState
import com.two17industries.rideman.data.TrackPointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TcxWriterTest {

    private fun ride() = RideEntity(
        id = 1,
        startedAt = 1_720_600_000_000, // 2024-07-10T08:26:40Z
        endedAt = 1_720_600_060_000,
        totalTimeMs = 60_000,
        distanceM = 0.0,
        maxSpeedMps = 5f,
        avgSpeedMps = 4f,
        stravaState = StravaUploadState.QUEUED,
    )

    private fun points() = listOf(
        TrackPointEntity(1, 1, 1_720_600_000_000, 40.4406, -88.0000, 220.0, 4.0f, 90f),
        TrackPointEntity(2, 1, 1_720_600_030_000, 40.4420, -88.0000, 221.0, 5.0f, 92f),
        TrackPointEntity(3, 1, 1_720_600_060_000, 40.4434, -88.0000, 222.0, 5.0f, 95f),
    )

    @Test fun declares_biking_sport_and_tcx_root() {
        val xml = TcxWriter.write(ride(), points())
        assertTrue(xml.contains("<TrainingCenterDatabase"))
        assertTrue(xml.contains("<Activity Sport=\"Biking\">"))
    }

    @Test fun timestamps_are_utc_iso8601_with_Z() {
        val xml = TcxWriter.write(ride(), points())
        assertTrue(xml.contains("<Time>2024-07-10T08:26:40.000Z</Time>"))
    }

    @Test fun distance_stream_is_cumulative_and_matches_haversine_total() {
        val pts = points()
        val expectedTotal =
            Geo.haversineMeters(pts[0].lat, pts[0].lng, pts[1].lat, pts[1].lng) +
                Geo.haversineMeters(pts[1].lat, pts[1].lng, pts[2].lat, pts[2].lng)
        val xml = TcxWriter.write(ride(), pts)
        // First trackpoint distance is 0; last equals the cumulative total.
        val distances = Regex("<DistanceMeters>([0-9.]+)</DistanceMeters>")
            .findAll(xml).map { it.groupValues[1].toDouble() }.toList()
        assertEquals(0.0, distances.first(), 0.001)
        assertEquals(expectedTotal, distances.last(), 0.01)
        // Monotonic non-decreasing.
        assertTrue(distances.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun includes_position_altitude_and_speed_extension() {
        val xml = TcxWriter.write(ride(), points())
        assertTrue(xml.contains("<LatitudeDegrees>40.4406</LatitudeDegrees>"))
        assertTrue(xml.contains("<AltitudeMeters>220.0</AltitudeMeters>"))
        assertTrue(xml.contains("<Speed>4.0</Speed>")) // TPX extension, m/s
    }

    @Test fun empty_track_still_produces_valid_activity() {
        val xml = TcxWriter.write(ride(), emptyList())
        assertTrue(xml.contains("<Activity Sport=\"Biking\">"))
        assertTrue(xml.contains("</TrainingCenterDatabase>"))
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.export.TcxWriterTest"`
Expected: FAIL — unresolved reference `TcxWriter`.

- [ ] **Step 3: Implement the TCX writer**

Create `export/TcxWriter.kt`:

```kotlin
package com.two17industries.rideman.export

import com.two17industries.rideman.core.Geo
import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.data.TrackPointEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Renders a ride into Garmin TCX XML with a cumulative <DistanceMeters> stream so Strava
 * honors rideman's haversine distance instead of recomputing from GPS. Pure function —
 * no I/O — so it is unit tested directly.
 */
object TcxWriter {

    private fun utc(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun write(ride: RideEntity, points: List<TrackPointEntity>): String {
        val fmt = utc()
        val startId = fmt.format(Date(ride.startedAt))
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append(
            """<TrainingCenterDatabase """ +
                """xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2" """ +
                """xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2">""",
        ).append('\n')
        sb.append("  <Activities>\n")
        sb.append("    <Activity Sport=\"Biking\">\n")
        sb.append("      <Id>$startId</Id>\n")
        sb.append("      <Lap StartTime=\"$startId\">\n")
        sb.append("        <TotalTimeSeconds>${ride.totalTimeMs / 1000.0}</TotalTimeSeconds>\n")

        // Cumulative distance mirrors RideTracker.add exactly.
        var cumulative = 0.0
        var prev: TrackPointEntity? = null
        val track = StringBuilder()
        for (p in points) {
            prev?.let { cumulative += Geo.haversineMeters(it.lat, it.lng, p.lat, p.lng) }
            prev = p
            track.append("          <Trackpoint>\n")
            track.append("            <Time>${fmt.format(Date(p.timestamp))}</Time>\n")
            track.append("            <Position>\n")
            track.append("              <LatitudeDegrees>${p.lat}</LatitudeDegrees>\n")
            track.append("              <LongitudeDegrees>${p.lng}</LongitudeDegrees>\n")
            track.append("            </Position>\n")
            p.altitudeM?.let { track.append("            <AltitudeMeters>$it</AltitudeMeters>\n") }
            track.append("            <DistanceMeters>$cumulative</DistanceMeters>\n")
            track.append("            <Extensions>\n")
            track.append("              <ns3:TPX>\n")
            track.append("                <ns3:Speed>${p.speedMps}</ns3:Speed>\n")
            track.append("              </ns3:TPX>\n")
            track.append("            </Extensions>\n")
            track.append("          </Trackpoint>\n")
        }

        sb.append("        <DistanceMeters>$cumulative</DistanceMeters>\n")
        sb.append("        <MaximumSpeed>${ride.maxSpeedMps}</MaximumSpeed>\n")
        sb.append("        <Intensity>Active</Intensity>\n")
        sb.append("        <TriggerMethod>Manual</TriggerMethod>\n")
        sb.append("        <Track>\n")
        sb.append(track)
        sb.append("        </Track>\n")
        sb.append("      </Lap>\n")
        sb.append("      <Creator xsi:type=\"Device_t\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")
        sb.append("        <Name>rideman</Name>\n")
        sb.append("      </Creator>\n")
        sb.append("    </Activity>\n")
        sb.append("  </Activities>\n")
        sb.append("</TrainingCenterDatabase>\n")
        return sb.toString()
    }
}
```

Note: the `<Speed>4.0</Speed>` the test asserts is matched by the substring inside `<ns3:Speed>4.0</ns3:Speed>` — `contains("<Speed>4.0</Speed>")` must find the exact string, so the test uses the non-prefixed form. Adjust the test assertion to `assertTrue(xml.contains("<ns3:Speed>4.0</ns3:Speed>"))` to match the namespaced tag.

- [ ] **Step 4: Fix the speed assertion to the namespaced tag**

In `TcxWriterTest.includes_position_altitude_and_speed_extension`, change the speed line to:

```kotlin
        assertTrue(xml.contains("<ns3:Speed>4.0</ns3:Speed>"))
```

- [ ] **Step 5: Run it and watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.export.TcxWriterTest"`
Expected: PASS (all five tests).

- [ ] **Step 6: Add the exporter interface and Room-backed exporter**

Create `export/RideExporter.kt`:

```kotlin
package com.two17industries.rideman.export

/** A ride serialized to an uploadable file plus the Strava data_type token. */
data class ExportedFile(val bytes: ByteArray, val dataType: String)

interface RideExporter {
    /** Returns the exported file, or null if the ride id does not exist. */
    suspend fun export(rideId: Long): ExportedFile?
}
```

Create `export/TcxExporter.kt`:

```kotlin
package com.two17industries.rideman.export

import com.two17industries.rideman.data.RideDao
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Reads a ride + its track points from Room and renders gzipped TCX. */
class TcxExporter(private val dao: RideDao) : RideExporter {
    override suspend fun export(rideId: Long): ExportedFile? {
        val ride = dao.getRide(rideId) ?: return null
        val points = dao.getTrackPoints(rideId)
        val xml = TcxWriter.write(ride, points)
        val gz = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(xml.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()
        return ExportedFile(bytes = gz, dataType = "tcx.gz")
    }
}
```

- [ ] **Step 7: Build to confirm everything compiles**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.export.*"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/export/ app/src/test/java/com/two17industries/rideman/export/
git commit -m "export: gzipped TCX writer with cumulative distance stream + Room-backed exporter"
```

---

### Task 5: HTTP abstraction and OkHttp implementation

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/strava/StravaHttp.kt`
- Create: `app/src/main/java/com/two17industries/rideman/strava/OkHttpStravaHttp.kt`

**Interfaces:**
- Produces: `data class HttpResponse(val code: Int, val body: String)`; `interface StravaHttp { suspend fun postForm(url, fields), suspend fun get(url, bearer), suspend fun postMultipart(url, bearer, fileField, fileName, fileBytes, textFields) }`; `class OkHttpStravaHttp : StravaHttp`. Consumed by Tasks 6 (auth) and 7 (uploader). Fakes in tests implement `StravaHttp`.

- [ ] **Step 1: Define the interface**

Create `strava/StravaHttp.kt`:

```kotlin
package com.two17industries.rideman.strava

/** Minimal HTTP surface Strava needs. Abstracted so auth/upload logic is unit-testable. */
data class HttpResponse(val code: Int, val body: String) {
    val isSuccess: Boolean get() = code in 200..299
    val isServerError: Boolean get() = code in 500..599
}

interface StravaHttp {
    suspend fun postForm(url: String, fields: Map<String, String>): HttpResponse

    suspend fun get(url: String, bearer: String): HttpResponse

    suspend fun postMultipart(
        url: String,
        bearer: String,
        fileFieldName: String,
        fileName: String,
        fileBytes: ByteArray,
        textFields: Map<String, String>,
    ): HttpResponse
}
```

- [ ] **Step 2: Implement with OkHttp**

Create `strava/OkHttpStravaHttp.kt`:

```kotlin
package com.two17industries.rideman.strava

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpStravaHttp(
    private val client: OkHttpClient = OkHttpClient(),
) : StravaHttp {

    override suspend fun postForm(url: String, fields: Map<String, String>): HttpResponse =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
            execute(Request.Builder().url(url).post(body).build())
        }

    override suspend fun get(url: String, bearer: String): HttpResponse =
        withContext(Dispatchers.IO) {
            execute(Request.Builder().url(url).header("Authorization", "Bearer $bearer").get().build())
        }

    override suspend fun postMultipart(
        url: String,
        bearer: String,
        fileFieldName: String,
        fileName: String,
        fileBytes: ByteArray,
        textFields: Map<String, String>,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        textFields.forEach { (k, v) -> builder.addFormDataPart(k, v) }
        builder.addFormDataPart(
            fileFieldName,
            fileName,
            fileBytes.toRequestBody("application/octet-stream".toMediaType()),
        )
        execute(
            Request.Builder().url(url)
                .header("Authorization", "Bearer $bearer")
                .post(builder.build())
                .build(),
        )
    }

    private fun execute(request: Request): HttpResponse =
        client.newCall(request).execute().use { resp ->
            HttpResponse(code = resp.code, body = resp.body?.string() ?: "")
        }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/strava/StravaHttp.kt app/src/main/java/com/two17industries/rideman/strava/OkHttpStravaHttp.kt
git commit -m "strava: HTTP abstraction with OkHttp implementation"
```

---

### Task 6: Token store, refresh decision (TDD), and StravaAuth

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/strava/StravaTokens.kt`
- Create: `app/src/main/java/com/two17industries/rideman/strava/StravaTokenLogic.kt`
- Create: `app/src/main/java/com/two17industries/rideman/strava/StravaTokenStore.kt`
- Create: `app/src/main/java/com/two17industries/rideman/strava/StravaAuth.kt`
- Test: `app/src/test/java/com/two17industries/rideman/strava/StravaTokenLogicTest.kt`
- Test: `app/src/test/java/com/two17industries/rideman/strava/StravaAuthTest.kt`

**Interfaces:**
- Consumes: `StravaHttp`, `HttpResponse`, `BuildConfig.STRAVA_CLIENT_ID/SECRET`.
- Produces: `data class StravaTokens(accessToken, refreshToken, expiresAtEpochSec, athleteFirstName)`; `object StravaTokenLogic { fun needsRefresh(nowEpochSec, expiresAtEpochSec, skewSec = 60): Boolean }`; `class StravaTokenStore(context)` with `save/load/clear/isConnected`; `class StravaAuth(clientId, clientSecret, store, http, now)` with `authorizeUrl()`, `suspend exchangeCode(code): Result<Unit>`, `suspend freshAccessToken(): String`, `disconnect()`, `isConnected`, `athleteFirstName`.

- [ ] **Step 1: Token model**

Create `strava/StravaTokens.kt`:

```kotlin
package com.two17industries.rideman.strava

data class StravaTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSec: Long,
    val athleteFirstName: String?,
)
```

- [ ] **Step 2: Write the failing test for the refresh decision**

Create `strava/StravaTokenLogicTest.kt`:

```kotlin
package com.two17industries.rideman.strava

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StravaTokenLogicTest {
    @Test fun fresh_token_does_not_need_refresh() {
        assertFalse(StravaTokenLogic.needsRefresh(nowEpochSec = 1000, expiresAtEpochSec = 5000))
    }

    @Test fun expired_token_needs_refresh() {
        assertTrue(StravaTokenLogic.needsRefresh(nowEpochSec = 6000, expiresAtEpochSec = 5000))
    }

    @Test fun token_within_skew_window_needs_refresh() {
        // expires in 30s, default skew 60s → refresh now.
        assertTrue(StravaTokenLogic.needsRefresh(nowEpochSec = 4970, expiresAtEpochSec = 5000))
    }
}
```

- [ ] **Step 3: Run it and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.strava.StravaTokenLogicTest"`
Expected: FAIL — unresolved reference `StravaTokenLogic`.

- [ ] **Step 4: Implement the refresh decision**

Create `strava/StravaTokenLogic.kt`:

```kotlin
package com.two17industries.rideman.strava

object StravaTokenLogic {
    /** True when the access token is expired or will expire within [skewSec]. */
    fun needsRefresh(nowEpochSec: Long, expiresAtEpochSec: Long, skewSec: Long = 60): Boolean =
        nowEpochSec >= (expiresAtEpochSec - skewSec)
}
```

- [ ] **Step 5: Run it and watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.strava.StravaTokenLogicTest"`
Expected: PASS.

- [ ] **Step 6: Implement the encrypted token store**

Create `strava/StravaTokenStore.kt`:

```kotlin
package com.two17industries.rideman.strava

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Persists Strava tokens encrypted at rest. */
class StravaTokenStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "strava_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val isConnected: Boolean get() = prefs.contains(KEY_REFRESH)

    fun save(tokens: StravaTokens) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putLong(KEY_EXPIRES, tokens.expiresAtEpochSec)
            .putString(KEY_NAME, tokens.athleteFirstName)
            .apply()
    }

    fun load(): StravaTokens? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return StravaTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSec = prefs.getLong(KEY_EXPIRES, 0),
            athleteFirstName = prefs.getString(KEY_NAME, null),
        )
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_NAME = "athlete_first_name"
    }
}
```

- [ ] **Step 7: Write the failing test for StravaAuth (URL + refresh via fake HTTP)**

Create `strava/StravaAuthTest.kt`. This uses a fake `StravaHttp` and a fake token persistence via an in-memory holder, so no Android classes are touched.

```kotlin
package com.two17industries.rideman.strava

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StravaAuthTest {

    private class FakeHttp(
        var formResponse: HttpResponse = HttpResponse(200, ""),
        var getResponse: HttpResponse = HttpResponse(200, ""),
    ) : StravaHttp {
        val formCalls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun postForm(url: String, fields: Map<String, String>): HttpResponse {
            formCalls += url to fields
            return formResponse
        }
        override suspend fun get(url: String, bearer: String) = getResponse
        override suspend fun postMultipart(
            url: String, bearer: String, fileFieldName: String, fileName: String,
            fileBytes: ByteArray, textFields: Map<String, String>,
        ) = HttpResponse(200, "")
    }

    // In-memory stand-in for StravaTokenStore (same shape, no Android).
    private class MemStore {
        var tokens: StravaTokens? = null
    }

    private fun auth(http: StravaHttp, store: MemStore, now: () -> Long) = StravaAuth(
        clientId = "CID",
        clientSecret = "SECRET",
        loadTokens = { store.tokens },
        saveTokens = { store.tokens = it },
        clearTokens = { store.tokens = null },
        http = http,
        nowEpochSec = now,
    )

    @Test fun authorize_url_contains_client_id_scope_and_redirect() {
        val a = auth(FakeHttp(), MemStore()) { 0 }
        val url = a.authorizeUrl()
        assertTrue(url.startsWith("https://www.strava.com/oauth/authorize"))
        assertTrue(url.contains("client_id=CID"))
        assertTrue(url.contains("redirect_uri=rideman%3A%2F%2Fstrava-callback"))
        assertTrue(url.contains("scope=activity%3Awrite%2Cread"))
        assertTrue(url.contains("response_type=code"))
    }

    @Test fun exchange_code_stores_tokens_and_name() = runTest {
        val http = FakeHttp(
            formResponse = HttpResponse(
                200,
                """{"access_token":"AAA","refresh_token":"RRR","expires_at":5000,
                   "athlete":{"firstname":"Will"}}""",
            ),
        )
        val store = MemStore()
        val a = auth(http, store) { 0 }
        val result = a.exchangeCode("CODE")
        assertTrue(result.isSuccess)
        assertEquals("AAA", store.tokens?.accessToken)
        assertEquals("RRR", store.tokens?.refreshToken)
        assertEquals(5000L, store.tokens?.expiresAtEpochSec)
        assertEquals("Will", store.tokens?.athleteFirstName)
        // Called the token endpoint with grant_type=authorization_code.
        assertEquals("authorization_code", http.formCalls.single().second["grant_type"])
    }

    @Test fun fresh_token_returns_stored_access_without_refresh() = runTest {
        val http = FakeHttp()
        val store = MemStore().apply {
            tokens = StravaTokens("STILL_GOOD", "RRR", expiresAtEpochSec = 9999, athleteFirstName = "Will")
        }
        val a = auth(http, store) { 1000 } // well before expiry
        assertEquals("STILL_GOOD", a.freshAccessToken())
        assertTrue(http.formCalls.isEmpty()) // no refresh call
    }

    @Test fun expired_token_triggers_refresh_and_persists_new_tokens() = runTest {
        val http = FakeHttp(
            formResponse = HttpResponse(
                200,
                """{"access_token":"NEW","refresh_token":"RRR2","expires_at":20000}""",
            ),
        )
        val store = MemStore().apply {
            tokens = StravaTokens("OLD", "RRR", expiresAtEpochSec = 5000, athleteFirstName = "Will")
        }
        val a = auth(http, store) { 6000 } // past expiry
        assertEquals("NEW", a.freshAccessToken())
        assertEquals("RRR2", store.tokens?.refreshToken)
        assertEquals(20000L, store.tokens?.expiresAtEpochSec)
        // Name is preserved across refresh (refresh response has no athlete).
        assertEquals("Will", store.tokens?.athleteFirstName)
        assertEquals("refresh_token", http.formCalls.single().second["grant_type"])
    }
}
```

- [ ] **Step 8: Run it and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.strava.StravaAuthTest"`
Expected: FAIL — unresolved reference `StravaAuth`.

- [ ] **Step 9: Implement StravaAuth**

`StravaAuth` takes function parameters for token persistence (so it is testable without Android). The production wiring passes a real `StravaTokenStore` in Task 9. It parses JSON with `org.json.JSONObject` (available on Android; for the JVM test, use kotlinx.serialization instead to stay Android-free).

Use kotlinx.serialization for parsing so the test runs on the JVM. Create `strava/StravaAuth.kt`:

```kotlin
package com.two17industries.rideman.strava

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder

class StravaAuth(
    private val clientId: String,
    private val clientSecret: String,
    private val loadTokens: () -> StravaTokens?,
    private val saveTokens: (StravaTokens) -> Unit,
    private val clearTokens: () -> Unit,
    private val http: StravaHttp,
    private val nowEpochSec: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val isConnected: Boolean get() = loadTokens() != null
    val athleteFirstName: String? get() = loadTokens()?.athleteFirstName

    fun authorizeUrl(): String {
        val redirect = enc(REDIRECT_URI)
        val scope = enc("activity:write,read")
        return "$AUTHORIZE_URL?client_id=$clientId&response_type=code" +
            "&redirect_uri=$redirect&approval_prompt=auto&scope=$scope"
    }

    /** Exchanges an auth code for tokens, fetches the athlete first name, and persists. */
    suspend fun exchangeCode(code: String): Result<Unit> = runCatching {
        val resp = http.postForm(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "code" to code,
                "grant_type" to "authorization_code",
            ),
        )
        require(resp.isSuccess) { "Token exchange failed: HTTP ${resp.code}" }
        val root = json.parseToJsonElement(resp.body).jsonObject
        val name = root["athlete"]?.jsonObject?.get("firstname")?.jsonPrimitive?.contentOrNull
        saveTokens(tokensFrom(root, fallbackName = name))
    }

    /** Returns a non-expired access token, refreshing if needed. Throws if not connected. */
    suspend fun freshAccessToken(): String {
        val current = loadTokens() ?: error("Not connected to Strava")
        if (!StravaTokenLogic.needsRefresh(nowEpochSec(), current.expiresAtEpochSec)) {
            return current.accessToken
        }
        val resp = http.postForm(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "grant_type" to "refresh_token",
                "refresh_token" to current.refreshToken,
            ),
        )
        require(resp.isSuccess) { "Token refresh failed: HTTP ${resp.code}" }
        val root = json.parseToJsonElement(resp.body).jsonObject
        val refreshed = tokensFrom(root, fallbackName = current.athleteFirstName)
        saveTokens(refreshed)
        return refreshed.accessToken
    }

    fun disconnect() = clearTokens()

    private fun tokensFrom(root: kotlinx.serialization.json.JsonObject, fallbackName: String?) =
        StravaTokens(
            accessToken = root["access_token"]!!.jsonPrimitive.content,
            refreshToken = root["refresh_token"]!!.jsonPrimitive.content,
            expiresAtEpochSec = root["expires_at"]?.jsonPrimitive?.longOrNull ?: 0L,
            athleteFirstName = fallbackName,
        )

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val AUTHORIZE_URL = "https://www.strava.com/oauth/authorize"
        const val TOKEN_URL = "https://www.strava.com/oauth/token"
        const val REDIRECT_URI = "rideman://strava-callback"
    }
}
```

- [ ] **Step 10: Run it and watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.strava.StravaAuthTest"`
Expected: PASS (four tests).

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/strava/StravaTokens.kt app/src/main/java/com/two17industries/rideman/strava/StravaTokenLogic.kt app/src/main/java/com/two17industries/rideman/strava/StravaTokenStore.kt app/src/main/java/com/two17industries/rideman/strava/StravaAuth.kt app/src/test/java/com/two17industries/rideman/strava/StravaTokenLogicTest.kt app/src/test/java/com/two17industries/rideman/strava/StravaAuthTest.kt
git commit -m "strava: encrypted token store, refresh decision, OAuth StravaAuth (TDD)"
```

---

### Task 7: Upload response parsing (TDD) and StravaUploader

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/strava/UploadResult.kt`
- Create: `app/src/main/java/com/two17industries/rideman/strava/UploadResponseParser.kt`
- Create: `app/src/main/java/com/two17industries/rideman/strava/StravaUploader.kt`
- Test: `app/src/test/java/com/two17industries/rideman/strava/UploadResponseParserTest.kt`

**Interfaces:**
- Consumes: `StravaHttp`, `StravaAuth`, `ExportedFile`, `HttpResponse`.
- Produces: `sealed interface UploadResult { Success(activityId), Duplicate(activityId?), Retryable(message), Terminal(message) }`; `object UploadResponseParser { fun parse(code, body): UploadResult }`; `class StravaUploader(http, auth) { suspend fun upload(file, externalId, activityName): UploadResult }`.

Strava upload flow: `POST /uploads` returns an Upload JSON `{id, external_id, error, status, activity_id}`. Poll `GET /uploads/{id}` until `activity_id` is non-null (done) or `error` is non-null (failed). A duplicate surfaces as an `error` string containing "duplicate".

- [ ] **Step 1: Define the result type**

Create `strava/UploadResult.kt`:

```kotlin
package com.two17industries.rideman.strava

sealed interface UploadResult {
    data class Success(val activityId: Long) : UploadResult
    data class Duplicate(val activityId: Long?) : UploadResult
    data class Retryable(val message: String) : UploadResult
    data class Terminal(val message: String) : UploadResult
    /** Upload accepted but still processing; caller should poll again. */
    data class Pending(val uploadId: Long) : UploadResult
}
```

- [ ] **Step 2: Write the failing test for the parser**

Create `strava/UploadResponseParserTest.kt`:

```kotlin
package com.two17industries.rideman.strava

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadResponseParserTest {
    @Test fun activity_id_present_is_success() {
        val r = UploadResponseParser.parse(200, """{"id":99,"activity_id":12345,"error":null}""")
        assertEquals(UploadResult.Success(12345), r)
    }

    @Test fun error_null_and_no_activity_is_pending() {
        val r = UploadResponseParser.parse(201, """{"id":99,"activity_id":null,"error":null,"status":"processing"}""")
        assertEquals(UploadResult.Pending(99), r)
    }

    @Test fun duplicate_error_is_duplicate() {
        val r = UploadResponseParser.parse(200, """{"id":99,"activity_id":null,"error":"duplicate of activity 777"}""")
        assertTrue(r is UploadResult.Duplicate)
    }

    @Test fun other_error_is_terminal() {
        val r = UploadResponseParser.parse(200, """{"id":99,"activity_id":null,"error":"empty file"}""")
        assertTrue(r is UploadResult.Terminal)
        assertEquals("empty file", (r as UploadResult.Terminal).message)
    }

    @Test fun http_5xx_is_retryable() {
        val r = UploadResponseParser.parse(503, "Service Unavailable")
        assertTrue(r is UploadResult.Retryable)
    }

    @Test fun http_401_is_terminal() {
        val r = UploadResponseParser.parse(401, "Unauthorized")
        assertTrue(r is UploadResult.Terminal)
    }
}
```

- [ ] **Step 3: Run it and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.strava.UploadResponseParserTest"`
Expected: FAIL — unresolved reference `UploadResponseParser`.

- [ ] **Step 4: Implement the parser**

Create `strava/UploadResponseParser.kt`:

```kotlin
package com.two17industries.rideman.strava

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Maps a Strava upload/poll HTTP response to an [UploadResult]. */
object UploadResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(code: Int, body: String): UploadResult {
        if (code in 500..599) return UploadResult.Retryable("HTTP $code")
        if (code == 429) return UploadResult.Retryable("rate limited")
        if (code !in 200..299) return UploadResult.Terminal("HTTP $code: ${body.take(200)}")

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return UploadResult.Terminal("unparseable response")

        val error = root["error"]?.jsonPrimitive?.contentOrNull
        val activityId = root["activity_id"]?.jsonPrimitive?.longOrNull
        val uploadId = root["id"]?.jsonPrimitive?.longOrNull ?: 0L

        return when {
            error != null && error.contains("duplicate", ignoreCase = true) -> {
                // "duplicate of activity 777" → pull the id when present.
                val dupId = Regex("(\\d+)").find(error)?.value?.toLongOrNull()
                UploadResult.Duplicate(dupId)
            }
            error != null -> UploadResult.Terminal(error)
            activityId != null -> UploadResult.Success(activityId)
            else -> UploadResult.Pending(uploadId)
        }
    }
}
```

- [ ] **Step 5: Run it and watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.strava.UploadResponseParserTest"`
Expected: PASS (six tests).

- [ ] **Step 6: Implement the uploader (POST + poll loop)**

Create `strava/StravaUploader.kt`:

```kotlin
package com.two17industries.rideman.strava

import com.two17industries.rideman.export.ExportedFile
import kotlinx.coroutines.delay

class StravaUploader(
    private val http: StravaHttp,
    private val auth: StravaAuth,
    private val pollDelayMs: Long = 2000,
    private val maxPolls: Int = 15,
) {
    /** Uploads a file and polls to a terminal result. */
    suspend fun upload(file: ExportedFile, externalId: String, activityName: String?): UploadResult {
        val bearer = try {
            auth.freshAccessToken()
        } catch (e: Exception) {
            return UploadResult.Terminal("auth: ${e.message}")
        }

        val fields = buildMap {
            put("data_type", file.dataType)
            put("external_id", externalId)
            put("sport_type", "Ride")
            activityName?.let { put("name", it) }
        }
        val post = http.postMultipart(
            url = UPLOADS_URL,
            bearer = bearer,
            fileFieldName = "file",
            fileName = "$externalId.${file.dataType}",
            fileBytes = file.bytes,
            textFields = fields,
        )
        var result = UploadResponseParser.parse(post.code, post.body)

        var polls = 0
        while (result is UploadResult.Pending && polls < maxPolls) {
            delay(pollDelayMs)
            polls++
            val poll = http.get("$UPLOADS_URL/${result.uploadId}", bearer)
            result = UploadResponseParser.parse(poll.code, poll.body)
        }
        return if (result is UploadResult.Pending) {
            UploadResult.Retryable("still processing after $maxPolls polls")
        } else {
            result
        }
    }

    private companion object {
        const val UPLOADS_URL = "https://www.strava.com/api/v3/uploads"
    }
}
```

- [ ] **Step 7: Build**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.strava.*"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/strava/UploadResult.kt app/src/main/java/com/two17industries/rideman/strava/UploadResponseParser.kt app/src/main/java/com/two17industries/rideman/strava/StravaUploader.kt app/src/test/java/com/two17industries/rideman/strava/UploadResponseParserTest.kt
git commit -m "strava: upload response parser (TDD) and uploader with poll loop"
```

---

### Task 8: Upload coordinator (TDD) plus WorkManager worker and scheduler

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/strava/StravaUploadCoordinator.kt`
- Create: `app/src/main/java/com/two17industries/rideman/strava/StravaUploadWorker.kt`
- Create: `app/src/main/java/com/two17industries/rideman/strava/StravaUploadScheduler.kt`
- Test: `app/src/test/java/com/two17industries/rideman/strava/StravaUploadCoordinatorTest.kt`

**Interfaces:**
- Consumes: `RideDao`, `RideExporter`, `StravaUploader`, `StravaExternalId`, `StravaUploadState`, `UploadResult`.
- Produces: `enum class UploadOutcome { SUCCESS, RETRY, FAILED }`; `class StravaUploadCoordinator(dao, exporter, uploader) { suspend fun uploadRide(rideId): UploadOutcome }`; `class StravaUploadWorker(context, params) : CoroutineWorker`; `object StravaUploadScheduler { fun enqueue(context, rideId) }`.

The coordinator owns all DB state transitions; the worker is a thin adapter mapping `UploadOutcome` → `Result`. Unit tests cover the coordinator with fakes.

- [ ] **Step 1: Write the failing test for the coordinator**

Create `strava/StravaUploadCoordinatorTest.kt`:

```kotlin
package com.two17industries.rideman.strava

import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.data.StravaUploadState
import com.two17industries.rideman.export.ExportedFile
import com.two17industries.rideman.export.RideExporter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class StravaUploadCoordinatorTest {

    private class FakeExporter(private val file: ExportedFile?) : RideExporter {
        override suspend fun export(rideId: Long): ExportedFile? = file
    }

    // Records the last status write so we can assert transitions.
    private class StatusRecorder {
        val writes = mutableListOf<StravaUploadState>()
        var lastActivityId: Long? = null
        var lastError: String? = null
    }

    private fun coordinator(
        ride: RideEntity?,
        file: ExportedFile?,
        result: UploadResult,
        rec: StatusRecorder,
    ): StravaUploadCoordinator = StravaUploadCoordinator(
        getRide = { ride },
        exporter = FakeExporter(file),
        upload = { _, _, _ -> result },
        updateStatus = { _, state, activityId, _, error ->
            rec.writes += state
            rec.lastActivityId = activityId
            rec.lastError = error
        },
    )

    private val sampleRide = RideEntity(
        id = 5, startedAt = 100, endedAt = 200, totalTimeMs = 100,
        distanceM = 10.0, maxSpeedMps = 1f, avgSpeedMps = 1f,
    )
    private val file = ExportedFile(byteArrayOf(1, 2, 3), "tcx.gz")

    @Test fun success_sets_uploaded_with_activity_id() = runTest {
        val rec = StatusRecorder()
        val c = coordinator(sampleRide, file, UploadResult.Success(999), rec)
        assertEquals(UploadOutcome.SUCCESS, c.uploadRide(5))
        assertEquals(StravaUploadState.UPLOADING, rec.writes.first())
        assertEquals(StravaUploadState.UPLOADED, rec.writes.last())
        assertEquals(999L, rec.lastActivityId)
    }

    @Test fun duplicate_is_treated_as_uploaded() = runTest {
        val rec = StatusRecorder()
        val c = coordinator(sampleRide, file, UploadResult.Duplicate(777), rec)
        assertEquals(UploadOutcome.SUCCESS, c.uploadRide(5))
        assertEquals(StravaUploadState.UPLOADED, rec.writes.last())
        assertEquals(777L, rec.lastActivityId)
    }

    @Test fun retryable_leaves_queued_and_signals_retry() = runTest {
        val rec = StatusRecorder()
        val c = coordinator(sampleRide, file, UploadResult.Retryable("offline"), rec)
        assertEquals(UploadOutcome.RETRY, c.uploadRide(5))
        assertEquals(StravaUploadState.QUEUED, rec.writes.last())
    }

    @Test fun terminal_sets_failed_with_message() = runTest {
        val rec = StatusRecorder()
        val c = coordinator(sampleRide, file, UploadResult.Terminal("bad file"), rec)
        assertEquals(UploadOutcome.FAILED, c.uploadRide(5))
        assertEquals(StravaUploadState.FAILED, rec.writes.last())
        assertEquals("bad file", rec.lastError)
    }

    @Test fun missing_ride_fails_without_upload() = runTest {
        val rec = StatusRecorder()
        val c = coordinator(null, file, UploadResult.Success(1), rec)
        assertEquals(UploadOutcome.FAILED, c.uploadRide(5))
    }

    @Test fun missing_export_fails() = runTest {
        val rec = StatusRecorder()
        val c = coordinator(sampleRide, null, UploadResult.Success(1), rec)
        assertEquals(UploadOutcome.FAILED, c.uploadRide(5))
        assertEquals(StravaUploadState.FAILED, rec.writes.last())
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.strava.StravaUploadCoordinatorTest"`
Expected: FAIL — unresolved reference `StravaUploadCoordinator`.

- [ ] **Step 3: Implement the coordinator**

Create `strava/StravaUploadCoordinator.kt`. Dependencies are passed as lambdas so it is unit-testable without Room; the production wiring (Task 9) supplies real DAO-backed lambdas.

```kotlin
package com.two17industries.rideman.strava

import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.data.StravaUploadState
import com.two17industries.rideman.export.ExportedFile
import com.two17industries.rideman.export.RideExporter

enum class UploadOutcome { SUCCESS, RETRY, FAILED }

class StravaUploadCoordinator(
    private val getRide: suspend (Long) -> RideEntity?,
    private val exporter: RideExporter,
    private val upload: suspend (ExportedFile, String, String?) -> UploadResult,
    private val updateStatus: suspend (
        rideId: Long,
        state: StravaUploadState,
        activityId: Long?,
        externalId: String?,
        error: String?,
    ) -> Unit,
) {
    suspend fun uploadRide(rideId: Long): UploadOutcome {
        val ride = getRide(rideId) ?: return UploadOutcome.FAILED
        val externalId = StravaExternalId.forRide(ride.id, ride.startedAt)

        updateStatus(rideId, StravaUploadState.UPLOADING, ride.stravaActivityId, externalId, null)

        val file = exporter.export(rideId)
        if (file == null) {
            updateStatus(rideId, StravaUploadState.FAILED, null, externalId, "export failed")
            return UploadOutcome.FAILED
        }

        return when (val result = upload(file, externalId, null)) {
            is UploadResult.Success -> {
                updateStatus(rideId, StravaUploadState.UPLOADED, result.activityId, externalId, null)
                UploadOutcome.SUCCESS
            }
            is UploadResult.Duplicate -> {
                updateStatus(rideId, StravaUploadState.UPLOADED, result.activityId, externalId, null)
                UploadOutcome.SUCCESS
            }
            is UploadResult.Retryable -> {
                updateStatus(rideId, StravaUploadState.QUEUED, ride.stravaActivityId, externalId, result.message)
                UploadOutcome.RETRY
            }
            is UploadResult.Terminal -> {
                updateStatus(rideId, StravaUploadState.FAILED, null, externalId, result.message)
                UploadOutcome.FAILED
            }
            is UploadResult.Pending -> {
                // Uploader only returns Pending as Retryable upstream; treat defensively.
                updateStatus(rideId, StravaUploadState.QUEUED, ride.stravaActivityId, externalId, "still processing")
                UploadOutcome.RETRY
            }
        }
    }
}
```

- [ ] **Step 4: Run it and watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.strava.StravaUploadCoordinatorTest"`
Expected: PASS (six tests).

- [ ] **Step 5: Implement the worker**

Create `strava/StravaUploadWorker.kt`. It builds real dependencies from `applicationContext` and delegates to the coordinator.

```kotlin
package com.two17industries.rideman.strava

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.two17industries.rideman.BuildConfig
import com.two17industries.rideman.data.RidemanDatabase
import com.two17industries.rideman.export.TcxExporter

class StravaUploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val rideId = inputData.getLong(KEY_RIDE_ID, -1L)
        if (rideId < 0) return Result.failure()

        val dao = RidemanDatabase.get(applicationContext).rideDao()
        val store = StravaTokenStore(applicationContext)
        val http = OkHttpStravaHttp()
        val auth = StravaAuth(
            clientId = BuildConfig.STRAVA_CLIENT_ID,
            clientSecret = BuildConfig.STRAVA_CLIENT_SECRET,
            loadTokens = { store.load() },
            saveTokens = { store.save(it) },
            clearTokens = { store.clear() },
            http = http,
            nowEpochSec = { System.currentTimeMillis() / 1000 },
        )
        val uploader = StravaUploader(http, auth)
        val exporter = TcxExporter(dao)
        val coordinator = StravaUploadCoordinator(
            getRide = { dao.getRide(it) },
            exporter = exporter,
            upload = { file, externalId, name -> uploader.upload(file, externalId, name) },
            updateStatus = { id, state, activityId, externalId, error ->
                dao.updateStravaStatus(id, state, activityId, externalId, error)
            },
        )

        return when (coordinator.uploadRide(rideId)) {
            UploadOutcome.SUCCESS -> Result.success()
            UploadOutcome.RETRY -> Result.retry()
            UploadOutcome.FAILED -> Result.failure()
        }
    }

    companion object {
        const val KEY_RIDE_ID = "rideId"
    }
}
```

- [ ] **Step 6: Implement the scheduler**

Create `strava/StravaUploadScheduler.kt`:

```kotlin
package com.two17industries.rideman.strava

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object StravaUploadScheduler {
    fun enqueue(context: Context, rideId: Long) {
        val request = OneTimeWorkRequestBuilder<StravaUploadWorker>()
            .setInputData(workDataOf(StravaUploadWorker.KEY_RIDE_ID to rideId))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("upload-$rideId", ExistingWorkPolicy.KEEP, request)
    }
}
```

- [ ] **Step 7: Build the whole app**

Run: `./gradlew :app:assembleDebug && ./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.strava.*"`
Expected: BUILD SUCCESSFUL and all strava tests PASS.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/strava/StravaUploadCoordinator.kt app/src/main/java/com/two17industries/rideman/strava/StravaUploadWorker.kt app/src/main/java/com/two17industries/rideman/strava/StravaUploadScheduler.kt app/src/test/java/com/two17industries/rideman/strava/StravaUploadCoordinatorTest.kt
git commit -m "strava: upload coordinator (TDD), WorkManager worker, scheduler"
```

---

### Task 9: Repository and ViewModel wiring — enqueue, connection state, retry, backfill

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/data/RideRepository.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/data/Settings.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/ui/RideViewModel.kt`

**Interfaces:**
- Consumes: `StravaTokenStore`, `StravaAuth`, `StravaUploadScheduler`, `StravaUploadState`, `StravaExternalId`, `RidemanSettings`.
- Produces on `RideViewModel`: `stravaConnected: StateFlow<Boolean>`, `stravaAthleteName: StateFlow<String?>`, `fun connectStravaUrl(): String`, `suspend fun onStravaAuthCode(code: String)`, `fun refreshStravaConnection()`, `fun disconnectStrava()`, `fun retryUpload(rideId: Long)`, `fun backfillUpload(rideIds: List<Long>)`; on `RidemanSettings`: `stravaUploadEnabled: Boolean = true`. `RideRepository` gains `suspend fun markQueuedAndEnqueue(context, rideId)`, `suspend fun getRideIdsNeedingUpload(): List<Long>` (via allRides flow filter in VM instead — see below).

- [ ] **Step 1: Add the upload-enabled setting**

In `data/Settings.kt`:
- Add `val stravaUploadEnabled: Boolean = true` to `RidemanSettings`.
- Add a key: in `Keys`, `val STRAVA_UPLOAD = booleanPreferencesKey("strava_upload_enabled")` (import `androidx.datastore.preferences.core.booleanPreferencesKey`).
- In the `settings` map, add `stravaUploadEnabled = p[Keys.STRAVA_UPLOAD] ?: true`.
- In `save`, add `p[Keys.STRAVA_UPLOAD] = s.stravaUploadEnabled`.

- [ ] **Step 2: Add repository helpers**

In `data/RideRepository.kt`, add methods (the repo already holds `dao`):

```kotlin
    suspend fun markQueued(rideId: Long, ride: RideEntity) {
        dao.updateStravaStatus(
            rideId = rideId,
            state = StravaUploadState.QUEUED,
            activityId = null,
            externalId = StravaExternalId.forRide(ride.id, ride.startedAt),
            error = null,
        )
    }

    suspend fun getRide(rideId: Long): RideEntity? = dao.getRide(rideId)
```

Add imports: `com.two17industries.rideman.data.StravaUploadState` is same package; import `com.two17industries.rideman.strava.StravaExternalId`.

- [ ] **Step 3: Wire Strava into the ViewModel — construction and connection state**

In `ui/RideViewModel.kt`, add imports:

```kotlin
import com.two17industries.rideman.strava.OkHttpStravaHttp
import com.two17industries.rideman.strava.StravaAuth
import com.two17industries.rideman.strava.StravaTokenStore
import com.two17industries.rideman.strava.StravaUploadScheduler
import com.two17industries.rideman.BuildConfig
```

After `private val repo = ...`, add:

```kotlin
    private val stravaStore = StravaTokenStore(app)
    private val stravaAuth = StravaAuth(
        clientId = BuildConfig.STRAVA_CLIENT_ID,
        clientSecret = BuildConfig.STRAVA_CLIENT_SECRET,
        loadTokens = { stravaStore.load() },
        saveTokens = { stravaStore.save(it) },
        clearTokens = { stravaStore.clear() },
        http = OkHttpStravaHttp(),
        nowEpochSec = { System.currentTimeMillis() / 1000 },
    )

    private val _stravaConnected = MutableStateFlow(stravaStore.isConnected)
    val stravaConnected: StateFlow<Boolean> = _stravaConnected.asStateFlow()

    private val _stravaAthleteName = MutableStateFlow(stravaAuth.athleteFirstName)
    val stravaAthleteName: StateFlow<String?> = _stravaAthleteName.asStateFlow()

    fun connectStravaUrl(): String = stravaAuth.authorizeUrl()

    /** Called by MainActivity.onResume after the OAuth callback runs. */
    fun refreshStravaConnection() {
        _stravaConnected.value = stravaStore.isConnected
        _stravaAthleteName.value = stravaAuth.athleteFirstName
    }

    fun disconnectStrava() {
        stravaAuth.disconnect()
        refreshStravaConnection()
    }

    fun retryUpload(rideId: Long) {
        viewModelScope.launch {
            val ride = repo.getRide(rideId) ?: return@launch
            repo.markQueued(rideId, ride)
            StravaUploadScheduler.enqueue(getApplication(), rideId)
        }
    }

    fun backfillUpload(rideIds: List<Long>) {
        viewModelScope.launch {
            for (id in rideIds) {
                val ride = repo.getRide(id) ?: continue
                repo.markQueued(id, ride)
                StravaUploadScheduler.enqueue(getApplication(), id)
            }
        }
    }
```

> Note: `exchangeCode` runs inside `StravaCallbackActivity` (Task 10), not the ViewModel, because the callback Activity may start in a fresh process. The ViewModel only re-reads connection state afterward.

- [ ] **Step 4: Enqueue on ride persist**

In `ui/RideViewModel.kt`, replace `persistLastRide()` with a version that captures the new rideId and enqueues when enabled+connected:

```kotlin
    fun persistLastRide() {
        val summary = lastSummary ?: return
        val snapshot = track.toList()
        viewModelScope.launch {
            val rideId = repo.saveRide(summary, snapshot, activePlanRideId)
            if (settings.value.stravaUploadEnabled && stravaStore.isConnected) {
                val ride = repo.getRide(rideId) ?: return@launch
                repo.markQueued(rideId, ride)
                StravaUploadScheduler.enqueue(getApplication(), rideId)
            }
        }
    }
```

(`settings` is the existing `StateFlow<RidemanSettings>`; `.value` reads the current snapshot.)

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run all unit tests (nothing regressed)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (existing + new).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/data/RideRepository.kt app/src/main/java/com/two17industries/rideman/data/Settings.kt app/src/main/java/com/two17industries/rideman/ui/RideViewModel.kt
git commit -m "wire Strava upload into repository and view model: enqueue, connect state, retry, backfill"
```

---

### Task 10: OAuth callback Activity, manifest, and Custom Tab launch

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/strava/StravaCallbackActivity.kt`
- Create: `app/src/main/java/com/two17industries/rideman/strava/CustomTabLauncher.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/two17industries/rideman/MainActivity.kt`

**Interfaces:**
- Consumes: `StravaAuth`, `StravaTokenStore`, `RideViewModel.refreshStravaConnection`, `RideViewModel.connectStravaUrl`.
- Produces: `object CustomTabLauncher { fun launch(context, url) }`; `StravaCallbackActivity` handling `rideman://strava-callback`.

- [ ] **Step 1: Custom Tab launcher**

Create `strava/CustomTabLauncher.kt`:

```kotlin
package com.two17industries.rideman.strava

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object CustomTabLauncher {
    fun launch(context: Context, url: String) {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    }
}
```

- [ ] **Step 2: Callback Activity**

Create `strava/StravaCallbackActivity.kt`. It reads the `code`, exchanges it, and finishes. It builds its own `StravaAuth` bound to a `StravaTokenStore` so it works even in a cold process.

```kotlin
package com.two17industries.rideman.strava

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.two17industries.rideman.BuildConfig
import kotlinx.coroutines.launch

class StravaCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent?.data?.getQueryParameter("code")
        if (code == null) {
            finish()
            return
        }
        val store = StravaTokenStore(applicationContext)
        val auth = StravaAuth(
            clientId = BuildConfig.STRAVA_CLIENT_ID,
            clientSecret = BuildConfig.STRAVA_CLIENT_SECRET,
            loadTokens = { store.load() },
            saveTokens = { store.save(it) },
            clearTokens = { store.clear() },
            http = OkHttpStravaHttp(),
            nowEpochSec = { System.currentTimeMillis() / 1000 },
        )
        lifecycleScope.launch {
            auth.exchangeCode(code)
            finish() // returns to MainActivity, whose onResume refreshes connection state
        }
    }
}
```

- [ ] **Step 3: Register the callback Activity and its deep link**

In `app/src/main/AndroidManifest.xml`, inside `<application>`, add:

```xml
        <activity
            android:name=".strava.StravaCallbackActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:theme="@android:style/Theme.Translucent.NoTitleBar">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="rideman" android:host="strava-callback" />
            </intent-filter>
        </activity>
```

- [ ] **Step 4: Refresh connection state when returning to MainActivity**

In `MainActivity.kt`, hold the ViewModel reference and refresh on resume. Change `App()` so the VM is hoisted, and add `onResume`:

```kotlin
    private var rideViewModel: RideViewModel? = null

    override fun onResume() {
        super.onResume()
        rideViewModel?.refreshStravaConnection()
    }
```

In the `App()` composable, capture the VM:

```kotlin
        val vm: RideViewModel = viewModel()
        rideViewModel = vm
```

- [ ] **Step 5: Build and install; verify the OAuth round-trip manually**

Run: `./gradlew :app:installDebug`
On device: this step is fully verified in Task 12 (needs UI from Task 11). For now confirm build/install succeed.
Expected: BUILD SUCCESSFUL; app installs.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/strava/StravaCallbackActivity.kt app/src/main/java/com/two17industries/rideman/strava/CustomTabLauncher.kt app/src/main/AndroidManifest.xml app/src/main/java/com/two17industries/rideman/MainActivity.kt
git commit -m "strava: OAuth callback activity, deep link, Custom Tab launch, resume refresh"
```

---

### Task 11: UI — Settings Strava section, History status chip + retry, backfill picker

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/ui/HistoryScreen.kt`
- Create: `app/src/main/java/com/two17industries/rideman/ui/BackfillScreen.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/ui/Nav.kt`

**Interfaces:**
- Consumes VM: `stravaConnected`, `stravaAthleteName`, `connectStravaUrl`, `disconnectStrava`, `retryUpload`, `backfillUpload`; `RideEntity.stravaState/stravaActivityId`; `CustomTabLauncher`.
- Produces: Settings row + upload toggle; History chip + retry; `BackfillScreen`; a new `Dest.BACKFILL` route.

- [ ] **Step 1: Add Strava controls to Settings**

In `ui/SettingsScreen.kt`, extend the signature and add a section. Change the function signature to:

```kotlin
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
```

Add a local state for the toggle near the other `remember`s:

```kotlin
    var stravaUploadEnabled by remember { mutableStateOf(current.stravaUploadEnabled) }
```

Add a `Section` before the `RIDE SCREENS` section:

```kotlin
        Section("STRAVA", accent) {
            if (stravaConnected) {
                Text(
                    "Connected" + (stravaAthleteName?.let { " as $it" } ?: ""),
                    color = accent, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Auto-upload rides", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    OptionPill(
                        text = if (stravaUploadEnabled) "On" else "Off",
                        selected = stravaUploadEnabled,
                        accent = accent,
                    ) { stravaUploadEnabled = !stravaUploadEnabled }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(2.dp, accent, RoundedCornerShape(50))
                        .clickable(onClick = onDisconnectStrava)
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                ) { Text("DISCONNECT", color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent)
                        .clickable(onClick = onConnectStrava)
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                ) { Text("CONNECT TO STRAVA", color = Background, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
```

In the SAVE button's `onSave(current.copy(...))`, add `stravaUploadEnabled = stravaUploadEnabled,` to the copy.

- [ ] **Step 2: Pass the new Settings params from Nav**

In `ui/Nav.kt`, collect the new flows near the other `collectAsState()` calls:

```kotlin
    val stravaConnected by vm.stravaConnected.collectAsState()
    val stravaAthleteName by vm.stravaAthleteName.collectAsState()
```

Update the `Dest.SETTINGS` branch to pass them and launch the Custom Tab (import `android.content.Context` via `LocalContext`):

```kotlin
        Dest.SETTINGS -> {
            BackHandler { dest = Dest.START }
            val context = androidx.compose.ui.platform.LocalContext.current
            SettingsScreen(
                current = settings,
                stravaConnected = stravaConnected,
                stravaAthleteName = stravaAthleteName,
                onConnectStrava = {
                    com.two17industries.rideman.strava.CustomTabLauncher.launch(context, vm.connectStravaUrl())
                },
                onDisconnectStrava = { vm.disconnectStrava() },
                onSave = { vm.saveSettings(it) },
                onDone = { dest = Dest.START },
                onCancel = { dest = Dest.START },
            )
        }
```

- [ ] **Step 3: Add a status chip + retry to History rows**

In `ui/HistoryScreen.kt`:
- Extend `HistoryScreen` and `RideRow` signatures with `onRetryUpload: (Long) -> Unit`.
- In `HistoryScreen`, pass `onRetryUpload = onRetryUpload` into `RideRow`.
- In `RideRow`, in the top `Row` (next to the PLAN/FREE label), render a chip based on `ride.stravaState`:

```kotlin
                StravaChip(state = ride.stravaState, accent = accent, onRetry = { onRetryUpload(ride.id) })
```

Add the composable and import (`import com.two17industries.rideman.data.StravaUploadState`):

```kotlin
@Composable
private fun StravaChip(state: StravaUploadState, accent: Color, onRetry: () -> Unit) {
    val (label, retry) = when (state) {
        StravaUploadState.NONE -> return
        StravaUploadState.QUEUED -> "⏳ Queued" to false
        StravaUploadState.UPLOADING -> "↑ Uploading" to false
        StravaUploadState.UPLOADED -> "✓ Strava" to false
        StravaUploadState.FAILED -> "⚠ Retry" to true
    }
    Text(
        label,
        color = if (state == StravaUploadState.FAILED) Color(0xFFFFCF3A) else accent,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge,
        modifier = if (retry) Modifier.clickable(onClick = onRetry) else Modifier,
    )
}
```

Note: `clickable` needs `import androidx.compose.foundation.clickable` (already imported in this file).

- [ ] **Step 4: Pass retry from Nav to History**

In `ui/Nav.kt` `Dest.HISTORY` branch, add `onRetryUpload = { vm.retryUpload(it) }` to the `HistoryScreen(...)` call.

- [ ] **Step 5: Backfill picker screen**

Create `ui/BackfillScreen.kt`:

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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.core.Units
import com.two17industries.rideman.data.RideEntity
import com.two17industries.rideman.data.StravaUploadState
import com.two17industries.rideman.ui.theme.Background
import com.two17industries.rideman.ui.theme.LocalAccent
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
    val accent = LocalAccent.current
    // Only rides not already uploaded are eligible.
    val eligible = remember(rides) { rides.filter { it.stravaState != StravaUploadState.UPLOADED } }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val fmt = remember { SimpleDateFormat("MMM d · h:mm a", Locale.US) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text(
            "◀ UPLOAD PAST RIDES",
            color = accent,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.clickable(onClick = onDone).padding(bottom = 12.dp),
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(eligible, key = { it.id }) { ride ->
                val isSel = ride.id in selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) accent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                        .clickable {
                            selected = if (isSel) selected - ride.id else selected + ride.id
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(fmt.format(Date(ride.startedAt)), color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        String.format(Locale.US, "%.1f %s", Units.distance(ride.distanceM, units), Units.distanceLabel(units).lowercase()),
                        color = if (isSel) accent else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Button(
            onClick = { onUpload(selected.toList()); onDone() },
            enabled = selected.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Background),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("UPLOAD ${selected.size} RIDE(S)", fontSize = 18.sp, fontWeight = FontWeight.Black) }
    }
}
```

- [ ] **Step 6: Route to the backfill screen from History**

In `ui/Nav.kt`:
- Add `BACKFILL` to the `Dest` enum.
- In the `Dest.HISTORY` `HistoryScreen(...)` call, the History header already has a back affordance; add an entry point by passing a new lambda. Simplest: add an "Upload past rides" affordance to `HistoryScreen` only when connected. Extend `HistoryScreen` with `stravaConnected: Boolean` and `onBackfill: () -> Unit`, and render a small tappable line under the header when `stravaConnected`:

```kotlin
        if (stravaConnected) {
            Text(
                "⭱ Upload past rides to Strava",
                color = accent,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clickable(onClick = onBackfill).padding(bottom = 8.dp),
            )
        }
```

- Pass `stravaConnected = stravaConnected` and `onBackfill = { dest = Dest.BACKFILL }` from Nav's `Dest.HISTORY` branch.
- Add the branch:

```kotlin
        Dest.BACKFILL -> {
            BackHandler { dest = Dest.HISTORY }
            BackfillScreen(
                rides = allRides,
                units = settings.units,
                onUpload = { vm.backfillUpload(it) },
                onDone = { dest = Dest.HISTORY },
            )
        }
```

- [ ] **Step 7: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Fix any missing imports the compiler flags (e.g., `LocalContext`, `clickable`).

- [ ] **Step 8: Run unit tests (no regressions)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/
git commit -m "ui: Strava settings section + upload toggle, History status chip + retry, backfill picker"
```

---

### Task 12: Manual end-to-end verification

**Files:** none (verification only).

This validates the whole feature against the real Strava API on the owner's account.

- [ ] **Step 1: Install on a real device with credentials set**

Ensure `local.properties` has real `strava.clientId`/`strava.clientSecret` and the Strava API app's Authorization Callback Domain is `rideman`.
Run: `./gradlew :app:installDebug`

- [ ] **Step 2: Connect**

Open Settings → CONNECT TO STRAVA → a Custom Tab opens Strava's auth page → Authorize → the app returns and Settings shows "Connected as ‹name›".
Expected: connected state persists after backgrounding the app.

- [ ] **Step 3: Record a short ride and confirm auto-upload**

Do a short outdoor ride (or drive) with GPS. End the ride and tap Done. Open History.
Expected: the ride's chip goes `⏳ Queued` → `↑ Uploading` → `✓ Strava` within a minute of connectivity. On strava.com the activity appears as a **Ride** with **distance matching** rideman's number and a sane map/route.

- [ ] **Step 4: Offline resilience**

Put the phone in airplane mode, record and end a short ride. Confirm chip stays `⏳ Queued`. Restore connectivity.
Expected: WorkManager uploads it automatically; chip flips to `✓ Strava`. Exactly one activity on Strava (no duplicate).

- [ ] **Step 5: Retry + dedup**

Force a failure (e.g., disconnect mid-upload), confirm chip shows `⚠ Retry`, tap it.
Expected: re-uploads and resolves to `✓ Strava` with no duplicate activity (same `external_id`).

- [ ] **Step 6: Backfill**

History → "Upload past rides to Strava" → select one past ride → Upload.
Expected: it uploads once; re-running backfill no longer lists it (state is UPLOADED).

- [ ] **Step 7: Toggle + disconnect**

Turn the auto-upload toggle Off, record a ride → it stays `NONE` (no chip). Turn on, Disconnect → Settings returns to CONNECT state; new rides do not upload.

- [ ] **Step 8: Final full test run and commit a note**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all green. No code commit required; if any manual fix was needed, commit it with a clear message.

---

## Self-Review

**Spec coverage:**
- OAuth on-device, embedded secret, EncryptedSharedPreferences → Tasks 1, 6, 10. ✓
- TCX with distance stream, exact-match distance, gzip → Task 4. ✓
- Auto upload via WorkManager with retry + `CONNECTED` constraint + backoff → Task 8. ✓
- Dedup via stable `external_id`; duplicate → UPLOADED → Tasks 3, 7, 8. ✓
- Per-ride state on `rides`, observed by History → Tasks 2, 11. ✓
- Settings connect/disconnect + upload toggle → Tasks 9, 11. ✓
- History chip + retry → Task 11. ✓
- Backfill picker → Task 11. ✓
- Encoder behind `RideExporter` interface (future FIT) → Task 4. ✓
- Error handling matrix (offline hold, revoked reconnect, 400 terminal, 5xx retry, duplicate) → Tasks 7, 8, 12. ✓
- Testing: TcxWriter, token refresh, upload parser, coordinator states, external_id + manual E2E → Tasks 3,4,6,7,8,12. ✓

**Placeholder scan:** No TBD/TODO; every code step has complete code. ✓

**Type consistency:** `StravaUploadState`, `UploadResult` (incl. `Pending`), `UploadOutcome`, `ExportedFile(bytes,dataType)`, `RideExporter.export(rideId): ExportedFile?`, `StravaAuth` lambda-injected persistence, `updateStravaStatus(rideId,state,activityId,externalId,error)` are used identically across tasks. ✓

**Known follow-ups (not blockers):** activity naming is left to Strava's auto-name (spec calls renaming out of scope); `sport_type=Ride` is sent on upload so TCX `Sport="Biking"` is reinforced by the form field.
