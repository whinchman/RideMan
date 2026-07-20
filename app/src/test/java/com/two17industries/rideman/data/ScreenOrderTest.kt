package com.two17industries.rideman.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenOrderTest {

    @Test fun fresh_install_gets_the_full_default_order_with_grid_first() {
        val result = ScreenOrder.migrate(saved = null, alreadyMigrated = false)
        assertEquals(RideScreen.entries.toList(), result)
        assertEquals(RideScreen.GRID, result.first())
    }

    @Test fun legacy_order_gets_grid_prepended() {
        val legacy = listOf(RideScreen.SPEED, RideScreen.ODOMETER, RideScreen.COMPASS)
        val result = ScreenOrder.migrate(saved = legacy, alreadyMigrated = false)
        assertEquals(
            listOf(RideScreen.GRID, RideScreen.SPEED, RideScreen.ODOMETER, RideScreen.COMPASS),
            result,
        )
    }

    @Test fun legacy_migration_preserves_a_users_custom_order() {
        val legacy = listOf(RideScreen.CADENCE, RideScreen.SPEED)
        val result = ScreenOrder.migrate(saved = legacy, alreadyMigrated = false)
        assertEquals(listOf(RideScreen.GRID, RideScreen.CADENCE, RideScreen.SPEED), result)
    }

    /** The bug this whole design exists to prevent. */
    @Test fun a_user_who_disabled_grid_keeps_it_disabled() {
        val disabled = listOf(RideScreen.SPEED, RideScreen.ODOMETER)
        val result = ScreenOrder.migrate(saved = disabled, alreadyMigrated = true)
        assertEquals(disabled, result)
    }

    @Test fun migration_does_not_duplicate_grid_if_it_is_somehow_already_there() {
        val saved = listOf(RideScreen.GRID, RideScreen.SPEED)
        val result = ScreenOrder.migrate(saved = saved, alreadyMigrated = false)
        assertEquals(listOf(RideScreen.GRID, RideScreen.SPEED), result)
    }

    @Test fun an_empty_saved_order_falls_back_to_the_default() {
        val result = ScreenOrder.migrate(saved = emptyList(), alreadyMigrated = true)
        assertEquals(RideScreen.entries.toList(), result)
    }

    @Test fun a_post_migration_order_is_returned_untouched() {
        val saved = listOf(RideScreen.SPEED, RideScreen.GRID, RideScreen.CADENCE)
        val result = ScreenOrder.migrate(saved = saved, alreadyMigrated = true)
        assertEquals(saved, result)
    }
}
