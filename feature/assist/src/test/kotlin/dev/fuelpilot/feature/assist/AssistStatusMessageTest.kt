package dev.fuelpilot.feature.assist

import dev.fuelpilot.ui.FuelRouteUi
import dev.fuelpilot.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistStatusMessageTest {
    private fun presentation(availability: AssistAvailability, vararg blocked: AssistSignal) =
        AssistPresentation(FuelRouteUi(), Screen.Widget, null, availability, blocked.toSet())

    @Test fun readyHasNoStatusMessage() {
        assertNull(presentation(AssistAvailability.READY).statusMessage())
    }

    @Test fun stationsOnlyNamesTheStationSourceNotTheVehicle() {
        assertEquals("차량과 경로는 확인했어요. 주유소 정보가 연결되면 안내할게요.",
            presentation(AssistAvailability.UNAVAILABLE, AssistSignal.STATIONS).statusMessage())
        assertEquals("차량과 경로는 확인했어요. 주유소 정보가 연결되면 안내할게요.",
            presentation(AssistAvailability.UNSUPPORTED, AssistSignal.STATIONS).statusMessage())
        assertEquals("주유소 정보를 새로 확인하고 있어요.",
            presentation(AssistAvailability.STALE, AssistSignal.STATIONS).statusMessage())
    }

    @Test fun multipleMissingInputsAreListedInAStableOrder() {
        assertEquals("차량 연료·주유소 정보를 확인할 수 없어요.",
            presentation(AssistAvailability.UNSUPPORTED, AssistSignal.STATIONS, AssistSignal.VEHICLE_ENERGY).statusMessage())
        assertEquals("현재 위치·내비 경로 정보를 새로 확인하고 있어요.",
            presentation(AssistAvailability.STALE, AssistSignal.ROUTE, AssistSignal.LOCATION).statusMessage())
    }

    @Test fun unknownCauseKeepsTheGenericMessages() {
        assertEquals("차량과 경로 정보를 확인할 수 없어요.", presentation(AssistAvailability.UNAVAILABLE).statusMessage())
        assertEquals("이 환경에서는 주유 안내 정보를 지원하지 않아요.", presentation(AssistAvailability.UNSUPPORTED).statusMessage())
        assertEquals("최신 차량과 경로 정보를 확인 중이에요.", presentation(AssistAvailability.STALE).statusMessage())
    }
}
