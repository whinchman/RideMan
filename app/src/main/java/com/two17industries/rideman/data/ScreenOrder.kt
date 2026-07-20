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
     * [saved] is what DataStore holds (null on a fresh install). [alreadyMigrated] is the
     * one-shot `grid_migrated` flag.
     *
     * GRID is prepended exactly once, to installs that predate it. After the flag is set the
     * user's order is authoritative forever — including a user who deliberately disabled GRID.
     * Inferring the migration from "GRID is missing" instead of a flag would resurrect the dash
     * every time somebody turned it off.
     */
    fun migrate(saved: List<RideScreen>?, alreadyMigrated: Boolean): List<RideScreen> {
        val default = RideScreen.entries.toList()
        if (saved.isNullOrEmpty()) return default
        if (alreadyMigrated) return saved
        if (saved.contains(RideScreen.GRID)) return saved
        return listOf(RideScreen.GRID) + saved
    }
}
