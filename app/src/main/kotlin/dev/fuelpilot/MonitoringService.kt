package dev.fuelpilot

import android.app.*
import android.annotation.SuppressLint
import android.os.Build
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.fuelpilot.feature.assist.AssistAvailability
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Started exclusively from a visible, user initiated Activity permission flow. */
class MonitoringService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as FuelPilotApplication
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
    }
    @SuppressLint("MissingPermission") // MonitoringControl validates location and notification grants before use.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { app.monitoring.stop(); stopSelf(); return START_NOT_STICKY }
        if (!app.monitoring.requested || !app.monitoring.permissionsSatisfied() || !app.monitoring.locationEnabled()) {
            app.monitoring.problem("권한과 위치 서비스를 확인한 뒤 앱에서 다시 시작해 주세요.")
            stopSelf(); return START_NOT_STICKY
        }
        try {
            val initial = ongoing("차량 정보를 확인하고 있어요.")
            if (Build.VERSION.SDK_INT >= 29) startForeground(FOREGROUND_ID, initial, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(FOREGROUND_ID, initial)
        } catch (_: Exception) {
            app.monitoring.problem("백그라운드 모니터링을 시작할 수 없어요. 앱에서 다시 시작해 주세요.")
            stopSelf(); return START_NOT_STICKY
        }
        started = true
        app.runtime.setMonitoring(true)
        app.monitoring.running()
        if (watchdog == null) watchdog = scope.launch {
            while (isActive) {
                delay(1_000)
                // StateFlow may emit no new state when an unsupported SDK remains unchanged.
                if (!app.monitoring.permissionsSatisfied() || !app.monitoring.locationEnabled()) {
                    app.runtime.setMonitoring(false)
                    app.runtime.retry() // Discard pending work and reacquire permission-gated sources if UI is still visible.
                    getSystemService(NotificationManager::class.java).cancel(ADVICE_ID)
                    app.monitoring.problem("권한 또는 위치 서비스가 꺼져 모니터링이 중단됐어요.")
                    stopSelf()
                    break
                }
            }
        }
        if (collector == null) collector = scope.launch {
            // Visibility must trigger publication even when an unchanged live snapshot emits nothing.
            monitoringSnapshots(app.runtime.state, app.uiVisibility).collect { (state, visible) ->
                if (!app.monitoring.permissionsSatisfied() || !app.monitoring.locationEnabled()) {
                    app.runtime.setMonitoring(false)
                    app.runtime.retry()
                    app.monitoring.problem("권한 또는 위치 서비스가 꺼져 모니터링이 중단됐어요.")
                    stopSelf(); return@collect
                }
                val status = when (state.presentation.availability) {
                    AssistAvailability.UNSUPPORTED -> "실제 차량 SDK 미연결 · 주유 안내를 제공할 수 없어요."
                    AssistAvailability.STALE -> "최신 차량 정보를 기다리고 있어요."
                    AssistAvailability.UNAVAILABLE -> "차량 정보를 확인할 수 없어요."
                    AssistAvailability.READY -> "주유 시점을 확인하고 있어요."
                }
                val manager = getSystemService(NotificationManager::class.java)
                try {
                    manager.notify(FOREGROUND_ID, ongoing(status))
                    val advice = if (visible) null else backgroundAdvice(state)
                    if (advice == null) {
                        manager.cancel(ADVICE_ID)
                        speechStop?.cancel()
                        app.dependencies.recommendationSpeech.stop()
                    }
                    advice?.let { current ->
                        if (!app.uiVisible && app.monitoring.claimAdvice(current.key) && !app.uiVisible) {
                            manager.notify(ADVICE_ID, recommendation(current))
                            scope.launch {
                                app.dependencies.recommendationSpeech.speak(recommendationSpeech(current))
                            }
                            speechStop?.cancel()
                            speechStop = scope.launch {
                                delay(RECOMMENDATION_HUN_TIMEOUT_MS)
                                app.dependencies.recommendationSpeech.stop()
                            }
                        }
                    }
                } catch (_: SecurityException) {
                    app.runtime.setMonitoring(false)
                    app.runtime.retry()
                    manager.cancel(ADVICE_ID)
                    app.monitoring.problem("알림 권한이 해제되어 모니터링이 중단됐어요.")
                    stopSelf()
                }
            }
        }
        // The OS may recreate an opted-in session; restore state before live subscriptions.
        // A failed permission/type check above remains NOT_STICKY and asks for a user retry.
        return START_STICKY
    }
    private var started = false
    private var collector: Job? = null
    private var watchdog: Job? = null
    private var speechStop: Job? = null
    private fun base(channel: String) = NotificationCompat.Builder(this, channel)
        .setSmallIcon(R.drawable.ic_monitoring)
        .setLargeIcon(BitmapFactory.decodeResource(resources, dev.fuelpilot.ui.R.drawable.podong_sunset,
            BitmapFactory.Options().apply { inSampleSize=8 }))
        .setContentIntent(openApp(this)).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    private fun ongoing(message: String): Notification {
        val stop = PendingIntent.getService(this, 2, Intent(this, MonitoringService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return base(MONITOR_CHANNEL).setContentTitle("WAYFILL 모니터링")
            .setContentText(message).setOngoing(true).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "중지", stop).build()
    }
    private fun recommendation(advice: BackgroundAdvice): Notification {
        val guide = RecommendationActionReceiver.pendingIntent(this, advice, RecommendationActionKind.GUIDE_NOW)
        val later = RecommendationActionReceiver.pendingIntent(this, advice, RecommendationActionKind.LATER)
        return base(ADVICE_CHANNEL)
            .setContentTitle(advice.reason)
            // Keep distance/detour first so a long station name cannot push the actionable facts out of the HUN.
            .setContentText(listOf(advice.routeFacts, advice.stationName).filter { it.isNotBlank() }.joinToString(" · "))
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(RECOMMENDATION_HUN_TIMEOUT_MS)
            .setAutoCancel(true)
            .addAction(0, getString(R.string.notification_action_guide_now), guide)
            .addAction(0, getString(R.string.notification_action_later), later)
            .build()
    }
    override fun onDestroy() {
        app.dependencies.recommendationSpeech.stop()
        speechStop?.cancel()
        scope.cancel(); if (started) app.runtime.setMonitoring(false)
        getSystemService(NotificationManager::class.java).cancel(ADVICE_ID)
        app.monitoring.stopped()
        super.onDestroy()
    }
    companion object {
        const val ACTION_STOP = "dev.fuelpilot.STOP_MONITORING"
        const val MONITOR_CHANNEL = "monitoring"
        const val ADVICE_CHANNEL = "fuel_recommendations"
        const val FOREGROUND_ID = 201
        const val ADVICE_ID = 202
        fun openApp(context: android.content.Context): PendingIntent = PendingIntent.getActivity(context, 1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun ensureChannels(context: android.content.Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(
                NotificationChannel(MONITOR_CHANNEL, context.getString(R.string.notification_channel_monitoring), NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(ADVICE_CHANNEL, context.getString(R.string.notification_channel_recommendations), NotificationManager.IMPORTANCE_HIGH)))
        }
    }
}
