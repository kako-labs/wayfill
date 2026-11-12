package dev.fuelpilot

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.fuelpilot.data.location.PleosFusedLocationPort
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Durable opt-in is distinct from the current process/service state. */
class MonitoringControl(private val app: FuelPilotApplication) {
    private val preferences = app.getSharedPreferences("monitoring", Context.MODE_PRIVATE)
    val requested get() = preferences.getBoolean("requested", false)
    private val mutableState = MutableStateFlow(MonitoringUiState(requested, false,
        if (requested) "모니터링을 다시 시작하려면 시작을 눌러 주세요." else "백그라운드 모니터링이 꺼져 있어요."))
    val state = mutableState.asStateFlow()

    fun permissionsSatisfied(): Boolean {
        val location = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notification = app.getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val automotive = app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        val fusedLocation = ContextCompat.checkSelfPermission(
            app,
            PleosFusedLocationPort.FUSED_LOCATION_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
        return monitoringPermissionsSatisfiedForEnvironment(location, notification, automotive, fusedLocation)
    }

    fun locationEnabled() = app.getSystemService(LocationManager::class.java).isLocationEnabled

    /** Called only by the visible Activity after an explicit user tap. */
    @SuppressLint("UseKtx") // KTX edit returns Unit; the commit result gates service startup.
    fun start() {
        if (!monitoringStartAllowed(app.runtime.state.value.ui.motion)) { problem("P단에 놓고 정차한 뒤 모니터링을 시작해 주세요."); return }
        if (!app.uiVisible) { problem("앱을 연 뒤 시작해 주세요."); return }
        if (!permissionsSatisfied()) { problem("위치와 알림 권한이 필요해요. 권한 설정을 확인해 주세요."); return }
        if (!locationEnabled()) { problem("기기의 위치 서비스를 켜 주세요."); return }
        if (!preferences.edit().putBoolean("requested", true).commit()) { problem("모니터링 설정을 저장하지 못했어요."); return }
        try { ContextCompat.startForegroundService(app, Intent(app, MonitoringService::class.java)) }
        catch (_: Exception) { problem("모니터링을 시작하지 못했어요. 앱에서 다시 시도해 주세요.") }
    }

    @SuppressLint("ImplicitSamInstance") // Android stopService resolves the Intent component, not object identity; Intent is not a SAM.
    fun stop() {
        // Always stop immediately, even when durable storage fails.
        app.runtime.setMonitoring(false)
        val saved = preferences.edit().putBoolean("requested", false).commit()
        app.getSystemService(NotificationManager::class.java).cancel(MonitoringService.ADVICE_ID)
        app.stopService(Intent(app, MonitoringService::class.java))
        mutableState.value = MonitoringUiState(requested, false, if (saved) "모니터링을 중지했어요." else "중지했지만 설정 저장에 실패했어요. 다시 중지해 주세요.")
    }

    fun running() { mutableState.value = MonitoringUiState(requested, true, "모니터링 중 · 실제 차량 SDK가 연결되지 않으면 주유 안내를 만들지 않아요.") }
    fun problem(message: String) { mutableState.value = MonitoringUiState(requested, false, message) }
    fun stopped() { if (state.value.running) problem("모니터링이 중단됐어요. 앱에서 다시 시작해 주세요.") }

    /** Persist before publishing so process recreation cannot replay the same recommendation immediately. */
    @SuppressLint("UseKtx")
    @Synchronized fun claimAdvice(key: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!validRecommendationIdentity(key)) return false
        val records = adviceRecords()
        if (!recommendationCooldownElapsed(records[key], nowMs)) return false
        return writeAdviceRecord(records, key, nowMs)
    }

    /** "Later" restarts the same per-recommendation cooldown from the user's action time. */
    @SuppressLint("UseKtx")
    @Synchronized fun deferAdvice(key: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!validRecommendationIdentity(key) || nowMs < 0) return false
        return writeAdviceRecord(adviceRecords(), key, nowMs)
    }

    private fun adviceRecords(): LinkedHashMap<String, Long> {
        val result = linkedMapOf<String, Long>()
        preferences.getString(ADVICE_RECORDS, "").orEmpty().lineSequence().forEach { line ->
            val fields = line.split('\t', limit = 2)
            val time = fields.getOrNull(0)?.toLongOrNull() ?: return@forEach
            val key = fields.getOrNull(1)?.let(::decodeKey) ?: return@forEach
            if (validRecommendationIdentity(key) && time >= 0) result[key] = time
        }
        return result
    }

    private fun writeAdviceRecord(records: LinkedHashMap<String, Long>, key: String, nowMs: Long): Boolean {
        records.remove(key)
        records[key] = nowMs
        val serialized = records.entries.takeLast(MAX_ADVICE_RECORDS)
            .joinToString("\n") { "${it.value}\t${encodeKey(it.key)}" }
        return preferences.edit().putString(ADVICE_RECORDS, serialized).commit()
    }

    private fun encodeKey(key: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(key.toByteArray(StandardCharsets.UTF_8))

    private fun decodeKey(value: String): String? = runCatching {
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val ADVICE_RECORDS = "advice_records_v2"
        const val MAX_ADVICE_RECORDS = 128
    }
}

internal fun monitoringPermissionsSatisfiedForEnvironment(
    location: Boolean,
    notification: Boolean,
    automotive: Boolean,
    fusedLocation: Boolean,
): Boolean = monitoringPermissionsSatisfied(location, notification) && (!automotive || fusedLocation)
