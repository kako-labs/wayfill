package dev.fuelpilot.feature.assist

/** Durable values only: animations and monotonic clock values never cross this boundary. */
data class AssistStoredState(
    val processedRefuels: Map<String, Long> = emptyMap(),
    val characterEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val routeId: String? = null,
    val destinationId: String? = null,
    val lastRecommendationKey: String? = null,
    val exposedRecommendationKeys: Set<String> = emptySet(),
) {
    fun pruned(nowMs: Long): AssistStoredState = copy(
        processedRefuels = processedRefuels.entries
            .filter { it.key.isNotBlank() && it.value >= nowMs - REFUEL_RETENTION_MS }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(256)
            .associate { it.key to it.value },
        exposedRecommendationKeys = exposedRecommendationKeys + listOfNotNull(lastRecommendationKey),
    )
}

private const val REFUEL_RETENTION_MS = 90L * 24 * 60 * 60 * 1000

/** The host provides one store instance for its assist lifecycle. No Android/UI types. */
interface AssistStateStore {
    suspend fun read(): AssistStoredState
    suspend fun write(state: AssistStoredState)
}
