package dev.fuelpilot

import dev.fuelpilot.feature.assist.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** One process session shared by the app and its foreground service. */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistRuntime(private val dependencies: AppDependencies, parent: CoroutineScope) {
    private val runtimeJob = SupervisorJob(parent.coroutineContext[Job])
    private val runtimeScope = CoroutineScope(parent.coroutineContext + runtimeJob)
    private val visible = MutableStateFlow(false)
    private val monitoring = MutableStateFlow(false)
    val monitoringActive = monitoring.asStateFlow()
    private data class Session(val assist: AssistViewModel, val job: Job)
    private fun createSession(): Session {
        val job = SupervisorJob(runtimeJob)
        val scope = CoroutineScope(runtimeScope.coroutineContext + job)
        val coordinator = dependencies.coordinator()
        val inputs = combine(visible, monitoring) { ui, service -> ui || service }.distinctUntilChanged()
            .flatMapLatest { active -> if (active) coordinator.liveIntents() else flowOf(AssistIntent.PauseMonitoring) }
        val assist = AssistViewModel(dependencies.store, inputs, scope = scope)
        coordinator.connectNavigation(assist, scope)
        return Session(assist, job)
    }
    private val session = MutableStateFlow(createSession())
    val state = session.flatMapLatest { it.assist.state }
        .stateIn(runtimeScope, SharingStarted.Eagerly, AssistViewState())
    fun setUiVisible(value: Boolean) { visible.value = value }
    fun setMonitoring(value: Boolean) { monitoring.value = value }
    fun dispatch(intent: AssistIntent) = session.value.assist.dispatch(intent)
    fun retry() {
        session.value.job.cancel()
        session.value = createSession()
    }
    fun close() { runtimeJob.cancel() }
}
