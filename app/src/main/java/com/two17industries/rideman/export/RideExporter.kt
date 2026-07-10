package com.two17industries.rideman.export

/** A ride serialized to an uploadable file plus the Strava data_type token. */
data class ExportedFile(val bytes: ByteArray, val dataType: String)

interface RideExporter {
    /** Returns the exported file, or null if the ride id does not exist. */
    suspend fun export(rideId: Long): ExportedFile?
}
