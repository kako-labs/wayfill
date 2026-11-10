package dev.fuelpilot.ui

enum class Motion { Parked, Moving, Unknown }
enum class Screen { Recommendation, Widget, Passing, Adding, Added, Refuel, Analysis, Settings, LongDrive }
enum class Variant { Normal, Small, Full, Recovery }
data class RefuelEvent(val id: String, val liters: Double? = null, val rangeKm: Double? = null, val isFull: Boolean = false, val wasLow: Boolean = false)
data class Celebration(val id: String, val variant: Variant, val startedAtMs: Long = 0L)
data class FuelUiState(
    val motion: Motion = Motion.Unknown, val screen: Screen = Screen.Widget,
    val characterEnabled: Boolean = true, val reduceMotion: Boolean = false,
    val celebration: Celebration? = null, val refuel: RefuelEvent? = null,
    val seenIds: Set<String> = emptySet(), val pendingStation: String? = null,
    val routeStation: String? = null, val error: String? = null,
)
sealed interface FuelUiEvent {
    data class MotionChanged(val value: Motion) : FuelUiEvent
    data class RefuelConfirmed(val value: RefuelEvent) : FuelUiEvent
    data class CelebrationFinished(val id: String) : FuelUiEvent
    data class CharacterChanged(val enabled: Boolean) : FuelUiEvent
    data class SelectStation(val id: String) : FuelUiEvent
    data class RouteAdded(val id: String) : FuelUiEvent
    data class RouteFailed(val id: String) : FuelUiEvent
    data class Show(val screen: Screen) : FuelUiEvent
    data object Continue : FuelUiEvent
}
fun refuelVariant(event: RefuelEvent): Variant = when {
    event.isFull && event.wasLow -> Variant.Recovery
    event.isFull -> Variant.Full
    event.liters?.let { it.isFinite() && it > 0 && it <= 10 } == true -> Variant.Small
    else -> Variant.Normal
}
enum class Pose { Peek, Wave, Cheek, Wiggle, Tuck, Sparkle }
data class CharacterPresentation(val visible: Boolean, val playful: Boolean)
/** Presence is permanent while a message is shown. Preference controls playful motion only. */
fun characterPresentation(state: FuelUiState) = CharacterPresentation(true, state.motion == Motion.Parked && state.characterEnabled && !state.reduceMotion)
fun poseSequence(variant: Variant): List<Pair<Pose, Long>> = when (variant) {
    Variant.Small -> listOf(Pose.Peek to 150L, Pose.Wave to 550L, Pose.Tuck to 300L)
    Variant.Full, Variant.Recovery -> listOf(Pose.Peek to 360L, Pose.Wave to 480L, Pose.Cheek to 480L, Pose.Wiggle to 600L, Pose.Tuck to 360L, Pose.Sparkle to 120L)
    Variant.Normal -> listOf(Pose.Peek to 300L, Pose.Wave to 400L, Pose.Cheek to 400L, Pose.Wiggle to 500L, Pose.Tuck to 300L, Pose.Sparkle to 100L)
}
fun celebrationPose(celebration: Celebration, nowMs: Long): Pose? {
    val elapsed = nowMs - celebration.startedAtMs
    if (elapsed < 0) return null
    var end = 0L
    for ((pose, duration) in poseSequence(celebration.variant)) {
        end += duration
        if (elapsed < end) return pose
    }
    return null
}
fun reduceFuelUi(state: FuelUiState, event: FuelUiEvent, nowMs: Long = System.nanoTime() / 1_000_000L): FuelUiState = when (event) {
    is FuelUiEvent.MotionChanged -> state.copy(
        motion = event.value,
        celebration = state.celebration.takeIf { event.value == Motion.Parked },
        screen = if (event.value != Motion.Parked && state.screen in listOf(Screen.Refuel, Screen.Recommendation, Screen.LongDrive, Screen.Settings, Screen.Analysis)) Screen.Widget else state.screen,
    )
    is FuelUiEvent.RefuelConfirmed -> {
        val e = event.value
        if (e.id.isBlank() || e.id in state.seenIds) state else {
            val clean = e.copy(liters = e.liters?.takeIf { it.isFinite() && it > 0 }, rangeKm = e.rangeKm?.takeIf { it.isFinite() && it >= 0 })
            state.copy(refuel = clean, seenIds = state.seenIds + e.id,
                routeStation = null, pendingStation = null,
                screen = if (state.motion == Motion.Parked) Screen.Refuel else Screen.Widget,
                celebration = if (state.motion == Motion.Parked && state.characterEnabled && !state.reduceMotion) Celebration(e.id, refuelVariant(clean), nowMs) else null,
            )
        }
    }
    is FuelUiEvent.CelebrationFinished -> if (state.celebration?.id == event.id) state.copy(celebration = null) else state
    is FuelUiEvent.CharacterChanged -> state.copy(characterEnabled = event.enabled, celebration = state.celebration.takeIf { event.enabled })
    is FuelUiEvent.SelectStation -> if (event.id.isBlank() || state.pendingStation != null) state else state.copy(pendingStation = event.id, screen = Screen.Adding, error = null)
    is FuelUiEvent.RouteAdded -> if (state.pendingStation == event.id) state.copy(pendingStation = null, routeStation = event.id, screen = Screen.Added, error = null) else state
    is FuelUiEvent.RouteFailed -> if (state.pendingStation == event.id) state.copy(pendingStation = null, screen = Screen.Recommendation, error = "경유지를 추가하지 못했어요. 다시 시도해 주세요.") else state
    is FuelUiEvent.Show -> state.copy(screen = event.screen, celebration = null)
    FuelUiEvent.Continue -> state.copy(screen = Screen.Widget, celebration = null)
}
