package dev.fuelpilot.feature.assist

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import dev.fuelpilot.ui.Motion
import dev.fuelpilot.ui.RefuelEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesAssistStateStoreTest {
    @Test fun reservePolicyRestoresAndObservationTracksDurableChanges() = runTest {
        val store = store("reserve")
        assertEquals(50, store.read().minimumReserveKm)
        store.write(store.read().copy(minimumReserveKm = 80))
        assertEquals(80, store.read().minimumReserveKm)
        assertEquals(80, store.observeMinimumReserveKm().first())
    }

    private val fileSystem = FakeFileSystem()
    private val now = 100L * 24 * 60 * 60 * 1000

    @Test fun capsToNewest256WithDeterministicTieBreaking() = runTest {
        val store = store("cap")
        store.write(AssistStoredState(processedRefuels = (0..299).reversed().associate { "id-%03d".format(it) to now }))
        val read = store.read().processedRefuels
        assertEquals(256, read.size)
        assertTrue("id-000" in read)
        assertTrue("id-255" in read)
        assertFalse("id-256" in read)
        store.write(AssistStoredState(processedRefuels = (0..299).associate { "id-$it" to now - it }))
        assertFalse("id-256" in store.read().processedRefuels)
    }

    @Test fun prunesOnlyEntriesOlderThan90DaysIncludingAfterTimeAdvances() = runTest {
        var clock = now
        val store = store("age") { clock }
        val cutoff = now - 90L * 24 * 60 * 60 * 1000
        store.write(AssistStoredState(processedRefuels = mapOf("old" to cutoff - 1, "boundary" to cutoff, "new" to now)))
        assertEquals(setOf("boundary", "new"), store.read().processedRefuels.keys)
        clock++
        assertEquals(setOf("new"), store.read().processedRefuels.keys)
    }

    @Test fun preferencesRouteDestinationAndRecommendationExposureRoundTrip() = runTest {
        val store = store("preferences")
        val state = AssistStoredState(characterEnabled = false, reduceMotion = true, routeId = "route",
            destinationId = "destination", lastRecommendationKey = "key", exposedRecommendationKeys = setOf("prior", "key"),
            processedRefuels = mapOf("receipt:with|separators\nand unicode 주유" to now))
        store.write(state)
        assertEquals(state, store.read())
        store.write(AssistStoredState())
        assertEquals(AssistStoredState(), store.read())
    }

    @Test fun recreationRestoresProcessedIdsBeforeDuplicateAndNeverRestoresCelebration() = runTest {
        val firstJob = Job()
        val firstDataStore = dataStore("restart", CoroutineScope(coroutineContext + firstJob))
        val firstStore = PreferencesAssistStateStore(firstDataStore) { now }
        val vm = AssistViewModel(firstStore, scope = backgroundScope, wallClockMs = { now }, monotonicClockMs = { 12345L })
        vm.state.first { it.ready }
        vm.dispatch(AssistIntent.MotionChanged(Motion.Parked))
        vm.dispatch(AssistIntent.RefuelConfirmed(RefuelEvent("receipt")))
        vm.state.first { it.ui.celebration != null }
        assertNotNull(vm.state.value.ui.celebration)
        assertEquals(12345L, vm.state.value.ui.celebration?.startedAtMs)
        firstJob.cancelAndJoin()
        val secondDataStore = dataStore("restart", backgroundScope)
        val restored = AssistViewModel(PreferencesAssistStateStore(secondDataStore) { now }, scope = backgroundScope, wallClockMs = { now })
        restored.dispatch(AssistIntent.MotionChanged(Motion.Parked))
        restored.dispatch(AssistIntent.RefuelConfirmed(RefuelEvent("receipt")))
        restored.state.first { it.ready }
        assertTrue(restored.state.value.ready)
        assertEquals(setOf("receipt"), restored.state.value.ui.seenIds)
        assertNull(restored.state.value.ui.celebration)
        assertNull(restored.state.value.ui.refuel)
        val keys = secondDataStore.data.first().asMap().keys.map { it.name }
        assertFalse(keys.any { it.contains("celebration", ignoreCase = true) || it.contains("startedAt", ignoreCase = true) })
    }

    private fun TestScope.store(name: String, clock: () -> Long = { now }): PreferencesAssistStateStore {
        return PreferencesAssistStateStore(dataStore(name, backgroundScope), clock)
    }

    // Real DataStore + serializer; only filesystem is isolated from desktop Android-stub rename behavior.
    private fun dataStore(name: String, scope: CoroutineScope) = PreferenceDataStoreFactory.create(
        storage = OkioStorage(fileSystem, PreferencesSerializer) { "/$name.preferences_pb".toPath() },
        scope = scope,
    )
}
