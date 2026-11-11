package dev.fuelpilot

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.datastore.preferences.preferencesDataStore
import dev.fuelpilot.feature.assist.PreferencesAssistStateStore
import dev.fuelpilot.data.location.UnsupportedLocationRepository
import dev.fuelpilot.data.navigation.UnsupportedNavigationRepository
import dev.fuelpilot.data.poi.gateway.GatewayConfig
import dev.fuelpilot.data.poi.gateway.GatewayStationDiscovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private val Context.assistDataStore by preferencesDataStore(name = "assist")

class FuelPilotApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val runtime by lazy { AssistRuntime(dependencies, applicationScope) }
    val monitoring by lazy { MonitoringControl(this) }
    var uiVisible: Boolean = false
        private set
    fun setUiVisible(value: Boolean) {
        uiVisible = value; runtime.setUiVisible(value)
        if (value) getSystemService(android.app.NotificationManager::class.java).cancel(MonitoringService.ADVICE_ID)
    }

    val dependencies by lazy {
        val location = UnsupportedLocationRepository
        val navigation = UnsupportedNavigationRepository
        // Public origin only. Missing or invalid configuration never falls back to fixtures.
        val config = BuildConfig.STATION_GATEWAY_ORIGIN.takeIf { it.isNotBlank() }
            ?.let { runCatching { GatewayConfig(it) }.getOrNull() }
        val stations = createProductionStations(location, navigation, GatewayStationDiscovery(config))
        AppDependencies(PreferencesAssistStateStore(assistDataStore),
            location = location, navigation = navigation, stations = stations,
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
}
