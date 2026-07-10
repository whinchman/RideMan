package com.two17industries.rideman.strava

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
        val resp = http.postForm(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "grant_type" to "refresh_token",
                "refresh_token" to current.refreshToken,
            ),
        )
        require(resp.isSuccess) { "Token refresh failed: HTTP ${resp.code}" }
        val root = json.parseToJsonElement(resp.body).jsonObject
        val refreshed = tokensFrom(root, fallbackName = current.athleteFirstName)
        saveTokens(refreshed)
        return refreshed.accessToken
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
        const val AUTHORIZE_URL = "https://www.strava.com/oauth/authorize"
        const val TOKEN_URL = "https://www.strava.com/oauth/token"
        const val REDIRECT_URI = "rideman://strava-callback"
    }
}
