package com.two17industries.rideman.strava

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder

class StravaAuth(
    private val clientId: String,
    private val clientSecret: String,
    private val loadTokens: () -> StravaTokens?,
    private val saveTokens: (StravaTokens) -> Unit,
    private val clearTokens: () -> Unit,
    private val http: StravaHttp,
    private val nowEpochSec: () -> Long,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val isConnected: Boolean get() = loadTokens() != null
    val athleteFirstName: String? get() = loadTokens()?.athleteFirstName

    fun authorizeUrl(): String {
        val redirect = enc(REDIRECT_URI)
        val scope = enc("activity:write,read")
        return "$AUTHORIZE_URL?client_id=$clientId&response_type=code" +
            "&redirect_uri=$redirect&approval_prompt=auto&scope=$scope"
    }

    /** Exchanges an auth code for tokens, fetches the athlete first name, and persists. */
    suspend fun exchangeCode(code: String): Result<Unit> = runCatching {
        val resp = http.postForm(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "code" to code,
                "grant_type" to "authorization_code",
            ),
        )
        require(resp.isSuccess) { "Token exchange failed: HTTP ${resp.code}" }
        val root = json.parseToJsonElement(resp.body).jsonObject
        val name = root["athlete"]?.jsonObject?.get("firstname")?.jsonPrimitive?.contentOrNull
        saveTokens(tokensFrom(root, fallbackName = name))
    }

    /** Returns a non-expired access token, refreshing if needed. Throws if not connected. */
    suspend fun freshAccessToken(): String {
        val current = loadTokens() ?: error("Not connected to Strava")
        if (!StravaTokenLogic.needsRefresh(nowEpochSec(), current.expiresAtEpochSec)) {
            return current.accessToken
        }
        return refreshMutex.withLock {
            // Another caller may have refreshed while we waited for the lock.
            val latest = loadTokens() ?: error("Not connected to Strava")
            if (!StravaTokenLogic.needsRefresh(nowEpochSec(), latest.expiresAtEpochSec)) {
                return@withLock latest.accessToken
            }
            val resp = http.postForm(
                TOKEN_URL,
                mapOf(
                    "client_id" to clientId,
                    "client_secret" to clientSecret,
                    "grant_type" to "refresh_token",
                    "refresh_token" to latest.refreshToken,
                ),
            )
            require(resp.isSuccess) { "Token refresh failed: HTTP ${resp.code}" }
            val root = json.parseToJsonElement(resp.body).jsonObject
            val refreshed = tokensFrom(root, fallbackName = latest.athleteFirstName)
            saveTokens(refreshed)
            refreshed.accessToken
        }
    }

    fun disconnect() = clearTokens()

    private fun tokensFrom(root: kotlinx.serialization.json.JsonObject, fallbackName: String?) =
        StravaTokens(
            accessToken = root["access_token"]!!.jsonPrimitive.content,
            refreshToken = root["refresh_token"]!!.jsonPrimitive.content,
            expiresAtEpochSec = root["expires_at"]?.jsonPrimitive?.longOrNull ?: 0L,
            athleteFirstName = fallbackName,
        )

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private companion object {
        /**
         * The **mobile** authorize endpoint, not the web one. The web endpoint
         * (`/oauth/authorize`) authorises fine and then strands the user in the browser: it
         * cannot redirect to a custom scheme like `rideman://`, which no browser will follow.
         * Strava's docs require the mobile endpoint on Android, reached by an implicit intent so
         * the Strava app itself can service the authorisation and hand back to us.
         */
        const val AUTHORIZE_URL = "https://www.strava.com/oauth/mobile/authorize"
        const val TOKEN_URL = "https://www.strava.com/oauth/token"
        const val REDIRECT_URI = "rideman://strava-callback"

        // Shared across every StravaAuth instance in the process, since the companion
        // object is one-per-class. This serializes concurrent refreshes (e.g. from
        // parallel backfill WorkManager jobs) so Strava's rotating refresh token
        // can't be raced and invalidated by a losing concurrent call.
        private val refreshMutex = Mutex()
    }
}
