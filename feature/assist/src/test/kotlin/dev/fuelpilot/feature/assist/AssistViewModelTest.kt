package dev.fuelpilot.feature.assist

import dev.fuelpilot.data.navigation.WaypointResult
import dev.fuelpilot.ui.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistViewModelTest {
    @Test fun unreadableStoreDoesNotStartLiveEventsOrPretendRestoreSucceeded() = runTest {
        var collected = false
        val vm = AssistViewModel(object : AssistStateStore {
            override suspend fun read(): AssistStoredState = throw java.io.IOException("unreadable")
            override suspend fun write(state: AssistStoredState) = Unit
        }, liveIntents = flow { collected = true }, scope = backgroundScope)
        runCurrent()
        assertFalse(collected); assertFalse(vm.state.value.ready)
        assertTrue(vm.state.value.ui.statusMessage?.contains("저장") == true)
    }

    @Test fun failedDurableWriteStopsNewActionsWithoutPublishingAnUnrecordedCelebration() = runTest {
        val vm = AssistViewModel(object : AssistStateStore {
            override suspend fun read() = AssistStoredState()
            override suspend fun write(state: AssistStoredState): Unit = throw java.io.IOException("disk full")
        }, scope = backgroundScope)
        runCurrent(); vm.dispatch(AssistIntent.MotionChanged(Motion.Parked)); runCurrent()
        vm.dispatch(AssistIntent.RefuelConfirmed(RefuelEvent("unrecorded"))); runCurrent()
        assertFalse(vm.state.value.ready); assertNull(vm.state.value.ui.celebration)
        assertTrue(vm.state.value.ui.statusMessage?.contains("저장") == true)
    }

    @Test fun rapidDoubleTapEmitsOneEffectAndEstablishesPendingBeforeDelivery() = runTest {
        val vm = model()
        val effects = mutableListOf<AssistEffect>()
        backgroundScope.launch { vm.effects.collect {
            assertEquals((it as AssistEffect.AddWaypoint).requestId, vm.state.value.pending?.requestId)
            effects += it
        } }
        readyRecommendation(vm)
        vm.dispatch(AssistIntent.SelectStation("station"))
        vm.dispatch(AssistIntent.SelectStation("station"))
        runCurrent()
        assertEquals(1, effects.size)
        assertEquals(Screen.Adding, vm.state.value.ui.screen)
    }

    @Test fun retryUsesNewRequestIdAndOldOrWrongStationCallbackCannotCompleteIt() = runTest {
        val vm = model()
        readyRecommendation(vm)
        vm.dispatch(AssistIntent.SelectStation("station")); runCurrent()
        val old = vm.state.value.pending!!
        vm.dispatch(AssistIntent.NavigationCompleted(WaypointResult.Failed(old.requestId, old.stationId)))
        vm.dispatch(AssistIntent.SelectStation("station")); runCurrent()
        val current = vm.state.value.pending!!
        assertNotEquals(old.requestId, current.requestId)
        vm.dispatch(AssistIntent.NavigationCompleted(WaypointResult.Succeeded(old.requestId, old.stationId)))
        vm.dispatch(AssistIntent.NavigationCompleted(WaypointResult.Succeeded(current.requestId, "other")))
        runCurrent()
        assertEquals(current, vm.state.value.pending)
        vm.dispatch(AssistIntent.NavigationCompleted(WaypointResult.Succeeded(current.requestId, current.stationId)))
        runCurrent()
        assertNull(vm.state.value.pending)
        assertEquals(Screen.Added, vm.state.value.ui.screen)
    }

    @Test fun everyNavigationFailureIsExplicitAndRetryable() = runTest {
        val outcomes: List<(String, String) -> WaypointResult> = listOf(
            WaypointResult::Failed, WaypointResult::TimedOut, WaypointResult::Cancelled, WaypointResult::Unsupported,
        )
        for (outcome in outcomes) {
            val vm = model()
            readyRecommendation(vm)
            vm.dispatch(AssistIntent.SelectStation("station")); runCurrent()
            val pending = vm.state.value.pending!!
            val result = outcome(pending.requestId, pending.stationId)
            vm.dispatch(AssistIntent.NavigationCompleted(result)); runCurrent()
            assertNull(vm.state.value.pending)
            assertEquals(Screen.Recommendation, vm.state.value.ui.screen)
            assertFalse(vm.state.value.ui.error.isNullOrBlank())
            assertEquals(result, vm.state.value.navigationResult)
            vm.dispatch(AssistIntent.SelectStation("station")); runCurrent()
            assertNotNull(vm.state.value.pending)
        }
    }

    @Test fun routeOrDestinationChangeClearsExposureAndInvalidatesPending() = runTest {
        for ((route, destination) in listOf("route-2" to "destination", "route" to "destination-2")) {
            val vm = model()
            readyRecommendation(vm)
            vm.dispatch(AssistIntent.SelectStation("station")); runCurrent()
            val old = vm.state.value.pending!!
            vm.dispatch(AssistIntent.RouteChanged(route, destination)); runCurrent()
            assertNull(vm.state.value.pending)
            assertNull(vm.state.value.persisted.lastRecommendationKey)
            assertTrue(vm.state.value.persisted.exposedRecommendationKeys.isEmpty())
            vm.dispatch(AssistIntent.NavigationCompleted(WaypointResult.Succeeded(old.requestId, old.stationId)))
            vm.dispatch(AssistIntent.PresentationChanged(recommendation()))
            runCurrent()
            assertEquals(Screen.Recommendation, vm.state.value.ui.screen)
            assertNull(vm.state.value.ui.routeStation)
        }
    }

    @Test fun repeatedRouteSnapshotDoesNotInvalidatePending() = runTest {
        val vm = model(); readyRecommendation(vm)
        vm.dispatch(AssistIntent.SelectStation("station")); runCurrent()
        val pending = vm.state.value.pending
        vm.dispatch(AssistIntent.RouteChanged("route", "destination")); runCurrent()
        assertEquals(pending, vm.state.value.pending)
    }

    @Test fun sameKeyDoesNotReexposeAfterDismissalEvenAfterAnotherKey() = runTest {
        val vm = model(); readyRecommendation(vm)
        vm.dispatch(AssistIntent.Continue)
        vm.dispatch(AssistIntent.PresentationChanged(recommendation())); runCurrent()
        assertEquals(Screen.Widget, vm.state.value.ui.screen)
        vm.dispatch(AssistIntent.PresentationChanged(recommendation(key = "other"))); runCurrent()
        assertEquals(Screen.Recommendation, vm.state.value.ui.screen)
        vm.dispatch(AssistIntent.Continue)
        vm.dispatch(AssistIntent.PresentationChanged(recommendation())); runCurrent()
        assertEquals(Screen.Widget, vm.state.value.ui.screen)
    }

    @Test fun passingStageUpdatesAnActiveRecommendationWithoutAnotherExposure() = runTest {
        val vm = model(); readyRecommendation(vm)
        vm.dispatch(AssistIntent.PresentationChanged(recommendation(screen = Screen.Passing))); runCurrent()
        assertEquals(Screen.Passing, vm.state.value.ui.screen)
        assertEquals(setOf("key"), vm.state.value.persisted.exposedRecommendationKeys)
    }

    @Test fun storedExposureSuppressesTheSameRecommendationAfterViewModelRecreation() = runTest {
        val store = MemoryStore()
        val first = AssistViewModel(store, scope = backgroundScope)
        readyRecommendation(first)
        val restored = AssistViewModel(store, scope = backgroundScope)
        readyRecommendation(restored)
        assertEquals(Screen.Widget, restored.state.value.ui.screen)
        assertEquals("key", restored.state.value.persisted.lastRecommendationKey)
    }

    @Test fun initialUnknownSnapshotDoesNotEraseRestoredRouteExposure() = runTest {
        val store = MemoryStore()
        val first = AssistViewModel(store, scope = backgroundScope)
        readyRecommendation(first)
        val restored = AssistViewModel(store, scope = backgroundScope)
        runCurrent()
        restored.dispatch(AssistIntent.MotionChanged(Motion.Unknown))
        readyRecommendation(restored)
        assertEquals(Screen.Widget, restored.state.value.ui.screen)
        assertEquals(setOf("key"), restored.state.value.persisted.exposedRecommendationKeys)
    }

    @Test fun invalidatedQueuedEffectsAreNotDeliveredToLateHostCollector() = runTest {
        for (invalidation in listOf(
            AssistIntent.RouteChanged("new-route", "destination"),
            AssistIntent.MotionChanged(Motion.Moving),
            AssistIntent.RefuelConfirmed(RefuelEvent("receipt")),
        )) {
            val vm = model(); readyRecommendation(vm)
            vm.dispatch(AssistIntent.SelectStation("station"))
            vm.dispatch(invalidation); runCurrent()
            val effects = mutableListOf<AssistEffect>()
            backgroundScope.launch { vm.effects.collect { effects += it } }
            runCurrent()
            assertTrue(effects.isEmpty())
        }
    }

    @Test fun arbitraryStationAndUserScreenCannotBypassCurrentRecommendationOrPendingPriority() = runTest {
        val vm = model(); readyRecommendation(vm)
        vm.dispatch(AssistIntent.SelectStation("not-recommended")); runCurrent()
        assertNull(vm.state.value.pending)
        vm.dispatch(AssistIntent.SelectStation("station"))
        vm.dispatch(AssistIntent.Show(Screen.Settings)); runCurrent()
        assertEquals(Screen.Adding, vm.state.value.ui.screen)
    }

    @Test fun movingAndUnknownCancelAllParkedDetailsAndNeverReplayCelebration() = runTest {
        for (motion in listOf(Motion.Moving, Motion.Unknown)) {
            for (screen in listOf(Screen.Analysis, Screen.Settings, Screen.LongDrive)) {
                val vm = model(); readyRecommendation(vm)
                vm.dispatch(AssistIntent.Show(screen)); runCurrent()
                assertEquals(screen, vm.state.value.ui.screen)
                vm.dispatch(AssistIntent.MotionChanged(motion)); runCurrent()
                assertEquals(Screen.Widget, vm.state.value.ui.screen)
                assertNull(vm.state.value.requestedScreen)
            }
            val vm = model(); readyRecommendation(vm)
            vm.dispatch(AssistIntent.RefuelConfirmed(RefuelEvent("receipt"))); runCurrent()
            assertNotNull(vm.state.value.ui.celebration)
            vm.dispatch(AssistIntent.MotionChanged(motion)); runCurrent()
            assertNull(vm.state.value.ui.celebration)
            assertEquals(Screen.Widget, vm.state.value.ui.screen)
            vm.dispatch(AssistIntent.MotionChanged(Motion.Parked))
            vm.dispatch(AssistIntent.RefuelConfirmed(RefuelEvent("receipt"))); runCurrent()
            assertNull(vm.state.value.ui.celebration)
            assertNotEquals(Screen.Refuel, vm.state.value.ui.screen)
        }
    }

    @Test fun departureClearsPendingAndExposureAndIgnoresItsCallback() = runTest {
        val vm = model(); readyRecommendation(vm)
        vm.dispatch(AssistIntent.SelectStation("station")); runCurrent()
        val old = vm.state.value.pending!!
        vm.dispatch(AssistIntent.MotionChanged(Motion.Moving)); runCurrent()
        assertNull(vm.state.value.pending)
        assertNull(vm.state.value.persisted.lastRecommendationKey)
        vm.dispatch(AssistIntent.NavigationCompleted(WaypointResult.Succeeded(old.requestId, old.stationId)))
        runCurrent()
        assertEquals(Screen.Widget, vm.state.value.ui.screen)
        assertNull(vm.state.value.ui.routeStation)
    }

    @Test fun newRefuelOutranksAnalysisSettingsAndPendingNavigation() = runTest {
        for (screen in listOf(Screen.Analysis, Screen.Settings, Screen.Adding)) {
            val vm = model(); readyRecommendation(vm)
            if (screen == Screen.Adding) vm.dispatch(AssistIntent.SelectStation("station"))
            else vm.dispatch(AssistIntent.Show(screen))
            vm.dispatch(AssistIntent.RefuelConfirmed(RefuelEvent("receipt"))); runCurrent()
            assertEquals(Screen.Refuel, vm.state.value.ui.screen)
            assertNotNull(vm.state.value.ui.celebration)
            vm.dispatch(AssistIntent.Show(Screen.Settings)); runCurrent()
            assertEquals(Screen.Refuel, vm.state.value.ui.screen)
        }
    }

    @Test fun newWarningOutranksUserScreenButRepeatedWarningDoesNot() = runTest {
        val vm = model(); readyRecommendation(vm)
        vm.dispatch(AssistIntent.Show(Screen.Analysis))
        vm.dispatch(AssistIntent.PresentationChanged(recommendation())); runCurrent()
        assertEquals(Screen.Analysis, vm.state.value.ui.screen)
        vm.dispatch(AssistIntent.PresentationChanged(recommendation(key = "new-warning"))); runCurrent()
        assertEquals(Screen.Recommendation, vm.state.value.ui.screen)
        vm.dispatch(AssistIntent.Show(Screen.Settings))
        vm.dispatch(AssistIntent.PresentationChanged(recommendation(key = "new-warning", screen = Screen.Passing)))
        runCurrent()
        assertEquals(Screen.Passing, vm.state.value.ui.screen)
    }

    @Test fun monitoringIsCtaFreeNoActionHiddenAndUnavailableExplicitWithoutNumbers() = runTest {
        val vm = model(); runCurrent()
        vm.dispatch(AssistIntent.MotionChanged(Motion.Parked))
        vm.dispatch(AssistIntent.PresentationChanged(recommendation().copy(targetScreen = Screen.Widget, recommendationKey = null)))
        runCurrent()
        assertTrue(vm.state.value.overlayVisible)
        assertEquals(Screen.Widget, vm.state.value.ui.screen)
        assertFalse(vm.state.value.ui.detailsAvailable)
        vm.dispatch(AssistIntent.SelectStation("station")); runCurrent()
        assertNull(vm.state.value.pending)
        vm.dispatch(AssistIntent.PresentationChanged(recommendation().copy(targetScreen = null, recommendationKey = null)))
        runCurrent()
        assertFalse(vm.state.value.overlayVisible)
        for (availability in listOf(AssistAvailability.UNAVAILABLE, AssistAvailability.STALE, AssistAvailability.UNSUPPORTED)) {
            vm.dispatch(AssistIntent.PresentationChanged(AssistPresentation(FuelRouteUi(), Screen.Widget, null, availability)))
            runCurrent()
            assertTrue(vm.state.value.overlayVisible)
            assertFalse(vm.state.value.ui.statusMessage.isNullOrBlank())
            assertNull(vm.state.value.presentation.route.rangeKm)
            assertNull(vm.state.value.presentation.route.recommended)
        }
    }

    @Test fun restoreCompletesBeforeLiveFlowCollectionAndQueuedRefuelProcessing() = runTest {
        val restored = CompletableDeferred<AssistStoredState>()
        var collected = false
        val store = object : AssistStateStore {
            override suspend fun read() = restored.await()
            override suspend fun write(state: AssistStoredState) = Unit
        }
        val vm = AssistViewModel(store, liveIntents = flow {
            collected = true
            emit(AssistIntent.MotionChanged(Motion.Parked))
            emit(AssistIntent.RefuelConfirmed(RefuelEvent("receipt")))
        }, scope = backgroundScope, wallClockMs = { 1000L })
        vm.dispatch(AssistIntent.MotionChanged(Motion.Parked))
        vm.dispatch(AssistIntent.RefuelConfirmed(RefuelEvent("receipt")))
        runCurrent()
        assertFalse(collected)
        assertFalse(vm.state.value.ready)
        restored.complete(AssistStoredState(processedRefuels = mapOf("receipt" to 900L), characterEnabled = false))
        runCurrent()
        assertTrue(collected)
        assertTrue(vm.state.value.ready)
        assertNull(vm.state.value.ui.celebration)
        assertNull(vm.state.value.ui.refuel)
        assertFalse(vm.state.value.ui.characterEnabled)
    }

    @Test fun pureReducerHonorsMotionRefuelPendingResultRecommendationAndFallbackPriority() {
        var state = AssistViewState(ready = true, ui = FuelUiState(motion = Motion.Parked))
        fun send(intent: AssistIntent) { state = reduceAssist(state, intent, 1000L, 25L, "request").state }
        send(AssistIntent.PresentationChanged(recommendation()))
        assertEquals(Screen.Recommendation, state.ui.screen)
        send(AssistIntent.SelectStation("station"))
        send(AssistIntent.PresentationChanged(recommendation(screen = Screen.Passing)))
        assertEquals(Screen.Adding, state.ui.screen)
        send(AssistIntent.NavigationCompleted(WaypointResult.Succeeded("request", "station")))
        assertEquals(Screen.Added, state.ui.screen)
        send(AssistIntent.RefuelConfirmed(RefuelEvent("receipt")))
        assertEquals(Screen.Refuel, state.ui.screen)
        send(AssistIntent.MotionChanged(Motion.Unknown))
        assertEquals(Screen.Widget, state.ui.screen)
        assertNull(state.ui.celebration)
        send(AssistIntent.MotionChanged(Motion.Parked))
        send(AssistIntent.Continue)
        send(AssistIntent.PresentationChanged(AssistPresentation(FuelRouteUi(), null, null, AssistAvailability.READY)))
        assertFalse(state.overlayVisible)
    }

    @Test fun motionPreferencesCancelAnimationWithoutHidingCharacterOrConfirmation() = runTest {
        val vm = model(); readyRecommendation(vm)
        vm.dispatch(AssistIntent.RefuelConfirmed(RefuelEvent("one"))); runCurrent()
        vm.dispatch(AssistIntent.ReduceMotionChanged(true)); runCurrent()
        assertNull(vm.state.value.ui.celebration)
        assertEquals(Screen.Refuel, vm.state.value.ui.screen)
        assertTrue(characterPresentation(vm.state.value.ui).visible)
        assertTrue(vm.state.value.persisted.reduceMotion)
        vm.dispatch(AssistIntent.CharacterChanged(false)); runCurrent()
        assertFalse(vm.state.value.persisted.characterEnabled)
    }

    @Test fun duplicateWhileMovingIsConsumedWithoutDelayedDance() = runTest {
        val vm = model(); runCurrent()
        vm.dispatch(AssistIntent.RefuelConfirmed(RefuelEvent("receipt")))
        vm.dispatch(AssistIntent.MotionChanged(Motion.Parked))
        vm.dispatch(AssistIntent.RefuelConfirmed(RefuelEvent("receipt"))); runCurrent()
        assertNull(vm.state.value.ui.celebration)
        assertNotEquals(Screen.Refuel, vm.state.value.ui.screen)
        assertEquals(1000L, vm.state.value.persisted.processedRefuels["receipt"])
    }

    private fun TestScope.model(): AssistViewModel {
        var sequence = 0
        return AssistViewModel(MemoryStore(), scope = backgroundScope, wallClockMs = { 1000L },
            monotonicClockMs = { 25L }, requestId = { "request-${++sequence}" })
    }

    private fun TestScope.readyRecommendation(vm: AssistViewModel) {
        runCurrent()
        vm.dispatch(AssistIntent.RouteChanged("route", "destination"))
        vm.dispatch(AssistIntent.MotionChanged(Motion.Parked))
        vm.dispatch(AssistIntent.PresentationChanged(recommendation()))
        runCurrent()
    }

    private fun recommendation(key: String = "key", screen: Screen = Screen.Recommendation) =
        AssistPresentation(FuelRouteUi(recommended = FuelStation("station", "Station", null, null)), screen, key, AssistAvailability.READY)

    private class MemoryStore : AssistStateStore {
        private var value = AssistStoredState()
        override suspend fun read() = value
        override suspend fun write(state: AssistStoredState) { value = state }
    }
}
