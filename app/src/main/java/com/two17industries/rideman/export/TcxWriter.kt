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
            // TCX v2 Trackpoint child order: Time, Position, AltitudeMeters, DistanceMeters,
            // HeartRateBpm, Cadence, SensorState, Extensions.
            p.heartRateBpm?.let {
                track.append("            <HeartRateBpm><Value>$it</Value></HeartRateBpm>\n")
            }
            track.append("            <Extensions>\n")
            track.append("              <ns3:TPX>\n")
            track.append("                <ns3:Speed>${p.speedMps}</ns3:Speed>\n")
            track.append("              </ns3:TPX>\n")
            track.append("            </Extensions>\n")
            track.append("          </Trackpoint>\n")
        }

        // Lap child order follows the TCX v2 XSD sequence: TotalTimeSeconds (above),
        // DistanceMeters, MaximumSpeed, AverageHeartRateBpm, MaximumHeartRateBpm, Intensity,
        // TriggerMethod, then Track.
        sb.append("        <DistanceMeters>$cumulative</DistanceMeters>\n")
        sb.append("        <MaximumSpeed>${ride.maxSpeedMps}</MaximumSpeed>\n")
        ride.avgHeartRateBpm?.let {
            sb.append("        <AverageHeartRateBpm><Value>$it</Value></AverageHeartRateBpm>\n")
        }
        ride.maxHeartRateBpm?.let {
            sb.append("        <MaximumHeartRateBpm><Value>$it</Value></MaximumHeartRateBpm>\n")
        }
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
