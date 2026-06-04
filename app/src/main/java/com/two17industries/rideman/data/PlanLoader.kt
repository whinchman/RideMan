package com.two17industries.rideman.data

import android.content.Context
import com.two17industries.rideman.core.Pace
import com.two17industries.rideman.core.Plan
import com.two17industries.rideman.core.PlanRide
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
private data class PlanDto(
    val title: String,
    val tolerancePercent: Int,
    val phases: List<PhaseDto>,
)

@Serializable
private data class PhaseDto(
    val number: Int,
    val name: String,
    val weeks: List<WeekDto>,
)

@Serializable
private data class WeekDto(
    val week: Int,
    val recovery: Boolean = false,
    val rides: List<RideDto>,
)

@Serializable
private data class RideDto(
    val slot: String,
    val kind: String,
    val targetMiles: Double,
    val pace: String,
    val longRide: Boolean = false,
    val guidance: String = "",
)

private val json = Json { ignoreUnknownKeys = true }

/** Parse plan JSON text into the domain [Plan]. Throws on malformed input. */
fun parsePlanJson(text: String): Plan {
    val dto = json.decodeFromString<PlanDto>(text)
    val rides = dto.phases.flatMap { phase ->
        phase.weeks.flatMap { week ->
            week.rides.map { ride ->
                PlanRide(
                    id = "w${week.week}${ride.slot}",
                    phaseNumber = phase.number,
                    phaseName = phase.name,
                    week = week.week,
                    slot = ride.slot,
                    kind = ride.kind,
                    targetMiles = ride.targetMiles,
                    pace = Pace.valueOf(ride.pace.uppercase()),
                    longRide = ride.longRide,
                    guidance = ride.guidance,
                    recoveryWeek = week.recovery,
                )
            }
        }
    }
    return Plan(title = dto.title, rides = rides, tolerancePercent = dto.tolerancePercent)
}

object PlanLoader {
    /** Read and parse the bundled plan asset. Throws if the asset is missing/malformed. */
    fun load(context: Context): Plan {
        val text = context.assets.open("plan.json").bufferedReader().use { it.readText() }
        return parsePlanJson(text)
    }
}
