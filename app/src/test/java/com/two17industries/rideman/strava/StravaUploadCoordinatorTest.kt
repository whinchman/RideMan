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
