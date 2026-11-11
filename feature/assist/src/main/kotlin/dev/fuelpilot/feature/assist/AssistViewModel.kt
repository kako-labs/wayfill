package dev.fuelpilot.feature.assist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.fuelpilot.ui.FuelUiState
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
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
    private val intents = Channel<AssistIntent>(Channel.UNLIMITED)
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
            val restored = store.read().pruned(wallClockMs())
            mutableState.value = AssistViewState(
                ready = true,
                persisted = restored,
                ui = FuelUiState(
                    characterEnabled = restored.characterEnabled,
                    reduceMotion = restored.reduceMotion,
                    seenIds = restored.processedRefuels.keys,
                    statusMessage = "차량과 경로 정보를 확인 중이에요.",
                ),
            )
            // Subscription itself is behind restoration, not merely the event handlers.
            launch { liveIntents.collect { intents.send(it) } }
            for (intent in intents) {
                val previous = mutableState.value
                val transition = reduceAssist(
                    previous, intent, wallClockMs(), monotonicClockMs(),
                    if (intent is AssistIntent.SelectStation) requestId() else "",
                )
                // Durable deduplication/exposure must succeed before publishing a celebration.
                if (transition.state.persisted != previous.persisted) store.write(transition.state.persisted)
                mutableState.value = transition.state
                transition.effects.forEach { outgoing.send(it) }
            }
        }
    }

    fun dispatch(intent: AssistIntent) {
        check(intents.trySend(intent).isSuccess) { "Assist intent channel is closed" }
    }
}
