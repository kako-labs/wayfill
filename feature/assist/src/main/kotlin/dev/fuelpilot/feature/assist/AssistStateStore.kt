package dev.fuelpilot.feature.assist

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun validReserveKm(value: Int) = value in 20..100 && value % 10 == 0

/** Durable values only: animations and monotonic clock values never cross this boundary. */
data class AssistStoredState(
    val processedRefuels: Map<String, Long> = emptyMap(),
    val characterEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val routeId: String? = null,
    val destinationId: String? = null,
    val lastRecommendationKey: String? = null,
    val exposedRecommendationKeys: Set<String> = emptySet(),
    val minimumReserveKm: Int = 50,
    val refuelHistory: List<RefuelRecord> = emptyList(),
) {
    fun pruned(nowMs: Long): AssistStoredState = copy(
        processedRefuels = processedRefuels.entries
            .filter { it.key.isNotBlank() && it.value >= nowMs - REFUEL_RETENTION_MS }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(256)
            .associate { it.key to it.value },
        exposedRecommendationKeys = exposedRecommendationKeys + listOfNotNull(lastRecommendationKey),
        minimumReserveKm = minimumReserveKm.takeIf(::validReserveKm) ?: 50,
        refuelHistory = retainedRefuelHistory(refuelHistory, nowMs),
    )
}

private const val REFUEL_RETENTION_MS = 90L * 24 * 60 * 60 * 1000

/** The host provides one store instance for its assist lifecycle. No Android/UI types. */
interface AssistStateStore {
    suspend fun read(): AssistStoredState
    suspend fun write(state: AssistStoredState)
    fun observeMinimumReserveKm(): Flow<Int> = flow { emit(read().minimumReserveKm) }
}
