package com.two17industries.rideman.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanProgressTest {

    private fun ride(id: String, miles: Double) = PlanRide(
        id = id, phaseNumber = 1, phaseName = "P", week = 1, slot = "A",
        kind = "easy", targetMiles = miles, pace = Pace.EASY,
        longRide = false, guidance = "", recoveryWeek = false,
    )

    private val plan = Plan(
        title = "T",
        rides = listOf(ride("w1A", 7.0), ride("w1B", 3.0), ride("w1C", 10.0)),
        tolerancePercent = 5,
    )

    private fun attempt(id: String, miles: Double) =
        PlanAttempt(id, miles * PlanGrading.METERS_PER_MILE)

    @Test fun ride_at_target_is_met() {
        assertTrue(PlanGrading.isMet(plan.rides[0], 7.0 * PlanGrading.METERS_PER_MILE, 5))
    }

    @Test fun ride_within_tolerance_is_met() {
        // 7 mi target, 5% tolerance -> 6.65 mi clears.
        assertTrue(PlanGrading.isMet(plan.rides[0], 6.65 * PlanGrading.METERS_PER_MILE, 5))
    }

    @Test fun ride_below_tolerance_is_not_met() {
        assertFalse(PlanGrading.isMet(plan.rides[0], 6.64 * PlanGrading.METERS_PER_MILE, 5))
    }

    @Test fun slot_complete_when_any_attempt_clears() {
        val p = PlanProgress(plan, listOf(attempt("w1A", 5.0), attempt("w1A", 7.1)))
        assertTrue(p.isComplete("w1A"))
    }

    @Test fun short_only_attempts_leave_slot_open() {
        val p = PlanProgress(plan, listOf(attempt("w1A", 5.0), attempt("w1A", 6.0)))
        assertFalse(p.isComplete("w1A"))
    }

    @Test fun next_incomplete_skips_completed_slots() {
        val p = PlanProgress(plan, listOf(attempt("w1A", 7.0)))
        assertEquals("w1B", p.nextIncomplete()?.id)
    }

    @Test fun next_incomplete_is_null_when_all_done() {
        val p = PlanProgress(plan, listOf(attempt("w1A", 7.0), attempt("w1B", 3.0), attempt("w1C", 10.0)))
        assertNull(p.nextIncomplete())
    }

    @Test fun completed_count_and_total() {
        val p = PlanProgress(plan, listOf(attempt("w1A", 7.0), attempt("w1C", 10.0)))
        assertEquals(2, p.completedCount())
        assertEquals(3, p.total)
    }

    @Test fun unknown_plan_ride_id_is_not_complete() {
        val p = PlanProgress(plan, listOf(attempt("w9Z", 99.0)))
        assertFalse(p.isComplete("w9Z"))
    }
}
