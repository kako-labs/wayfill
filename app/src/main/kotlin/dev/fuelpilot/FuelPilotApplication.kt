package dev.fuelpilot

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.datastore.preferences.preferencesDataStore
import dev.fuelpilot.feature.assist.PreferencesAssistStateStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private val Context.assistDataStore by preferencesDataStore(name = "assist")

class FuelPilotApplication : Application() {
    val dependencies by lazy {
        AppDependencies(PreferencesAssistStateStore(assistDataStore), reduceMotion = observeReducedMotion())
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
