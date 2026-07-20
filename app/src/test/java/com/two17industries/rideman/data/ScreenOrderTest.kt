package com.two17industries.rideman.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenOrderTest {

    @Test fun fresh_install_gets_the_full_default_order_with_grid_first() {
        val result = ScreenOrder.migrate(saved = null, gridMigrated = false, hrMigrated = false)
        assertEquals(RideScreen.entries.toList(), result)
        assertEquals(RideScreen.GRID, result.first())
    }

    @Test fun legacy_order_gets_grid_prepended() {
        val legacy = listOf(RideScreen.SPEED, RideScreen.ODOMETER, RideScreen.COMPASS)
        val result = ScreenOrder.migrate(saved = legacy, gridMigrated = false, hrMigrated = true)
        assertEquals(
            listOf(RideScreen.GRID, RideScreen.SPEED, RideScreen.ODOMETER, RideScreen.COMPASS),
            result,
        )
    }

    @Test fun legacy_migration_preserves_a_users_custom_order() {
        val legacy = listOf(RideScreen.CADENCE, RideScreen.SPEED)
        val result = ScreenOrder.migrate(saved = legacy, gridMigrated = false, hrMigrated = true)
        assertEquals(listOf(RideScreen.GRID, RideScreen.CADENCE, RideScreen.SPEED), result)
    }

    /** The bug this whole design exists to prevent. */
    @Test fun a_user_who_disabled_grid_keeps_it_disabled() {
        val disabled = listOf(RideScreen.SPEED, RideScreen.ODOMETER)
        val result = ScreenOrder.migrate(saved = disabled, gridMigrated = true, hrMigrated = true)
        assertEquals(disabled, result)
    }

    @Test fun migration_does_not_duplicate_grid_if_it_is_somehow_already_there() {
        val saved = listOf(RideScreen.GRID, RideScreen.SPEED)
        val result = ScreenOrder.migrate(saved = saved, gridMigrated = false, hrMigrated = true)
        assertEquals(listOf(RideScreen.GRID, RideScreen.SPEED), result)
    }

    @Test fun an_empty_saved_order_falls_back_to_the_default() {
        val result = ScreenOrder.migrate(saved = emptyList(), gridMigrated = true, hrMigrated = true)
        assertEquals(RideScreen.entries.toList(), result)
    }

    @Test fun a_post_migration_order_is_returned_untouched() {
        val saved = listOf(RideScreen.SPEED, RideScreen.GRID, RideScreen.CADENCE)
        val result = ScreenOrder.migrate(saved = saved, gridMigrated = true, hrMigrated = true)
        assertEquals(saved, result)
    }

    @Test
    fun `heart rate is appended once to a pre-existing order`() {
        val saved = listOf(RideScreen.GRID, RideScreen.SPEED)
        assertEquals(
            listOf(RideScreen.GRID, RideScreen.SPEED, RideScreen.HEART_RATE),
            ScreenOrder.migrate(saved, gridMigrated = true, hrMigrated = false),
        )
    }

    @Test
    fun `heart rate is not re-added once the flag is set`() {
        val saved = listOf(RideScreen.GRID, RideScreen.SPEED)
        assertEquals(saved, ScreenOrder.migrate(saved, gridMigrated = true, hrMigrated = true))
    }

    @Test
    fun `a deliberately disabled heart rate page is not resurrected`() {
        // Same rule the GRID migration follows: the flag decides, not the absence.
        val saved = listOf(RideScreen.GRID, RideScreen.SPEED)
        assertEquals(saved, ScreenOrder.migrate(saved, gridMigrated = true, hrMigrated = true))
    }

    @Test
    fun `heart rate is not duplicated if already present`() {
        val saved = listOf(RideScreen.GRID, RideScreen.HEART_RATE)
        assertEquals(saved, ScreenOrder.migrate(saved, gridMigrated = true, hrMigrated = false))
    }

    @Test
    fun `a fresh install gets every screen`() {
        assertEquals(
            RideScreen.entries.toList(),
            ScreenOrder.migrate(null, gridMigrated = false, hrMigrated = false),
        )
    }

    @Test
    fun `both migrations can apply at once`() {
        val saved = listOf(RideScreen.SPEED)
        assertEquals(
            listOf(RideScreen.GRID, RideScreen.SPEED, RideScreen.HEART_RATE),
            ScreenOrder.migrate(saved, gridMigrated = false, hrMigrated = false),
        )
    }
}
