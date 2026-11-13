package dev.fuelpilot

import android.app.NotificationManager
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** Android location FGS cannot be launched silently from boot. Ask for a tap. */
class BootReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission") // Checked through MonitoringControl before posting.
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val app = context.applicationContext as FuelPilotApplication
        if (bootMonitoringAction(app.monitoring.requested) != BootMonitoringAction.ASK_USER_TO_RESUME || !app.monitoring.permissionsSatisfied()) return
        MonitoringService.ensureChannels(context)
        try { context.getSystemService(NotificationManager::class.java).notify(203,
            NotificationCompat.Builder(context, MonitoringService.MONITOR_CHANNEL)
                .setSmallIcon(R.drawable.ic_monitoring).setContentTitle("WAYFILL 모니터링 재개")
                .setContentText("앱을 열고 시작을 눌러 모니터링을 재개해 주세요.")
                .setContentIntent(MonitoringService.openApp(context)).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).build())
        } catch (_: SecurityException) { /* Permission revoked after the check; wait for explicit app launch. */ }
    }
}
