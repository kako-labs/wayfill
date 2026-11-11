package dev.fuelpilot.data.energy

sealed interface EnergyResult {
    data class Available(val rangeRemainingKm: Double) : EnergyResult

    data class Stale(val rangeRemainingKm: Double) : EnergyResult

    data object Unavailable : EnergyResult

    data object Unsupported : EnergyResult
}

interface EnergyRepository {
    suspend fun readEnergy(): EnergyResult
}

object UnsupportedEnergyRepository : EnergyRepository {
    override suspend fun readEnergy(): EnergyResult = EnergyResult.Unsupported
}
