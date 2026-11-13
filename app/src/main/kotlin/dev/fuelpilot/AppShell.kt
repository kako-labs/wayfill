package dev.fuelpilot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.fuelpilot.feature.assist.*
import dev.fuelpilot.ui.*
import kotlin.math.roundToInt

private val Honey = FuelPilotColors.Gold
private val Graphite = FuelPilotColors.Surface
private val WarmLine = FuelPilotColors.Line
private val Muted = FuelPilotColors.Muted

@Composable
fun AppShell(
    state: AssistViewState, onIntent: (AssistIntent) -> Unit, modifier: Modifier = Modifier,
    monitoring: MonitoringUiState? = null, onMonitoringChange: (Boolean) -> Unit = {},
    onRetry: () -> Unit = {}, onOpenPermissionSettings: () -> Unit = {}, onDemoLaunch: (() -> Unit)? = null,
) {
    FuelPilotTheme {
    var page by rememberSaveable { mutableStateOf("home") }
    LaunchedEffect(state.ui.motion) {
        if (state.ui.motion != Motion.Parked) page = "home"
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
    val smallRecommendation=useCompactCompanion(maxWidth.value,maxHeight.value) && state.assistVisible &&
        state.presentation.availability==AssistAvailability.READY && state.presentation.targetScreen in setOf(Screen.Recommendation,Screen.Passing)
    if (state.ui.motion != Motion.Parked || smallRecommendation) {
        // Driving/unknown states use only the compact companion beside navigation: no parked navigation, scrolling,
        // settings, monitoring controls, or home actions may leak into this branch.
        Box(Modifier.fillMaxSize().background(FuelPilotColors.Backdrop)) {
            FuelPilotUi(state = state, onIntent = onIntent, modifier = Modifier.fillMaxSize())
        }
        return@BoxWithConstraints
    }
    BackHandler(page != "home") { page = "home" }
    Box(Modifier.fillMaxSize().background(FuelPilotColors.Backdrop)) {
      SunsetBackdrop(Modifier.matchParentSize())
      Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=28.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("WAYFILL",Modifier.weight(1f),color=FuelPilotColors.Text,fontSize=28.sp,fontWeight=FontWeight.ExtraBold)
            Surface(color=Color(0x88624743),shape=RoundedCornerShape(99.dp),border=BorderStroke(1.dp,WarmLine)) {
                Text("정차 중",Modifier.padding(horizontal=24.dp,vertical=12.dp),color=FuelPilotColors.Text,fontSize=20.sp)
            }
        }
        Box(Modifier.weight(1f)) {
            when (page) {
                "settings" -> FuelPilotSettings(state, onIntent, { page = "home" })
                "records" -> FuelHistoryScreen(state,onIntent)
                "assist" -> if (state.assistVisible) FuelPilotUi(state, { if (it == AssistIntent.Continue) page = "home"; onIntent(it) })
                    else HomeContent(state, monitoring, onMonitoringChange, onRetry, onOpenPermissionSettings, onDemoLaunch, onIntent)
                else -> HomeContent(state, monitoring, onMonitoringChange, onRetry, onOpenPermissionSettings, onDemoLaunch, onIntent)
            }
        }
        Surface(Modifier.padding(horizontal=24.dp,vertical=12.dp),color=Color(0x99624743),shape=RoundedCornerShape(99.dp),border=BorderStroke(1.dp,WarmLine)) {
            Row(Modifier.fillMaxWidth().padding(6.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                for((key,label) in listOf("home" to "홈","assist" to "주유 안내","records" to "기록","settings" to "설정")) {
                    TextButton(onClick={page=key},modifier=Modifier.weight(1f).heightIn(min=76.dp).testTag("nav_$key"),
                        colors=ButtonDefaults.textButtonColors(containerColor=if(page==key)Color(0x668F633E)else Color.Transparent),shape=RoundedCornerShape(99.dp)) {
                        Text(label,color=if(page==key)Honey else Muted,fontSize=22.sp,fontWeight=if(page==key)FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
      }

    }
    }
    }
}

@Composable private fun HomeContent(state: AssistViewState, monitoring: MonitoringUiState?, onMonitoringChange: (Boolean) -> Unit,
    onRetry: () -> Unit, onOpenPermissionSettings: () -> Unit, onDemoLaunch: (() -> Unit)?, onIntent: (AssistIntent) -> Unit) {
    val status = state.ui.statusMessage ?: when {
                    !state.ready -> "저장된 설정을 불러오고 있어요."
                    state.presentation.availability == AssistAvailability.UNSUPPORTED -> "실제 차량 SDK가 연결되지 않아 주유 안내를 제공할 수 없어요."
                    state.presentation.availability == AssistAvailability.STALE -> "최신 차량 정보가 필요해요."
                    state.presentation.availability != AssistAvailability.READY -> "차량 정보를 확인할 수 없어요."
                    !state.assistVisible -> "이번 경로는 중간 주유 없이 갈 수 있어요."
                    else -> "현재 경로의 주유 정보를 확인해 주세요."
                }
    val readyLabel = when {
        !state.ready -> "준비 중"
        state.presentation.availability == AssistAvailability.READY -> if (state.ui.motion == Motion.Parked) "정차 · 분석 가능" else "주행 중"
        state.presentation.blockedBy.onlyStationsMissing() -> "주유소 연결 필요"
        state.presentation.availability == AssistAvailability.UNSUPPORTED -> "연결 필요"
        else -> "정보 확인 필요"
    }
    val stateTag = when {
        !state.ready -> "loading"
        state.presentation.availability == AssistAvailability.UNSUPPORTED -> "unsupported"
        state.presentation.availability == AssistAvailability.STALE -> "stale"
        state.presentation.availability == AssistAvailability.UNAVAILABLE -> "unavailable"
        state.ui.error != null -> "error"
        state.ui.refuel != null -> "success"
        else -> "ready"
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if(state.assistVisible && (state.ui.screen in setOf(Screen.Refuel,Screen.Adding,Screen.Added) || state.ui.error!=null)) {
            FuelPilotUi(state,onIntent,Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        // In a split window the current recommendation is the home, not a dashboard above the useful action.
        if (useCompactCompanion(maxWidth.value, maxHeight.value) && state.assistVisible &&
            state.presentation.availability == AssistAvailability.READY &&
            state.presentation.targetScreen in setOf(Screen.Recommendation, Screen.Passing)) {
            FuelPilotUi(state, onIntent, Modifier.fillMaxSize())
            return@BoxWithConstraints
        }
        val wide = maxWidth >= 900.dp
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 1500.dp).fillMaxWidth().fillMaxHeight()
                .verticalScroll(rememberScrollState()).padding(if (wide) 32.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!(wide && state.presentation.availability == AssistAvailability.READY && state.ui.screen in setOf(Screen.Recommendation,Screen.Passing))) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PodongBadge(Modifier.size(if (wide) 112.dp else 88.dp), still = state.ui.reduceMotion || !state.ui.characterEnabled)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("뽀동이와 함께 확인해요", color = Color.White, fontSize = if (wide) 32.sp else 27.sp, lineHeight = if (wide) 40.sp else 35.sp, fontWeight = FontWeight.Bold)
                    Text("경로와 차량 연료를 비교해 필요한 순간만 알려드려요.", color = Muted, fontSize = if (wide) 22.sp else 18.sp, lineHeight = if (wide) 30.sp else 25.sp)
                }
                Surface(color = Color(0xFF705044), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, WarmLine)) {
                    Text(readyLabel, Modifier.padding(horizontal = 16.dp, vertical = 11.dp), color = Honey, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (wide && state.presentation.availability == AssistAvailability.READY &&
                state.ui.screen in setOf(Screen.Recommendation, Screen.Passing)) {
                FuelHomeOverview(state, onIntent)
                HomeControls(state, monitoring, onMonitoringChange, onRetry, onOpenPermissionSettings, onDemoLaunch)
            } else if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                    HomeStatusCard(status, stateTag, state, Modifier.weight(0.95f))
                    Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        HomeMetricsCard(state)
                        HomeControls(state, monitoring, onMonitoringChange, onRetry, onOpenPermissionSettings, onDemoLaunch)
                    }
                }
            } else {
                HomeStatusCard(status, stateTag, state)
                HomeMetricsCard(state)
                HomeControls(state, monitoring, onMonitoringChange, onRetry, onOpenPermissionSettings, onDemoLaunch)
            }
            Text("주행을 시작하면 간단한 주유 안내로 전환돼요.", color = Muted, fontSize = 17.sp, lineHeight = 24.sp)
        }
    }
}

@Composable private fun HomeStatusCard(status: String, stateTag: String, state: AssistViewState, modifier: Modifier = Modifier) {
    Surface(
        modifier.testTag("home_state_$stateTag"),
        color = Graphite,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (stateTag in setOf("unsupported", "error", "unavailable")) Color(0xFF735044) else FuelPilotColors.Line),
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(30.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            val blocked = state.presentation.blockedBy
            Text(
                when (stateTag) {
                    "loading" -> "준비 중"
                    "stale" -> "최신 정보 확인 중"
                    "unsupported", "unavailable" -> when {
                        blocked.onlyStationsMissing() -> "주유소 미연결"
                        blocked.isNotEmpty() -> "${blocked.labels()} 확인 필요"
                        stateTag == "unsupported" -> "차량 연결 필요"
                        else -> "정보 없음"
                    }
                    "error" -> "확인 필요"
                    "success" -> "완료"
                    else -> "현재 상태"
                },
                color = if (stateTag in setOf("unsupported", "error", "unavailable")) Color(0xFFFF9A88) else Honey,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(status, color = Color.White, fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
            state.ui.error?.let { Text(it, color = Color(0xFFFF9A88), fontSize = 22.sp, lineHeight = 31.sp) }
            if (stateTag in setOf("loading", "stale")) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Honey, trackColor = WarmLine)
            }
            HorizontalDivider(color = WarmLine)
            Text(
                if (stateTag == "unsupported") "지원 SDK나 권한이 준비되면 실제 차량 정보로 다시 확인해요. 예시 수치로 대신하지 않습니다."
                else "주행 중에는 이 화면을 숨기고 내비게이션 곁의 간단한 안내만 보여요.",
                color = Muted,
                fontSize = 20.sp,
                lineHeight = 29.sp,
            )
        }
    }
}

@Composable private fun HomeMetricsCard(state: AssistViewState) {
    val route = state.presentation.route
    Surface(color = FuelPilotColors.Surface, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, WarmLine)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("경로 요약", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeMetric("주행 가능", route.rangeKm?.let { "약 ${it.roundToInt()}km" } ?: "확인 중", Modifier.weight(1f), emphasize = true)
                HomeMetric("목적지", route.destinationKm?.let { "약 ${it.roundToInt()}km" } ?: "확인 중", Modifier.weight(1f))
                HomeMetric("판단 여유", "${state.persisted.minimumReserveKm}km", Modifier.weight(1f))
            }
        }
    }
}

@Composable private fun HomeControls(
    state: AssistViewState,
    monitoring: MonitoringUiState?,
    onMonitoringChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onDemoLaunch: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        monitoring?.let { monitor ->
            Surface(color = FuelPilotColors.Surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, WarmLine)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("백그라운드 확인", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(monitor.message, color = Muted, fontSize = 20.sp, lineHeight = 28.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (monitor.requested && !monitor.running) {
                            TextButton(onClick = { onMonitoringChange(false) }, modifier = Modifier.weight(1f).heightIn(min = 76.dp)) { Text("예약 해제", fontSize = 19.sp) }
                        } else Spacer(Modifier.weight(1f))
                        Button(onClick = { onMonitoringChange(!monitor.running) }, enabled = monitor.running || state.ui.motion != Motion.Moving, modifier = Modifier.weight(1.25f).heightIn(min = 76.dp).testTag("monitoring_toggle"), shape = RoundedCornerShape(16.dp)) {
                            Text(if (monitor.running) "모니터링 중지" else "모니터링 시작", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(colors=ButtonDefaults.outlinedButtonColors(contentColor=Honey), onClick = onRetry, modifier = Modifier.weight(1f).heightIn(min = 76.dp).testTag("retry"), shape = RoundedCornerShape(16.dp)) { Text("차량 정보 다시 확인", fontSize = 20.sp) }
            OutlinedButton(colors=ButtonDefaults.outlinedButtonColors(contentColor=Honey), onClick = onOpenPermissionSettings, modifier = Modifier.weight(1f).heightIn(min = 76.dp).testTag("permission_settings"), shape = RoundedCornerShape(16.dp)) { Text("권한 설정", fontSize = 20.sp) }
        }
        onDemoLaunch?.let { launch ->
            OutlinedButton(colors=ButtonDefaults.outlinedButtonColors(contentColor=Honey), onClick = launch, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).testTag("demo_launch"), shape = RoundedCornerShape(16.dp)) {
                Text("시연 모드 · 예시 데이터", fontSize = 20.sp)
            }
        }
    }
}

@Composable private fun HomeMetric(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Surface(modifier, color = Color(0xFF503D39), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, WarmLine)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = Muted, fontSize = 16.sp)
            Text(value, color = if (emphasize) Honey else Color.White, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
        }
    }
}
