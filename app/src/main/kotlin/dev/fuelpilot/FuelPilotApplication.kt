package dev.fuelpilot

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.os.SystemClock
import androidx.datastore.preferences.preferencesDataStore
import dev.fuelpilot.feature.assist.PreferencesAssistStateStore
import dev.fuelpilot.data.energy.AaosRangeEnergyRepository
import dev.fuelpilot.data.energy.AaosVehiclePropertySource
import dev.fuelpilot.data.energy.UnsupportedEnergyRepository
import dev.fuelpilot.data.location.PleosFusedLocationPort
import dev.fuelpilot.data.location.PleosFusedLocationRepository
import dev.fuelpilot.data.location.UnsupportedLocationRepository
import dev.fuelpilot.data.navigation.PleosNaviHelperPort
import dev.fuelpilot.data.navigation.PleosNavigationRepository
import dev.fuelpilot.data.navigation.UnsupportedNavigationRepository
import dev.fuelpilot.data.poi.gateway.GatewayConfig
import dev.fuelpilot.data.poi.gateway.GatewayStationDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private val Context.assistDataStore by preferencesDataStore(name = "assist")

class FuelPilotApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val runtime by lazy { AssistRuntime(dependencies, applicationScope) }
    val monitoring by lazy { MonitoringControl(this) }
    private val visible = MutableStateFlow(false)
    val uiVisibility = visible.asStateFlow()
    val uiVisible: Boolean get() = visible.value
    fun setUiVisible(value: Boolean) {
        visible.value = value; runtime.setUiVisible(value)
        if (value) {
            getSystemService(android.app.NotificationManager::class.java).cancel(MonitoringService.ADVICE_ID)
            dependencies.recommendationSpeech.stop()
        }
    }

    val dependencies by lazy {
        val automotive = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        val vehicleSource = if (automotive) AaosVehiclePropertySource(this) else null
        val energy = vehicleSource?.let {
            AaosRangeEnergyRepository(it, SystemClock::elapsedRealtimeNanos)
        } ?: UnsupportedEnergyRepository
        val naviPort = if (automotive) PleosNaviHelperPort(this) else null
        val fusedLocation = if (automotive) {
            PleosFusedLocationRepository(PleosFusedLocationPort(this))
        } else UnsupportedLocationRepository
        // The navigation's own position is the origin its trip previews use; FusedLocation only when it has none.
        val location = naviPort?.let { NavigationLocationRepository(it, fallback = fusedLocation) } ?: fusedLocation
        val navigation = if (naviPort != null) {
            PleosNavigationRepository(
                naviPort,
                hasRoutePermission = { hasPermission(NAVI_ROUTE_PERMISSION) },
                hasRouteSearchPermission = { hasPermission(NAVI_ROUTE_SEARCH_PERMISSION) },
            )
        } else UnsupportedNavigationRepository
        // Public origin only. Missing or invalid configuration never falls back to fixtures.
        val config = BuildConfig.STATION_GATEWAY_ORIGIN.takeIf { it.isNotBlank() }
            ?.let { runCatching { GatewayConfig(it) }.getOrNull() }
        // The vehicle's own INFO_FUEL_TYPE selects the provider product; no gasoline default is assumed.
        val fuelProvider = vehicleSource?.let(::VehicleStationFuelProvider)
        val stations = createProductionStations(location, navigation, GridCachingStationDiscovery(GatewayStationDiscovery(config)),
            roadMetrics = naviPort?.let(::NavigationTripRoadMetrics) ?: UnsupportedStationRoadMetrics,
            coordinateTransform = ValidatedKatecTransform,
            fuelSource = fuelProvider?.let { provider -> suspend { provider.read() } })
        AppDependencies(PreferencesAssistStateStore(assistDataStore),
            energy = energy, location = location, navigation = navigation, stations = stations,
            // Refuel detection stays off until the tank-level rule is validated on a real combustion vehicle.
            vehicleEvents = vehicleSource?.let { AaosVehicleEvents(it, fuelLevel = it, refuelDetectionEnabled = false) }
                ?: UnsupportedVehicleEvents,
            recommendationSpeech = if (automotive) PleosTtsSpeechPort(this) else UnsupportedRecommendationSpeechPort,
            reduceMotion = observeReducedMotion())
    }

    private fun observeReducedMotion() = callbackFlow {
        fun publish() { trySend(Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = publish()
        }
        contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        publish()
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged()

    private fun hasPermission(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val NAVI_ROUTE_PERMISSION = "pleos.car.permission.NAVI_ROUTE"
        const val NAVI_ROUTE_SEARCH_PERMISSION = "pleos.car.permission.NAVI_ROUTE_SEARCH"
    }
}
