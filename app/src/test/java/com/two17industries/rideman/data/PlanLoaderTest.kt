package com.two17industries.rideman.data

import com.two17industries.rideman.core.Pace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlanLoaderTest {

    private val sampleJson = """
        {
          "title": "Test Plan",
          "tolerancePercent": 5,
          "phases": [
            { "number": 1, "name": "Base", "weeks": [
              { "week": 1, "recovery": false, "rides": [
                { "slot": "A", "kind": "easy", "targetMiles": 2.0, "pace": "easy", "longRide": false, "guidance": "x" },
                { "slot": "B", "kind": "endurance", "targetMiles": 3.0, "pace": "steady", "longRide": true, "guidance": "y" }
              ]}
            ]}
          ]
        }
    """.trimIndent()

    @Test fun parses_title_and_tolerance() {
        val plan = parsePlanJson(sampleJson)
        assertEquals("Test Plan", plan.title)
        assertEquals(5, plan.tolerancePercent)
    }

    @Test fun flattens_rides_in_order_with_stable_ids() {
        val plan = parsePlanJson(sampleJson)
        assertEquals(listOf("w1A", "w1B"), plan.rides.map { it.id })
    }

    @Test fun maps_fields_and_pace() {
        val plan = parsePlanJson(sampleJson)
        val b = plan.byId.getValue("w1B")
        assertEquals(1, b.phaseNumber)
        assertEquals("Base", b.phaseName)
        assertEquals(1, b.week)
        assertEquals("B", b.slot)
        assertEquals(3.0, b.targetMiles, 0.0)
        assertEquals(Pace.STEADY, b.pace)
        assertTrue(b.longRide)
        assertFalse(plan.byId.getValue("w1A").recoveryWeek)
    }

    @Test fun real_asset_has_42_rides_with_unique_ids() {
        val json = File("src/main/assets/plan.json").readText()
        val plan = parsePlanJson(json)
        assertEquals(42, plan.rides.size)
        assertEquals(42, plan.rides.map { it.id }.toSet().size)
    }

    @Test fun real_asset_week_14_C_is_the_goal_ride() {
        val json = File("src/main/assets/plan.json").readText()
        val plan = parsePlanJson(json)
        val goal = plan.byId.getValue("w14C")
        assertEquals(10.0, goal.targetMiles, 0.0)
        assertTrue(goal.longRide)
    }

    @Test fun real_asset_marks_weeks_4_and_8_as_recovery() {
        val json = File("src/main/assets/plan.json").readText()
        val plan = parsePlanJson(json)
        assertTrue(plan.byId.getValue("w4A").recoveryWeek)
        assertTrue(plan.byId.getValue("w8B").recoveryWeek)
        assertFalse(plan.byId.getValue("w3A").recoveryWeek)
    }
}
