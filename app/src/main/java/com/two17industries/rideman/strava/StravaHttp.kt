package com.two17industries.rideman.strava

/** Minimal HTTP surface Strava needs. Abstracted so auth/upload logic is unit-testable. */
data class HttpResponse(val code: Int, val body: String) {
    val isSuccess: Boolean get() = code in 200..299
    val isServerError: Boolean get() = code in 500..599
}

interface StravaHttp {
    suspend fun postForm(url: String, fields: Map<String, String>): HttpResponse

    suspend fun get(url: String, bearer: String): HttpResponse

    suspend fun postMultipart(
        url: String,
        bearer: String,
        fileFieldName: String,
        fileName: String,
        fileBytes: ByteArray,
        textFields: Map<String, String>,
    ): HttpResponse
}
