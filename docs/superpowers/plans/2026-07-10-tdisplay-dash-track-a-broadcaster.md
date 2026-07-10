# T-Display Dash — Track A: rideman BLE Broadcaster — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a BLE-central broadcaster to rideman that, during a ride, connects to the T-Display S3 and writes the frozen 16-byte telemetry packet (speed / distance / elapsed / GPS-heading) ~1 Hz — and prove the wire format on-device before any firmware exists.

**Architecture:** A self-contained `dash/` module runs inside the existing `LocationForegroundService` (so it survives screen-off / phone-in-pack). It owns its own `RideTracker` fed from `LocationBus` (deterministic, so its distance matches the ViewModel's), a 1 Hz ticker, and a `DashBleClient` (Android BLE central) that scans for the board's service UUID, connects, and writes packets. Encoding is a pure, unit-tested function that locks the contract. Everything is additive — no change to GPS tracking, the phone dash, `RideTracker`, or Strava upload.

**Tech Stack:** Kotlin, Android platform BLE (`android.bluetooth`, no new library), Coroutines, DataStore, JUnit4. Contract source of truth: `strava-bike-puter/docs/superpowers/specs/2026-07-10-tdisplay-dash-design.md`.

## Global Constraints

- Package root `com.two17industries.rideman`; source `app/src/main/java/com/two17industries/rideman/`, tests `app/src/test/java/com/two17industries/rideman/`.
- `minSdk = 34`, `compileSdk = 36`, Kotlin 2.1.0, Java 17. Use the API-33+ `BluetoothGatt.writeCharacteristic(char, value, writeType)` (not the deprecated setValue path).
- **Additive only:** do NOT modify GPS tracking behavior, `RideTracker`, `Geo`, the phone dash, or Strava code. The only existing files edited are `AndroidManifest.xml`, `data/Settings.kt`, `location/LocationForegroundService.kt`, `MainActivity.kt`, `ui/SettingsScreen.kt`, `ui/Nav.kt`.
- **Frozen BLE contract** (must match verbatim; do not change without a `version` bump on both tracks):
  - Service UUID `a8dc6189-de03-4f47-82ed-830b7b48d183`; Telemetry characteristic `5d48becb-087d-4c7e-88c4-83d30e7860e9` (Write Without Response); advertised name `rideman-dash`.
  - Packet: 16 bytes, little-endian — `u8 version=1`, `u8 flags` (bit0 units 1=US, bit1 ride-active, bit2 gps-fix-valid), `u16 speed_cmps`, `u32 distance_m`, `u32 elapsed_s`, `u16 heading_deg` (0–359, GPS course), `i16 altitude_m`.
- **Heading source = GPS course:** use `LocationSample.headingDeg` (which is `Location.bearing`), NOT `RideUiState.headingDeg`.
- **Elapsed = real 1 Hz clock** (`now − rideStart`), not `RideUiState.elapsedMs`.
- Tests JUnit4 (`org.junit.Test`, `org.junit.Assert.*`); assert real behavior.
- **Shared round-trip vectors** (asserted here in Track A's encoder test AND later in Track B's decoder test — the anti-drift guard). Bytes are hex, in wire order:

  | # | Telemetry (SI) | flags | 16 bytes (hex) |
  |---|----------------|-------|----------------|
  | V1 | speed 6.7 m/s, dist 12437 m, elapsed 2847 s, head 315°, alt 221 m, US, ride, fix | `07` | `01 07 9E 02 95 30 00 00 1F 0B 00 00 3B 01 DD 00` |
  | V2 | all-zero, metric, ride active, NO fix | `02` | `01 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00` |
  | V3 | speed 13.9 m/s, dist 50000 m, elapsed 7325 s, head 372°→12°, alt −45 m, metric, ride, fix | `06` | `01 06 6E 05 50 C3 00 00 9D 1C 00 00 0C 00 D3 FF` |

- Work on branch `tdisplay-dash-broadcaster` in `bike_helper_app` (create it before Task 1).

---

### Task 1: Permissions, foreground-service type, and the `dashEnabled` setting

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/two17industries/rideman/data/Settings.kt`
- Modify: `app/src/main/java/com/two17industries/rideman/MainActivity.kt`

**Interfaces:**
- Produces: `RidemanSettings.dashEnabled: Boolean = false`; BLE runtime permissions requested at launch; the location FGS also declares the `connectedDevice` type.

- [ ] **Step 1: Manifest — BLE permissions + connectedDevice FGS type**

In `AndroidManifest.xml`, add after the existing `<uses-permission>` lines:

```xml
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
```

Change the service declaration's type to include connectedDevice:

```xml
        <service
            android:name=".location.LocationForegroundService"
            android:exported="false"
            android:foregroundServiceType="location|connectedDevice" />
```

- [ ] **Step 2: Settings — add the `dashEnabled` flag (default off; opt-in, needs hardware)**

In `data/Settings.kt`:
- Add `val dashEnabled: Boolean = false` to `RidemanSettings` (after `stravaUploadEnabled`).
- In `Keys`, add `val DASH_ENABLED = booleanPreferencesKey("dash_enabled")`.
- In the `settings` map, add `dashEnabled = p[Keys.DASH_ENABLED] ?: false`.
- In `save`, add `p[Keys.DASH_ENABLED] = s.dashEnabled`.

- [ ] **Step 3: Request BLE permissions at launch**

In `MainActivity.kt`, extend the `permissions.launch(arrayOf(...))` call to include:

```kotlin
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
```

- [ ] **Step 4: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/two17industries/rideman/data/Settings.kt app/src/main/java/com/two17industries/rideman/MainActivity.kt
git commit -m "dash: BLE permissions, connectedDevice FGS type, dashEnabled setting"
```

---

### Task 2: BLE UUID constants and the connection-status singleton

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/dash/DashBleContract.kt`
- Create: `app/src/main/java/com/two17industries/rideman/dash/DashStatus.kt`

**Interfaces:**
- Produces: `object DashBleContract { val SERVICE_UUID, TELEMETRY_UUID: UUID; const val DEVICE_NAME }`; `enum class DashConnectionState { DISABLED, SCANNING, CONNECTED, DISCONNECTED }`; `object DashStatus { val state: StateFlow<DashConnectionState>; fun set(...) }`.

- [ ] **Step 1: Contract constants**

Create `dash/DashBleContract.kt`:

```kotlin
package com.two17industries.rideman.dash

import java.util.UUID

/** Frozen BLE contract — see strava-bike-puter tdisplay-dash design spec. */
object DashBleContract {
    val SERVICE_UUID: UUID = UUID.fromString("a8dc6189-de03-4f47-82ed-830b7b48d183")
    val TELEMETRY_UUID: UUID = UUID.fromString("5d48becb-087d-4c7e-88c4-83d30e7860e9")
    const val DEVICE_NAME = "rideman-dash"
}
```

- [ ] **Step 2: Status singleton**

Create `dash/DashStatus.kt`:

```kotlin
package com.two17industries.rideman.dash

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class DashConnectionState { DISABLED, SCANNING, CONNECTED, DISCONNECTED }

/** Single-process channel from the BLE client to the UI (mirrors LocationBus). */
object DashStatus {
    private val _state = MutableStateFlow(DashConnectionState.DISABLED)
    val state: StateFlow<DashConnectionState> = _state
    fun set(s: DashConnectionState) { _state.value = s }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/dash/DashBleContract.kt app/src/main/java/com/two17industries/rideman/dash/DashStatus.kt
git commit -m "dash: BLE contract UUIDs + connection-status singleton"
```

---

### Task 3: Telemetry model + packet encoder (TDD — locks the contract)

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/dash/TelemetryPacket.kt`
- Test: `app/src/test/java/com/two17industries/rideman/dash/TelemetryPacketTest.kt`

**Interfaces:**
- Produces: `data class Telemetry(speedMps: Float, distanceM: Double, elapsedSec: Long, headingDeg: Float, altitudeM: Double, unitsUS: Boolean, rideActive: Boolean, gpsValid: Boolean)`; `object TelemetryPacket { const val SIZE = 16; const val VERSION = 1; fun encode(t: Telemetry): ByteArray }`.

- [ ] **Step 1: Write the failing test (the shared vectors)**

Create `dash/TelemetryPacketTest.kt`:

```kotlin
package com.two17industries.rideman.dash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryPacketTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun v1_moving_us_units() {
        val t = Telemetry(
            speedMps = 6.7f, distanceM = 12437.0, elapsedSec = 2847,
            headingDeg = 315f, altitudeM = 221.0,
            unitsUS = true, rideActive = true, gpsValid = true,
        )
        assertArrayEquals(
            bytes(0x01, 0x07, 0x9E, 0x02, 0x95, 0x30, 0x00, 0x00, 0x1F, 0x0B, 0x00, 0x00, 0x3B, 0x01, 0xDD, 0x00),
            TelemetryPacket.encode(t),
        )
    }

    @Test fun v2_zero_metric_no_fix() {
        val t = Telemetry(0f, 0.0, 0, 0f, 0.0, unitsUS = false, rideActive = true, gpsValid = false)
        assertArrayEquals(
            bytes(0x01, 0x02, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            TelemetryPacket.encode(t),
        )
    }

    @Test fun v3_clamps_and_wraps() {
        val t = Telemetry(
            speedMps = 13.9f, distanceM = 50000.0, elapsedSec = 7325,
            headingDeg = 372f, altitudeM = -45.0, // heading wraps to 12
            unitsUS = false, rideActive = true, gpsValid = true,
        )
        assertArrayEquals(
            bytes(0x01, 0x06, 0x6E, 0x05, 0x50, 0xC3, 0x00, 0x00, 0x9D, 0x1C, 0x00, 0x00, 0x0C, 0x00, 0xD3, 0xFF),
            TelemetryPacket.encode(t),
        )
    }

    @Test fun packet_is_16_bytes() {
        val t = Telemetry(1f, 1.0, 1, 1f, 1.0, unitsUS = true, rideActive = true, gpsValid = true)
        assertEquals(16, TelemetryPacket.encode(t).size)
    }

    @Test fun speed_clamps_at_u16_max() {
        val t = Telemetry(1000f, 0.0, 0, 0f, 0.0, unitsUS = false, rideActive = false, gpsValid = false)
        val out = TelemetryPacket.encode(t)
        // 1000 m/s -> 100000 cm/s, clamps to 65535 = 0xFFFF
        assertEquals(0xFF.toByte(), out[2])
        assertEquals(0xFF.toByte(), out[3])
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.dash.TelemetryPacketTest"`
Expected: FAIL — unresolved reference `Telemetry` / `TelemetryPacket`.

- [ ] **Step 3: Implement**

Create `dash/TelemetryPacket.kt`:

```kotlin
package com.two17industries.rideman.dash

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** SI-unit snapshot of the ride, ready to serialize onto the wire. */
data class Telemetry(
    val speedMps: Float,
    val distanceM: Double,
    val elapsedSec: Long,
    val headingDeg: Float,
    val altitudeM: Double,
    val unitsUS: Boolean,
    val rideActive: Boolean,
    val gpsValid: Boolean,
)

/** Encodes [Telemetry] into the frozen 16-byte little-endian packet. */
object TelemetryPacket {
    const val SIZE = 16
    const val VERSION = 1

    private const val FLAG_UNITS_US = 0x01
    private const val FLAG_RIDE_ACTIVE = 0x02
    private const val FLAG_GPS_VALID = 0x04

    fun encode(t: Telemetry): ByteArray {
        var flags = 0
        if (t.unitsUS) flags = flags or FLAG_UNITS_US
        if (t.rideActive) flags = flags or FLAG_RIDE_ACTIVE
        if (t.gpsValid) flags = flags or FLAG_GPS_VALID

        val speedCmps = (t.speedMps * 100f).roundToInt().coerceIn(0, 0xFFFF)
        val distance = t.distanceM.roundToLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val elapsed = t.elapsedSec.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        val heading = (((t.headingDeg.roundToInt() % 360) + 360) % 360) // 0..359
        val altitude = t.altitudeM.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

        return ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(VERSION.toByte())
            put(flags.toByte())
            putShort((speedCmps and 0xFFFF).toShort())
            putInt(distance)
            putInt(elapsed)
            putShort((heading and 0xFFFF).toShort())
            putShort(altitude.toShort())
        }.array()
    }
}
```

- [ ] **Step 4: Run it and watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.dash.TelemetryPacketTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/dash/TelemetryPacket.kt app/src/test/java/com/two17industries/rideman/dash/TelemetryPacketTest.kt
git commit -m "dash: Telemetry model + 16-byte packet encoder (TDD, shared vectors)"
```

---

### Task 4: Pure telemetry builder (TDD)

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/dash/TelemetryBuilder.kt`
- Test: `app/src/test/java/com/two17industries/rideman/dash/TelemetryBuilderTest.kt`

**Interfaces:**
- Consumes: `LocationSample` (`core/Models.kt`), `Telemetry`.
- Produces: `object TelemetryBuilder { fun build(sample: LocationSample?, distanceM: Double, elapsedSec: Long, unitsUS: Boolean): Telemetry }`.

- [ ] **Step 1: Write the failing test**

Create `dash/TelemetryBuilderTest.kt`:

```kotlin
package com.two17industries.rideman.dash

import com.two17industries.rideman.core.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryBuilderTest {

    @Test fun null_sample_is_no_fix_zeroed_but_keeps_distance_and_time() {
        val t = TelemetryBuilder.build(sample = null, distanceM = 100.0, elapsedSec = 5, unitsUS = true)
        assertFalse(t.gpsValid)
        assertEquals(0f, t.speedMps, 0f)
        assertEquals(0f, t.headingDeg, 0f)
        assertEquals(100.0, t.distanceM, 0.0)   // distance/elapsed come from the tracker/clock, not the fix
        assertEquals(5L, t.elapsedSec)
        assertTrue(t.rideActive)
        assertTrue(t.unitsUS)
    }

    @Test fun present_sample_passes_through_speed_heading_altitude() {
        val s = LocationSample(
            epochMillis = 0, lat = 40.0, lng = -88.0,
            speedMps = 6.7f, headingDeg = 315f, gpsAltitudeM = 221.0,
        )
        val t = TelemetryBuilder.build(sample = s, distanceM = 12437.0, elapsedSec = 2847, unitsUS = false)
        assertTrue(t.gpsValid)
        assertEquals(6.7f, t.speedMps, 0f)
        assertEquals(315f, t.headingDeg, 0f)   // GPS course, from the sample bearing
        assertEquals(221.0, t.altitudeM, 0.0)
        assertFalse(t.unitsUS)
    }

    @Test fun null_altitude_becomes_zero() {
        val s = LocationSample(0, 40.0, -88.0, 3f, 90f, gpsAltitudeM = null)
        val t = TelemetryBuilder.build(s, 0.0, 0, unitsUS = true)
        assertEquals(0.0, t.altitudeM, 0.0)
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.dash.TelemetryBuilderTest"`
Expected: FAIL — unresolved reference `TelemetryBuilder`.

- [ ] **Step 3: Implement**

Create `dash/TelemetryBuilder.kt`:

```kotlin
package com.two17industries.rideman.dash

import com.two17industries.rideman.core.LocationSample

/**
 * Assembles a [Telemetry] snapshot. distance/elapsed are supplied by the broadcaster's own
 * RideTracker + clock; speed/heading/altitude come from the latest GPS fix (heading is the
 * GPS course-over-ground). A null fix means "no data yet" — gpsValid=false, motion fields 0,
 * but distance and elapsed still flow through.
 */
object TelemetryBuilder {
    fun build(sample: LocationSample?, distanceM: Double, elapsedSec: Long, unitsUS: Boolean): Telemetry =
        Telemetry(
            speedMps = sample?.speedMps ?: 0f,
            distanceM = distanceM,
            elapsedSec = elapsedSec,
            headingDeg = sample?.headingDeg ?: 0f,
            altitudeM = sample?.gpsAltitudeM ?: 0.0,
            unitsUS = unitsUS,
            rideActive = true,
            gpsValid = sample != null,
        )
}
```

- [ ] **Step 4: Run it and watch it pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.two17industries.rideman.dash.TelemetryBuilderTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/dash/TelemetryBuilder.kt app/src/test/java/com/two17industries/rideman/dash/TelemetryBuilderTest.kt
git commit -m "dash: pure telemetry builder (TDD)"
```

---

### Task 5: BLE central client (scan → connect → write)

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/dash/DashBleClient.kt`

**Interfaces:**
- Consumes: `DashBleContract`, `DashStatus`, Android `android.bluetooth.*`.
- Produces: `class DashBleClient(context: Context) { fun start(); fun write(bytes: ByteArray); fun stop() }`. Updates `DashStatus`. Not unit-tested (Android BLE framework) — verified in Task 9.

- [ ] **Step 1: Implement the client**

Create `dash/DashBleClient.kt`:

```kotlin
package com.two17industries.rideman.dash

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import androidx.core.content.ContextCompat

/**
 * BLE central: scans for the T-Display by service UUID, connects, and writes telemetry to the
 * Telemetry characteristic (write-without-response). Auto-reconnects on drop. All calls are
 * no-ops (not crashes) when BLE permissions are missing or Bluetooth is off.
 */
@SuppressLint("MissingPermission") // guarded by hasBlePermissions()
class DashBleClient(private val context: Context) {

    private val manager by lazy { context.getSystemService(BluetoothManager::class.java) }
    private val scanner: BluetoothLeScanner? get() = manager?.adapter?.bluetoothLeScanner

    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var scanning = false
    private var wantRunning = false

    fun start() {
        if (!hasBlePermissions() || manager?.adapter?.isEnabled != true) {
            DashStatus.set(DashConnectionState.DISABLED)
            return
        }
        wantRunning = true
        startScan()
    }

    fun stop() {
        wantRunning = false
        stopScan()
        gatt?.close()
        gatt = null
        characteristic = null
        DashStatus.set(DashConnectionState.DISABLED)
    }

    fun write(bytes: ByteArray) {
        val g = gatt ?: return
        val c = characteristic ?: return
        if (!hasBlePermissions()) return
        g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
    }

    private fun startScan() {
        val s = scanner ?: return
        if (scanning) return
        scanning = true
        DashStatus.set(DashConnectionState.SCANNING)
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(DashBleContract.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        s.startScan(listOf(filter), settings, scanCallback)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        if (hasBlePermissions()) scanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            stopScan()
            result.device.connectGatt(context, false, gattCallback, BluetoothGatt.TRANSPORT_LE)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    gatt = g
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    characteristic = null
                    g.close()
                    if (gatt === g) gatt = null
                    DashStatus.set(DashConnectionState.DISCONNECTED)
                    if (wantRunning) startScan() // auto-reconnect
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(DashBleContract.SERVICE_UUID)
                ?.getCharacteristic(DashBleContract.TELEMETRY_UUID)
            if (ch != null) {
                characteristic = ch
                DashStatus.set(DashConnectionState.CONNECTED)
            } else {
                g.disconnect()
            }
        }
    }

    private fun hasBlePermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/dash/DashBleClient.kt
git commit -m "dash: BLE central client (scan, connect, write-no-response, auto-reconnect)"
```

---

### Task 6: The broadcaster (RideTracker + LocationBus + 1 Hz ticker)

**Files:**
- Create: `app/src/main/java/com/two17industries/rideman/dash/DashBroadcaster.kt`

**Interfaces:**
- Consumes: `LocationBus`, `RideTracker` (`core/`), `SettingsStore`, `DashBleClient`, `TelemetryBuilder`, `TelemetryPacket`.
- Produces: `class DashBroadcaster(context, scope) { fun start(); fun stop() }`.

- [ ] **Step 1: Implement the broadcaster**

Create `dash/DashBroadcaster.kt`:

```kotlin
package com.two17industries.rideman.dash

import android.content.Context
import com.two17industries.rideman.core.LocationSample
import com.two17industries.rideman.core.RideTracker
import com.two17industries.rideman.core.UnitSystem
import com.two17industries.rideman.data.SettingsStore
import com.two17industries.rideman.location.LocationBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the T-Display during a ride: owns its own RideTracker fed from LocationBus (so its
 * distance matches the ViewModel's identical accumulation), and every second builds a
 * telemetry packet and writes it to the board. Runs inside the location foreground service.
 */
class DashBroadcaster(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val client = DashBleClient(appContext)
    private val settingsStore = SettingsStore(appContext)

    private val startMillis = System.currentTimeMillis()
    private val tracker = RideTracker(startMillis)
    @Volatile private var latest: LocationSample? = null
    @Volatile private var unitsUS: Boolean = true

    private val jobs = mutableListOf<Job>()

    fun start() {
        client.start()
        jobs += scope.launch {
            LocationBus.latest.collect { sample ->
                sample ?: return@collect
                tracker.add(sample)
                latest = sample
            }
        }
        jobs += scope.launch {
            settingsStore.settings.map { it.units }.collect { unitsUS = it == UnitSystem.AMERICAN }
        }
        jobs += scope.launch {
            while (isActive) {
                val elapsedSec = (System.currentTimeMillis() - startMillis) / 1000
                val telemetry = TelemetryBuilder.build(latest, tracker.distanceM, elapsedSec, unitsUS)
                client.write(TelemetryPacket.encode(telemetry))
                delay(1000)
            }
        }
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        client.stop()
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/dash/DashBroadcaster.kt
git commit -m "dash: broadcaster — own RideTracker + 1 Hz packet writer"
```

---

### Task 7: Host the broadcaster in the foreground service

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/location/LocationForegroundService.kt`

**Interfaces:**
- Consumes: `SettingsStore`, `DashBroadcaster`. Produces: dash broadcasting that starts with the ride when `dashEnabled`, and includes the `connectedDevice` FGS type.

- [ ] **Step 1: Add a scope, start the broadcaster when enabled, include connectedDevice FGS type**

In `LocationForegroundService.kt`:
- Add imports: `android.content.pm.ServiceInfo`, `com.two17industries.rideman.dash.DashBroadcaster`, `com.two17industries.rideman.data.SettingsStore`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.SupervisorJob`, `kotlinx.coroutines.cancel`, `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.launch`.
- Add fields:

```kotlin
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var broadcaster: DashBroadcaster? = null
```

- Change the `startForeground(...)` call to declare both types:

```kotlin
        startForeground(
            NOTIF_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
```

- After `requestUpdates()` in `onStartCommand`, start the dash if enabled:

```kotlin
        scope.launch {
            if (SettingsStore(applicationContext).settings.first().dashEnabled) {
                broadcaster = DashBroadcaster(applicationContext, scope).also { it.start() }
            }
        }
```

- In `onDestroy`, before `super.onDestroy()`:

```kotlin
        broadcaster?.stop()
        broadcaster = null
        scope.cancel()
```

- [ ] **Step 2: Build + install; verify no FGS-type crash**

Run: `./gradlew :app:installDebug`
On device (dash toggle can be off for now): start a ride, confirm the location notification appears and the app does NOT crash on `startForeground` (Android 14 enforces declared FGS types).
Expected: BUILD SUCCESSFUL; ride starts normally.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/location/LocationForegroundService.kt
git commit -m "dash: host broadcaster in the location foreground service (gated by dashEnabled)"
```

---

### Task 8: Settings UI — toggle + live connection indicator

**Files:**
- Modify: `app/src/main/java/com/two17industries/rideman/ui/SettingsScreen.kt`

**Interfaces:**
- Consumes: `DashStatus`, `DashConnectionState`, `RidemanSettings.dashEnabled`.
- Produces: a "HANDLEBAR DASHBOARD" settings section with an on/off pill and a live status line.

- [ ] **Step 1: Add the section**

In `ui/SettingsScreen.kt`:
- Add imports: `androidx.compose.runtime.collectAsState`, `com.two17industries.rideman.dash.DashConnectionState`, `com.two17industries.rideman.dash.DashStatus`.
- Add a local state near the other `remember`s:

```kotlin
    var dashEnabled by remember { mutableStateOf(current.dashEnabled) }
```

- Add a `Section` (place it just before the `STRAVA` section):

```kotlin
        Section("HANDLEBAR DASHBOARD", accent) {
            val dashState by DashStatus.state.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("T-Display over BLE", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                OptionPill(
                    text = if (dashEnabled) "On" else "Off",
                    selected = dashEnabled,
                    accent = accent,
                ) { dashEnabled = !dashEnabled }
            }
            if (dashEnabled) {
                val label = when (dashState) {
                    DashConnectionState.CONNECTED -> "Connected"
                    DashConnectionState.SCANNING -> "Searching…"
                    DashConnectionState.DISCONNECTED -> "Disconnected"
                    DashConnectionState.DISABLED -> "Idle (starts with your ride)"
                }
                Text(label, color = accent.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
```

- In the SAVE button's `onSave(current.copy(...))`, add `dashEnabled = dashEnabled,`.

- [ ] **Step 2: Build + install**

Run: `./gradlew :app:installDebug`
On device: Settings shows the HANDLEBAR DASHBOARD toggle; toggling + SAVE persists (reopen Settings to confirm the state sticks).
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/two17industries/rideman/ui/SettingsScreen.kt
git commit -m "dash: Settings toggle + live BLE connection indicator"
```

---

### Task 9: On-device format validation (contract lock — no firmware needed)

**Files:** none (validation only). This proves the broadcaster's wire output against the frozen contract, using a phone BLE tool as a stand-in for the T-Display.

- [ ] **Step 1: Stand up a stand-in peripheral**

On a SECOND phone/tablet, install **nRF Connect for Mobile** (Nordic). Use its **Advertiser / GATT Server** feature to:
- Create a GATT server with a service whose UUID is `a8dc6189-de03-4f47-82ed-830b7b48d183`, containing one characteristic `5d48becb-087d-4c7e-88c4-83d30e7860e9` with **Write / Write Without Response** enabled.
- Advertise it with the device name `rideman-dash` and the service UUID included in the advertisement.

- [ ] **Step 2: Connect from rideman**

On the rideman phone: Settings → HANDLEBAR DASHBOARD → On → SAVE. Grant the Bluetooth permissions when prompted. Start a ride. Within a few seconds the Settings status (or a re-open) should read **Connected**, and nRF Connect should show an incoming connection + ~1 Hz writes to the characteristic.

- [ ] **Step 3: Decode and verify the bytes**

In nRF Connect, open the characteristic's write log and read the 16-byte hex values. Verify against the contract:
- Byte 0 = `01` (version); byte 1 flags has bit1 (ride active) set, bit0 = your units, bit2 set once GPS has a fix.
- Bytes 2–3 (u16 LE) = speed in cm/s → matches the phone dash's speed (×100).
- Bytes 4–7 (u32 LE) = distance in metres → matches the phone's distance.
- Bytes 8–11 (u32 LE) = elapsed seconds, incrementing ~1/sec.
- Bytes 12–13 (u16 LE) = heading 0–359 (GPS course) — changes as you change direction while moving; ~0/low-confidence when stationary.
- Bytes 14–15 (i16 LE) = altitude in metres.

Move around (or drive) and confirm speed/distance/heading track reality and update ~1 Hz.

- [ ] **Step 4: Verify reconnect + teardown**

Toggle Bluetooth off/on on the peripheral (or walk out of range): rideman status → Disconnected → Searching → Connected again (auto-reconnect). End the ride: writes stop and the client tears down (status returns to Idle/Disabled).

- [ ] **Step 5: Record results**

Note in the branch (commit message or a short `docs/superpowers/` note) that the wire format was validated against the frozen contract on-device, including one real decoded packet. This is the gate that locks the contract before Track B (firmware) begins.

---

## Self-Review

**Spec coverage (Track A portion of the design):**
- Phone = BLE central, connects + writes 16-byte packet ~1 Hz → Tasks 5, 6. ✓
- Frozen contract (UUIDs, name, 16-byte little-endian layout) → Tasks 2, 3 (+ shared vectors). ✓
- Heading = GPS course from `LocationSample.headingDeg`; elapsed = real 1 Hz clock; own RideTracker for distance → Tasks 4, 6. ✓
- Units mirror rideman via flag → Tasks 3, 4, 6. ✓
- Runs in the foreground service (survives screen-off) → Task 7. ✓
- Settings toggle + connection indicator; opt-in default off → Tasks 1, 8. ✓
- BLE permissions + connectedDevice FGS type → Tasks 1, 7. ✓
- Validation before firmware (contract lock) → Task 9. ✓

**Placeholder scan:** none — every code step is complete; the shared byte-vectors are concrete.

**Type consistency:** `Telemetry` fields, `TelemetryPacket.encode`, `TelemetryBuilder.build(sample, distanceM, elapsedSec, unitsUS)`, `DashConnectionState`, `DashStatus.state/set`, `DashBleClient.start/write/stop`, `DashBroadcaster.start/stop` are used identically across tasks. `LocationSample` fields (`speedMps`, `headingDeg`, `gpsAltitudeM`) match `core/Models.kt`.

**Deferred to Track B (firmware):** decode side of the shared vectors, the 2×2 render, battery/buttons/sleep. Track A ends with the wire format proven, so the contract is locked.
