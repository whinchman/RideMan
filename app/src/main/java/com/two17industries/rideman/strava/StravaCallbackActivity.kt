package com.two17industries.rideman.strava

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.two17industries.rideman.BuildConfig
import kotlinx.coroutines.launch

class StravaCallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = intent?.data?.getQueryParameter("code")
        if (code == null) {
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
            auth.exchangeCode(code)
            finish() // returns to MainActivity, whose onResume refreshes connection state
        }
    }
}
