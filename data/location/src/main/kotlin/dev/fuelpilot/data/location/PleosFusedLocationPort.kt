package dev.fuelpilot.data.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class PleosFusedLocationPort(context: Context) : FusedLocationPort {
    private val appContext = context.applicationContext

    override fun isSupported(): Boolean = try { FusedLocation(appContext).isSupported() } catch (_: Exception) { false }

    override fun hasPermission(): Boolean =
        appContext.checkSelfPermission(FUSED_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

    // Lint cannot follow the custom permission helper into async callbacks. Revocation is caught below;
    // callback removal still runs best-effort after revocation so the SDK can release its subscription.
    @SuppressLint("MissingPermission")
    override suspend fun awaitNextLocationUpdate(): FusedLocationRead = suspendCancellableCoroutine { continuation ->
        if (!hasPermission()) {
            continuation.resume(FusedLocationRead.Unsupported)
            return@suspendCancellableCoroutine
        }
        val sdk = try { FusedLocation(appContext) } catch (_: Exception) {
            continuation.resume(FusedLocationRead.Unsupported)
            return@suspendCancellableCoroutine
        }
        val finished = AtomicBoolean(false)
        lateinit var listener: FusedLocationListener
        val registered = AtomicBoolean(false)
        fun finish(result: FusedLocationRead) {
            if (finished.compareAndSet(false, true)) {
                if (registered.compareAndSet(true, false)) runCatching {
                    sdk.unregisterFusedLocationCallback(listener)
                }
                runCatching { sdk.release() }
                if (continuation.isActive) continuation.resume(result)
            }
        }
        continuation.invokeOnCancellation {
            if (finished.compareAndSet(false, true)) {
                if (registered.compareAndSet(true, false)) runCatching {
                    sdk.unregisterFusedLocationCallback(listener)
                }
                runCatching { sdk.release() }
            }
        }
        try {
            if (!sdk.isSupported()) {
                finish(FusedLocationRead.Unsupported)
                return@suspendCancellableCoroutine
            }
            sdk.initialize()
            listener = object : FusedLocationListener {
                override fun onChanged(location: LocationData?) {
                    finish(location?.let {
                        FusedLocationRead.Available(
                            FusedLocationSample(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                confidence = it.confidence.toDouble(),
                                timestampMillis = it.timestampMillis,
                            )
                        )
                    } ?: FusedLocationRead.Unavailable)
                }

                override fun onError(exception: Exception) = finish(FusedLocationRead.Unavailable)
            }
            registered.set(true)
            sdk.registerFusedLocationCallback(listener)
        } catch (_: SecurityException) {
            finish(FusedLocationRead.Unsupported)
        } catch (_: Exception) {
            finish(FusedLocationRead.Unavailable)
        }
    }

    companion object { const val FUSED_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION" }
}
