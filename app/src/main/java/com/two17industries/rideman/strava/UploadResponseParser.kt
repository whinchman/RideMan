package com.two17industries.rideman.strava

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Maps a Strava upload/poll HTTP response to an [UploadResult]. */
object UploadResponseParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(code: Int, body: String): UploadResult {
        if (code in 500..599) return UploadResult.Retryable("HTTP $code")
        if (code == 429) return UploadResult.Retryable("rate limited")
        if (code !in 200..299) return UploadResult.Terminal("HTTP $code: ${body.take(200)}")

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return UploadResult.Terminal("unparseable response")

        val error = root["error"]?.jsonPrimitive?.contentOrNull
        val activityId = root["activity_id"]?.jsonPrimitive?.longOrNull
        val uploadId = root["id"]?.jsonPrimitive?.longOrNull ?: 0L

        return when {
            error != null && error.contains("duplicate", ignoreCase = true) -> {
                // "duplicate of activity 777" → pull the id when present.
                val dupId = Regex("(\\d+)").find(error)?.value?.toLongOrNull()
                UploadResult.Duplicate(dupId)
            }
            error != null -> UploadResult.Terminal(error)
            activityId != null -> UploadResult.Success(activityId)
            else -> UploadResult.Pending(uploadId)
        }
    }
}
