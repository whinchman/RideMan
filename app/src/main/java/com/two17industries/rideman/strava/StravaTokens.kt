package com.two17industries.rideman.strava

data class StravaTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSec: Long,
    val athleteFirstName: String?,
)
