package dev.fuelpilot

import dev.fuelpilot.data.energy.*
import dev.fuelpilot.data.location.*
import dev.fuelpilot.data.navigation.*
import dev.fuelpilot.data.poi.*
import dev.fuelpilot.feature.assist.AssistStateStore
import dev.fuelpilot.ui.Motion
import dev.fuelpilot.ui.RefuelEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/** SDK implementations must provide verified motion and stable refuel IDs. */
interface VehicleEvents {
    val motion: Flow<Motion>
    val refuels: Flow<RefuelEvent>
}

object UnsupportedVehicleEvents : VehicleEvents {
    override val motion = flowOf(Motion.Unknown)
    override val refuels = emptyFlow<RefuelEvent>()
}

class AppDependencies(
    val store: AssistStateStore,
    val energy: EnergyRepository = UnsupportedEnergyRepository,
    val location: LocationRepository = UnsupportedLocationRepository,
    val stations: StationRepository = UnsupportedStationRepository,
    val navigation: NavigationRepository = UnsupportedNavigationRepository,
    val vehicleEvents: VehicleEvents = UnsupportedVehicleEvents,
    val refreshIntervalMs: Long = 5000L,
    val navigationTimeoutMs: Long = 10_000L,
    val reduceMotion: Flow<Boolean> = emptyFlow(),
    val readTimeoutMs: Long = 5000L,
) {
    init { require(refreshIntervalMs > 0 && navigationTimeoutMs > 0 && readTimeoutMs > 0) }
    fun coordinator() = AppCoordinator(this)
}
