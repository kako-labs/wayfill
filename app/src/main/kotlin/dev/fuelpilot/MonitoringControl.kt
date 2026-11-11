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
        return monitoringPermissionsSatisfied(location, notification)
    }
    fun locationEnabled() = app.getSystemService(LocationManager::class.java).isLocationEnabled
    /** Called only by the visible Activity after an explicit user tap. */
    @SuppressLint("UseKtx") // KTX edit returns Unit; the commit result gates service startup.
    fun start() {
        if (app.runtime.state.value.ui.motion == dev.fuelpilot.ui.Motion.Moving) { problem("주행 중에는 모니터링을 시작할 수 없어요. 정차 후 다시 시도해 주세요."); return }
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
    /** Persist before notifying. Bounded across process death, no replay after restart. */
    @SuppressLint("UseKtx") // A successful synchronous commit must precede notification publication.
    @Synchronized fun claimAdvice(key: String): Boolean {
        val keys = preferences.getString("advice_keys", "")!!.split('\n').filter { it.isNotEmpty() }
        if (key in keys || '\n' in key) return false
        return preferences.edit().putString("advice_keys", (keys + key).takeLast(128).joinToString("\n")).commit()
    }
}
