package com.two17industries.rideman.strava

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.two17industries.rideman.BuildConfig
import kotlinx.coroutines.launch

private const val TAG = "StravaCallback"

class StravaCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent?.data?.getQueryParameter("code")
        // Never log the code itself — it is a bearer credential until it is exchanged.
        Log.i(TAG, "callback received, code present=${code != null}")
        if (code == null) {
            Log.w(TAG, "no ?code= in callback — nothing to exchange")
            finish()
            return
        }
        val store = StravaTokenStore(applicationContext)
        val auth = StravaAuth(
            clientId = BuildConfig.STRAVA_CLIENT_ID,
            clientSecret = BuildConfig.STRAVA_CLIENT_SECRET,
            loadTokens = { store.load() },
            saveTokens = { store.save(it) },
            clearTokens = { store.clear() },
            http = OkHttpStravaHttp(),
            nowEpochSec = { System.currentTimeMillis() / 1000 },
        )
        lifecycleScope.launch {
            // Never swallow this: a failed exchange used to close the activity silently, leaving
            // the user "authorised" on Strava's side but disconnected here, with no error anywhere.
            auth.exchangeCode(code)
                .onFailure { Log.w(TAG, "Strava code exchange FAILED", it) }
                .onSuccess { Log.i(TAG, "Strava code exchange OK — tokens stored") }
            finish() // returns to MainActivity, whose onResume refreshes connection state
        }
    }
}
