package com.two17industries.rideman.strava

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object StravaUploadScheduler {
    fun enqueue(context: Context, rideId: Long) {
        val request = OneTimeWorkRequestBuilder<StravaUploadWorker>()
            .setInputData(workDataOf(StravaUploadWorker.KEY_RIDE_ID to rideId))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("upload-$rideId", ExistingWorkPolicy.KEEP, request)
    }
}
