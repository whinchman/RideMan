package com.two17industries.rideman.strava

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.two17industries.rideman.BuildConfig
import com.two17industries.rideman.data.RidemanDatabase
import com.two17industries.rideman.export.TcxExporter

class StravaUploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val rideId = inputData.getLong(KEY_RIDE_ID, -1L)
        if (rideId < 0) return Result.failure()

        val dao = RidemanDatabase.get(applicationContext).rideDao()
        val store = StravaTokenStore(applicationContext)
        val http = OkHttpStravaHttp()
        val auth = StravaAuth(
            clientId = BuildConfig.STRAVA_CLIENT_ID,
            clientSecret = BuildConfig.STRAVA_CLIENT_SECRET,
            loadTokens = { store.load() },
            saveTokens = { store.save(it) },
            clearTokens = { store.clear() },
            http = http,
            nowEpochSec = { System.currentTimeMillis() / 1000 },
        )
        val uploader = StravaUploader(http, auth)
        val exporter = TcxExporter(dao)
        val coordinator = StravaUploadCoordinator(
            getRide = { dao.getRide(it) },
            exporter = exporter,
            upload = { file, externalId, name -> uploader.upload(file, externalId, name) },
            updateStatus = { id, state, activityId, externalId, error ->
                dao.updateStravaStatus(id, state, activityId, externalId, error)
            },
        )

        return when (coordinator.uploadRide(rideId)) {
            UploadOutcome.SUCCESS -> Result.success()
            UploadOutcome.RETRY -> Result.retry()
            UploadOutcome.FAILED -> Result.failure()
        }
    }

    companion object {
        const val KEY_RIDE_ID = "rideId"
    }
}
