package dev.fuelpilot

import dev.fuelpilot.data.energy.*
import dev.fuelpilot.data.location.*
import dev.fuelpilot.data.navigation.*
import dev.fuelpilot.data.poi.*
import dev.fuelpilot.feature.assist.*
import dev.fuelpilot.model.*
import dev.fuelpilot.ui.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefuelRefreshTest {
    @Test fun verifiedRefuelImmediatelyRefreshesEnergyAndSuppressesConsecutiveDuplicateReads()=runTest {
        var range=150.0;var reads=0
        val events=MutableSharedFlow<RefuelEvent>(extraBufferCapacity=8)
        val energy=object:EnergyRepository{override suspend fun readEnergy():EnergyResult {reads++;return EnergyResult.Available(range)}}
        val location=object:LocationRepository{override suspend fun readLocation()=LocationResult.Available(VehicleLocation(37.5,127.0,5.0))}
        val nav=object:NavigationRepository {
            override suspend fun readRoute()=RouteResult.Available(ActiveRoute("route","destination",250.0))
            override suspend fun addWaypoint(request:WaypointRequest)=WaypointResult.Unsupported(request.requestId,request.stationId)
        }
        val stations=object:StationRepository{override suspend fun findStations()=StationResult.Available(listOf(
            FuelStationCandidate("a","주유소",15.0,15.0,10,2,OpenStatus.OPEN,true,true)))}
        val store=object:AssistStateStore {
            override suspend fun read()=AssistStoredState()
            override suspend fun write(state:AssistStoredState){}
        }
        val vehicle=object:VehicleEvents{override val motion=flowOf(Motion.Parked);override val refuels=events}
        val coordinator=AppCoordinator(AppDependencies(store,energy,location,stations,nav,vehicle,refreshIntervalMs=60000))
        val seen=mutableListOf<AssistIntent>()
        val job=backgroundScope.launch{coordinator.liveIntents().collect{seen+=it}}
        runCurrent()
        assertEquals(150.0,(seen.last{it is AssistIntent.PresentationChanged} as AssistIntent.PresentationChanged).value.route.rangeKm!!,0.0)
        range=550.0
        events.emit(RefuelEvent("verified",40.0,550.0,true));runCurrent()
        val fresh=seen.last{it is AssistIntent.PresentationChanged} as AssistIntent.PresentationChanged
        assertEquals(550.0,fresh.value.route.rangeKm!!,0.0)
        assertTrue(seen.indexOfFirst{it is AssistIntent.RefuelConfirmed}<seen.lastIndex)
        val after=reads
        events.emit(RefuelEvent("verified",40.0,550.0,true));runCurrent()
        assertEquals(after,reads)
        job.cancel()
    }
}
