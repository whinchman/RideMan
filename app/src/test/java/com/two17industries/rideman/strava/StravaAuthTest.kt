package com.two17industries.rideman.strava

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StravaAuthTest {

    private class FakeHttp(
        var formResponse: HttpResponse = HttpResponse(200, ""),
        var getResponse: HttpResponse = HttpResponse(200, ""),
    ) : StravaHttp {
        val formCalls = mutableListOf<Pair<String, Map<String, String>>>()
        override suspend fun postForm(url: String, fields: Map<String, String>): HttpResponse {
            formCalls += url to fields
            return formResponse
        }
        override suspend fun get(url: String, bearer: String) = getResponse
        override suspend fun postMultipart(
            url: String, bearer: String, fileFieldName: String, fileName: String,
            fileBytes: ByteArray, textFields: Map<String, String>,
        ) = HttpResponse(200, "")
    }

    // In-memory stand-in for StravaTokenStore (same shape, no Android).
    private class MemStore {
        var tokens: StravaTokens? = null
    }

    private fun auth(http: StravaHttp, store: MemStore, now: () -> Long) = StravaAuth(
        clientId = "CID",
        clientSecret = "SECRET",
        loadTokens = { store.tokens },
        saveTokens = { store.tokens = it },
        clearTokens = { store.tokens = null },
        http = http,
        nowEpochSec = now,
    )

    @Test fun authorize_url_contains_client_id_scope_and_redirect() {
        val a = auth(FakeHttp(), MemStore()) { 0 }
        val url = a.authorizeUrl()
        assertTrue(url.startsWith("https://www.strava.com/oauth/mobile/authorize"))
        assertTrue(url.contains("client_id=CID"))
        assertTrue(url.contains("redirect_uri=rideman%3A%2F%2Fstrava-callback"))
        assertTrue(url.contains("scope=activity%3Awrite%2Cread"))
        assertTrue(url.contains("response_type=code"))
    }

    /**
     * The *web* endpoint (/oauth/authorize) will not redirect to a custom scheme: it authorises
     * fine and then strands the user in the browser, because `rideman://` is not a protocol a
     * browser will follow. Android must use the mobile endpoint. Observed on-device 2026-07-14:
     * "I authorized, but it never came back."
     */
    @Test fun authorize_url_is_the_mobile_endpoint_not_the_web_one() {
        val a = auth(FakeHttp(), MemStore()) { 0 }
        val url = a.authorizeUrl()
        assertTrue("must use /oauth/mobile/authorize", url.contains("/oauth/mobile/authorize"))
        assertFalse(
            "must NOT use the web /oauth/authorize — it cannot redirect to rideman://",
            url.substringBefore('?').endsWith("/oauth/authorize"),
        )
    }

    @Test fun exchange_code_stores_tokens_and_name() = runTest {
        val http = FakeHttp(
            formResponse = HttpResponse(
                200,
                """{"access_token":"AAA","refresh_token":"RRR","expires_at":5000,
                   "athlete":{"firstname":"Will"}}""",
            ),
        )
        val store = MemStore()
        val a = auth(http, store) { 0 }
        val result = a.exchangeCode("CODE")
        assertTrue(result.isSuccess)
        assertEquals("AAA", store.tokens?.accessToken)
        assertEquals("RRR", store.tokens?.refreshToken)
        assertEquals(5000L, store.tokens?.expiresAtEpochSec)
        assertEquals("Will", store.tokens?.athleteFirstName)
        // Called the token endpoint with grant_type=authorization_code.
        assertEquals("authorization_code", http.formCalls.single().second["grant_type"])
    }

    @Test fun fresh_token_returns_stored_access_without_refresh() = runTest {
        val http = FakeHttp()
        val store = MemStore().apply {
            tokens = StravaTokens("STILL_GOOD", "RRR", expiresAtEpochSec = 9999, athleteFirstName = "Will")
        }
        val a = auth(http, store) { 1000 } // well before expiry
        assertEquals("STILL_GOOD", a.freshAccessToken())
        assertTrue(http.formCalls.isEmpty()) // no refresh call
    }

    @Test fun expired_token_triggers_refresh_and_persists_new_tokens() = runTest {
        val http = FakeHttp(
            formResponse = HttpResponse(
                200,
                """{"access_token":"NEW","refresh_token":"RRR2","expires_at":20000}""",
            ),
        )
        val store = MemStore().apply {
            tokens = StravaTokens("OLD", "RRR", expiresAtEpochSec = 5000, athleteFirstName = "Will")
        }
        val a = auth(http, store) { 6000 } // past expiry
        assertEquals("NEW", a.freshAccessToken())
        assertEquals("RRR2", store.tokens?.refreshToken)
        assertEquals(20000L, store.tokens?.expiresAtEpochSec)
        // Name is preserved across refresh (refresh response has no athlete).
        assertEquals("Will", store.tokens?.athleteFirstName)
        assertEquals("refresh_token", http.formCalls.single().second["grant_type"])
    }

    @Test fun second_call_after_refresh_reuses_new_token_without_refreshing_again() = runTest {
        val http = FakeHttp(
            formResponse = HttpResponse(
                200,
                """{"access_token":"NEW","refresh_token":"RRR2","expires_at":20000}""",
            ),
        )
        val store = MemStore().apply {
            tokens = StravaTokens("OLD", "RRR", expiresAtEpochSec = 5000, athleteFirstName = "Will")
        }
        val a = auth(http, store) { 6000 } // past expiry, before new expiry (20000)
        assertEquals("NEW", a.freshAccessToken())
        // Second call should see the already-refreshed token and skip a redundant refresh.
        assertEquals("NEW", a.freshAccessToken())
        assertEquals(1, http.formCalls.size)
    }
}
