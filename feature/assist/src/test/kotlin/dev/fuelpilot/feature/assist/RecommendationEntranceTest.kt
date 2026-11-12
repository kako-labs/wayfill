package dev.fuelpilot.feature.assist

import dev.fuelpilot.ui.*
import org.junit.Assert.*
import org.junit.Test

class RecommendationEntranceTest {
    private fun step(state:AssistViewState,intent:AssistIntent,time:Long)=reduceAssist(state,intent,10000,time,"request").state
    private val presentation=AssistPresentation(FuelRouteUi(recommended=FuelStation("a","주유소",3.0,1)),Screen.Recommendation,"recommendation",AssistAvailability.READY)
    @Test fun sameRecommendationAndPageChangesRetainEntranceTimeRatherThanReplay() {
        val first=step(AssistViewState(ready=true,ui=FuelUiState(motion=Motion.Parked)),AssistIntent.PresentationChanged(presentation),100)
        assertEquals(100L,first.recommendationStartedAtMs)
        val repeated=step(first,AssistIntent.PresentationChanged(presentation),1000)
        assertEquals(100L,repeated.recommendationStartedAtMs)
        val settings=step(repeated,AssistIntent.Show(Screen.Settings),1200)
        assertEquals(100L,settings.recommendationStartedAtMs)
    }
    @Test fun departureClearsBroadEntranceAndStoppingCannotReplayExposedRecommendation() {
        val first=step(AssistViewState(ready=true,ui=FuelUiState(motion=Motion.Parked)),AssistIntent.PresentationChanged(presentation),100)
        val moving=step(first,AssistIntent.MotionChanged(Motion.Moving),200)
        assertNull(moving.recommendationStartedAtMs)
        val stopped=step(moving,AssistIntent.MotionChanged(Motion.Parked),300)
        assertNull(stopped.recommendationStartedAtMs)
    }
}
