package dev.fuelpilot.feature.assist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fuelpilot.ui.FuelUiState
import dev.fuelpilot.ui.FuelRouteUi
import dev.fuelpilot.ui.Screen
import dev.fuelpilot.ui.Motion
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** One serialized owner of UI state and navigation requests; the host handles effects. */
class AssistViewModel(
    private val store: AssistStateStore,
    liveIntents: Flow<AssistIntent> = emptyFlow(),
    scope: CoroutineScope? = null,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val monotonicClockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val requestId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private sealed interface Message {
        data class Input(val intent: AssistIntent) : Message
        data class Saved(val value: AssistStoredState) : Message
        data object StorageFailed : Message
    }
    private val messages = Channel<Message>(Channel.UNLIMITED)
    private val outgoing = Channel<AssistEffect>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(AssistViewState())
    val state = mutableState.asStateFlow()

    /** Collect once in the host. Effects are queued, never replayed to a new collector. */
    val effects: Flow<AssistEffect> = outgoing.receiveAsFlow().filter { effect ->
        when (effect) {
            is AssistEffect.AddWaypoint -> mutableState.value.pending?.let {
                it.requestId == effect.requestId && it.stationId == effect.stationId
            } == true
        }
    }

    init {
        (scope ?: viewModelScope).launch {
            val restored = try { store.read().pruned(wallClockMs()) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    mutableState.value = storageUnavailable(mutableState.value)
                    return@launch
                }
            var logical = AssistViewState(
                ready = true,
                persisted = restored,
                ui = FuelUiState(
                    characterEnabled = restored.characterEnabled,
                    reduceMotion = restored.reduceMotion,
                    seenIds = restored.processedRefuels.keys,
                    statusMessage = "차량과 경로 정보를 확인 중이에요.",
                ),
            )
            var durable = restored
            mutableState.value = logical
            // One ordered writer acknowledges snapshots; the intent actor never waits on disk.
            val writes = Channel<AssistStoredState>(Channel.UNLIMITED)
            val writer = launch {
                for (snapshot in writes) {
                    try {
                        store.write(snapshot)
                        messages.send(Message.Saved(snapshot))
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) {
                        messages.send(Message.StorageFailed)
                        break
                    }
                }
            }
            // Subscription itself is behind restoration, not merely the event handlers.
            val liveCollection = launch { liveIntents.collect { messages.send(Message.Input(it)) } }
            var deferredEffect: AssistEffect.AddWaypoint? = null
            try {
                for (message in messages) {
                    when (message) {
                        is Message.Input -> {
                            val intent = message.intent
                            val transition = reduceAssist(
                                logical, intent, wallClockMs(), monotonicClockMs(),
                                if (intent is AssistIntent.SelectStation) requestId() else "",
                            )
                            if (transition.state.persisted != logical.persisted) writes.send(transition.state.persisted)
                            logical = transition.state
                            transition.effects.forEach { effect ->
                                when (effect) { is AssistEffect.AddWaypoint -> deferredEffect = effect }
                            }
                        }
                        is Message.Saved -> durable = message.value
                        Message.StorageFailed -> {
                            mutableState.value = storageUnavailable(logical.copy(persisted = durable))
                            break
                        }
                    }
                    // Safety transitions are immediate. Positive exposure waits for its own durable ID.
                    mutableState.value = visibleState(logical, durable)
                    deferredEffect?.let { effect ->
                        val pending = logical.pending
                        if (pending?.requestId != effect.requestId || pending.stationId != effect.stationId) {
                            deferredEffect = null
                        } else if (mutableState.value.pending == pending) {
                            outgoing.send(effect)
                            deferredEffect = null
                        }
                    }
                }
            } finally {
                liveCollection.cancel()
                writes.close()
                writer.cancel()
            }
        }
    }

    fun dispatch(intent: AssistIntent) {
        check(messages.trySend(Message.Input(intent)).isSuccess) { "Assist intent channel is closed" }
    }

    private fun visibleState(logical: AssistViewState, durable: AssistStoredState): AssistViewState {
        val refuelUnsaved = logical.ui.refuel?.id?.let { it !in durable.processedRefuels } == true
        val policyUnsaved = logical.persisted.minimumReserveKm != durable.minimumReserveKm ||
            logical.persisted.fuelPreference != durable.fuelPreference
        val exposureUnsaved = logical.activeRecommendationKey?.let { it !in durable.exposedRecommendationKeys } == true
        val positiveExposureUnsaved =
            (logical.ui.motion == Motion.Parked && (refuelUnsaved || policyUnsaved)) ||
                (logical.ui.motion != Motion.Unknown && exposureUnsaved)
        if (positiveExposureUnsaved) {
            return logical.copy(
                pending = null, persisted = durable,
                presentation = AssistPresentation(FuelRouteUi(), Screen.Widget, null, AssistAvailability.UNAVAILABLE),
                ui = logical.ui.copy(screen = Screen.Widget, refuel = null, celebration = null,
                    pendingStation = null, detailsAvailable = false, statusMessage = "안내 기록을 저장 중이에요."),
            )
        }
        return logical.copy(persisted = durable)
    }

    private fun storageUnavailable(previous: AssistViewState) = previous.copy(
        ready = false, pending = null, navigationResult = null, requestedScreen = null,
        activeRecommendationKey = null, assistVisible = true,
        presentation = AssistPresentation(FuelRouteUi(), Screen.Widget, null, AssistAvailability.UNAVAILABLE),
        ui = previous.ui.copy(screen = Screen.Widget, refuel = null, celebration = null,
            pendingStation = null, routeStation = null, error = null, detailsAvailable = false,
            statusMessage = "안내 기록을 저장하거나 읽을 수 없어요. 앱을 다시 실행해 주세요."),
    )
}
