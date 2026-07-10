package com.two17industries.rideman.strava

sealed interface UploadResult {
    data class Success(val activityId: Long) : UploadResult
    data class Duplicate(val activityId: Long?) : UploadResult
    data class Retryable(val message: String) : UploadResult
    data class Terminal(val message: String) : UploadResult
    /** Upload accepted but still processing; caller should poll again. */
    data class Pending(val uploadId: Long) : UploadResult
}
