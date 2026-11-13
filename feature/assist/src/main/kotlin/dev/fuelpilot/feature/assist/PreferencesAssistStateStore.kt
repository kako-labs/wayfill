package dev.fuelpilot.feature.assist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/** Use a single Preferences DataStore per file, owned by the host application. */
class PreferencesAssistStateStore(
    private val dataStore: DataStore<Preferences>,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
) : AssistStateStore {
    override suspend fun read(): AssistStoredState {
        val preferences = dataStore.updateData { current ->
            encode(decode(current).pruned(wallClockMs()), current)
        }
        return decode(preferences)
    }

    override suspend fun write(state: AssistStoredState) {
        dataStore.updateData { current -> encode(state.pruned(wallClockMs()), current) }
    }

    override fun observeMinimumReserveKm(): Flow<Int> = dataStore.data
        .map { (it[RESERVE] ?: 50).takeIf(::validReserveKm) ?: 50 }.distinctUntilChanged()

    private fun decode(preferences: Preferences) = AssistStoredState(
        processedRefuels = preferences[REFUELS].orEmpty().mapNotNull { entry ->
            // Only the first ':' is a separator; event IDs may contain arbitrary text.
            val separator = entry.indexOf(':')
            if (separator < 0) null else entry.substring(0, separator).toLongOrNull()?.let {
                entry.substring(separator + 1) to it
            }
        }.toMap(),
        characterEnabled = preferences[CHARACTER] ?: true,
        reduceMotion = preferences[REDUCE_MOTION] ?: false,
        routeId = preferences[ROUTE],
        destinationId = preferences[DESTINATION],
        lastRecommendationKey = preferences[LAST_RECOMMENDATION],
        exposedRecommendationKeys = preferences[EXPOSED].orEmpty(),
        minimumReserveKm = (preferences[RESERVE] ?: 50).takeIf(::validReserveKm) ?: 50,
        refuelHistory = preferences[HISTORY].orEmpty().mapNotNull(RefuelRecordCodec::decode)
            .sortedWith(compareByDescending<RefuelRecord>{it.observedAtMs}.thenBy{it.id}),
    )

    private fun encode(state: AssistStoredState, current: Preferences): Preferences =
        current.toMutablePreferences().apply {
            this[REFUELS] = state.processedRefuels.entries.sortedBy { it.key }
                .map { "${it.value}:${it.key}" }.toSet()
            this[CHARACTER] = state.characterEnabled
            this[REDUCE_MOTION] = state.reduceMotion
            this[EXPOSED] = state.exposedRecommendationKeys.sorted().toSet()
            this[RESERVE] = state.minimumReserveKm
            this[HISTORY] = state.refuelHistory.map(RefuelRecordCodec::encode).toSet()
            if (state.routeId == null) remove(ROUTE) else this[ROUTE] = state.routeId
            if (state.destinationId == null) remove(DESTINATION) else this[DESTINATION] = state.destinationId
            if (state.lastRecommendationKey == null) remove(LAST_RECOMMENDATION)
            else this[LAST_RECOMMENDATION] = state.lastRecommendationKey
        }

    private companion object {
        val REFUELS = stringSetPreferencesKey("assist.processed_refuels")
        val CHARACTER = booleanPreferencesKey("assist.character_motion")
        val REDUCE_MOTION = booleanPreferencesKey("assist.reduce_motion")
        val ROUTE = stringPreferencesKey("assist.route_id")
        val DESTINATION = stringPreferencesKey("assist.destination_id")
        val LAST_RECOMMENDATION = stringPreferencesKey("assist.last_recommendation")
        val EXPOSED = stringSetPreferencesKey("assist.exposed_recommendations")
        val RESERVE = intPreferencesKey("assist.minimum_reserve_km")
        val HISTORY = stringSetPreferencesKey("assist.refuel_history_v1")
    }
}
