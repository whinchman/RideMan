package com.two17industries.rideman.strava

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object CustomTabLauncher {
    fun launch(context: Context, url: String) {
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
    }
}
