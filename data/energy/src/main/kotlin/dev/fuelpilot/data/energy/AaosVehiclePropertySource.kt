package dev.fuelpilot.data.energy

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Direct AAOS source. It never estimates range from fuel percentage or economy. */
class AaosVehiclePropertySource(
    context: Context,
    private val connectionTimeoutMs: Long = 2_000L,
    private val maxConnectionAttempts: Int = 2,
    private val retryDelayMs: Long = 150L,
    private val safetyPollMs: Long = 1_000L,
) : VehiclePropertySource {
    private val appContext = context.applicationContext

    init {
        require(connectionTimeoutMs > 0 && maxConnectionAttempts > 0 && retryDelayMs >= 0 && safetyPollMs > 0)
    }

    override suspend fun readRangeMeters(): VehicleSignal<Float> {
        if (!isAutomotive() || !hasPermission(Car.PERMISSION_ENERGY)) return VehicleSignal.Unsupported
        return withCar { car ->
            val manager = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                ?: return@withCar VehicleSignal.Unsupported
            val config = try { manager.getCarPropertyConfig(VehiclePropertyIds.RANGE_REMAINING) }
                catch (_: SecurityException) { return@withCar VehicleSignal.Unsupported }
                catch (_: Exception) { return@withCar VehicleSignal.Unavailable }
                ?: return@withCar VehicleSignal.Unsupported
            if (config.propertyType != Float::class.javaObjectType) return@withCar VehicleSignal.Unsupported
            val value = try {
                manager.getProperty(Float::class.javaObjectType, VehiclePropertyIds.RANGE_REMAINING, GLOBAL_AREA)
            } catch (_: SecurityException) {
                return@withCar VehicleSignal.Unsupported
            } catch (_: Exception) {
                return@withCar VehicleSignal.Unavailable
            }
            if (value.propertyStatus != CarPropertyValue.STATUS_AVAILABLE) VehicleSignal.Unavailable
            else VehicleSignal.Available(value.value, value.timestamp)
        }
    }

    override fun observeSafety(): Flow<VehicleSignal<VehicleSafetySnapshot>> = flow {
        if (!isAutomotive() || !hasPermission(Car.PERMISSION_POWERTRAIN)) {
            emit(VehicleSignal.Unsupported)
            return@flow
        }
        while (currentCoroutineContext().isActive) {
            emit(readSafety())
            delay(safetyPollMs)
        }
    }.distinctUntilChanged()

    private suspend fun readSafety(): VehicleSignal<VehicleSafetySnapshot> = withCar { car ->
        val manager = car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            ?: return@withCar VehicleSignal.Unsupported
        val config = try { manager.getCarPropertyConfig(VehiclePropertyIds.GEAR_SELECTION) }
            catch (_: SecurityException) { return@withCar VehicleSignal.Unsupported }
            catch (_: Exception) { return@withCar VehicleSignal.Unavailable }
            ?: return@withCar VehicleSignal.Unsupported
        if (config.propertyType != Int::class.javaObjectType) return@withCar VehicleSignal.Unsupported
        val value = try {
            manager.getProperty(Int::class.javaObjectType, VehiclePropertyIds.GEAR_SELECTION, GLOBAL_AREA)
        } catch (_: SecurityException) {
            return@withCar VehicleSignal.Unsupported
        } catch (_: Exception) {
            return@withCar VehicleSignal.Unavailable
        }
        if (value.propertyStatus != CarPropertyValue.STATUS_AVAILABLE) return@withCar VehicleSignal.Unavailable
        val ux = readUxRestrictions(car)
        VehicleSignal.Available(
            VehicleSafetySnapshot(mapGear(value.value), ux.first, ux.second),
            value.timestamp,
        )
    }

    private fun readUxRestrictions(car: Car): Pair<UxRestrictionState, Int?> = try {
        val manager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            ?: return UxRestrictionState.UNKNOWN to null
        val restrictions = manager.currentCarUxRestrictions
            ?: return UxRestrictionState.UNKNOWN to null
        val active = restrictions.activeRestrictions
        val restricted = restrictions.isRequiresDistractionOptimization ||
            active != CarUxRestrictions.UX_RESTRICTIONS_BASELINE
        (if (restricted) UxRestrictionState.RESTRICTED else UxRestrictionState.NOT_RESTRICTED) to active
    } catch (_: Exception) {
        UxRestrictionState.UNKNOWN to null
    }

    private suspend fun <T> withCar(block: (Car) -> VehicleSignal<T>): VehicleSignal<T> {
        repeat(maxConnectionAttempts) { attempt ->
            val car = connect()
            if (car != null) {
                try {
                    return block(car)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: SecurityException) {
                    return VehicleSignal.Unsupported
                } catch (_: Exception) {
                    if (attempt + 1 == maxConnectionAttempts) return VehicleSignal.Unavailable
                } finally {
                    runCatching { car.disconnect() }
                }
            }
            if (attempt + 1 < maxConnectionAttempts) delay(retryDelayMs)
        }
        return VehicleSignal.Unavailable
    }

    private suspend fun connect(): Car? = withTimeoutOrNull(connectionTimeoutMs) {
        suspendCancellableCoroutine { continuation ->
            val car = try {
                Car.createCar(
                    appContext,
                    Handler(Looper.getMainLooper()),
                    Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                ) { connectedCar, ready ->
                    if (ready && continuation.isActive) continuation.resume(connectedCar)
                }
            } catch (_: Exception) {
                continuation.resume(null)
                null
            }
            continuation.invokeOnCancellation { runCatching { car?.disconnect() } }
        }
    }

    private fun isAutomotive() = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    private fun hasPermission(permission: String) = appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun mapGear(value: Int) = when (value) {
        VehicleGear.GEAR_PARK -> GearSelection.PARK
        VehicleGear.GEAR_DRIVE -> GearSelection.DRIVE
        VehicleGear.GEAR_REVERSE -> GearSelection.REVERSE
        VehicleGear.GEAR_NEUTRAL -> GearSelection.NEUTRAL
        else -> GearSelection.OTHER
    }

    private companion object { const val GLOBAL_AREA = 0 }
}
