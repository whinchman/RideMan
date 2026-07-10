package com.two17industries.rideman.strava

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpStravaHttp(
    private val client: OkHttpClient = OkHttpClient(),
) : StravaHttp {

    override suspend fun postForm(url: String, fields: Map<String, String>): HttpResponse =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
            execute(Request.Builder().url(url).post(body).build())
        }

    override suspend fun get(url: String, bearer: String): HttpResponse =
        withContext(Dispatchers.IO) {
            execute(Request.Builder().url(url).header("Authorization", "Bearer $bearer").get().build())
        }

    override suspend fun postMultipart(
        url: String,
        bearer: String,
        fileFieldName: String,
        fileName: String,
        fileBytes: ByteArray,
        textFields: Map<String, String>,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        textFields.forEach { (k, v) -> builder.addFormDataPart(k, v) }
        builder.addFormDataPart(
            fileFieldName,
            fileName,
            fileBytes.toRequestBody("application/octet-stream".toMediaType()),
        )
        execute(
            Request.Builder().url(url)
                .header("Authorization", "Bearer $bearer")
                .post(builder.build())
                .build(),
        )
    }

    private fun execute(request: Request): HttpResponse =
        client.newCall(request).execute().use { resp ->
            HttpResponse(code = resp.code, body = resp.body?.string() ?: "")
        }
}
