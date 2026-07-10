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
