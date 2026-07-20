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
        // Inspect only the per-trackpoint distances inside <Track>, not the Lap summary.
        val trackXml = xml.substringAfter("<Track>").substringBefore("</Track>")
        val distances = Regex("<DistanceMeters>([0-9.]+)</DistanceMeters>")
            .findAll(trackXml).map { it.groupValues[1].toDouble() }.toList()
        // First trackpoint distance is 0; last equals the cumulative total.
        assertEquals(0.0, distances.first(), 0.001)
        assertEquals(expectedTotal, distances.last(), 0.01)
        // Monotonic non-decreasing.
        assertTrue(distances.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun includes_position_altitude_and_speed_extension() {
        val xml = TcxWriter.write(ride(), points())
        assertTrue(xml.contains("<LatitudeDegrees>40.4406</LatitudeDegrees>"))
        assertTrue(xml.contains("<AltitudeMeters>220.0</AltitudeMeters>"))
        assertTrue(xml.contains("<ns3:Speed>4.0</ns3:Speed>")) // TPX extension, m/s
    }

    @Test fun empty_track_still_produces_valid_activity() {
        val xml = TcxWriter.write(ride(), emptyList())
        assertTrue(xml.contains("<Activity Sport=\"Biking\">"))
        assertTrue(xml.contains("</TrainingCenterDatabase>"))
    }

    @Test
    fun `heart rate is emitted between DistanceMeters and Extensions`() {
        val ride = RideEntity(
            id = 1, startedAt = 0L, endedAt = 1_000L, totalTimeMs = 1_000L,
            distanceM = 10.0, maxSpeedMps = 5f, avgSpeedMps = 5f,
        )
        val points = listOf(
            TrackPointEntity(
                rideId = 1, timestamp = 0L, lat = 1.0, lng = 2.0,
                altitudeM = null, speedMps = 5f, headingDeg = 0f,
                accuracyM = null, heartRateBpm = 142,
            ),
        )
        val xml = TcxWriter.write(ride, points)
        assertTrue(xml.contains("<HeartRateBpm><Value>142</Value></HeartRateBpm>"))
        // XSD child order: DistanceMeters, then HeartRateBpm, then Extensions.
        val d = xml.indexOf("<DistanceMeters>", xml.indexOf("<Trackpoint>"))
        val h = xml.indexOf("<HeartRateBpm>")
        val e = xml.indexOf("<Extensions>")
        assertTrue("expected DistanceMeters < HeartRateBpm < Extensions", d < h && h < e)
    }

    @Test
    fun `a point without heart rate emits no HeartRateBpm tag`() {
        val ride = RideEntity(
            id = 1, startedAt = 0L, endedAt = 1_000L, totalTimeMs = 1_000L,
            distanceM = 10.0, maxSpeedMps = 5f, avgSpeedMps = 5f,
        )
        val points = listOf(
            TrackPointEntity(
                rideId = 1, timestamp = 0L, lat = 1.0, lng = 2.0,
                altitudeM = null, speedMps = 5f, headingDeg = 0f,
                accuracyM = null, heartRateBpm = null,
            ),
        )
        assertTrue(!TcxWriter.write(ride, points).contains("<HeartRateBpm>"))
    }

    @Test
    fun `a dropout mid ride produces exactly one HeartRateBpm tag`() {
        val ride = RideEntity(
            id = 1, startedAt = 0L, endedAt = 2_000L, totalTimeMs = 2_000L,
            distanceM = 20.0, maxSpeedMps = 5f, avgSpeedMps = 5f,
        )
        val points = listOf(
            TrackPointEntity(
                rideId = 1, timestamp = 0L, lat = 1.0, lng = 2.0,
                altitudeM = null, speedMps = 5f, headingDeg = 0f,
                accuracyM = null, heartRateBpm = 130,
            ),
            TrackPointEntity(
                rideId = 1, timestamp = 1_000L, lat = 1.1, lng = 2.1,
                altitudeM = null, speedMps = 5f, headingDeg = 0f,
                accuracyM = null, heartRateBpm = null,
            ),
        )
        val xml = TcxWriter.write(ride, points)
        assertEquals(1, Regex("<HeartRateBpm>").findAll(xml).count())
    }

    @Test
    fun `lap level heart rate is emitted between MaximumSpeed and Intensity`() {
        val ride = RideEntity(
            id = 1, startedAt = 0L, endedAt = 1_000L, totalTimeMs = 1_000L,
            distanceM = 10.0, maxSpeedMps = 5f, avgSpeedMps = 5f,
            avgHeartRateBpm = 138, maxHeartRateBpm = 171,
        )
        val xml = TcxWriter.write(ride, emptyList())
        assertTrue(xml.contains("<AverageHeartRateBpm><Value>138</Value></AverageHeartRateBpm>"))
        assertTrue(xml.contains("<MaximumHeartRateBpm><Value>171</Value></MaximumHeartRateBpm>"))
        val m = xml.indexOf("<MaximumSpeed>")
        val a = xml.indexOf("<AverageHeartRateBpm>")
        val i = xml.indexOf("<Intensity>")
        assertTrue("expected MaximumSpeed < AverageHeartRateBpm < Intensity", m < a && a < i)
    }

    @Test
    fun `a ride with no heart rate emits no lap level tags`() {
        val ride = RideEntity(
            id = 1, startedAt = 0L, endedAt = 1_000L, totalTimeMs = 1_000L,
            distanceM = 10.0, maxSpeedMps = 5f, avgSpeedMps = 5f,
        )
        val xml = TcxWriter.write(ride, emptyList())
        assertTrue(!xml.contains("AverageHeartRateBpm"))
        assertTrue(!xml.contains("MaximumHeartRateBpm"))
    }
}
