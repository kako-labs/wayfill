package dev.fuelpilot

import dev.fuelpilot.feature.assist.*
import dev.fuelpilot.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistRuntimeTest {
    @Test fun backgroundAdviceRequiresVerifiedPresentationAndKnownMotion() {
        val state = AssistViewState(ready = true,
            ui = FuelUiState(motion = Motion.Moving),
            presentation = AssistPresentation(
                FuelRouteUi(recommended = FuelStation("verified","Station",3.0,1)),
                Screen.Passing,"stable-key",AssistAvailability.READY))
        assertNotNull(backgroundAdvice(state))
        assertNull(backgroundAdvice(state.copy(ui = state.ui.copy(motion = Motion.Unknown))))
        assertNull(backgroundAdvice(state.copy(presentation = state.presentation.copy(availability = AssistAvailability.STALE))))
        assertNull(backgroundAdvice(state.copy(presentation = state.presentation.copy(targetScreen = null))))
        assertNotEquals(backgroundAdvice(state)?.key,
            backgroundAdvice(state.copy(presentation = state.presentation.copy(targetScreen = Screen.Recommendation)))?.key)
        assertNull(backgroundAdvice(state.copy(pending = dev.fuelpilot.data.navigation.WaypointRequest("request","verified"))))
        assertNull(backgroundAdvice(state.copy(ui = state.ui.copy(refuel = RefuelEvent("filled")))))
    }

    @Test fun uiAndServiceShareOneLiveSubscriptionAndUserStopPausesIt() = runTest {
        var subscriptions = 0
        var active = 0
        val events = object : VehicleEvents {
            override val motion = flow { subscriptions++; active++; try { emit(Motion.Parked); awaitCancellation() } finally { active-- } }
            override val refuels = emptyFlow<RefuelEvent>()
        }
        val runtime = AssistRuntime(AppDependencies(Store(), vehicleEvents = events), backgroundScope)
        runtime.setUiVisible(true); runCurrent(); assertEquals(1, subscriptions)
        runtime.setMonitoring(true); runCurrent()
        runtime.setMonitoring(false); runCurrent()
        assertEquals(Motion.Parked, runtime.state.value.ui.motion)
        assertEquals(1, active)
        runtime.setMonitoring(true); runtime.setUiVisible(false); runCurrent()
        assertEquals(1, subscriptions); assertEquals(1, active)
        runtime.setMonitoring(false); runCurrent()
        assertEquals(0, active); assertEquals(Motion.Unknown, runtime.state.value.ui.motion)
        runtime.close()
    }

    @Test fun retryRestoresDedupButNeverReplaysCelebrationOrPendingEffects() = runTest {
        val store = Store()
        val runtime = AssistRuntime(AppDependencies(store), backgroundScope)
        runtime.setUiVisible(true); runCurrent()
        runtime.dispatch(AssistIntent.MotionChanged(Motion.Parked))
        runtime.dispatch(AssistIntent.RefuelConfirmed(RefuelEvent("receipt"))); runCurrent()
        assertNotNull(runtime.state.value.ui.celebration)
        runtime.retry(); runCurrent()
        assertNull(runtime.state.value.ui.celebration)
        runtime.dispatch(AssistIntent.MotionChanged(Motion.Parked))
        runtime.dispatch(AssistIntent.RefuelConfirmed(RefuelEvent("receipt"))); runCurrent()
        assertNull(runtime.state.value.ui.celebration)
        assertEquals(setOf("receipt"), runtime.state.value.persisted.processedRefuels.keys)
        runtime.close()
    }

    @Test fun permissionsAndBootRequireUserActionInsteadOfSilentBackgroundStart() {
        assertFalse(monitoringPermissionsSatisfied(location = false, notification = true))
        assertFalse(monitoringPermissionsSatisfied(location = true, notification = false))
        assertTrue(monitoringPermissionsSatisfied(location = true, notification = true))
        assertEquals(BootMonitoringAction.NONE, bootMonitoringAction(false))
        assertEquals(BootMonitoringAction.ASK_USER_TO_RESUME, bootMonitoringAction(true))
    }

    private class Store : AssistStateStore {
        private val saved = MutableStateFlow(AssistStoredState())
        override suspend fun read() = saved.value
        override suspend fun write(state: AssistStoredState) { saved.value = state }
        override fun observeMinimumReserveKm() = saved.map { it.minimumReserveKm }.distinctUntilChanged()
    }
}
