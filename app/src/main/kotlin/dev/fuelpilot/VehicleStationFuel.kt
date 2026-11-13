package dev.fuelpilot

import dev.fuelpilot.data.energy.VehicleFuelType
import dev.fuelpilot.data.energy.VehicleFuelTypeSource
import dev.fuelpilot.data.energy.VehicleSignal
import dev.fuelpilot.data.poi.gateway.StationFuel

/**
 * Maps the vehicle's own fuel types to the provider product. An electric part of a plug-in hybrid is
 * ignored; a vehicle with two liquid fuels (e.g. gasoline + LPG bi-fuel) or none stays unverified.
 * Premium gasoline cannot be inferred from the vehicle and is never chosen here.
 */
internal fun stationFuelFor(types: Set<VehicleFuelType>): StationFuel? = types.mapNotNull {
    when (it) {
        VehicleFuelType.UNLEADED -> StationFuel.GASOLINE
        VehicleFuelType.DIESEL, VehicleFuelType.BIODIESEL -> StationFuel.DIESEL
        VehicleFuelType.LPG -> StationFuel.LPG
        else -> null
    }
}.toSet().singleOrNull()

/** INFO_FUEL_TYPE is static, so the first available read is kept for the process. */
class VehicleStationFuelProvider(private val source: VehicleFuelTypeSource) {
    @Volatile private var cached: StationFuel? = null

    suspend fun read(): StationFuel? {
        cached?.let { return it }
        val read = source.readFuelTypes() as? VehicleSignal.Available ?: return null
        return stationFuelFor(read.value)?.also { cached = it }
    }
}

/**
 * The fuel to search for: the vehicle's own INFO_FUEL_TYPE first; only when it reports no liquid fuel (an
 * electric or unsupported vehicle) the driver's choice from settings. Null means neither is known.
 */
class StationFuelResolver(
    private val vehicle: (suspend () -> StationFuel?)?,
    private val preference: suspend () -> StationFuel?,
) {
    suspend fun read(): StationFuel? = vehicle?.invoke() ?: preference()
}
