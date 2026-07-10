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
