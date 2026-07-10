package com.two17industries.rideman.strava

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Persists Strava tokens encrypted at rest. */
class StravaTokenStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "strava_tokens",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val isConnected: Boolean get() = prefs.contains(KEY_REFRESH)

    fun save(tokens: StravaTokens) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putLong(KEY_EXPIRES, tokens.expiresAtEpochSec)
            .putString(KEY_NAME, tokens.athleteFirstName)
            .apply()
    }

    fun load(): StravaTokens? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        return StravaTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSec = prefs.getLong(KEY_EXPIRES, 0),
            athleteFirstName = prefs.getString(KEY_NAME, null),
        )
    }

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_NAME = "athlete_first_name"
    }
}
