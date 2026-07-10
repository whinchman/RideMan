package com.two17industries.rideman.strava

import com.two17industries.rideman.export.ExportedFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class StravaUploaderTest {

    /** Minimal StravaHttp fake: canned multipart response + a queue of poll responses. */
    private class FakeHttp(
        var multipartResponse: HttpResponse = HttpResponse(200, ""),
        pollResponses: List<HttpResponse> = emptyList(),
        /** Returned once the queue is drained (keeps the loop fed). */
        var pollFallback: HttpResponse = HttpResponse(200, ""),
    ) : StravaHttp {
        private val pollQueue = ArrayDeque(pollResponses)
        var getCalls = 0
            private set

        override suspend fun postForm(url: String, fields: Map<String, String>): HttpResponse =
            HttpResponse(200, "")

        override suspend fun get(url: String, bearer: String): HttpResponse {
            getCalls++
            return if (pollQueue.isNotEmpty()) pollQueue.removeFirst() else pollFallback
        }

        override suspend fun postMultipart(
            url: String, bearer: String, fileFieldName: String, fileName: String,
            fileBytes: ByteArray, textFields: Map<String, String>,
        ): HttpResponse = multipartResponse
    }

    private class MemStore {
        var tokens: StravaTokens? = null
    }

    private fun auth(http: StravaHttp, store: MemStore) = StravaAuth(
        clientId = "CID",
        clientSecret = "SECRET",
        loadTokens = { store.tokens },
        saveTokens = { store.tokens = it },
        clearTokens = { store.tokens = null },
        http = http,
        nowEpochSec = { 0 },
    )

    private val file = ExportedFile(byteArrayOf(1), "tcx.gz")

    @Test fun auth_failure_returns_terminal_and_does_not_throw() = runTest {
        val http = FakeHttp()
        val store = MemStore() // no tokens -> freshAccessToken() throws "Not connected"
        val uploader = StravaUploader(http, auth(http, store), pollDelayMs = 1, maxPolls = 3)

        val result = uploader.upload(file, externalId = "ext-1", activityName = "Ride")

        assertTrue(result is UploadResult.Terminal)
    }

    @Test fun immediate_success_returns_success() = runTest {
        val http = FakeHttp(
            multipartResponse = HttpResponse(201, """{"id":99,"activity_id":12345,"error":null}"""),
        )
        val store = MemStore().apply {
            tokens = StravaTokens("AAA", "RRR", expiresAtEpochSec = Long.MAX_VALUE, athleteFirstName = "x")
        }
        val uploader = StravaUploader(http, auth(http, store), pollDelayMs = 1, maxPolls = 3)

        val result = uploader.upload(file, externalId = "ext-1", activityName = "Ride")

        assertEquals(UploadResult.Success(12345), result)
        assertEquals(0, http.getCalls) // no polling needed
    }

    @Test fun pending_then_exhausted_returns_retryable() = runTest {
        val pending = HttpResponse(201, """{"id":99,"activity_id":null,"error":null}""")
        val http = FakeHttp(
            multipartResponse = pending,
            pollFallback = pending, // every poll stays Pending
        )
        val store = MemStore().apply {
            tokens = StravaTokens("AAA", "RRR", expiresAtEpochSec = Long.MAX_VALUE, athleteFirstName = "x")
        }
        val maxPolls = 3
        val uploader = StravaUploader(http, auth(http, store), pollDelayMs = 1, maxPolls = maxPolls)

        val result = uploader.upload(file, externalId = "ext-1", activityName = "Ride")

        assertTrue(result is UploadResult.Retryable)
        assertEquals(maxPolls, http.getCalls) // polled exactly maxPolls times before giving up
    }
}
