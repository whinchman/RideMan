package com.two17industries.rideman.data

/** Lifecycle of a ride's upload to Strava. Persisted as its name in the rides table. */
enum class StravaUploadState { NONE, QUEUED, UPLOADING, UPLOADED, FAILED }
