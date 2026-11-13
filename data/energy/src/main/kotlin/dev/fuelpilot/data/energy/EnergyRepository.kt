package dev.fuelpilot.data.energy

import kotlinx.coroutines.flow.Flow

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

sealed interface VehicleSignal<out T> {
    data class Available<T>(val value: T, val timestampNanos: Long) : VehicleSignal<T>
    data object Unavailable : VehicleSignal<Nothing>
    data object Unsupported : VehicleSignal<Nothing>
}

enum class GearSelection { PARK, DRIVE, REVERSE, NEUTRAL, OTHER }
enum class UxRestrictionState { NOT_RESTRICTED, RESTRICTED, UNKNOWN }

data class VehicleSafetySnapshot(
    val gear: GearSelection,
    val uxRestrictions: UxRestrictionState,
    val activeRestrictions: Int? = null,
)

interface VehiclePropertySource {
    suspend fun readRangeMeters(): VehicleSignal<Float>
    fun observeSafety(): Flow<VehicleSignal<VehicleSafetySnapshot>>
}

/** The vehicle's own fuel types (AAOS INFO_FUEL_TYPE). A plug-in hybrid reports more than one. */
enum class VehicleFuelType { UNLEADED, LEADED, DIESEL, BIODIESEL, E85, LPG, CNG, LNG, ELECTRIC, HYDROGEN, OTHER }

fun interface VehicleFuelTypeSource {
    suspend fun readFuelTypes(): VehicleSignal<Set<VehicleFuelType>>
}

/** Fuel in the tank (AAOS FUEL_LEVEL) and the tank size (INFO_FUEL_CAPACITY), both in millilitres. */
data class FuelLevelSample(val levelMl: Float, val capacityMl: Float)

fun interface FuelLevelSource {
    suspend fun readFuelLevel(): VehicleSignal<FuelLevelSample>
}

class AaosRangeEnergyRepository(
    private val source: VehiclePropertySource,
    private val elapsedRealtimeNanos: () -> Long,
    private val staleAfterNanos: Long = 30_000_000_000L,
) : EnergyRepository {
    init { require(staleAfterNanos > 0) }

    override suspend fun readEnergy(): EnergyResult = when (val read = source.readRangeMeters()) {
        is VehicleSignal.Available -> {
            val meters = read.value.toDouble()
            if (!meters.isFinite() || meters < 0.0 || read.timestampNanos < 0L) EnergyResult.Unavailable
            else {
                val km = meters / 1000.0
                val age = elapsedRealtimeNanos() - read.timestampNanos
                when {
                    age < 0L -> EnergyResult.Unavailable
                    age <= staleAfterNanos -> EnergyResult.Available(km)
                    else -> EnergyResult.Stale(km)
                }
            }
        }
        VehicleSignal.Unavailable -> EnergyResult.Unavailable
        VehicleSignal.Unsupported -> EnergyResult.Unsupported
    }
}
