package com.two17industries.rideman.data

/**
 * Ride-screen order, and the one-shot migration that introduces GRID.
 *
 * Kept pure and separate from SettingsStore so it can be unit-tested — SettingsStore needs a
 * Context and DataStore, and this project has no Robolectric.
 */
object ScreenOrder {

    /**
     * Resolves the stored screen order.
     *
     * [saved] is what DataStore holds (null on a fresh install). [gridMigrated] and
     * [hrMigrated] are the one-shot `grid_migrated` / `hr_migrated` flags.
     *
     * GRID is prepended and HEART_RATE appended exactly once, to installs that predate them.
     * After each flag is set the user's order is authoritative forever — including a user who
     * deliberately disabled either page. Inferring a migration from "the screen is missing"
     * instead of a flag would resurrect it every time somebody turned it off.
     */
    fun migrate(
        saved: List<RideScreen>?,
        gridMigrated: Boolean,
        hrMigrated: Boolean,
    ): List<RideScreen> {
        val default = RideScreen.entries.toList()
        if (saved.isNullOrEmpty()) return default
        var out = saved
        if (!gridMigrated && !out.contains(RideScreen.GRID)) {
            out = listOf(RideScreen.GRID) + out
        }
        if (!hrMigrated && !out.contains(RideScreen.HEART_RATE)) {
            // Appended, not prepended: heart rate is a secondary page, and GRID must stay first.
            out = out + RideScreen.HEART_RATE
        }
        return out
    }
}
