package dev.fuelpilot.ui

import java.util.Locale

/** Shared facts for the companion and notification. Never infers a last safe stop from missing data. */
data class FuelRecommendationCopy(val reason: String, val station: String, val routeFacts: String)

fun fuelRecommendationCopy(route: FuelRouteUi, passing: Boolean): FuelRecommendationCopy {
    fun valid(value: Double?) = value?.takeIf { it.isFinite() && it >= 0 }
    fun distance(value: Double) = if (value % 1.0 == 0.0) "${value.toLong()}km"
        else String.format(Locale.ROOT, "%.1fkm", value)
    fun text(value: String) = value.replace(Regex("\\s+"), " ").trim()
    val range = valid(route.rangeKm)
    val next = valid(route.nextAfterPassing?.distanceKm)
    val destination = valid(route.destinationKm)
    val reason = when {
        passing && range != null && next != null && next > range -> "지나치면 다음 주유소까지 연료 부족 예상"
        range != null && destination != null && destination > range -> "목적지 전에 주유가 필요해요"
        else -> "현재 경로의 주유 추천"
    }
    val station = route.recommended
    val facts = listOfNotNull(
        valid(station?.distanceKm)?.let { "${distance(it)} 앞" },
        station?.extraMinutes?.takeIf { it >= 0 }?.let { "경로 +${it}분" },
    ).joinToString(" · ")
    return FuelRecommendationCopy(reason, station?.name?.let(::text).orEmpty(), facts)
}

/** Split-window layout depends on the app's actual bounds, never the display or device type. */
fun useCompactCompanion(widthDp: Float, heightDp: Float) = widthDp < 760f || heightDp < 480f
