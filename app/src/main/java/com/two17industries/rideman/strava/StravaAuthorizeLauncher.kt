package com.two17industries.rideman.strava

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Opens Strava's *mobile* authorize URL as an implicit intent.
 *
 * Deliberately NOT a Custom Tab. Strava's mobile OAuth flow ends by redirecting to our custom
 * scheme (`rideman://strava-callback?code=…`), and a browser tab will not follow a non-http(s)
 * protocol — the user authorises successfully and then lands nowhere, which is exactly the bug
 * this replaced. An implicit intent lets the installed Strava app claim the URL and hand the code
 * back to us; with no Strava app installed it falls through to a browser, which can still complete
 * the flow.
 *
 * [CustomTabLauncher] remains correct for ordinary https links (e.g. viewing an activity).
 */
object StravaAuthorizeLauncher {
    fun launch(context: Context, authorizeUrl: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No browser and no Strava app: nothing can service the authorisation.
            Log.w("StravaAuthorize", "nothing can handle the Strava authorize URL", e)
        }
    }
}
