package dev.fuelpilot

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.fuelpilot.feature.assist.AssistIntent

/** Notification actions remain inside the shared application runtime and never launch an Activity. */
class RecommendationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSystemService(NotificationManager::class.java).cancel(MonitoringService.ADVICE_ID)
        val action = RecommendationNotificationAction.parse(context, intent) ?: return
        val app = context.applicationContext as? FuelPilotApplication ?: return

        val environmentValid = runCatching {
            app.monitoring.requested &&
                app.monitoring.permissionsSatisfied() &&
                app.monitoring.locationEnabled() &&
                app.runtime.monitoringActive.value &&
                !app.uiVisible
        }.getOrDefault(false)
        if (!environmentValid) return

        val current = app.runtime.state.value
        if (recommendationActionDecision(current, action.key, action.stationId) !=
            RecommendationActionDecision.DISPATCH) return

        when (action.kind) {
            RecommendationActionKind.GUIDE_NOW ->
                app.runtime.dispatch(AssistIntent.SelectStation(action.stationId, action.key))
            RecommendationActionKind.LATER ->
                app.monitoring.deferAdvice(action.key)
        }
    }

    companion object {
        fun pendingIntent(
            context: Context,
            advice: BackgroundAdvice,
            kind: RecommendationActionKind,
        ): PendingIntent {
            val intent = RecommendationNotificationAction(kind, advice.key, advice.stationId).intent(context)
            return PendingIntent.getBroadcast(
                context,
                kind.requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

enum class RecommendationActionKind(
    internal val intentAction: String,
    internal val pathValue: String,
    internal val requestCode: Int,
) {
    GUIDE_NOW("dev.fuelpilot.action.GUIDE_RECOMMENDATION", "guide", 301),
    LATER("dev.fuelpilot.action.DEFER_RECOMMENDATION", "later", 302),
}

private data class RecommendationNotificationAction(
    val kind: RecommendationActionKind,
    val key: String,
    val stationId: String,
) {
    fun intent(context: Context): Intent {
        val data = Uri.Builder()
            .scheme(SCHEME)
            .authority(context.packageName)
            .appendPath(PATH_RECOMMENDATION)
            .appendPath(kind.pathValue)
            .appendQueryParameter(QUERY_KEY, key)
            .appendQueryParameter(QUERY_STATION, stationId)
            .build()
        return Intent(context, RecommendationActionReceiver::class.java)
            .setPackage(context.packageName)
            .setAction(kind.intentAction)
            .setData(data)
    }

    companion object {
        fun parse(context: Context, intent: Intent): RecommendationNotificationAction? {
            val data = intent.data ?: return null
            if (data.scheme != SCHEME || data.authority != context.packageName ||
                data.pathSegments.size != 2 || data.pathSegments[0] != PATH_RECOMMENDATION) return null
            val kind = RecommendationActionKind.entries.singleOrNull {
                it.intentAction == intent.action && it.pathValue == data.pathSegments[1]
            } ?: return null
            val key = data.getQueryParameter(QUERY_KEY) ?: return null
            val stationId = data.getQueryParameter(QUERY_STATION) ?: return null
            if (!validRecommendationIdentity(key) || !validRecommendationIdentity(stationId)) return null
            return RecommendationNotificationAction(kind, key, stationId)
        }
    }
}

private const val SCHEME = "fuelpilot"
private const val PATH_RECOMMENDATION = "recommendation"
private const val QUERY_KEY = "key"
private const val QUERY_STATION = "station"
