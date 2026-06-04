package com.two17industries.rideman.core

/** Pace descriptor from the plan's legend. */
enum class Pace { EASY, STEADY, BRISK }

/**
 * One ride slot in the plan. [id] is stable ("w{week}{slot}", e.g. "w3B") and is the
 * value stored as RideEntity.planRideId. [targetMiles] is the distance target used for
 * completion grading; pace and guidance are descriptive only.
 */
data class PlanRide(
    val id: String,
    val phaseNumber: Int,
    val phaseName: String,
    val week: Int,
    val slot: String,            // "A" | "B" | "C"
    val kind: String,            // "easy" | "endurance" | "quality"
    val targetMiles: Double,
    val pace: Pace,
    val longRide: Boolean,
    val guidance: String,
    val recoveryWeek: Boolean,
)

/** The whole plan, in ride order. */
class Plan(
    val title: String,
    val rides: List<PlanRide>,
    val tolerancePercent: Int,
) {
    /** Lookup by [PlanRide.id]. */
    val byId: Map<String, PlanRide> = rides.associateBy { it.id }
}
