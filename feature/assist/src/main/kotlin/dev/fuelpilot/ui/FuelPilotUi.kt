package dev.fuelpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import dev.fuelpilot.data.navigation.WaypointResult
import dev.fuelpilot.feature.assist.AssistAvailability
import dev.fuelpilot.feature.assist.AssistIntent
import dev.fuelpilot.feature.assist.AssistViewState

private val Graphite = FuelPilotColors.Surface
private val Gold = FuelPilotColors.Gold
private val GoldText = FuelPilotColors.Gold
private val White = FuelPilotColors.Text
private val Muted = FuelPilotColors.Muted
private val Coral = Color(0xFFFF9A88)
private val Line = FuelPilotColors.Line
private val Raised = FuelPilotColors.Surface
private val Inset = Color(0xFF211D17)
private val CompanionText = TextStyle(lineBreak = LineBreak.Simple.copy(wordBreak = LineBreak.WordBreak.Phrase))
// Android can still split Korean syllables under an English system locale. Keep Hangul words together;
// accessibility receives the original string, without the invisible display-only word joiners.
private val HangulBoundary = Regex("(?<=[가-힣])(?=[가-힣])")
private fun companionText(value: String) = value.replace(HangulBoundary, "\u2060")

/** Distances include the host's real station-access route, not straight-line distance. */
data class FuelStation(val id: String, val name: String, val distanceKm: Double?, val extraMinutes: Int?, val remainingAtStationKm: Double? = null)
data class FuelRouteUi(
    val rangeKm: Double? = null,
    val destinationKm: Double? = null,
    val recommended: FuelStation? = null,
    val nextAfterPassing: FuelStation? = null,
    val stations: List<FuelStation> = emptyList(),
    /** Why this station is better, from the existing app's ranking logic. */
    val recommendationReason: String? = null,
)
private fun valid(n: Double?) = n?.takeIf { it.isFinite() && it >= 0 }
private fun number(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else String.format(Locale.ROOT, "%.1f", n)
private fun km(n: Double?) = valid(n)?.let { "${number(it)}km" } ?: "확인 중"
private fun shortfall(range: Double?, distance: Double?): Double? {
    val r = valid(range) ?: return null; val d = valid(distance) ?: return null
    return (d - r).takeIf { it > 0 }
}

internal enum class AdaptiveMode { Glanceable, Detailed }
internal enum class AdaptiveStatus { Loading, Stale, Unsupported, Unavailable, Error, Pending, Success, Refuel, Recommendation, Monitoring }
internal data class AdaptiveUiModel(
    val mode: AdaptiveMode,
    val status: AdaptiveStatus,
    /** Non-null only when the host contract can accept this exact recommendation now. */
    val actionableStationId: String?,
    val actionableRecommendationKey: String?,
)

/** Pure UI policy used by both Compose and state-focused tests. */
internal fun adaptiveUiModel(state: AssistViewState): AdaptiveUiModel {
    val ui = state.ui
    val detailed = ui.motion == Motion.Parked
    val presentation = state.presentation
    val stationId = presentation.route.recommended?.id?.takeIf { it.isNotBlank() }
    val recommendationKey = presentation.recommendationKey?.takeIf { it.isNotBlank() }
    val exposed = recommendationKey != null &&
        (state.activeRecommendationKey == recommendationKey || recommendationKey !in state.persisted.exposedRecommendationKeys)
    val recommendation = state.ready && presentation.availability == AssistAvailability.READY &&
        presentation.targetScreen in setOf(Screen.Recommendation, Screen.Passing) &&
        stationId != null && recommendationKey != null && exposed
    val eligibleSurface = when (ui.motion) {
        Motion.Parked -> ui.screen in setOf(Screen.Recommendation, Screen.Passing, Screen.Analysis)
        Motion.Moving -> true
        Motion.Unknown -> false
    }
    val actionable = stationId.takeIf {
        eligibleSurface && recommendation && state.pending == null && ui.refuel == null && ui.celebration == null &&
            state.navigationResult !is WaypointResult.Succeeded && ui.routeStation == null
    }
    val status = when {
        !state.ready -> AdaptiveStatus.Loading
        ui.error != null -> AdaptiveStatus.Error
        ui.refuel != null -> AdaptiveStatus.Refuel
        state.pending != null || ui.screen == Screen.Adding -> AdaptiveStatus.Pending
        state.navigationResult is WaypointResult.Succeeded || ui.routeStation != null || ui.screen == Screen.Added -> AdaptiveStatus.Success
        presentation.availability == AssistAvailability.STALE -> AdaptiveStatus.Stale
        presentation.availability == AssistAvailability.UNSUPPORTED -> AdaptiveStatus.Unsupported
        presentation.availability == AssistAvailability.UNAVAILABLE -> AdaptiveStatus.Unavailable
        recommendation -> AdaptiveStatus.Recommendation
        else -> AdaptiveStatus.Monitoring
    }
    return AdaptiveUiModel(
        if (detailed) AdaptiveMode.Detailed else AdaptiveMode.Glanceable,
        status,
        actionable,
        recommendationKey.takeIf { actionable != null },
    )
}

/**
 * The host collects AssistViewModel effects and returns request-ID-tagged navigation results.
 * NoAction leaves only the host-provided surface. The assist UI never renders its own map.
 */
@Composable
fun FuelPilotUi(
    state: AssistViewState,
    onIntent: (AssistIntent) -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable BoxScope.() -> Unit = {},
) {
    if (state.assistVisible) {
        FuelPilotContent(state, onIntent, modifier, mapContent)
    } else {
        Box(modifier.fillMaxSize(), content = mapContent)
    }
}

@Composable
private fun FuelPilotContent(
    viewState: AssistViewState,
    onIntent: (AssistIntent) -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable BoxScope.() -> Unit = {},
) {
    val state = viewState.ui
    val route = viewState.presentation.route
    val model = adaptiveUiModel(viewState)
    val onEvent = onIntent
    val parked = state.motion == Motion.Parked
    val playfulCharacter = characterPresentation(state).playful
    val station = route.recommended
    val select: (FuelStation) -> Unit = {
        if (state.pendingStation == null) {
            onEvent(AssistIntent.SelectStation(it.id, model.actionableRecommendationKey))
        }
    }
    CompositionLocalProvider(LocalPodongStill provides (!parked || !state.characterEnabled || state.reduceMotion)) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Gold, onPrimary = Color(0xFF241703), surface = Graphite, onSurface = White, background = FuelPilotColors.Background)) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            SunsetBackdrop(Modifier.matchParentSize())
            mapContent()
            // Moving/unknown is a non-scrolling companion beside the native navigation surface.
            val compactWindow = useCompactCompanion(maxWidth.value, maxHeight.value)
            if(parked && !compactWindow && model.status in setOf(AdaptiveStatus.Refuel,AdaptiveStatus.Pending,AdaptiveStatus.Success,AdaptiveStatus.Error)) {
                SunsetStatusScreen(viewState,model,onEvent)
                return@BoxWithConstraints
            }
            if(parked && !compactWindow && state.screen in setOf(Screen.Recommendation,Screen.Passing)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp)
                    .testTag(if(state.screen==Screen.Passing)"parked_passing" else "parked_recommendation"),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    FuelHomeOverview(viewState,onEvent,actionPrefix="parked")
                    TextButton(onClick={onEvent(AssistIntent.Show(Screen.Analysis))},modifier=Modifier.heightIn(min=76.dp).testTag("parked_details")) {
                        Text("추천 근거 자세히 보기 ›",color=GoldText,fontSize=22.sp)
                    }
                }
                return@BoxWithConstraints
            }
            val compactParked = compactWindow && state.screen !in setOf(Screen.Analysis, Screen.Settings, Screen.Refuel)
            if (model.mode == AdaptiveMode.Glanceable || compactParked) {
                RedesignedCompanion(state, route, model, maxWidth >= 900.dp, compactWindow, maxHeight < 480.dp,
                    viewState.presentation.targetScreen == Screen.Passing, onEvent)
            } else {
            val screen = state.screen
            if (screen == Screen.Analysis) {
                Analysis(state, route, maxWidth < 840.dp, model.actionableStationId, select, onEvent)
            } else if (screen == Screen.Settings) {
                Settings(state, onEvent, viewState.persisted.minimumReserveKm, true)
            } else {
                val wideParked = maxWidth >= 900.dp
                Column(
                    Modifier.align(if (wideParked) Alignment.Center else Alignment.BottomStart).padding(if (maxWidth < 520.dp) 12.dp else 24.dp)
                        .widthIn(max = if (wideParked) 1460.dp else 680.dp).fillMaxWidth()
                        .heightIn(max = (maxHeight - 48.dp).coerceAtLeast(1.dp)).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    when (screen) {
                        Screen.Recommendation -> {
                            val reason = if (shortfall(route.rangeKm, route.destinationKm) != null) "목적지까지 가려면 중간에 주유해야 해요" else "현재 경로의 주유 정보를 확인해 주세요"
                            VehicleLayout(
                                wide = wideParked,
                                intro = { PodongIntro(reason, calm = !playfulCharacter || viewState.recommendationStartedAtMs==null, startedAtMs=viewState.recommendationStartedAtMs) },
                            ) {
                                Panel(showIdentity = false, modifier = Modifier.testTag("parked_recommendation")) {
                                    StatusRow("경로 분석 완료", "정차 중")
                                    RouteMetrics(route.rangeKm, route.destinationKm)
                                    StationCard(station)
                                    state.error?.let { Text(it, color = Coral, fontSize = 21.sp, lineHeight = 29.sp) }
                                    Actions(station, model.actionableStationId, select, onEvent)
                                    TextButton(
                                        onClick = { onEvent(AssistIntent.Show(Screen.Analysis)) },
                                        modifier = Modifier.heightIn(min = 76.dp).testTag("parked_details"),
                                    ) { Text("추천 근거 자세히 보기 ›", color = Muted, fontSize = 21.sp) }
                                }
                            }
                        }
                        Screen.Passing -> {
                            val reason = if (shortfall(route.rangeKm, route.nextAfterPassing?.distanceKm) != null) "이곳을 지나면 다음 주유소까지\n연료가 부족할 수 있어요" else "앞 주유소와 다음 주유 기회를\n확인해 주세요"
                            VehicleLayout(wideParked, { PodongIntro(reason, calm = !playfulCharacter || viewState.recommendationStartedAtMs==null, startedAtMs=viewState.recommendationStartedAtMs) }) {
                                Panel(modifier = Modifier.testTag("parked_passing"), showIdentity = false) {
                                    StatusRow("지나치기 전 확인", km(station?.distanceKm), alert = true)
                                    Copy(reason, 30)
                                    RouteMetrics(route.rangeKm, route.nextAfterPassing?.distanceKm)
                                    StationCard(station)
                                    Actions(station, model.actionableStationId, select, onEvent)
                                    TextButton(
                                        onClick = { onEvent(AssistIntent.Show(Screen.Analysis)) },
                                        modifier = Modifier.heightIn(min = 76.dp).testTag("parked_details"),
                                    ) { Text("다음 주유소까지 거리 보기 ›", color = Muted, fontSize = 21.sp) }
                                }
                            }
                        }
                        Screen.Adding, Screen.Added -> VehicleLayout(
                            wideParked,
                            { PodongIntro(if (screen == Screen.Adding) "내비게이션 응답을 확인하고 있어요" else "주유소로 차분히 안내할게요", calm = true) },
                        ) {
                            Panel(modifier = Modifier.testTag(if (screen == Screen.Adding) "navigation_pending" else "navigation_success"), showIdentity = false) {
                                Text(if (screen == Screen.Adding) "내비게이션에 전달했어요" else "✓ 내비게이션 경로가 바뀌었어요", color = GoldText, fontSize = 21.sp)
                                Copy(if (screen == Screen.Adding) "경로 추가를 확인하고 있어요" else "주유소로 안내할게요", 32)
                                Text(if (screen == Screen.Adding) "경로 재탐색 결과를 기다리고 있어요." else "도착하면 P에 놓고 시동을 끈 뒤 주유하세요. 화면을 닫아도 차량이 확인한 주유 완료 이벤트는 다음 실행에서 이어서 보여줘요.", color = Muted, fontSize = 22.sp, lineHeight = 31.sp)
                                if (screen == Screen.Adding) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold, trackColor = Line)
                                Primary("내비게이션으로 돌아가기", enabled = screen == Screen.Added) { onEvent(AssistIntent.Continue) }
                            }
                        }
                        Screen.Refuel -> {
                            val active = state.celebration?.takeIf { playfulCharacter }
                            VehicleLayout(
                                wideParked,
                                intro = {
                                    if (active != null) CelebrationHeader(active, onEvent)
                                    else PodongIntro(when (state.refuel?.let(::refuelVariant)) { Variant.Small -> "냠, 간식!"; Variant.Full -> "빵빵해요!"; Variant.Recovery -> "살았다…!"; else -> "든든하게 채웠어요" }, calm = !playfulCharacter, clip = "calm")
                                },
                            ) {
                                Panel(showIdentity = false, modifier = Modifier.testTag("refuel_success")) {
                                    Text("✓ 차량에서 주유 완료 확인", color = GoldText, fontSize = 21.sp)
                                    Copy("다시 출발할 준비가 됐어요", 34)
                                    val range = valid(state.refuel?.rangeKm)
                                    if (range != null) { Text("${number(range)} km", color = GoldText, fontSize = 58.sp, fontWeight = FontWeight.Bold); Text("지금 연료로 갈 수 있는 예상 거리예요.", color = Muted, fontSize = 22.sp) }
                                    else Text("차량의 주행 가능 거리를 확인 중이에요.", color = Muted, fontSize = 22.sp)
                                    valid(state.refuel?.liters)?.takeIf { it > 0 }?.let { Text("오늘은 ${number(it)}L 먹었어요!", color = White, fontSize = 24.sp) }
                                    Text("주유 중 화면을 켜 둘 필요는 없어요. 이 인사는 확인된 주유 완료 이벤트 뒤에만 한 번 보여요.", color = Muted, fontSize = 20.sp, lineHeight = 29.sp)
                                    Primary("내비게이션으로 돌아가기") { onEvent(AssistIntent.Continue) }
                                }
                            }
                        }
                        Screen.LongDrive -> VehicleLayout(wideParked, { PodongIntro("여행 준비 완료!", calm = !playfulCharacter) }) {
                            Panel(showIdentity = false, modifier = Modifier.testTag("long_drive_status")) {
                                val enough = valid(route.rangeKm)?.let { r -> valid(route.destinationKm)?.let { d -> r >= d } } == true
                                StatusRow("경로 분석 완료", "출발 전")
                                Copy(if (enough) "이번 경로는 주유 없이 갈 수 있을 것으로 예상돼요" else "출발 전 연료와 경로를 확인해 주세요", 32)
                                RouteMetrics(route.rangeKm, route.destinationKm)
                                Primary("내비게이션으로 돌아가기") { onEvent(AssistIntent.Continue) }
                            }
                        }
                        else -> ParkedDashboard(state, route, model, wideParked, onEvent)
                    }
                }
            }
            }
        }
    }
}

}

@Composable private fun VehicleLayout(
    wide: Boolean,
    intro: @Composable () -> Unit,
    body: @Composable () -> Unit,
) {
    if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(0.78f)) { intro() }
            Box(Modifier.weight(1.22f)) { body() }
        }
    } else {
        Column(Modifier.fillMaxWidth()) { intro(); body() }
    }
}

@Composable private fun Panel(modifier: Modifier = Modifier, showIdentity: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        color = Raised,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFF4A4032)),
        tonalElevation = 2.dp,
        shadowElevation = 12.dp,
    ) {
        Column(Modifier.padding(30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (showIdentity) MessageIdentity()
            content()
        }
    }
}

@Composable private fun ParkedDashboard(
    state: FuelUiState,
    route: FuelRouteUi,
    model: AdaptiveUiModel,
    wide: Boolean,
    onEvent: (AssistIntent) -> Unit,
) {
    val station = route.recommended
    val intro = when (model.status) {
        AdaptiveStatus.Loading, AdaptiveStatus.Stale -> "잠시만요, 확인하고 있어요"
        AdaptiveStatus.Unsupported -> "여기서는 안내가 어려워요"
        AdaptiveStatus.Unavailable -> "차량 정보를 확인해 주세요"
        AdaptiveStatus.Error -> "다시 한번 확인해 볼까요?"
        AdaptiveStatus.Success -> "안내 준비가 끝났어요"
        AdaptiveStatus.Refuel -> "주유 완료를 확인했어요"
        else -> "정차 중에 자세히 볼 수 있어요"
    }
    VehicleLayout(wide, { PodongIntro(intro, calm = !characterPresentation(state).playful) }) {
        Panel(showIdentity = false, modifier = Modifier.testTag("state_${model.status.name.lowercase(Locale.ROOT)}")) {
            StatusRow(
                label = when (model.status) {
                    AdaptiveStatus.Loading -> "불러오는 중"
                    AdaptiveStatus.Stale -> "최신 정보 확인 중"
                    AdaptiveStatus.Unsupported -> "지원되지 않음"
                    AdaptiveStatus.Unavailable -> "정보 없음"
                    AdaptiveStatus.Error -> "확인 필요"
                    AdaptiveStatus.Success -> "안내 준비 완료"
                    AdaptiveStatus.Refuel -> "주유 확인 완료"
                    else -> "정차 모드"
                },
                state = "P · 상세 기능 사용 가능",
                alert = model.status in setOf(AdaptiveStatus.Error, AdaptiveStatus.Unsupported, AdaptiveStatus.Unavailable),
            )
            state.statusMessage?.let { Text(it, color = if (model.status == AdaptiveStatus.Unsupported) Coral else White, fontSize = 24.sp, lineHeight = 33.sp) }
            state.error?.let { Text(it, color = Coral, fontSize = 22.sp, lineHeight = 31.sp) }
            if (model.status in setOf(AdaptiveStatus.Loading, AdaptiveStatus.Stale)) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Gold, trackColor = Line)
            }
            if (model.status == AdaptiveStatus.Unsupported) {
                Text("지원 SDK 또는 권한이 준비되기 전에는 실제 안내를 시작하지 않아요. 임의의 거리나 주유소를 표시하지 않습니다.", color = Muted, fontSize = 20.sp, lineHeight = 29.sp)
            }
            if (station != null && model.status == AdaptiveStatus.Recommendation) {
                StationCard(station)
                route.recommendationReason?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Muted, fontSize = 21.sp, lineHeight = 30.sp)
                }
                Actions(station, model.actionableStationId, {
                    onEvent(AssistIntent.SelectStation(it.id, model.actionableRecommendationKey))
                }, onEvent)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.detailsAvailable) {
                    OutlinedButton(
                        onClick = { onEvent(AssistIntent.Show(Screen.Analysis)) },
                        modifier = Modifier.weight(1f).heightIn(min = 76.dp).testTag("parked_details"),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("추천 근거", fontSize = 21.sp) }
                }
                OutlinedButton(
                    onClick = { onEvent(AssistIntent.Show(Screen.Settings)) },
                    modifier = Modifier.weight(1f).heightIn(min = 76.dp).testTag("parked_settings"),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("설정", fontSize = 21.sp) }
            }
        }
    }
}

@Composable private fun Copy(text: String, size: Int) { Text(text, color = White, fontSize = size.sp, lineHeight = (size * 1.4).sp, fontWeight = FontWeight.Bold) }
@Composable private fun StatusRow(label: String, state: String, alert: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(color = if (alert) Coral else Gold, shape = RoundedCornerShape(99.dp), modifier = Modifier.size(8.dp)) {}
            Text(label, color = if (alert) Coral else GoldText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Surface(color = Color(0xFF242019), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Line)) {
            Text(state, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = Muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
@Composable private fun RouteMetrics(range: Double?, destination: Double?) {
    val shortage = shortfall(range, destination)
    Surface(color = Color(0xFF100F0D), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Line)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val remaining = shortage?.let(::km) ?: run {
                val r = valid(range); val d = valid(destination)
                if (r != null && d != null) km((r - d).coerceAtLeast(0.0)) else "확인 중"
            }
            if (maxWidth < 420.dp) {
                Column(Modifier.fillMaxWidth()) {
                    Metric("주행 가능", km(range), Modifier.fillMaxWidth())
                    HorizontalDivider(color = Line)
                    Metric("목적지", km(destination), Modifier.fillMaxWidth())
                    HorizontalDivider(color = Line)
                    Metric(if (shortage != null) "부족 거리" else "남는 거리", remaining, Modifier.fillMaxWidth(), shortage != null)
                }
            } else Row(Modifier.fillMaxWidth()) {
                Metric("주행 가능", km(range), Modifier.weight(1f))
                Metric("목적지", km(destination), Modifier.weight(1f))
                Metric(if (shortage != null) "부족 거리" else "남는 거리", remaining, Modifier.weight(1f), shortage != null)
            }
        }
    }
}
@Composable private fun Metric(label: String, value: String, modifier: Modifier, risk: Boolean = false) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Color(0xFF9E9384), fontSize = 16.sp)
        Text(value, color = if (risk) Coral else White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable private fun StationCard(station: FuelStation?) {
    Surface(color = Inset, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF40382D))) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("추천 주유소 · 경로 기준", color = GoldText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            StationCopy(station)
        }
    }
}
@Composable private fun Primary(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF241703))) { Text(text, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
}
@Composable private fun StationCopy(station: FuelStation?) {
    Copy(station?.name ?: "주유소 정보를 확인 중이에요", 32)
    if (station != null) Text(buildString { append("${km(station.distanceKm)} 앞"); station.extraMinutes?.takeIf { it >= 0 }?.let { append(" · 경로에서 ${it}분 추가") } }, color = Muted, fontSize = 23.sp, lineHeight = 31.sp)
}
@Composable private fun Actions(station: FuelStation?, actionableStationId: String?, select: (FuelStation) -> Unit, onEvent: (AssistIntent) -> Unit) {
    val actionable = station?.takeIf { it.id == actionableStationId }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 440.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { actionable?.let(select) }, enabled = actionable != null, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).testTag("parked_guide_now"), shape = RoundedCornerShape(16.dp)) { Text("여기서 주유하기", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                Button(onClick = { onEvent(AssistIntent.Continue) }, modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp).testTag("parked_later"), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2620), contentColor = White)) { Text("계속 가기", fontSize = 21.sp) }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { actionable?.let(select) }, enabled = actionable != null, modifier = Modifier.weight(1.65f).heightIn(min = 88.dp).testTag("parked_guide_now"), shape = RoundedCornerShape(16.dp)) { Text("여기서 주유하기", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            Button(onClick = { onEvent(AssistIntent.Continue) }, modifier = Modifier.weight(1f).heightIn(min = 88.dp).testTag("parked_later"), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2620), contentColor = White)) { Text("계속 가기", fontSize = 21.sp) }
        }
    }
}
@Composable private fun MessageIdentity() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PodongSprite(modifier = Modifier.width(64.dp).height(52.dp))
        Text("뽀동이가 알려드려요", color = Muted, fontSize = 16.sp)
    }
}
@Composable private fun PodongIntro(text: String, calm: Boolean = false, clip: String = "recommend", startedAtMs:Long?=null) {
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("podong_message")) {
        val characterSize = when {
            calm -> 96.dp
            maxWidth < 420.dp -> 132.dp
            else -> 190.dp
        }
        Row(Modifier.fillMaxWidth().heightIn(min = if (calm) 112.dp else 150.dp), verticalAlignment = Alignment.Bottom) {
            Surface(Modifier.weight(1f).padding(bottom = 20.dp), color = Color(0xFFFFF4DF), shape = RoundedCornerShape(20.dp)) {
                Text(text, Modifier.padding(20.dp), color = Color(0xFF2E1E06), fontSize = 27.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
            }
            PodongSprite(Modifier.size(characterSize), name = if (calm) "calm" else clip,startedAtMs=startedAtMs)
        }
    }
}
@Composable private fun CelebrationHeader(celebration: Celebration, onEvent: (AssistIntent) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        val message = when (celebration.variant) { Variant.Small -> "냠, 간식!"; Variant.Full -> "빵빵해요!"; Variant.Recovery -> "살았다…!"; else -> "든든하게 채웠어요" }
        Surface(Modifier.weight(1f).padding(bottom = 20.dp), shape = RoundedCornerShape(18.dp), color = Color(0xFFFFF4DF)) { Text(message, Modifier.padding(18.dp), color = Color(0xFF2E1E06), fontSize = 23.sp, fontWeight = FontWeight.Bold) }
        PodongSprite(Modifier.width(190.dp).height(190.dp), clipName(celebration.variant), celebration.startedAtMs, celebrationDuration(celebration.variant)) {
            onEvent(AssistIntent.CelebrationFinished(celebration.id))
        }
    }
}
@Composable private fun Analysis(state: FuelUiState, route: FuelRouteUi, narrow: Boolean, actionableStationId: String?, select: (FuelStation) -> Unit, onEvent: (AssistIntent) -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0x5534272D)).verticalScroll(rememberScrollState())) {
        ScreenHeader(if (state.refuel != null) "주유 후 연료 확인" else "주유가 필요한 이유", onEvent)
        if (state.refuel != null) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Copy("현재 연료로 갈 수 있는 거리를\n다시 확인해 주세요.", 28)
                Text("주행 가능 거리 ${km(state.refuel.rangeKm)}", color = GoldText, fontSize = 30.sp)
                Text("주유 전 분석은 재사용하지 않아요. 호스트 앱에서 최신 연료와 경로로 다시 판단해 주세요.", color = Muted, fontSize = 20.sp)
            }
        } else {
            val reason: @Composable () -> Unit = {
                val next = route.nextAfterPassing
                val target = if (state.motion != Motion.Parked && next != null) next.distanceKm else route.destinationKm
                val shortage = shortfall(route.rangeKm, target)
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Copy(if (shortage != null) if (next != null && state.motion != Motion.Parked) "다음 주유소까지\n연료가 부족할 수 있어요" else "목적지까지 가려면\n중간 주유가 필요해요" else "현재 연료와 경로를\n확인해 주세요", 30)
                    Text("지금 연료로 갈 수 있는 거리\n${km(route.rangeKm)}", color = GoldText, fontSize = 24.sp)
                    Text("${if (next != null && state.motion != Motion.Parked) "다음 주유소까지" else "목적지까지"}\n${km(target)}", color = White, fontSize = 24.sp)
                    shortage?.let { Text("약 ${number(it)}km 부족해요.", color = Coral, fontSize = 24.sp) }
                    Text("차량의 주행 가능 거리와 경로 거리로 비교한 예상이에요. 실제 주행 상황에 따라 달라져요.", color = Muted, fontSize = 18.sp)
                    route.recommendationReason?.let { Text(it, color = GoldText, fontSize = 20.sp, lineHeight = 29.sp) }
                }
            }
            val stations: @Composable () -> Unit = {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Copy("어디서 주유하면 좋을까요?", 25)
                    route.stations.forEach { s ->
                        HorizontalDivider(color = Line)
                        if (s.id == route.recommended?.id) Text("추천", color = GoldText, fontSize = 18.sp)
                        StationCopy(s)
                        valid(s.remainingAtStationKm)?.let { Text("이 주유소에 도착하면 약 ${number(it)}km를 더 갈 수 있어요.", color = Muted, fontSize = 18.sp) }
                        shortfall(route.rangeKm, s.distanceKm)?.let { Text("도달하기까지 약 ${number(it)}km 부족해요.", color = Coral, fontSize = 18.sp) }
                    }
                    route.recommended?.takeIf { it.id == actionableStationId }?.let { Primary("${it.name}에서 주유하기") { select(it) } }
                }
            }
            if (narrow) { reason(); stations() } else Row { Box(Modifier.weight(1f)) { reason() }; Box(Modifier.weight(1f).background(Graphite)) { stations() } }
        }
    }
}
@Composable private fun ScreenHeader(title: String, onEvent: (AssistIntent) -> Unit, backLabel: String = "안내로 돌아가기 ›") {
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PodongSprite(modifier = Modifier.width(48.dp).height(44.dp))
            Text(title, color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        TextButton(onClick = { onEvent(AssistIntent.Continue) }, modifier = Modifier.heightIn(min = 76.dp)) { Text(backLabel, color = GoldText, fontSize = 21.sp) }
    }
    HorizontalDivider(color = Line)
}
@Composable private fun Settings(state: FuelUiState, onEvent: (AssistIntent) -> Unit, reserve: Int, editable: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("settings_screen")) {
        val wide=maxWidth>=1000.dp
        val reserveCard:@Composable ()->Unit={
            SettingsCard(featured=true) {
                Copy("주유 판단 여유거리",28)
                Text("목적지 도착 뒤 남겨둘 연료 여유를 설정해요.",color=Muted,fontSize=20.sp,lineHeight=29.sp)
                Text("${reserve} km",Modifier.padding(vertical=12.dp),color=GoldText,fontSize=58.sp,fontWeight=FontWeight.Bold)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    Button(onClick={onEvent(AssistIntent.ReserveChanged(reserve-10))},enabled=editable&&reserve>20,
                        modifier=Modifier.weight(1f).heightIn(min=76.dp),shape=RoundedCornerShape(99.dp)){Text("−10km",fontSize=24.sp)}
                    Button(onClick={onEvent(AssistIntent.ReserveChanged(reserve+10))},enabled=editable&&reserve<100,
                        modifier=Modifier.weight(1f).heightIn(min=76.dp),shape=RoundedCornerShape(99.dp)){Text("+10km",fontSize=24.sp)}
                }
                Text("20~100 km · 10 km 단위",color=Muted,fontSize=17.sp)
                Text("주유 시점을 판단하는 추정 거리 기준이에요.",color=Muted,fontSize=18.sp,lineHeight=27.sp)
                if(!editable) Text("차량의 정차 상태를 확인한 뒤 변경할 수 있어요.",color=Coral,fontSize=18.sp)
            }
        }
        val motionCard:@Composable ()->Unit={
            SettingsCard {
                Copy("정차 중 귀여운 움직임",28)
                Text("정차·주유 후에는 짧게 반응해요.\n꺼도 뽀동이는 계속 곁에 있어요.",color=Muted,fontSize=20.sp,lineHeight=29.sp)
                Row(Modifier.fillMaxWidth().heightIn(min=76.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(state.characterEnabled)"움직임 켜짐" else "움직임 꺼짐",Modifier.weight(1f),color=GoldText,fontSize=22.sp)
                    Box(Modifier.sizeIn(minWidth=76.dp,minHeight=76.dp),contentAlignment=Alignment.Center) {
                        Switch(checked=state.characterEnabled,enabled=editable,onCheckedChange={onEvent(AssistIntent.CharacterChanged(it))})
                    }
                }
                HorizontalDivider(color=Line)
                Copy("주행 중에는 차분하게",24)
                Text("출발하면 정지 모습으로 바뀌고, 현재 주유소와 필요한 행동만 보여줘요.",color=Muted,fontSize=20.sp,lineHeight=29.sp)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(if(wide)32.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("정차 중에 여유 있게",color=GoldText,fontSize=17.sp)
                    Copy("주유 안내 설정",36)
                }
                PodongBadge(Modifier.size(if(wide)140.dp else 90.dp),still=state.reduceMotion||!state.characterEnabled)
                TextButton(onClick={onEvent(AssistIntent.Continue)},modifier=Modifier.heightIn(min=76.dp)){Text("홈으로",color=GoldText,fontSize=21.sp)}
            }
            if(wide) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp),verticalAlignment=Alignment.Top) {
                Box(Modifier.weight(1f)){reserveCard()}
                Box(Modifier.weight(1f)){motionCard()}
            } else {reserveCard();motionCard()}
            Text("캐릭터 모션은 무음이에요. 시스템 알림 소리는 차량의 알림 설정을 따라요.",color=Muted,fontSize=17.sp,lineHeight=26.sp)
        }
    }
}

@Composable private fun SettingsCard(featured: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = FuelPilotColors.Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (featured) FuelPilotColors.Gold else Line),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(26.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable fun FuelPilotSettings(state: AssistViewState, onIntent: (AssistIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    FuelPilotTheme {
    CompositionLocalProvider(LocalPodongStill provides (!state.ui.characterEnabled || state.ui.reduceMotion)) {
        Box(modifier.fillMaxSize()) {
            SunsetBackdrop(Modifier.matchParentSize(),dimmed=true)
            Settings(state.ui, { if (it == AssistIntent.Continue) onBack() else onIntent(it) }, state.persisted.minimumReserveKm, state.ready && state.ui.motion == Motion.Parked)
        }
    }
    }
}

@Composable fun PodongBadge(modifier: Modifier = Modifier, still: Boolean = false) {
    CompositionLocalProvider(LocalPodongStill provides still) { PodongSprite(modifier) }
}
