package com.two17industries.rideman.strava

object StravaExternalId {
    fun forRide(rideId: Long, startedAtEpochMillis: Long): String =
        "rideman-$rideId-$startedAtEpochMillis"
}
