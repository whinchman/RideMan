package com.two17industries.rideman.strava

import com.two17industries.rideman.export.ExportedFile
import kotlinx.coroutines.delay

class StravaUploader(
    private val http: StravaHttp,
    private val auth: StravaAuth,
    private val pollDelayMs: Long = 2000,
    private val maxPolls: Int = 15,
) {
    /** Uploads a file and polls to a terminal result. */
    suspend fun upload(file: ExportedFile, externalId: String, activityName: String?): UploadResult {
        val bearer = try {
            auth.freshAccessToken()
        } catch (e: Exception) {
            return UploadResult.Terminal("auth: ${e.message}")
        }

        val fields = buildMap {
            put("data_type", file.dataType)
            put("external_id", externalId)
            put("sport_type", "Ride")
            activityName?.let { put("name", it) }
        }
        val post = http.postMultipart(
            url = UPLOADS_URL,
            bearer = bearer,
            fileFieldName = "file",
            fileName = "$externalId.${file.dataType}",
            fileBytes = file.bytes,
            textFields = fields,
        )
        var result = UploadResponseParser.parse(post.code, post.body)

        var polls = 0
        while (result is UploadResult.Pending && polls < maxPolls) {
            delay(pollDelayMs)
            polls++
            val poll = http.get("$UPLOADS_URL/${result.uploadId}", bearer)
            result = UploadResponseParser.parse(poll.code, poll.body)
        }
        return if (result is UploadResult.Pending) {
            UploadResult.Retryable("still processing after $maxPolls polls")
        } else {
            result
        }
    }

    private companion object {
        const val UPLOADS_URL = "https://www.strava.com/api/v3/uploads"
    }
}
