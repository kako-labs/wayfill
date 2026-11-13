package dev.fuelpilot.data.poi

import dev.fuelpilot.model.FuelStationCandidate

sealed interface StationResult {
    data class Available(val stations: List<FuelStationCandidate>) : StationResult

    data class Stale(val stations: List<FuelStationCandidate>) : StationResult

    data object Unavailable : StationResult

    data object Unsupported : StationResult
}

interface StationRepository {
    suspend fun findStations(): StationResult
}

object UnsupportedStationRepository : StationRepository {
    override suspend fun findStations(): StationResult = StationResult.Unsupported
}
