package com.two17industries.rideman.strava

object StravaTokenLogic {
    /** True when the access token is expired or will expire within [skewSec]. */
    fun needsRefresh(nowEpochSec: Long, expiresAtEpochSec: Long, skewSec: Long = 60): Boolean =
        nowEpochSec >= (expiresAtEpochSec - skewSec)
}
