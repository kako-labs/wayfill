package dev.fuelpilot

import dev.fuelpilot.feature.assist.AssistViewState

data class RecommendationSpeech(
    val recommendationKey: String,
    val text: String,
    val localeTag: String = "ko-KR",
)

sealed interface RecommendationSpeechResult {
    data object Spoken : RecommendationSpeechResult
    data object Suppressed : RecommendationSpeechResult
    data object Unsupported : RecommendationSpeechResult
    data object Failed : RecommendationSpeechResult
}

/**
 * PLEOS/Gleo owners implement this port with the host's audio-focus and driver-distraction policy.
 * The production default is deliberately silent and unsupported.
 */
interface RecommendationSpeechPort {
    suspend fun speak(value: RecommendationSpeech): RecommendationSpeechResult
    fun stop()
}

object UnsupportedRecommendationSpeechPort : RecommendationSpeechPort {
    override suspend fun speak(value: RecommendationSpeech) = RecommendationSpeechResult.Unsupported
    override fun stop() = Unit
}

fun recommendationSpeech(advice: BackgroundAdvice) = RecommendationSpeech(
    recommendationKey = advice.key,
    text = listOf(advice.routeFacts, "${advice.stationName}에서 주유가 필요해요. 안내할까요?")
        .filter { it.isNotBlank() }
        .joinToString(". "),
)

data class RecommendationVoiceCommand(
    val kind: RecommendationActionKind,
    val recommendationKey: String,
    val stationId: String,
)

/** Gleo and notification buttons must pass the same immutable recommendation identity check. */
fun recommendationVoiceCommand(
    state: AssistViewState,
    kind: RecommendationActionKind,
    recommendationKey: String,
    stationId: String,
): RecommendationVoiceCommand? = if (
    recommendationActionDecision(state, recommendationKey, stationId) == RecommendationActionDecision.DISPATCH
) RecommendationVoiceCommand(kind, recommendationKey, stationId) else null
