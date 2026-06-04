package com.two17industries.rideman.core

/** One ride that was tagged to a plan slot. distanceM is the ride's total distance in meters. */
data class PlanAttempt(val planRideId: String, val distanceM: Double)

/** Distance-target grading for a plan ride. */
object PlanGrading {
    const val METERS_PER_MILE = 1609.344

    fun targetMeters(ride: PlanRide): Double = ride.targetMiles * METERS_PER_MILE

    /** Minimum distance (meters) that completes the slot, given the tolerance. */
    fun threshold(ride: PlanRide, tolerancePercent: Int): Double =
        targetMeters(ride) * (1.0 - tolerancePercent / 100.0)

    /** True if a ride of [distanceM] meters clears [ride]'s target within tolerance. */
    fun isMet(ride: PlanRide, distanceM: Double, tolerancePercent: Int): Boolean =
        distanceM >= threshold(ride, tolerancePercent)
}

/**
 * Derived progress over a [Plan] given the rides tagged to it. A slot is complete when
 * its single best attempt clears the target distance within the plan's tolerance.
 */
class PlanProgress(private val plan: Plan, attempts: List<PlanAttempt>) {

    private val bestMetersBySlot: Map<String, Double> =
        attempts.groupBy { it.planRideId }
            .mapValues { (_, list) -> list.maxOf { it.distanceM } }

    fun isComplete(rideId: String): Boolean {
        val ride = plan.byId[rideId] ?: return false
        val best = bestMetersBySlot[rideId] ?: return false
        return PlanGrading.isMet(ride, best, plan.tolerancePercent)
    }

    /** First ride in plan order that is not complete, or null if the whole plan is done. */
    fun nextIncomplete(): PlanRide? = plan.rides.firstOrNull { !isComplete(it.id) }

    fun completedCount(): Int = plan.rides.count { isComplete(it.id) }

    val total: Int get() = plan.rides.size
}
