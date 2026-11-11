package dev.fuelpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fuelpilot.feature.assist.*
import dev.fuelpilot.ui.*
import kotlin.math.roundToInt

private val Honey = Color(0xFFF0C46E)
private val Graphite = Color(0xFF171512)

@Composable
fun AppShell(
    state: AssistViewState, onIntent: (AssistIntent) -> Unit, modifier: Modifier = Modifier,
    monitoring: MonitoringUiState? = null, onMonitoringChange: (Boolean) -> Unit = {},
    onRetry: () -> Unit = {}, onOpenPermissionSettings: () -> Unit = {}, onDemoLaunch: (() -> Unit)? = null,
) {
    var page by rememberSaveable { mutableStateOf("home") }
    BackHandler(page != "home") { page = "home" }
    Column(modifier.fillMaxSize().background(Color(0xFF0C0B09))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((key,label) in listOf("home" to "홈", "assist" to "주유 안내", "settings" to "설정")) {
                TextButton(onClick = { page = key }, modifier = Modifier.heightIn(min = 76.dp).testTag("nav_$key")) {
                    Text(label, color = if (page == key) Honey else Color(0xFFB9AE9D), fontSize = 22.sp)
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (page) {
                "settings" -> FuelPilotSettings(state, onIntent, { page = "home" })
                "assist" -> if (state.overlayVisible) FuelPilotUi(state, { if (it == AssistIntent.Continue) page = "home"; onIntent(it) })
                    else HomeContent(state, monitoring, onMonitoringChange, onRetry, onOpenPermissionSettings, onDemoLaunch)
                else -> HomeContent(state, monitoring, onMonitoringChange, onRetry, onOpenPermissionSettings, onDemoLaunch)
            }
        }
    }
}

@Composable private fun HomeContent(state: AssistViewState, monitoring: MonitoringUiState?, onMonitoringChange: (Boolean) -> Unit,
    onRetry: () -> Unit, onOpenPermissionSettings: () -> Unit, onDemoLaunch: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("FUEL PILOT", color = Honey, fontSize = 30.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PodongBadge(Modifier.size(80.dp), still = state.ui.reduceMotion || !state.ui.characterEnabled)
            Text("뽀동이와 함께 주유 시점을 확인해요", color = Color.White, fontSize = 25.sp)
        }
        Surface(color = Graphite) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(state.ui.statusMessage ?: when {
                    !state.ready -> "저장된 설정을 불러오고 있어요."
                    state.presentation.availability == AssistAvailability.UNSUPPORTED -> "실제 차량 SDK가 연결되지 않아 주유 안내를 제공할 수 없어요."
                    state.presentation.availability == AssistAvailability.STALE -> "최신 차량 정보가 필요해요."
                    state.presentation.availability != AssistAvailability.READY -> "차량 정보를 확인할 수 없어요."
                    !state.overlayVisible -> "이번 경로는 중간 주유 없이 갈 수 있어요."
                    else -> "현재 경로의 주유 정보를 확인해 주세요."
                }, color = Color.White, fontSize = 25.sp)
                val route = state.presentation.route
                Text("주행 가능 거리: ${route.rangeKm?.let { "약 ${it.roundToInt()}km" } ?: "확인 중"}", color = Honey, fontSize = 24.sp)
                Text("목적지까지: ${route.destinationKm?.let { "약 ${it.roundToInt()}km" } ?: "확인 중"}", color = Color(0xFFB9AE9D), fontSize = 22.sp)
                Text("설정한 주유 판단 여유: ${state.persisted.minimumReserveKm}km", color = Color(0xFFB9AE9D), fontSize = 22.sp)
            }
        }
        monitoring?.let { monitor ->
            Text(monitor.message, color = Honey, fontSize = 22.sp)
            Button(onClick = { onMonitoringChange(!monitor.running) }, enabled = monitor.running || state.ui.motion != Motion.Moving, modifier = Modifier.heightIn(min = 64.dp).testTag("monitoring_toggle")) {
                Text(if (monitor.running) "모니터링 중지" else "모니터링 시작", fontSize = 22.sp)
            }
            if (monitor.requested && !monitor.running) TextButton(onClick = { onMonitoringChange(false) }) { Text("모니터링 예약 해제") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 64.dp).testTag("retry")) { Text("다시 확인", fontSize = 22.sp) }
            OutlinedButton(onClick = onOpenPermissionSettings, modifier = Modifier.heightIn(min = 64.dp)) { Text("권한 설정", fontSize = 22.sp) }
        }
        onDemoLaunch?.let { launch -> OutlinedButton(onClick = launch, modifier = Modifier.heightIn(min = 64.dp).testTag("demo_launch")) { Text("시연 모드 · 예시 데이터", fontSize = 22.sp) } }
        Text("정차 중 설정을 바꿀 수 있어요. 차량 정보가 없으면 거리를 임의로 표시하지 않아요.", color = Color(0xFFB9AE9D), fontSize = 20.sp)
    }
}
